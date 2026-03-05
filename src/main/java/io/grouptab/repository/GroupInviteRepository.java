package io.grouptab.repository;

import io.grouptab.model.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {

    // Look up an invite by its token — used when someone clicks a join link
    Optional<GroupInvite> findByToken(String token);
}