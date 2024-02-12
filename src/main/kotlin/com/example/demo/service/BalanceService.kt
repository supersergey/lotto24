package com.example.demo.service

import com.example.demo.exception.NotFoundException.AccountNotFoundException
import com.example.demo.model.Balance
import com.example.demo.model.TenantCustomerId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BalanceService(private val mongoTemplate: MongoTemplate) {
    fun findBalanceById(tenantId: UUID, customerId: UUID): Balance {
        return mongoTemplate.findById<Balance>(
            TenantCustomerId(tenantId, customerId)
        ) ?: throw AccountNotFoundException(tenantId, customerId)
    }
}
