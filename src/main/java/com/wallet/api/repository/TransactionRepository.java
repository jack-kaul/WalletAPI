package com.wallet.api.repository;

import com.wallet.api.entity.Transaction;
import com.wallet.api.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Returns all transactions where this wallet is either sender or receiver,
    // ordered newest first. This is your first real JPQL query — note it uses
    // entity field names (fromWallet, toWallet), not column names.
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.fromWallet = :wallet OR t.toWallet = :wallet " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findAllByWallet(@Param("wallet") Wallet wallet);
}
