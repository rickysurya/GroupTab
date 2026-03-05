package io.grouptab.dto;

// What we return for each member in a group
public record MemberResponse(Long userId, String username, String role) {}