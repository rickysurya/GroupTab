package io.grouptab.repository;

import io.grouptab.model.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {
    List<ExpenseSplit> findByExpenseGroupId(Long groupId);

    List<ExpenseSplit> findByExpenseGroupIdAndUserId(Long groupId, Long userId);

}
