package io.grouptab.repository;

import io.grouptab.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroupId(Long groupId);

    List<Settlement> findByGroupIdAndSettledAtIsNull(Long groupId);
}
