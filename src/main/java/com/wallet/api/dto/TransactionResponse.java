package com.wallet.api.dto;

import com.wallet.api.entity.Transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private Long transactionId;
    private Long fromWalletId;   // null for DEPOSIT
    private Long toWalletId;
    private BigDecimal amount;
    private TransactionType type;
    private LocalDateTime createdAt;
}
