package io.grouptab.dto;

// What we return to the client for a group — only what the frontend needs
// Never expose internal DB fields directly
public record GroupResponse(Long id, String name, String role) {}