package io.grouptab.controller;

import io.grouptab.model.ChatMessage;
import io.grouptab.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat/{groupId}")
    @SendTo("/topic/group/{groupId}")
    public ChatMessage sendMessage(@DestinationVariable Long groupId, ChatMessage message){
        return chatService.sendMessage(groupId, message);
    }

    @GetMapping("/chat/history/{groupId}")
    @ResponseBody
    // TODO Replace with frontend url in the future
    @CrossOrigin(origins = "*")
    public List<ChatMessage> getHistory(@PathVariable Long groupId){
        return chatService.getHistory(groupId);
    }
}
