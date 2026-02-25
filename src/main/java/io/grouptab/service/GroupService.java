package io.grouptab.service;

import io.grouptab.exception.AppException;
import io.grouptab.model.ChatGroup;
import io.grouptab.repository.ChatGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder; // NEW
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final ChatGroupRepository groupRepository;

    public List<ChatGroup> getAllGroups() {
        return groupRepository.findAll();
    }

    public ChatGroup createGroup(ChatGroup group) {
        // CHANGED: adminUsername now comes from the verified JWT, not the request body
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        group.setAdminUsername(username);
        return groupRepository.save(group);
    }

    // CHANGED: removed requestingUser param — identity comes from security context now
    public void deleteGroup(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        ChatGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Group not found with id: " + id));

        if (!group.getAdminUsername().equals(username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only the channel admin can delete it");
        }

        groupRepository.deleteById(id);
    }
}