package io.grouptab.service;

import io.grouptab.dto.GroupResponse;
import io.grouptab.dto.InviteResponse;
import io.grouptab.dto.MemberResponse;
import io.grouptab.exception.AppException;
import io.grouptab.model.ChatGroup;
import io.grouptab.model.GroupInvite;
import io.grouptab.model.GroupMember;
import io.grouptab.model.GroupMember.Role;
import io.grouptab.model.User;
import io.grouptab.repository.ChatGroupRepository;
import io.grouptab.repository.GroupInviteRepository;
import io.grouptab.repository.GroupMemberRepository;
import io.grouptab.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final ChatGroupRepository    groupRepository;
    private final GroupMemberRepository  memberRepository;
    private final GroupInviteRepository  inviteRepository;
    private final UserRepository         userRepository;

    // Returns only groups the authenticated user is a member of
    public List<GroupResponse> getMyGroups() {
        User user = getAuthenticatedUser();
        return memberRepository.findByUserId(user.getId())
                .stream()
                .map(m -> new GroupResponse(
                        m.getGroup().getId(),
                        m.getGroup().getName(),
                        m.getRole().name()
                ))
                .toList();
    }

    // Creates a group and automatically makes the creator an ADMIN member
    @Transactional
    public GroupResponse createGroup(ChatGroup group) {
        User user = getAuthenticatedUser();

        ChatGroup saved = groupRepository.save(group);

        // Add creator as ADMIN member
        GroupMember member = new GroupMember();
        member.setUser(user);
        member.setGroup(saved);
        member.setRole(Role.ADMIN);
        member.setJoinedAt(Instant.now());
        memberRepository.save(member);

        return new GroupResponse(saved.getId(), saved.getName(), Role.ADMIN.name());
    }

    // Only ADMIN members can delete their group
    @Transactional
    public void deleteGroup(Long groupId) {
        User user = getAuthenticatedUser();

        // Verify group exists
        groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Group not found"));

        // Verify the user is an admin of this group
        if (!memberRepository.existsByUserIdAndGroupIdAndRole(user.getId(), groupId, Role.ADMIN)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only the group admin can delete it");
        }

        groupRepository.deleteById(groupId);
    }

    // Returns all members of a group — only accessible to members of that group
    public List<MemberResponse> getMembers(Long groupId) {
        User user = getAuthenticatedUser();

        // Verify the requesting user is actually a member
        if (!memberRepository.existsByUserIdAndGroupId(user.getId(), groupId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }

        return memberRepository.findByGroupId(groupId)
                .stream()
                .map(m -> new MemberResponse(
                        m.getUser().getId(),
                        m.getUser().getUsername(),
                        m.getRole().name()
                ))
                .toList();
    }

    // Generates a unique invite token — only admins can create invites
    public InviteResponse createInvite(Long groupId, HttpServletRequest request) {
        User user = getAuthenticatedUser();

        ChatGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Group not found"));

        // Only admins can generate invite links
        if (!memberRepository.existsByUserIdAndGroupIdAndRole(user.getId(), groupId, Role.ADMIN)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can create invite links");
        }

        // Generate a random unique token
        String token = UUID.randomUUID().toString().replace("-", "");

        GroupInvite invite = new GroupInvite();
        invite.setToken(token);
        invite.setGroup(group);
        invite.setCreatedBy(user);
        invite.setExpiresAt(null); // null = never expires
        inviteRepository.save(invite);

        // Build the full invite URL the frontend can display or copy
        String baseUrl    = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String inviteUrl  = baseUrl + "/join/" + token;

        return new InviteResponse(token, inviteUrl);
    }

    // Joins a group using an invite token
    @Transactional
    public GroupResponse joinGroup(String token) {
        User user = getAuthenticatedUser();

        // Find the invite
        GroupInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Invite not found or invalid"));

        // Check expiry if set
        if (invite.getExpiresAt() != null && Instant.now().isAfter(invite.getExpiresAt())) {
            throw new AppException(HttpStatus.GONE, "This invite link has expired");
        }

        // Check if already a member
        if (memberRepository.existsByUserIdAndGroupId(user.getId(), invite.getGroup().getId())) {
            throw new AppException(HttpStatus.CONFLICT, "You are already a member of this group");
        }

        // Add as MEMBER
        GroupMember member = new GroupMember();
        member.setUser(user);
        member.setGroup(invite.getGroup());
        member.setRole(Role.MEMBER);
        member.setJoinedAt(Instant.now());
        memberRepository.save(member);

        return new GroupResponse(
                invite.getGroup().getId(),
                invite.getGroup().getName(),
                Role.MEMBER.name()
        );
    }

    // Helper — loads the authenticated user from the security context
    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
}