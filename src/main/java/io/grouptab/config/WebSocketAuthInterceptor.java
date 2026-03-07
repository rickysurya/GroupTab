package io.grouptab.config;

import io.grouptab.repository.GroupMemberRepository;
import io.grouptab.repository.UserRepository;
import io.grouptab.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil               jwtUtil;
    private final UserDetailsService    userDetailsService;
    private final UserRepository        userRepository;
    private final GroupMemberRepository memberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        // ── CONNECT: validate JWT and set user on the session ────────────────
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtil.isValid(token)) {
                    String username    = jwtUtil.extractUsername(token);
                    var    userDetails = userDetailsService.loadUserByUsername(username);
                    var    auth        = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    accessor.setUser(auth);
                }
            }
        }

        // ── SUBSCRIBE: verify the user is a member of the group they're subscribing to ──
        // Destination format: /topic/group.{groupId}
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();

            if (destination != null && destination.startsWith("/topic/group.")) {
                // Extract groupId from the destination
                String groupIdStr = destination.substring("/topic/group.".length());

                try {
                    Long groupId = Long.parseLong(groupIdStr);

                    // Get the authenticated user from the STOMP session
                    var principal = accessor.getUser();

                    //only for debug
                    System.out.println("SUBSCRIBE DEBUG: destination=" + destination
                            + " groupId=" + groupId
                            + " principal=" + (principal != null ? principal.getName() : "NULL"));

                    if (principal == null) {
                        // No auth set — reject
                        throw new IllegalStateException("Unauthorized");
                    }

                    // Look up the user in DB
                    var user = userRepository.findByUsername(principal.getName())
                            .orElseThrow(() -> new IllegalStateException("User not found"));

                    // only for debug
                    boolean isMember = memberRepository.existsByUserIdAndGroupId(user.getId(), groupId);
                    System.out.println("SUBSCRIBE DEBUG: userId=" + user.getId() + " isMember=" + isMember);

                    // Check they're actually a member of this group
                    if (!memberRepository.existsByUserIdAndGroupId(user.getId(), groupId)) {
                        throw new IllegalStateException("Not a member of this group");
                    }

                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Invalid group destination");
                }
            }
        }

        return message;
    }
}