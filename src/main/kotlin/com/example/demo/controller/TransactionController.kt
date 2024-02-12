package com.example.demo.controller

import com.example.demo.controller.dto.AddTransactionRequest
import com.example.demo.controller.dto.TransactionDto
import com.example.demo.model.Transaction
import com.example.demo.service.TransactionService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class TransactionController(private val transactionService: TransactionService) {
    @PostMapping("/api/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addTransaction(@RequestBody addTransactionRequest: AddTransactionRequest): UUID {
        return transactionService.bookAmount(
            addTransactionRequest.tenantId,
            addTransactionRequest.customerId,
            addTransactionRequest.amount,
            addTransactionRequest.agent
        )
    }

    @GetMapping("/api/transactions")
    fun fetchTransactions(
        @RequestParam tenantId: UUID,
        @RequestParam customerId: UUID,
        @RequestParam(required = false, defaultValue = "0") skip: Int,
        @RequestParam(required = false, defaultValue = "100000") limit: Int
    ): List<TransactionDto> = transactionService.fetchTransactions(tenantId, customerId, skip, limit)
        .map { it.toDto() }

    private fun Transaction.toDto() = TransactionDto(
        tenantId = tenantId,
        customerId = customerId,
        amount = amount,
        agent = agent,
        createdOn = createdOn
    )

    @DeleteMapping("/api/transactions/{transactionId}")
    fun rollBackTransaction(@PathVariable transactionId: UUID): UUID {
        transactionService.rollbackTransaction(transactionId)
        return transactionId
    }
}