package com.example.demo.controller

import com.example.demo.controller.dto.TransactionDto
import com.example.demo.exception.InsufficientAccountBalanceException
import com.example.demo.exception.NotFoundException.TransactionNotFoundException
import com.example.demo.model.Transaction
import com.example.demo.service.TransactionService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.bytebuddy.utility.RandomString
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.stream.Stream
import kotlin.random.Random.Default.nextInt

@WebMvcTest(controllers = [TransactionController::class])
@AutoConfigureMockMvc
@Import(TransactionControllerTestConfiguration::class)
class TransactionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionService: TransactionService

    private val tenantId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()
    private val amount = nextInt(1, 10).toDouble()
    private val agent = RandomString.make()

    @BeforeEach
    fun init() {
        clearMocks(transactionService)
    }

    @Test
    fun `should fetch transactions`() {
        val tr1Id = UUID.randomUUID()
        val tr2Id = UUID.randomUUID()
        val tr1Amount = nextInt(1, 10).toDouble()
        val tr2Amount = nextInt(1, 10).toDouble()
        val tr1CreatedOn = LocalDateTime.of(2021, 1, 1, 1, 1).toInstant(ZoneOffset.UTC)
        val tr2CreatedOn = LocalDateTime.of(2022, 2, 2, 2, 2).toInstant(ZoneOffset.UTC)
        every { transactionService.fetchTransactions(any(), any(), any(), any()) } returns
            listOf(
                Transaction(tr1Id, tenantId, customerId, tr1Amount, agent, tr1CreatedOn),
                Transaction(tr2Id, tenantId, customerId, tr2Amount, agent, tr2CreatedOn),
            )

        val rawResponse = mockMvc.get("/api/transactions") {
            param("tenantId", tenantId.toString())
            param("customerId", customerId.toString())
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsByteArray

        val transactions = objectMapper.readValue<List<TransactionDto>>(rawResponse)

        assertThat(transactions).containsExactly(
            TransactionDto(tenantId, customerId, tr1Amount, agent, tr1CreatedOn),
            TransactionDto(tenantId, customerId, tr2Amount, agent, tr2CreatedOn)
        )

        verify { transactionService.fetchTransactions(tenantId, customerId, 0, 100000) }
    }

    @Test
    fun `should return 400 on missing tenantId`() {
        mockMvc.get("/api/transactions") {
            param("customerId", customerId.toString())
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { transactionService.fetchTransactions(any(), any(), any(), any()) }
    }

    @Test
    fun `should return 400 on missing customerId`() {
        mockMvc.get("/api/transactions") {
            param("tenantId", tenantId.toString())
        }.andExpect {
            status { isBadRequest() }
        }

        verify(exactly = 0) { transactionService.fetchTransactions(any(), any(), any(), any()) }
    }

    @Test
    fun `should add transaction`() {
        val uuid = UUID.randomUUID()

        every { transactionService.bookAmount(any(), any(), any(), any()) } returns uuid

        val rawResponse = mockMvc.post("/api/transactions") {
            content = """
                {
                "tenantId": "$tenantId",
                "customerId": "$customerId",
                "amount": $amount,
                "agent": "$agent"
                }
            """.trimIndent()
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.contentAsByteArray

        val actual = objectMapper.readValue<UUID>(rawResponse)

        assertThat(actual).isEqualTo(uuid)
        verify { transactionService.bookAmount(tenantId, customerId, amount, agent) }
    }

    @Test
    fun `should return 403 on attempt to exceed balance`() {
        every {
            transactionService.bookAmount(
                any(),
                any(),
                any(),
                any()
            )
        } throws InsufficientAccountBalanceException(tenantId, customerId, amount)
        mockMvc.post("/api/transactions") {
            content = """
                {
                "tenantId": "$tenantId",
                "customerId": "$customerId",
                "amount": $amount,
                "agent": "$agent"
                }
            """.trimIndent()
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isForbidden() }
            content { string(equalTo("Unable to book [$amount] for tenant: [$tenantId], customer: [$customerId]")) }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidTransactionBookingRequests")
    fun `should return 400 on invalid transaction booking requests`(invalidRequest: String) {
        mockMvc.post("/api/transactions") {
            content = invalidRequest
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `should return id of rolled back transaction on successful rollback request`() {
        val transactionId = UUID.randomUUID()
        every { transactionService.rollbackTransaction(any()) } just runs
        val rawResponse = mockMvc.delete("/api/transactions/${transactionId}").andExpect {
            status { isOk() }
        }.andReturn().response.contentAsByteArray

        val actual = objectMapper.readValue<UUID>(rawResponse)
        assertThat(actual).isEqualTo(transactionId)

        verify { transactionService.rollbackTransaction(transactionId) }
    }

    @Test
    fun `should return 404 on invalid Id on a rollback request`() {
        val transactionId = UUID.randomUUID()
        every { transactionService.rollbackTransaction(any()) } throws TransactionNotFoundException(transactionId)
        mockMvc.delete("/api/transactions/$transactionId").andExpect {
            status { isNotFound() }
            content { string(equalTo("Transaction not found, id: [$transactionId]")) }
        }

        verify { transactionService.rollbackTransaction(transactionId) }
        verify(exactly = 0) { transactionService.bookAmount(any(), any(), any(), any()) }
    }

    companion object {
        @JvmStatic
        fun invalidTransactionBookingRequests(): Stream<String> = Stream.of(
            """
                {
                "tenantId": "${UUID.randomUUID()}",
                "amount": 10.0,
                "agent": "a sender"
                }
            """.trimIndent(),
            """
                {
                "customerId": "${UUID.randomUUID()}",
                "amount": 10.0,
                "agent": "a sender"
                }
            """.trimIndent(),
            """
                {
                "customerId": "${UUID.randomUUID()}",
                "tenantId": "${UUID.randomUUID()}",
                amount: 10.0
                }
            """.trimIndent(),
            """
                {
                "customerId": "wrong",
                "tenantId": "${UUID.randomUUID()}",
                amount: 10.0,
                "agent": "a sender"
                }
            """.trimIndent(),
            """
                {
                "customerId": "${UUID.randomUUID()}",
                "tenantId": "wrong",
                amount: 10.0,
                "agent": "a sender"
                }
            """.trimIndent(),
            """
                {
                "customerId": "${UUID.randomUUID()}",
                "tenantId": "${UUID.randomUUID()}",
                amount: "wrong",
                "agent": "a sender"
                }
            """.trimIndent()
        )
    }
}

@TestConfiguration
class TransactionControllerTestConfiguration {
    @Bean
    @Primary
    fun transactionService(): TransactionService {
        return mockk()
    }
}