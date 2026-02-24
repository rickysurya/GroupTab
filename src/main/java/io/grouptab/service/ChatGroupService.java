package io.grouptab.service;

import io.grouptab.exception.GroupNotFoundException;
import io.grouptab.exception.UnauthorizedException;
import io.grouptab.model.ChatGroup;
import io.grouptab.repository.ChatGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatGroupService {
    private final ChatGroupRepository chatGroupRepository;

    public List<ChatGroup> getAllGroups(){
        return chatGroupRepository.findAll();
    }
    public ChatGroup createGroup(ChatGroup group){
        return chatGroupRepository.save(group);
    }

    public void deleteGroup(Long id, String requestingUser){
        ChatGroup group = chatGroupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException(id));

        if (!group.getAdminUsername().equals(requestingUser)) {
            throw new UnauthorizedException("Only the channel admin can delete it");
        }

        chatGroupRepository.deleteById(id);

    }
}