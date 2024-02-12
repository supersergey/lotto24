package com.example.demo.model

import org.springframework.data.annotation.Id
import java.time.Instant
import java.util.*

data class TenantCustomerId(
    val tenantId: UUID,
    val customerId: UUID
)

data class Balance(
    @Id
    val id: TenantCustomerId,
    val total: Double
)

data class Transaction(
    @Id
    val id: UUID = UUID.randomUUID(),
    val tenantId: UUID,
    val customerId: UUID,
    val amount: Double,
    val agent: String,
    val createdOn: Instant = Instant.now()
)