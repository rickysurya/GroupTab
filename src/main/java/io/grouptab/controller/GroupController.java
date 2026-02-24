package io.grouptab.controller;

import io.grouptab.model.ChatGroup;
import io.grouptab.service.ChatGroupRepository;
import io.grouptab.service.ChatGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
//TODO
@CrossOrigin(origins = "*")
public class GroupController {

    private final ChatGroupService chatGroupService;

    @GetMapping
    public List<ChatGroup> getAllGroups() {
        return chatGroupService.getAllGroups();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatGroup createGroup(@RequestBody ChatGroup group) {
        return chatGroupService.createGroup(group);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id, @RequestParam String admin) {
        chatGroupService.deleteGroup(id, admin);
        return ResponseEntity.noContent().build();
    }
}