package com.banking.core.service;

import com.banking.core.domain.entity.Account;
import com.banking.core.domain.entity.User;
import com.banking.core.domain.enums.AccountStatus;
import com.banking.core.domain.enums.AccountType;
import com.banking.core.domain.enums.Role;
import com.banking.core.dto.request.TransferRequest;
import com.banking.core.repository.AccountRepository;
import com.banking.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Concurrency integration test verifying deadlock prevention and ACID integrity
 * during mutual concurrent transfers between accounts.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentTransferIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private Account accountA;
    private Account accountB;

    @BeforeEach
    void setup() {
        User user = userRepository.save(User.builder()
            .email("tester" + System.currentTimeMillis() + "@test.com")
            .password("Password@123")
            .firstName("Test")
            .lastName("User")
            .role(Role.ADMIN) // ADMIN bypasses ownership check for raw concurrency test
            .build());

        accountA = accountRepository.save(Account.builder()
            .user(user)
            .accountNumber("ACCT-A-" + System.currentTimeMillis())
            .accountType(AccountType.SAVINGS)
            .status(AccountStatus.ACTIVE)
            .balance(new BigDecimal("10000.0000"))
            .currency("USD")
            .build());

        accountB = accountRepository.save(Account.builder()
            .user(user)
            .accountNumber("ACCT-B-" + System.currentTimeMillis())
            .accountType(AccountType.SAVINGS)
            .status(AccountStatus.ACTIVE)
            .balance(new BigDecimal("10000.0000"))
            .currency("USD")
            .build());
    }

    @Test
    @DisplayName("Mutual concurrent transfers between Account A and Account B must not deadlock")
    @WithMockUser(username = "admin@banking.com", roles = {"ADMIN"})
    void testMutualConcurrentTransfersNoDeadlock() throws InterruptedException {
        int threadCount = 20;
        int transfersPerDirection = 10;
        BigDecimal transferAmount = new BigDecimal("10.0000");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Direction 1: A -> B
        for (int i = 0; i < transfersPerDirection; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for simultaneous trigger
                    TransferRequest req = TransferRequest.builder()
                        .sourceAccountId(accountA.getId())
                        .targetAccountId(accountB.getId())
                        .amount(transferAmount)
                        .description("A to B transfer")
                        .build();
                    transferService.transferFunds(req, "127.0.0.1");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Direction 2: B -> A (Opposite direction concurrently)
        for (int i = 0; i < transfersPerDirection; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for simultaneous trigger
                    TransferRequest req = TransferRequest.builder()
                        .sourceAccountId(accountB.getId())
                        .targetAccountId(accountA.getId())
                        .amount(transferAmount)
                        .description("B to A transfer")
                        .build();
                    transferService.transferFunds(req, "127.0.0.1");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Trigger all threads at the same instant
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // Total successful transfers must equal thread count (no deadlocks occurred)
        assertEquals(20, successCount.get(), "All concurrent transfers must complete without deadlock");
        assertEquals(0, failureCount.get(), "No transfer should fail under deadlock");

        // System total money must be strictly conserved
        Account refreshedA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account refreshedB = accountRepository.findById(accountB.getId()).orElseThrow();

        BigDecimal totalBalance = refreshedA.getBalance().add(refreshedB.getBalance());
        assertEquals(new BigDecimal("20000.0000"), totalBalance, "Total money across accounts must be conserved");
    }
}
