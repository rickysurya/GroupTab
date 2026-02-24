package io.grouptab.service;


import io.grouptab.model.ChatMessage;
import io.grouptab.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessage sendMessage(Long groupId, ChatMessage message) {
        message.setTimestamp(Instant.now());
        message.setGroupId(groupId);
        if (message.getMessageType() == null){
            message.setMessageType(ChatMessage.MessageType.CHAT);
        }
        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> getHistory(Long groupId) {
        return chatMessageRepository.findTop50ByGroupIdOrderByTimestampAsc(groupId);
    }
}
