package io.grouptab.repository;

import io.grouptab.model.GroupMember;
import io.grouptab.model.GroupMember.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    // All memberships for a user — used to list their groups
    List<GroupMember> findByUserId(Long userId);

    // All members of a group — used for member list endpoint
    List<GroupMember> findByGroupId(Long groupId);

    // Check if a user is already a member — used before joining
    boolean existsByUserIdAndGroupId(Long userId, Long groupId);

    // Get a specific membership — used for role checks and subscription validation
    Optional<GroupMember> findByUserIdAndGroupId(Long userId, Long groupId);

    // Check if a user has a specific role in a group — used for admin checks
    boolean existsByUserIdAndGroupIdAndRole(Long userId, Long groupId, Role role);
}