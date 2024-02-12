package com.example.demo.exception

import java.util.*

class InsufficientAccountBalanceException(
    tenantId: UUID, customerId: UUID, amount: Double
) : RuntimeException(
    "Unable to book [$amount] for tenant: [$tenantId], customer: [$customerId]"
)

sealed class NotFoundException(message: String) : RuntimeException(message) {
    class AccountNotFoundException(tenantId: UUID, customerId: UUID) :
        NotFoundException("Account not found, tenantId: [$tenantId], customerId: [$customerId]")

    class TransactionNotFoundException(transactionId: UUID) : NotFoundException(
        "Transaction not found, id: [$transactionId]"
    )
}
