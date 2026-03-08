package io.grouptab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

// One line item in an ITEMIZED expense
public record ItemRequest(
        @NotBlank String name,
        @Positive BigDecimal price,
        List<Long> splitAmong  // userIds who share this item — null or empty means split among all participants
) {}