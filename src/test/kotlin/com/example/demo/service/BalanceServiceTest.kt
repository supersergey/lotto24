package com.example.demo.service

import com.example.demo.TestContainersMongoConfig
import com.example.demo.exception.NotFoundException.AccountNotFoundException
import com.example.demo.model.Balance
import com.example.demo.model.TenantCustomerId
import net.bytebuddy.utility.RandomString
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID
import kotlin.random.Random.Default.nextInt

@SpringBootTest
@Import(TestContainersMongoConfig::class)
class BalanceServiceTest {
    @Autowired
    private lateinit var balanceService: BalanceService

    @Autowired
    private lateinit var transactionService: TransactionService

    private lateinit var tenantId: UUID
    private lateinit var customerId: UUID
    private val amount = nextInt(1, 10).toDouble()
    private val agent = RandomString.make()

    @BeforeEach
    fun setUp() {
        tenantId = UUID.randomUUID()
        customerId = UUID.randomUUID()
    }

    @Test
    fun `should return balance`() {
        transactionService.bookAmount(tenantId, customerId, amount, agent)

        val actual = balanceService.findBalanceById(tenantId, customerId)

        assertThat(actual).isEqualTo(
            Balance(TenantCustomerId(tenantId, customerId), amount)
        )
    }

    @Test
    fun `should throw on invalid ids`() {
        val actual = catchThrowable { balanceService.findBalanceById(tenantId, customerId) }

        assertThat(actual)
            .isExactlyInstanceOf(AccountNotFoundException::class.java)
            .hasMessage("Account not found, tenantId: [$tenantId], customerId: [$customerId]")
    }
}
