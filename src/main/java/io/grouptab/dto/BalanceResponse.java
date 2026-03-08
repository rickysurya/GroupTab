package io.grouptab.dto;

import java.math.BigDecimal;
import java.util.List;

// The overall balance summary for a group
// Shows what each member owes or is owed, and the suggested settlements
public record BalanceResponse(
        List<MemberBalance> balances,
        List<SettlementSuggestion> suggestions
) {
    // Net balance per member — positive means they are owed money, negative means they owe
    public record MemberBalance(Long userId, String username, BigDecimal netBalance) {}

    // A suggested payment to settle up
    public record SettlementSuggestion(
            Long fromUserId, String fromUsername,
            Long toUserId,   String toUsername,
            BigDecimal amount
    ) {}
}