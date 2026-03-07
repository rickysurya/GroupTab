//package io.grouptab.model;
//
//import jakarta.persistence.*;
//import jakarta.validation.constraints.NotBlank;
//import jakarta.validation.constraints.NotNull;
//import jakarta.validation.constraints.Positive;
//import lombok.Getter;
//import lombok.NoArgsConstructor;
//import lombok.Setter;
//
//import java.math.BigDecimal;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//
//@Entity
//@Getter
//@Setter
//@NoArgsConstructor
//@Table(name = "expenses")
//public class Expense {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @NotBlank
//    @Column(nullable = false)
//    private String title;
//
//    // Who actually paid the bill
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "paid_by", nullable = false)
//    private User paidBy;
//
//    // Who logged this expense — may differ from paidBy
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "created_by", nullable = false)
//    private User createdBy;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "group_id", nullable = false)
//    private ChatGroup group;
//
//    // Stored as BigDecimal for precise money arithmetic — never use float/double for money
//    @NotNull
//    @Positive
//    @Column(nullable = false, precision = 15, scale = 2)
//    private BigDecimal totalAmount;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private SplitType splitType;
//
//    @Column(nullable = false)
//    private Instant createdAt;
//
//    // Items only populated when splitType = ITEMIZED
//    // CascadeType.ALL — when expense is deleted, items are deleted too
//    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<ExpenseItem> items = new ArrayList<>();
//
//    // The calculated splits — one row per member who owes
//    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<ExpenseSplit> splits = new ArrayList<>();
//
//    public enum SplitType {
//        EQUAL,    // divide total equally among selected members
//        CUSTOM,   // manually enter each person's share
//        ITEMIZED  // line items assigned to specific members
//    }
//}