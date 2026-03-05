package io.grouptab.controller;

import io.grouptab.dto.GroupResponse;
import io.grouptab.dto.InviteResponse;
import io.grouptab.dto.MemberResponse;
import io.grouptab.model.ChatGroup;
import io.grouptab.service.GroupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    // CHANGED: returns only groups the user is a member of, not all groups
    @GetMapping
    public List<GroupResponse> getMyGroups() {
        return groupService.getMyGroups();
    }

    // CHANGED: now returns GroupResponse which includes the user's role
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@Valid @RequestBody ChatGroup group) {
        return groupService.createGroup(group);
    }

    // CHANGED: no more ?admin= param — identity from JWT, role from GroupMember
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    // NEW: list members of a group — only accessible to members
    @GetMapping("/{id}/members")
    public List<MemberResponse> getMembers(@PathVariable Long id) {
        return groupService.getMembers(id);
    }

    // NEW: generate an invite link — only admins can do this
    @PostMapping("/{id}/invite")
    public InviteResponse createInvite(@PathVariable Long id, HttpServletRequest request) {
        return groupService.createInvite(id, request);
    }

    // NEW: join a group via invite token
    @PostMapping("/join/{token}")
    public GroupResponse joinGroup(@PathVariable String token) {
        return groupService.joinGroup(token);
    }
}