package io.grouptab.dto;

import java.math.BigDecimal;

// One entry in a CUSTOM split — who owes how much
public record SplitEntryRequest(Long userId, BigDecimal amount) {}