package com.example.demo.service

import com.example.demo.exception.InsufficientAccountBalanceException
import com.example.demo.exception.NotFoundException.TransactionNotFoundException
import com.example.demo.model.Balance
import com.example.demo.model.TenantCustomerId
import com.example.demo.model.Transaction
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findAndModify
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TransactionService(
    private val mongoTemplate: MongoTemplate
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val upsertAndReturnNew = FindAndModifyOptions().upsert(true).returnNew(true)

    fun bookAmount(tenantId: UUID, customerId: UUID, amount: Double, agent: String): UUID {
        val query = Query()
            .addCriteria(where("id").`is`(TenantCustomerId(tenantId, customerId)))
        val update = Update().inc("total", amount)

        return runCatching {
            val newBalance = mongoTemplate.findAndModify<Balance>(query, update, upsertAndReturnNew)
            if (compareValues(newBalance?.total, 0.0) < 0) {
                mongoTemplate.findAndModify<Balance>(query, Update().inc("total", amount * -1), upsertAndReturnNew)
                throw InsufficientAccountBalanceException(tenantId, customerId, amount)
            }
            mongoTemplate.save(
                Transaction(tenantId = tenantId, customerId = customerId, amount = amount, agent = agent)
            ).id
        }.getOrElse {
            logger.error("Error while updating balance, tenantId: [$tenantId], customerId: [$customerId], amount: [$amount]")
            throw it
        }
    }

    fun fetchTransactions(tenantId: UUID, customerId: UUID, skip: Int = 0, limit: Int = 100000): List<Transaction> {
        return mongoTemplate.find<Transaction>(
            Query()
                .addCriteria(where("tenantId").`is`(tenantId))
                .addCriteria(where("customerId").`is`(customerId))
                .with(Sort.by("createdOn").descending())
                .skip(skip.toLong())
                .limit(limit)
        )
    }

    fun rollbackTransaction(transactionId: UUID) {
        val toRollBack = mongoTemplate
            .findOne<Transaction>(Query().addCriteria(where("id").`is`(transactionId)))
            ?: throw TransactionNotFoundException(transactionId)
        bookAmount(toRollBack.tenantId, toRollBack.customerId, toRollBack.amount * -1, toRollBack.agent)
    }
}
