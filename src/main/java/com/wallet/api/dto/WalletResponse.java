package com.wallet.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WalletResponse {
    private Long walletId;
    private Long userId;
    private String userName;
    private String userEmail;
    private BigDecimal balance;
    private LocalDateTime createdAt;
}
