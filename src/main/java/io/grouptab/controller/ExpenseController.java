package io.grouptab.controller;

import io.grouptab.dto.BalanceResponse;
import io.grouptab.dto.ExpenseRequest;
import io.grouptab.dto.ExpenseResponse;
import io.grouptab.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    // GET /api/groups/{groupId}/expenses
    @GetMapping
    public List<ExpenseResponse> getExpenses(@PathVariable Long groupId) {
        return expenseService.getExpenses(groupId);
    }

    // POST /api/groups/{groupId}/expenses
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@PathVariable Long groupId,
                                         @Valid @RequestBody ExpenseRequest request) {
        return expenseService.createExpense(groupId, request);
    }

    // DELETE /api/groups/{groupId}/expenses/{expenseId}
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long groupId,
                                              @PathVariable Long expenseId) {
        expenseService.deleteExpense(groupId, expenseId);
        return ResponseEntity.noContent().build();
    }

    // GET /api/groups/{groupId}/expenses/balances
    @GetMapping("/balances")
    public BalanceResponse getBalances(@PathVariable Long groupId) {
        return expenseService.getBalances(groupId);
    }

    // POST /api/groups/{groupId}/expenses/settle
    @PostMapping("/settle")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> settle(@PathVariable Long groupId,
                                       @RequestParam Long fromUserId,
                                       @RequestParam Long toUserId,
                                       @RequestParam BigDecimal amount) {
        expenseService.settle(groupId, fromUserId, toUserId, amount);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}