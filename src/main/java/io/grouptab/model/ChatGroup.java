package io.grouptab.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "chat_groups")
public class ChatGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Channel name must not be blank")
    @Size(min = 2, max = 50, message = "Channel name must be between 2 and 50 characters")
    @Column(nullable = false, unique = true)
    private String name;
//    // Currency for all expenses in this group — set on creation, e.g. "IDR", "USD"
//    // ADDED: needed for expense tracking
//    @NotBlank(message = "Currency must not be blank")
//    @Column(nullable = false, length = 3)
//    private String currency;

}