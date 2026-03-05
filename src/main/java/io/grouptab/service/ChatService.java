package io.grouptab.service;

import io.grouptab.exception.AppException;
import io.grouptab.model.ChatMessage;
import io.grouptab.repository.ChatMessageRepository;
import io.grouptab.repository.GroupMemberRepository;
import io.grouptab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository        userRepository;

    public ChatMessage processAndSave(Long groupId, ChatMessage message) {
        // Verify the sender is actually a member of this group before saving
        // Username comes from the STOMP session set by WebSocketAuthInterceptor
        var user = userRepository.findByUsername(message.getUsername())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!memberRepository.existsByUserIdAndGroupId(user.getId(), groupId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }

        message.setGroupId(groupId);
        message.setTimestamp(Instant.now());
        if (message.getMessageType() == null) {
            message.setMessageType(ChatMessage.MessageType.CHAT);
        }
        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> getHistory(Long groupId) {
        return chatMessageRepository.findTop50ByGroupIdOrderByTimestampAsc(groupId);
    }
}