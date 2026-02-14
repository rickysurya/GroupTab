package io.grouptab.controller;

import io.grouptab.model.ChatMessage;
import io.grouptab.repository.ChatMessageRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.List;

@Controller
public class ChatController {

    private final ChatMessageRepository repository;

    public ChatController(ChatMessageRepository repository){
        this.repository = repository;
    }

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public ChatMessage send(ChatMessage message){
        message.setTimestamp(Instant.now());
        return message;
    }

    @GetMapping("/api/chat/history")
    @ResponseBody
    public List<ChatMessage> getHistory(){
        return repository.findTop50ByOrderByTimestampDesc();
    }
}
