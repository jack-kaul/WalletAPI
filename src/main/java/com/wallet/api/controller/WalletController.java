package com.wallet.api.controller;

import com.wallet.api.dto.*;
import com.wallet.api.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Controllers should be thin — no business logic here.
// Every method just validates input (via @Valid) and delegates to the service.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    // ─────────────────────────────────────────────────────────────
    // POST /api/v1/wallets
    // Creates a new user + wallet.
    // Returns 201 Created with the wallet details.
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {

        WalletResponse response = walletService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/v1/wallets/{walletId}/deposit
    // Adds money to a wallet.
    // Returns 200 OK with updated wallet (new balance visible).
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/wallets/{walletId}/deposit")
    public ResponseEntity<WalletResponse> deposit(
            @PathVariable Long walletId,
            @Valid @RequestBody DepositRequest request) {

        WalletResponse response = walletService.deposit(walletId, request);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/v1/transfers
    // Transfers money between two wallets.
    // Returns 200 OK with the transaction record.
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request) {

        TransactionResponse response = walletService.transfer(request);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/v1/wallets/{walletId}/balance
    // Returns current wallet balance + metadata.
    // (In Step 4, this will hit Redis cache first.)
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/wallets/{walletId}/balance")
    public ResponseEntity<WalletResponse> getBalance(
            @PathVariable Long walletId) {

        WalletResponse response = walletService.getBalance(walletId);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/v1/wallets/{walletId}/transactions
    // Returns all transactions for this wallet (sent + received),
    // newest first.
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/wallets/{walletId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(
            @PathVariable Long walletId) {

        List<TransactionResponse> response = walletService.getTransactionHistory(walletId);
        return ResponseEntity.ok(response);
    }
}
