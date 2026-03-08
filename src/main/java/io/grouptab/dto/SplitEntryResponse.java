package io.grouptab.dto;

import java.math.BigDecimal;

// One split entry in the expense response
public record SplitEntryResponse(Long userId, String username, BigDecimal amount) {}