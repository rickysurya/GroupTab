package io.grouptab.dto;

// Returned after generating an invite — frontend uses the token to build the link
public record InviteResponse(String token, String inviteUrl) {}