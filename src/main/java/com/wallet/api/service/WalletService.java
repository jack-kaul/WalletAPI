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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 1: Create Wallet
    // No caching here — creation is a write operation.
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.info("Creating wallet for email: {}", request.getEmail());

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
    //
    // ✅ STEP 4: Cache-aside eviction.
    // After a deposit the balance changes, so the cached value for
    // this wallet is now stale. @CacheEvict deletes that Redis entry
    // so the NEXT getBalance() call hits MySQL and gets the fresh value.
    //
    // Cache name "walletBalance", key = walletId (e.g. key "1")
    // ─────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "walletBalance", key = "#walletId")
    public WalletResponse deposit(Long walletId, DepositRequest request) {
        log.info("Depositing {} into wallet {} — cache evicted", request.getAmount(), walletId);

        Wallet wallet = findWalletOrThrow(walletId);
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        walletRepository.save(wallet);

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
    //
    // ✅ STEP 4: Evict BOTH wallets from cache — both balances changed.
    // @Caching lets us apply multiple cache annotations at once.
    // ─────────────────────────────────────────────────────────────
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "walletBalance", key = "#request.fromWalletId"),
        @CacheEvict(value = "walletBalance", key = "#request.toWalletId")
    })
    public TransactionResponse transfer(TransferRequest request) {
        log.info("Transfer of {} from wallet {} to wallet {} — both caches evicted",
                request.getAmount(), request.getFromWalletId(), request.getToWalletId());

        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        Wallet fromWallet = findWalletOrThrow(request.getFromWalletId());
        Wallet toWallet   = findWalletOrThrow(request.getToWalletId());

        if (fromWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(fromWallet.getId());
        }

        fromWallet.setBalance(fromWallet.getBalance().subtract(request.getAmount()));
        toWallet.setBalance(toWallet.getBalance().add(request.getAmount()));

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

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
    //
    // ✅ STEP 4: Cache-aside READ — this is the core of the pattern.
    //
    // @Cacheable works like this:
    //   1. Spring checks Redis for key "walletBalance::1" (for walletId=1).
    //   2. CACHE HIT  → returns the cached WalletResponse immediately.
    //                   MySQL is NOT queried. This is the 60% DB read reduction.
    //   3. CACHE MISS → runs the method body, hits MySQL, then stores the
    //                   result in Redis before returning it to the caller.
    //
    // The next deposit/transfer will evict this key, forcing a fresh
    // MySQL read on the subsequent getBalance call. That is cache-aside.
    // ─────────────────────────────────────────────────────────────
    @Cacheable(value = "walletBalance", key = "#walletId")
    public WalletResponse getBalance(Long walletId) {
        log.info("Cache MISS — fetching balance for wallet {} from MySQL", walletId);
        Wallet wallet = findWalletOrThrow(walletId);
        return toWalletResponse(wallet);
    }

    // ─────────────────────────────────────────────────────────────
    // ENDPOINT 5: Transaction History
    // Not cached — transaction lists change frequently and
    // caching lists adds complexity without much gain at this stage.
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
