package com.example.demo.controller

import com.example.demo.controller.dto.BalanceDto
import com.example.demo.model.Balance
import com.example.demo.service.BalanceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class BalanceController(private val balanceService: BalanceService) {
    @GetMapping("/api/balances")
    fun fetchBalance(@RequestParam tenantId: UUID, @RequestParam customerId: UUID): BalanceDto {
        return balanceService.findBalanceById(tenantId, customerId).toDto()
    }

    private fun Balance.toDto(): BalanceDto = BalanceDto(this.total)
}
