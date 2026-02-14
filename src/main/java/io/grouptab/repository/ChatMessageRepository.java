package io.grouptab.repository;

import io.grouptab.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Custom query method: "Find the top 50, order by time descending"
    List<ChatMessage> findTop50ByOrderByTimestampDesc();
}