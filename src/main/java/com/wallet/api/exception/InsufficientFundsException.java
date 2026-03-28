package com.wallet.api.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long walletId) {
        super("Insufficient funds in wallet with id: " + walletId);
    }
}
