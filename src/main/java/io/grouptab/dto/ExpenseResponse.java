package io.grouptab.dto;

import io.grouptab.model.Expense.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// What we return to the client for an expense
public record ExpenseResponse(
        Long id,
        String title,
        String paidByUsername,
        String createdByUsername,
        BigDecimal totalAmount,
        SplitType splitType,
        Instant createdAt,
        List<SplitEntryResponse> splits
) {}