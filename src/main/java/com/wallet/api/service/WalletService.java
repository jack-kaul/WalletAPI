package com.wallet.api.service;

import com.wallet.api.dto.*;
import com.wallet.api.entity.Transaction;
import com.wallet.api.entity.Transaction.TransactionType;
import com.wallet.api.entity.User;
import com.wallet.api.entity.Wallet;
import com.wallet.api.exception.InsufficientFundsException;
import com.wallet.api.exception.WalletNotFoundException;
import com.wallet.api.repository.TransactionRepository;
import com.wallet.api.repository.UserRepository;
import com.wallet.api.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

// @Slf4j gives us a `log` variable for free (via Lombok)
// @RequiredArgsConstructor generates a constructor for all `final` fields — this is
// how Spring injects dependencies without @Autowired
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 1: Create Wallet
    // Creates a User + Wallet in one shot.
    // If a user with the same email already exists, we reuse them
    // and create a new wallet for them (one user can have many wallets).
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.info("Creating wallet for email: {}", request.getEmail());

        // findByEmail returns an Optional; if empty, build and save a new User
        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .name(request.getName())
                            .email(request.getEmail())
                            .build();
                    return userRepository.save(newUser);
                });

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();
        wallet = walletRepository.save(wallet);

        log.info("Wallet created with id: {} for user: {}", wallet.getId(), user.getId());
        return toWalletResponse(wallet);
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 2: Deposit
    // Adds money to a wallet. No source validation needed —
    // money comes from "outside" (cash, bank transfer, etc.)
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public WalletResponse deposit(Long walletId, DepositRequest request) {
        log.info("Depositing {} into wallet {}", request.getAmount(), walletId);

        Wallet wallet = findWalletOrThrow(walletId);

        // Add to balance — always use BigDecimal.add(), never += on floats/doubles
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        walletRepository.save(wallet);

        // Record the transaction — fromWallet is null for deposits
        Transaction transaction = Transaction.builder()
                .fromWallet(null)
                .toWallet(wallet)
                .amount(request.getAmount())
                .type(TransactionType.DEPOSIT)
                .build();
        transactionRepository.save(transaction);

        log.info("Deposit complete. New balance for wallet {}: {}", walletId, wallet.getBalance());
        return toWalletResponse(wallet);
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 3: Transfer
    // Moves money between two wallets atomically.
    // @Transactional means: if anything fails mid-way, the entire
    // operation is rolled back — no partial transfers.
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        log.info("Transfer of {} from wallet {} to wallet {}",
                request.getAmount(), request.getFromWalletId(), request.getToWalletId());

        // Guard: can't transfer to yourself
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        Wallet fromWallet = findWalletOrThrow(request.getFromWalletId());
        Wallet toWallet   = findWalletOrThrow(request.getToWalletId());

        // Guard: must have enough balance
        if (fromWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(fromWallet.getId());
        }

        // Debit sender, credit receiver
        fromWallet.setBalance(fromWallet.getBalance().subtract(request.getAmount()));
        toWallet.setBalance(toWallet.getBalance().add(request.getAmount()));

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // Record the transaction
        Transaction transaction = Transaction.builder()
                .fromWallet(fromWallet)
                .toWallet(toWallet)
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .build();
        transaction = transactionRepository.save(transaction);

        log.info("Transfer complete. TxnId: {}", transaction.getId());
        return toTransactionResponse(transaction);
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 4: Get Balance
    // Simple read — no @Transactional needed for reads.
    // (In Step 4, we'll add Redis here so this hits cache first.)
    // ─────────────────────────────────────────────────────────────
    public WalletResponse getBalance(Long walletId) {
        log.info("Fetching balance for wallet {}", walletId);
        Wallet wallet = findWalletOrThrow(walletId);
        return toWalletResponse(wallet);
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 5: Transaction History
    // Returns all transactions for a wallet (sent + received),
    // newest first — from the custom JPQL query in the repository.
    // ─────────────────────────────────────────────────────────────
    public List<TransactionResponse> getTransactionHistory(Long walletId) {
        log.info("Fetching transaction history for wallet {}", walletId);
        Wallet wallet = findWalletOrThrow(walletId);

        return transactionRepository.findAllByWallet(wallet)
                .stream()
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private Wallet findWalletOrThrow(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    // Maps Wallet entity → WalletResponse DTO
    // We never expose raw entities to the API layer — DTOs give us
    // control over what fields are returned.
    private WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUser().getId())
                .userName(wallet.getUser().getName())
                .userEmail(wallet.getUser().getEmail())
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .build();
    }

    // Maps Transaction entity → TransactionResponse DTO
    private TransactionResponse toTransactionResponse(Transaction txn) {
        return TransactionResponse.builder()
                .transactionId(txn.getId())
                .fromWalletId(txn.getFromWallet() != null ? txn.getFromWallet().getId() : null)
                .toWalletId(txn.getToWallet().getId())
                .amount(txn.getAmount())
                .type(txn.getType())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
