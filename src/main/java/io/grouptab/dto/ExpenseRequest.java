package io.grouptab.dto;

import io.grouptab.model.Expense.SplitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

// What the client sends when creating an expense
public record ExpenseRequest(

        @NotBlank
        String title,

        // Who paid — userId of the payer (must be a group member)
        @NotNull
        Long paidByUserId,

        // Total amount — required for EQUAL and CUSTOM, auto-calculated for ITEMIZED
        BigDecimal totalAmount,

        @NotNull
        SplitType splitType,

        // EQUAL: list of userIds to split among
        // CUSTOM: list of ExpenseSplitRequest with userId + amount
        // ITEMIZED: list of items with their own splitAmong
        List<Long> splitAmong,           // for EQUAL
        List<SplitEntryRequest> splits,  // for CUSTOM
        List<ItemRequest> items          // for ITEMIZED
) {}