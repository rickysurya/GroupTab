package io.grouptab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_message_group_id", columnList = "groupId")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Message content must not be blank")
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    @Column(nullable = false, length = 2000)
    private String content;

    @NotBlank(message = "Username must not be blank")
    @Size(max = 100, message = "Username must not exceed 100 characters")
    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType = MessageType.CHAT;

    public enum MessageType {
        CHAT, JOIN, LEAVE
    }
}
