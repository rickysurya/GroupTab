//package io.grouptab.model;
//
//import jakarta.persistence.*;
//import lombok.Getter;
//import lombok.NoArgsConstructor;
//import lombok.Setter;
//
//import java.math.BigDecimal;
//
//@Entity
//@Getter
//@Setter
//@NoArgsConstructor
//@Table(name = "expense_splits")
//public class ExpenseSplit {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "expense_id", nullable = false)
//    private Expense expense;
//
//    // The member who owes this amount
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "user_id", nullable = false)
//    private User user;
//
//    // Their share of the expense
//    @Column(nullable = false, precision = 15, scale = 2)
//    private BigDecimal amount;
//}