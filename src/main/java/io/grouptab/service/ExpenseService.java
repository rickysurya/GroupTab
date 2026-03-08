package io.grouptab.service;

import io.grouptab.dto.*;
import io.grouptab.exception.AppException;
import io.grouptab.model.*;
import io.grouptab.model.Expense.SplitType;
import io.grouptab.model.GroupMember.Role;
import io.grouptab.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository      expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final SettlementRepository   settlementRepository;
    private final UserRepository         userRepository;
    private final ChatGroupRepository    groupRepository;
    private final GroupMemberRepository  memberRepository;

    // ── Create Expense ────────────────────────────────────────────────────────
    @Transactional
    public ExpenseResponse createExpense(Long groupId, ExpenseRequest req) {
        User creator = getAuthenticatedUser();

        // Verify creator is a member
        if (!memberRepository.existsByUserIdAndGroupId(creator.getId(), groupId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }

        ChatGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Group not found"));

        // Load the payer — must also be a group member
        User paidBy = userRepository.findById(req.paidByUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Payer not found"));

        if (!memberRepository.existsByUserIdAndGroupId(paidBy.getId(), groupId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Payer must be a member of the group");
        }

        Expense expense = new Expense();
        expense.setTitle(req.title());
        expense.setPaidBy(paidBy);
        expense.setCreatedBy(creator);
        expense.setGroup(group);
        expense.setSplitType(req.splitType());
        expense.setCreatedAt(Instant.now());

        // Build splits based on type
        List<ExpenseSplit> splits = switch (req.splitType()) {
            case EQUAL    -> buildEqualSplits(expense, req, groupId);
            case CUSTOM   -> buildCustomSplits(expense, req, groupId);
            case ITEMIZED -> buildItemizedSplits(expense, req, groupId);
        };

        // Set total — for ITEMIZED calculate from items, otherwise use provided value
        BigDecimal total = req.splitType() == SplitType.ITEMIZED
                ? splits.stream().map(ExpenseSplit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : req.totalAmount();

        expense.setTotalAmount(total);
        expense.getSplits().addAll(splits);

        Expense saved = expenseRepository.save(expense);
        return toResponse(saved);
    }

    // ── Get Expenses for Group ────────────────────────────────────────────────
    public List<ExpenseResponse> getExpenses(Long groupId) {
        User user = getAuthenticatedUser();
        if (!memberRepository.existsByUserIdAndGroupId(user.getId(), groupId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
        return expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Delete Expense ────────────────────────────────────────────────────────
    @Transactional
    public void deleteExpense(Long groupId, Long expenseId) {
        User user = getAuthenticatedUser();

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Expense not found"));

        if (!expense.getGroup().getId().equals(groupId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Expense not found in this group");
        }

        // Only creator or group admin can delete
        boolean isCreator = expense.getCreatedBy().getId().equals(user.getId());
        boolean isAdmin   = memberRepository.existsByUserIdAndGroupIdAndRole(user.getId(), groupId, Role.ADMIN);

        if (!isCreator && !isAdmin) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only the creator or a group admin can delete this expense");
        }

        expenseRepository.delete(expense);
    }

    // ── Get Balances ──────────────────────────────────────────────────────────
    // Calculates net balance per member and suggests minimum settlements
    public BalanceResponse getBalances(Long groupId) {
        User user = getAuthenticatedUser();
        if (!memberRepository.existsByUserIdAndGroupId(user.getId(), groupId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }

        // Load all expenses for this group with their payers
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);

        // Load all splits for this group
        List<ExpenseSplit> splits = splitRepository.findByExpenseGroupId(groupId);

        // Build a map of userId → net balance
        // Positive = owed money, Negative = owes money
        Map<Long, BigDecimal> balanceMap = new HashMap<>();

        // For each expense: payer is credited the full amount
        for (Expense e : expenses) {
            Long payerId = e.getPaidBy().getId();
            balanceMap.merge(payerId, e.getTotalAmount(), BigDecimal::add);
        }

        // For each split: that user is debited their share
        for (ExpenseSplit split : splits) {
            Long userId = split.getUser().getId();
            balanceMap.merge(userId, split.getAmount().negate(), BigDecimal::add);
        }

        // Load usernames for display
        List<GroupMember> members = memberRepository.findByGroupId(groupId);
        Map<Long, String> usernameMap = members.stream()
                .collect(Collectors.toMap(
                        m -> m.getUser().getId(),
                        m -> m.getUser().getUsername()
                ));

        // Build balance list
        List<BalanceResponse.MemberBalance> balances = balanceMap.entrySet().stream()
                .map(e -> new BalanceResponse.MemberBalance(
                        e.getKey(),
                        usernameMap.getOrDefault(e.getKey(), "Unknown"),
                        e.getValue().setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();

        // Calculate minimum settlements using a greedy algorithm
        List<BalanceResponse.SettlementSuggestion> suggestions =
                calculateSettlements(balanceMap, usernameMap);

        return new BalanceResponse(balances, suggestions);
    }

    // ── Settle Up ─────────────────────────────────────────────────────────────
    @Transactional
    public void settle(Long groupId, Long fromUserId, Long toUserId, BigDecimal amount) {
        User user = getAuthenticatedUser();

        // Only the person paying can mark it as settled
        if (!user.getId().equals(fromUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can only confirm your own payments");
        }

        ChatGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Group not found"));

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        Settlement settlement = new Settlement();
        settlement.setGroup(group);
        settlement.setFromUser(user);
        settlement.setToUser(toUser);
        settlement.setAmount(amount);
        settlement.setSettledAt(Instant.now());
        settlementRepository.save(settlement);
    }

    // ── Split Builders ────────────────────────────────────────────────────────

    private List<ExpenseSplit> buildEqualSplits(Expense expense, ExpenseRequest req, Long groupId) {
        if (req.totalAmount() == null || req.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Total amount required for equal split");
        }
        if (req.splitAmong() == null || req.splitAmong().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Select at least one member to split among");
        }

        // Verify all selected users are members
        req.splitAmong().forEach(uid -> {
            if (!memberRepository.existsByUserIdAndGroupId(uid, groupId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "User " + uid + " is not a member of this group");
            }
        });

        // Divide total equally, scale to 2 decimal places
        int    count     = req.splitAmong().size();
        BigDecimal share = req.totalAmount().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        return req.splitAmong().stream().map(uid -> {
            User u = userRepository.findById(uid)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found: " + uid));
            ExpenseSplit split = new ExpenseSplit();
            split.setExpense(expense);
            split.setUser(u);
            split.setAmount(share);
            return split;
        }).toList();
    }

    private List<ExpenseSplit> buildCustomSplits(Expense expense, ExpenseRequest req, Long groupId) {
        if (req.splits() == null || req.splits().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Splits required for custom split");
        }
        if (req.totalAmount() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Total amount required for custom split");
        }

        // Verify the entered amounts add up to the total
        BigDecimal sum = req.splits().stream()
                .map(SplitEntryRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(req.totalAmount()) != 0) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Split amounts must add up to the total. Got " + sum + ", expected " + req.totalAmount());
        }

        return req.splits().stream().map(entry -> {
            if (!memberRepository.existsByUserIdAndGroupId(entry.userId(), groupId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "User " + entry.userId() + " is not a member");
            }
            User u = userRepository.findById(entry.userId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
            ExpenseSplit split = new ExpenseSplit();
            split.setExpense(expense);
            split.setUser(u);
            split.setAmount(entry.amount());
            return split;
        }).toList();
    }

    private List<ExpenseSplit> buildItemizedSplits(Expense expense, ExpenseRequest req, Long groupId) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Items required for itemized split");
        }

        // Collect all participants across all items — needed for "split all" fallback
        Set<Long> allParticipants = req.items().stream()
                .filter(item -> item.splitAmong() != null && !item.splitAmong().isEmpty())
                .flatMap(item -> item.splitAmong().stream())
                .collect(Collectors.toSet());

        // Accumulate per-user totals across all items
        Map<Long, BigDecimal> userTotals = new HashMap<>();

        for (ItemRequest item : req.items()) {
            // If splitAmong is empty — split this item among all participants
            List<Long> members = (item.splitAmong() == null || item.splitAmong().isEmpty())
                    ? new ArrayList<>(allParticipants)
                    : item.splitAmong();

            if (members.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Item '" + item.name() + "' has no members to split among");
            }

            BigDecimal share = item.price().divide(BigDecimal.valueOf(members.size()), 2, RoundingMode.HALF_UP);
            members.forEach(uid -> userTotals.merge(uid, share, BigDecimal::add));

            // Save the item
            ExpenseItem expenseItem = new ExpenseItem();
            expenseItem.setName(item.name());
            expenseItem.setPrice(item.price());
            expenseItem.setExpense(expense);
            expenseItem.setSplitAmong(members);
            expense.getItems().add(expenseItem);
        }

        // Convert accumulated totals into ExpenseSplit rows
        return userTotals.entrySet().stream().map(entry -> {
            User u = userRepository.findById(entry.getKey())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
            ExpenseSplit split = new ExpenseSplit();
            split.setExpense(expense);
            split.setUser(u);
            split.setAmount(entry.getValue());
            return split;
        }).toList();
    }

    // ── Settlement Algorithm ──────────────────────────────────────────────────
    // Greedy algorithm — reduces the number of transactions needed to settle all debts
    // Instead of everyone paying everyone, finds the minimum set of payments
    private List<BalanceResponse.SettlementSuggestion> calculateSettlements(
            Map<Long, BigDecimal> balanceMap,
            Map<Long, String> usernameMap) {

        // Separate into creditors (positive balance) and debtors (negative balance)
        // Use TreeMap to get consistent ordering
        TreeMap<Long, BigDecimal> creditors = new TreeMap<>();
        TreeMap<Long, BigDecimal> debtors   = new TreeMap<>();

        balanceMap.forEach((uid, balance) -> {
            int cmp = balance.compareTo(BigDecimal.ZERO);
            if (cmp > 0) creditors.put(uid, balance);
            else if (cmp < 0) debtors.put(uid, balance.negate()); // store as positive
        });

        List<BalanceResponse.SettlementSuggestion> suggestions = new ArrayList<>();

        // Match debtors with creditors greedily
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            Map.Entry<Long, BigDecimal> debtor   = debtors.firstEntry();
            Map.Entry<Long, BigDecimal> creditor = creditors.firstEntry();

            // The payment is the smaller of what the debtor owes and what the creditor is owed
            BigDecimal payment = debtor.getValue().min(creditor.getValue());

            suggestions.add(new BalanceResponse.SettlementSuggestion(
                    debtor.getKey(),   usernameMap.getOrDefault(debtor.getKey(), "Unknown"),
                    creditor.getKey(), usernameMap.getOrDefault(creditor.getKey(), "Unknown"),
                    payment.setScale(2, RoundingMode.HALF_UP)
            ));

            // Reduce balances by the payment amount
            BigDecimal remainingDebt   = debtor.getValue().subtract(payment);
            BigDecimal remainingCredit = creditor.getValue().subtract(payment);

            // Remove if fully settled, otherwise update
            if (remainingDebt.compareTo(BigDecimal.ZERO) == 0) debtors.remove(debtor.getKey());
            else debtors.put(debtor.getKey(), remainingDebt);

            if (remainingCredit.compareTo(BigDecimal.ZERO) == 0) creditors.remove(creditor.getKey());
            else creditors.put(creditor.getKey(), remainingCredit);
        }

        return suggestions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ExpenseResponse toResponse(Expense expense) {
        List<SplitEntryResponse> splitResponses = expense.getSplits().stream()
                .map(s -> new SplitEntryResponse(
                        s.getUser().getId(),
                        s.getUser().getUsername(),
                        s.getAmount()
                ))
                .toList();

        return new ExpenseResponse(
                expense.getId(),
                expense.getTitle(),
                expense.getPaidBy().getUsername(),
                expense.getCreatedBy().getUsername(),
                expense.getTotalAmount(),
                expense.getSplitType(),
                expense.getCreatedAt(),
                splitResponses
        );
    }

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}