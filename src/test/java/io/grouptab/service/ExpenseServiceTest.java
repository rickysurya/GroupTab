package io.grouptab.service;

import io.grouptab.dto.*;
import io.grouptab.exception.AppException;
import io.grouptab.model.*;
import io.grouptab.model.Expense.SplitType;
import io.grouptab.model.GroupMember.Role;
import io.grouptab.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;

@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseServiceTest {

    @Mock ExpenseRepository      expenseRepository;
    @Mock ExpenseSplitRepository splitRepository;
    @Mock SettlementRepository   settlementRepository;
    @Mock UserRepository         userRepository;
    @Mock ChatGroupRepository    groupRepository;
    @Mock GroupMemberRepository  memberRepository;

    @InjectMocks ExpenseService expenseService;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    User user1, user2, user3;
    ChatGroup group;

    @BeforeEach
    void setUp() {
        user1 = new User(); user1.setId(1L); user1.setUsername("alice");
        user2 = new User(); user2.setId(2L); user2.setUsername("bob");
        user3 = new User(); user3.setId(3L); user3.setUsername("charlie");

        group = new ChatGroup(); group.setId(1L); group.setName("Dinner Group"); group.setCurrency("IDR");

        // Set alice as the authenticated user
        var auth = new UsernamePasswordAuthenticationToken("alice", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Default stubs used by most tests
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user1));
        when(memberRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    }

    // ── EQUAL split ───────────────────────────────────────────────────────────

    @Test
    void equalSplit_dividesTotalEvenly() {
        when(memberRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(true);
        when(memberRepository.existsByUserIdAndGroupId(3L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user3));
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        ExpenseRequest req = new ExpenseRequest(
                "Dinner",
                1L,                          // alice paid
                new BigDecimal("90000"),      // 90k total
                SplitType.EQUAL,
                List.of(1L, 2L, 3L),         // split among all 3
                null,
                null
        );

        ExpenseResponse res = expenseService.createExpense(1L, req);

        assertThat(res.splits()).hasSize(3);
        // Each person owes 30000
        res.splits().forEach(s ->
                assertThat(s.amount()).isEqualByComparingTo("30000")
        );
        assertThat(res.totalAmount()).isEqualByComparingTo("90000");
    }

    @Test
    void equalSplit_roundsCorrectly_whenNotDivisible() {
        // 100k split 3 ways = 33333.33 each
        when(memberRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(true);
        when(memberRepository.existsByUserIdAndGroupId(3L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user3));
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        ExpenseRequest req = new ExpenseRequest(
                "Lunch", 1L, new BigDecimal("100000"), SplitType.EQUAL,
                List.of(1L, 2L, 3L), null, null
        );

        ExpenseResponse res = expenseService.createExpense(1L, req);

        res.splits().forEach(s ->
                assertThat(s.amount()).isEqualByComparingTo("33333.33")
        );
    }

    @Test
    void equalSplit_rejects_whenNoMembersSelected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        ExpenseRequest req = new ExpenseRequest(
                "Dinner", 1L, new BigDecimal("90000"), SplitType.EQUAL,
                List.of(), null, null  // empty splitAmong
        );

        assertThatThrownBy(() -> expenseService.createExpense(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Select at least one member");
    }

    @Test
    void equalSplit_rejects_whenNonMemberIncluded() {
        when(memberRepository.existsByUserIdAndGroupId(99L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        ExpenseRequest req = new ExpenseRequest(
                "Dinner", 1L, new BigDecimal("90000"), SplitType.EQUAL,
                List.of(1L, 99L), null, null  // 99L is not a member
        );

        assertThatThrownBy(() -> expenseService.createExpense(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not a member");
    }

    // ── CUSTOM split ──────────────────────────────────────────────────────────

    @Test
    void customSplit_acceptsWhenAmountsSumToTotal() {
        when(memberRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        List<SplitEntryRequest> splits = List.of(
                new SplitEntryRequest(1L, new BigDecimal("60000")),
                new SplitEntryRequest(2L, new BigDecimal("40000"))
        );

        ExpenseRequest req = new ExpenseRequest(
                "Dinner", 1L, new BigDecimal("100000"), SplitType.CUSTOM,
                null, splits, null
        );

        ExpenseResponse res = expenseService.createExpense(1L, req);

        assertThat(res.splits()).hasSize(2);
        assertThat(res.splits().get(0).amount()).isEqualByComparingTo("60000");
        assertThat(res.splits().get(1).amount()).isEqualByComparingTo("40000");
    }

    @Test
    void customSplit_rejects_whenAmountsDontSumToTotal() {
        List<SplitEntryRequest> splits = List.of(
                new SplitEntryRequest(1L, new BigDecimal("60000")),
                new SplitEntryRequest(2L, new BigDecimal("30000"))  // 90k != 100k total
        );

        when(memberRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        ExpenseRequest req = new ExpenseRequest(
                "Dinner", 1L, new BigDecimal("100000"), SplitType.CUSTOM,
                null, splits, null
        );

        assertThatThrownBy(() -> expenseService.createExpense(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("must add up to the total");
    }

    // ── ITEMIZED split ────────────────────────────────────────────────────────

    @Test
    void itemizedSplit_calculatesCorrectlyPerItem() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user3));
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        List<ItemRequest> items = List.of(
                new ItemRequest("Nasi Goreng", new BigDecimal("25000"), List.of(1L, 2L)),  // shared
                new ItemRequest("Ayam Bakar",  new BigDecimal("40000"), List.of(3L)),       // charlie only
                new ItemRequest("Es Teh",      new BigDecimal("35000"), List.of(3L))        // charlie only
        );

        ExpenseRequest req = new ExpenseRequest(
                "Dinner", 1L, null, SplitType.ITEMIZED,
                null, null, items
        );

        ExpenseResponse res = expenseService.createExpense(1L, req);

        // alice: 12500 (half of Nasi Goreng)
        // bob:   12500 (half of Nasi Goreng)
        // charlie: 75000 (Ayam Bakar + Es Teh)
        assertThat(res.splits()).hasSize(3);

        BigDecimal aliceAmount   = splitAmount(res, "alice");
        BigDecimal bobAmount     = splitAmount(res, "bob");
        BigDecimal charlieAmount = splitAmount(res, "charlie");

        assertThat(aliceAmount).isEqualByComparingTo("12500");
        assertThat(bobAmount).isEqualByComparingTo("12500");
        assertThat(charlieAmount).isEqualByComparingTo("75000");

        // Total should be sum of all items
        assertThat(res.totalAmount()).isEqualByComparingTo("100000");
    }

    @Test
    void itemizedSplit_rejects_whenNoItems() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        ExpenseRequest req = new ExpenseRequest(
                "Dinner", 1L, null, SplitType.ITEMIZED,
                null, null, List.of()  // empty items
        );

        assertThatThrownBy(() -> expenseService.createExpense(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Items required");
    }

    // ── Balance calculation ───────────────────────────────────────────────────

    @Test
    void getBalances_calculatesNetCorrectly() {
        // alice paid 100k, everyone owes 33.33k each
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setTotalAmount(new BigDecimal("90000"));
        expense.setPaidBy(user1); // alice paid

        ExpenseSplit s1 = new ExpenseSplit(); s1.setUser(user1); s1.setAmount(new BigDecimal("30000"));
        ExpenseSplit s2 = new ExpenseSplit(); s2.setUser(user2); s2.setAmount(new BigDecimal("30000"));
        ExpenseSplit s3 = new ExpenseSplit(); s3.setUser(user3); s3.setAmount(new BigDecimal("30000"));

        GroupMember m1 = new GroupMember(); m1.setUser(user1);
        GroupMember m2 = new GroupMember(); m2.setUser(user2);
        GroupMember m3 = new GroupMember(); m3.setUser(user3);

        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(expense));
        when(splitRepository.findByExpenseGroupId(1L)).thenReturn(List.of(s1, s2, s3));
        when(memberRepository.findByGroupId(1L)).thenReturn(List.of(m1, m2, m3));

        BalanceResponse res = expenseService.getBalances(1L);

        // alice paid 90k, owes 30k → net +60k (owed money)
        // bob owes 30k → net -30k
        // charlie owes 30k → net -30k
        var aliceBalance   = res.balances().stream().filter(b -> b.username().equals("alice")).findFirst().orElseThrow();
        var bobBalance     = res.balances().stream().filter(b -> b.username().equals("bob")).findFirst().orElseThrow();
        var charlieBalance = res.balances().stream().filter(b -> b.username().equals("charlie")).findFirst().orElseThrow();

        assertThat(aliceBalance.netBalance()).isEqualByComparingTo("60000");
        assertThat(bobBalance.netBalance()).isEqualByComparingTo("-30000");
        assertThat(charlieBalance.netBalance()).isEqualByComparingTo("-30000");
    }

    @Test
    void getBalances_suggestsMinimumSettlements() {
        // Same scenario as above — bob and charlie each owe alice 30k
        Expense expense = new Expense();
        expense.setId(1L);
        expense.setTotalAmount(new BigDecimal("90000"));
        expense.setPaidBy(user1);

        ExpenseSplit s1 = new ExpenseSplit(); s1.setUser(user1); s1.setAmount(new BigDecimal("30000"));
        ExpenseSplit s2 = new ExpenseSplit(); s2.setUser(user2); s2.setAmount(new BigDecimal("30000"));
        ExpenseSplit s3 = new ExpenseSplit(); s3.setUser(user3); s3.setAmount(new BigDecimal("30000"));

        GroupMember m1 = new GroupMember(); m1.setUser(user1);
        GroupMember m2 = new GroupMember(); m2.setUser(user2);
        GroupMember m3 = new GroupMember(); m3.setUser(user3);

        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(expense));
        when(splitRepository.findByExpenseGroupId(1L)).thenReturn(List.of(s1, s2, s3));
        when(memberRepository.findByGroupId(1L)).thenReturn(List.of(m1, m2, m3));

        BalanceResponse res = expenseService.getBalances(1L);

        // Should suggest 2 settlements: bob→alice 30k, charlie→alice 30k
        assertThat(res.suggestions()).hasSize(2);
        res.suggestions().forEach(s -> {
            assertThat(s.toUsername()).isEqualTo("alice");
            assertThat(s.amount()).isEqualByComparingTo("30000");
        });
    }

    // ── Authorization ─────────────────────────────────────────────────────────

    @Test
    void createExpense_rejects_whenNotMember() {
        when(memberRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        ExpenseRequest req = new ExpenseRequest(
                "Dinner", 1L, new BigDecimal("90000"), SplitType.EQUAL,
                List.of(1L), null, null
        );

        assertThatThrownBy(() -> expenseService.createExpense(1L, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("not a member of this group");
    }

    @Test
    void deleteExpense_rejects_whenNotCreatorOrAdmin() {
        Expense expense = new Expense();
        expense.setId(10L);
        expense.setCreatedBy(user2); // bob created it
        expense.setGroup(group);

        when(expenseRepository.findById(10L)).thenReturn(Optional.of(expense));
        when(memberRepository.existsByUserIdAndGroupIdAndRole(1L, 1L, Role.ADMIN)).thenReturn(false);

        // alice tries to delete bob's expense but is not admin
        assertThatThrownBy(() -> expenseService.deleteExpense(1L, 10L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Only the creator or a group admin");
    }

    @Test
    void deleteExpense_allows_whenAdmin() {
        Expense expense = new Expense();
        expense.setId(10L);
        expense.setCreatedBy(user2); // bob created it
        expense.setGroup(group);

        when(expenseRepository.findById(10L)).thenReturn(Optional.of(expense));
        when(memberRepository.existsByUserIdAndGroupIdAndRole(1L, 1L, Role.ADMIN)).thenReturn(true);

        // alice is admin so can delete
        assertThatNoException().isThrownBy(() -> expenseService.deleteExpense(1L, 10L));
        verify(expenseRepository).delete(expense);
    }

    // ── Settle ───────────────────────────────────────────────────────────────

    @Test
    void settle_savesSettlementWhenCallerIsFromUser() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));

        assertThatNoException().isThrownBy(() ->
                expenseService.settle(1L, 1L, 2L, new BigDecimal("30000"))
        );

        verify(settlementRepository).save(argThat(s ->
                s.getFromUser().equals(user1) &&
                        s.getToUser().equals(user2) &&
                        s.getAmount().compareTo(new BigDecimal("30000")) == 0 &&
                        s.getSettledAt() != null
        ));
    }

    @Test
    void settle_rejects_whenCallerIsNotFromUser() {
        assertThatThrownBy(() ->
                expenseService.settle(1L, 2L, 3L, new BigDecimal("30000"))
        )
                .isInstanceOf(AppException.class)
                .hasMessageContaining("your own payments");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private BigDecimal splitAmount(ExpenseResponse res, String username) {
        return res.splits().stream()
                .filter(s -> s.username().equals(username))
                .map(SplitEntryResponse::amount)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No split found for " + username));
    }
}