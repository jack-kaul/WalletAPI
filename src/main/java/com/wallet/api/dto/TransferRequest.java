package com.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferRequest {

    @NotNull(message = "fromWalletId is required")
    private Long fromWalletId;

    @NotNull(message = "toWalletId is required")
    private Long toWalletId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
    private BigDecimal amount;
}
