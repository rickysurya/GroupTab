package io.grouptab.controller;

import io.grouptab.model.ChatGroup;
import io.grouptab.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// CHANGED: removed @CrossOrigin — handled globally by SecurityConfig now
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public List<ChatGroup> getAllGroups() {
        return groupService.getAllGroups();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatGroup createGroup(@Valid @RequestBody ChatGroup group) {
        return groupService.createGroup(group);
    }

    // CHANGED: removed @RequestParam String admin — no longer needed, identity from JWT
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}