package com.example.demo.controller

import com.example.demo.controller.dto.BalanceDto
import com.example.demo.exception.NotFoundException.AccountNotFoundException
import com.example.demo.model.Balance
import com.example.demo.model.TenantCustomerId
import com.example.demo.service.BalanceService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.*
import kotlin.random.Random.Default.nextInt

@WebMvcTest(controllers = [BalanceController::class])
@AutoConfigureMockMvc
@Import(BalanceControllerTestConfiguration::class)
class BalanceControllerTest {
    @Autowired
    private lateinit var balanceService: BalanceService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val customerId = UUID.randomUUID()
    private val tenantId = UUID.randomUUID()
    private val amount = nextInt(1, 10).toDouble()

    @BeforeEach
    fun setUp() {
        clearMocks(balanceService)
    }

    @Test
    fun `should return customer's balance`() {
        val balance = Balance(
            TenantCustomerId(tenantId, customerId), amount
        )
        every { balanceService.findBalanceById(any(), any()) } returns balance

        val rawResponse = mockMvc.get("/api/balances") {
            param("tenantId", "$tenantId")
            param("customerId", "$customerId")
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsByteArray

        val actual = objectMapper.readValue<BalanceDto>(rawResponse)

        assertThat(actual).isEqualTo(BalanceDto(amount))

        verify { balanceService.findBalanceById(tenantId, customerId) }
    }

    @Test
    fun `should return 404 on unknown ids`() {
        every { balanceService.findBalanceById(any(), any()) } throws AccountNotFoundException(tenantId, customerId)

        mockMvc.get("/api/balances") {
            param("tenantId", "$tenantId")
            param("customerId", "$customerId")
        }.andExpect {
            status { isNotFound() }
            content { string(equalTo("Account not found, tenantId: [$tenantId], customerId: [$customerId]")) }
        }

        verify { balanceService.findBalanceById(tenantId, customerId) }
    }
}

@TestConfiguration
class BalanceControllerTestConfiguration {
    @Bean
    @Primary
    fun balanceService(): BalanceService {
        return mockk()
    }
}