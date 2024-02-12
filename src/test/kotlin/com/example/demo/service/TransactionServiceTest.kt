package com.example.demo.service

import com.example.demo.TestContainersMongoConfig
import com.example.demo.exception.InsufficientAccountBalanceException
import com.example.demo.exception.NotFoundException
import net.bytebuddy.utility.RandomString
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.shaded.com.google.common.util.concurrent.AtomicDouble
import java.util.UUID
import kotlin.random.Random.Default.nextInt

@SpringBootTest
@Import(TestContainersMongoConfig::class)
class TransactionServiceTest {
    @Autowired
    private lateinit var transactionService: TransactionService

    @Autowired
    private lateinit var balanceService: BalanceService

    private lateinit var customerId: UUID
    private lateinit var tenantId: UUID
    private val agent = RandomString.make()

    @BeforeEach
    fun setUp() {
        customerId = UUID.randomUUID()
        tenantId = UUID.randomUUID()
    }

    @Test
    fun `should add amount`() {
        val threadCount = 100
        val iterations = 50
        val sum = AtomicDouble()
        val threads = (0 until threadCount)
            .map {
                Runnable {
                    repeat(iterations) {
                        val randomAmount = nextInt(1, 10).toDouble()
                        transactionService.bookAmount(tenantId, customerId, randomAmount, agent)
                        sum.addAndGet(randomAmount)
                        Thread.sleep(10)
                    }
                }
            }.map {
                Thread(it)
            }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val actualBalance = balanceService.findBalanceById(tenantId, customerId)
        assertThat(actualBalance.total).isEqualTo(sum.get())

        val actualTransactions = transactionService.fetchTransactions(tenantId, customerId)

        assertThat(actualTransactions).hasSize(threadCount * iterations)
    }

    @Test
    fun `should not allow crediting on negative amount`() {
        transactionService.bookAmount(tenantId, customerId, 20.0, agent)
        transactionService.bookAmount(tenantId, customerId, -19.0, agent)
        transactionService.bookAmount(tenantId, customerId, -1.0, agent)

        val actual = catchThrowable { transactionService.bookAmount(tenantId, customerId, -1.0, agent) }
        assertThat(actual).isExactlyInstanceOf(InsufficientAccountBalanceException::class.java)

        val actualTransactions = transactionService.fetchTransactions(tenantId, customerId)
        assertThat(actualTransactions).hasSize(3)

        val actualBalance = balanceService.findBalanceById(tenantId, customerId)
        assertThat(actualBalance.total).isEqualTo(0.0)
    }

    @Test
    fun `should fetch transactions for the given tenant and customer sorted by timestamp desc`() {
        transactionService.bookAmount(tenantId, customerId, 10.0, agent)
        transactionService.bookAmount(tenantId, customerId, 15.0, agent)
        transactionService.bookAmount(UUID.randomUUID(), UUID.randomUUID(), 20.0, agent)

        val actual = transactionService.fetchTransactions(tenantId, customerId)
        assertThat(actual).hasSize(2)
        assertThat(actual.first().amount).isEqualTo(15.0)
        assertThat(actual.last().amount).isEqualTo(10.0)
    }

    @Test
    fun `should apply skip and limit when fetching transactions`() {
        transactionService.bookAmount(tenantId, customerId, 10.0, agent)
        transactionService.bookAmount(tenantId, customerId, 15.0, agent)
        transactionService.bookAmount(tenantId, customerId, 20.0, agent)

        val actual = transactionService.fetchTransactions(tenantId, customerId, 1, 1)
        assertThat(actual).hasSize(1)
        assertThat(actual.first().amount).isEqualTo(15.0)
    }

    @Test
    fun `should issue a compensating transaction on rollback request`() {
        val id = transactionService.bookAmount(tenantId, customerId, 10.0, agent)

        transactionService.rollbackTransaction(id)

        val actualTransactions = transactionService.fetchTransactions(tenantId, customerId)

        assertThat(actualTransactions).hasSize(2)
        assertThat(actualTransactions.first().amount).isEqualTo(-10.0)
        assertThat(actualTransactions.last().amount).isEqualTo(10.0)

        val actualBalance = balanceService.findBalanceById(tenantId, customerId).total
        assertThat(actualBalance).isEqualTo(0.0)
    }

    @Test
    fun `should throw on unknown transaction id on a rollback request`() {
        val transactionId = UUID.randomUUID()
        val actual = catchThrowable { transactionService.rollbackTransaction(transactionId) }

        assertThat(actual)
            .isExactlyInstanceOf(NotFoundException.TransactionNotFoundException::class.java)
            .hasMessage("Transaction not found, id: [$transactionId]")
    }
}
