package com.wallet.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor      // ← Jackson needs this to deserialize from Redis
@AllArgsConstructor     // ← @Builder requires this when NoArgs is present
public class WalletResponse {
    private Long walletId;
    private Long userId;
    private String userName;
    private String userEmail;
    private BigDecimal balance;
    private LocalDateTime createdAt;
}