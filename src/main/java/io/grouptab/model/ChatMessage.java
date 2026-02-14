package io.grouptab.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Data;


@Entity
@Data
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;
    private String username;
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    private MessageType messageType;

    public enum MessageType{
        CHAT, JOIN, LEAVE
    }

}
