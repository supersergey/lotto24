package com.example.demo.controller.dto

import java.time.Instant
import java.util.UUID

data class AddTransactionRequest(
    val customerId: UUID,
    val tenantId: UUID,
    val amount: Double,
    val agent: String,
)

data class TransactionDto(
    val tenantId: UUID,
    val customerId: UUID,
    val amount: Double,
    val agent: String,
    val createdOn: Instant,
)
