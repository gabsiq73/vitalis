package com.vitalis.demo.service;

import com.vitalis.demo.config.AuditConfig;
import com.vitalis.demo.dto.request.DepotBottleDebtRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.mapper.ClientMapperImpl;
import com.vitalis.demo.mapper.DepotBottleDebtMapperImpl;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import com.vitalis.demo.validator.ClientValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:depot_bottle_debts;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AuditConfig.class, DepotBottleDebtService.class, DepotBottleDebtMapperImpl.class,
        ClientService.class, ClientMapperImpl.class, ClientValidator.class, OrderBalanceService.class,
        LoanedBottleService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DepotBottleDebtServiceTest {
    @Autowired DepotBottleDebtService service;
    @Autowired DepotBottleDebtRepository debts;
    @Autowired ClientRepository clients;
    @Autowired LoanedBottleRepository loans;
    @Autowired LoanedBottleService loanService;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean ProductService productService;
    TransactionTemplate tx;
    UUID clientId;
    LocalDateTime date = LocalDateTime.of(2026, 11, 12, 9, 30);

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            debts.deleteAll();
            clientId = client("Cliente").getId();
        });
    }

    @Test
    void creationPersistsClientQuantityDateAndNotesInIndependentTransaction() {
        var created = service.save(request(4, "Cliente deixou os garrafões no depósito"));
        assertThat(created.id()).isNotNull();
        assertThat(created.clientId()).isEqualTo(clientId);
        assertThat(created.clientName()).isEqualTo("Cliente");
        assertThat(created.quantity()).isEqualTo(4);
        assertThat(created.debtDate()).isEqualTo(date);
        assertThat(created.notes()).isEqualTo("Cliente deixou os garrafões no depósito");
        assertThat(created.status()).isEqualTo(BottleDebtStatus.PENDING);
        assertThat(created.completionDate()).isNull();
        assertThat(service.findById(created.id())).isEqualTo(created);
        assertClientBalancesUnchanged();
    }

    @Test
    void notesAreOptional() {
        assertThat(service.save(request(1, null)).notes()).isNull();
    }

    @Test
    void missingZeroAndNegativeQuantitiesAreRejectedWithoutPersistence() {
        for (Integer quantity : Arrays.asList(null, 0, -1)) {
            assertThatThrownBy(() -> service.save(request(quantity, null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("quantidade");
        }
        assertThat(debts.count()).isZero();
    }

    @Test
    void missingDateIsRejected() {
        assertThatThrownBy(() -> service.save(new DepotBottleDebtRequestDTO(clientId, 1, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("data");
        assertThat(debts.count()).isZero();
    }

    @Test
    void nonexistentClientIsRejectedOnCreationAndFiltering() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.save(new DepotBottleDebtRequestDTO(missing, 1, date, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.findPending(missing, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(debts.count()).isZero();
    }

    @Test
    void pendingQueriesFilterByClientAndExcludeCompletedRecordsWithPagination() {
        var first = service.save(request(1, null));
        var second = service.save(new DepotBottleDebtRequestDTO(clientId, 2, date.plusDays(1), null));
        var completed = service.save(request(3, null));
        service.complete(completed.id());
        UUID otherId = tx.execute(status -> client("Outro cliente").getId());
        var other = service.save(new DepotBottleDebtRequestDTO(otherId, 5, date.plusDays(2), null));

        var page = service.findPending(clientId, PageRequest.of(0, 1, Sort.by("debtDate")));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).containsExactly(first);
        assertThat(service.findPending(clientId, PageRequest.of(1, 1, Sort.by("debtDate"))).getContent())
                .containsExactly(second);
        assertThat(service.findPending(null, PageRequest.of(0, 10)).getContent())
                .containsExactlyInAnyOrder(first, second, other);
    }

    @Test
    void existingClientWithoutDebtsHasEmptyPage() {
        assertThat(service.findPending(clientId, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void completionPreservesHistoryAndRepeatedCompletionCannotChangeIt() {
        var created = service.save(request(3, "Devolver ao cliente"));
        LocalDateTime before = LocalDateTime.now();
        service.complete(created.id());
        var completed = service.findById(created.id());
        assertThat(completed.status()).isEqualTo(BottleDebtStatus.COMPLETED);
        assertThat(completed.completionDate()).isBetween(before, LocalDateTime.now());
        assertThat(completed.quantity()).isEqualTo(created.quantity());
        assertThat(completed.debtDate()).isEqualTo(created.debtDate());
        assertThat(completed.notes()).isEqualTo(created.notes());
        assertThatThrownBy(() -> service.complete(created.id())).isInstanceOf(BusinessException.class);
        assertThat(service.findById(created.id())).isEqualTo(completed);
        assertThat(service.findPending(clientId, PageRequest.of(0, 10))).isEmpty();
        assertClientBalancesUnchanged();
    }

    @Test
    void nonexistentRecordIsNotFoundForLookupAndCompletion() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.findById(missing)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.complete(missing)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concurrentCompletionsHaveOnlyOneSuccess() throws Exception {
        UUID id = service.save(request(2, null)).id();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> complete = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Início não liberado");
                try {
                    service.complete(id);
                    return true;
                } catch (BusinessException alreadyCompleted) {
                    return false;
                }
            };
            var first = executor.submit(complete);
            var second = executor.submit(complete);
            start.countDown();
            assertThat(Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(service.findById(id).status()).isEqualTo(BottleDebtStatus.COMPLETED);
        assertThat(service.findById(id).completionDate()).isNotNull();
    }

    @Test
    void depotDebtDoesNotAffectLoanQueriesReturnsStockOrFinancialBalance() {
        UUID loanId = tx.execute(status -> {
            Product water = new Product();
            water.setName("Água " + UUID.randomUUID());
            water.setType(ProductType.WATER);
            products.saveAndFlush(water);
            Stock stock = new Stock();
            stock.setProduct(water);
            stock.setQuantityInStock(10);
            stocks.saveAndFlush(stock);
            LoanedBottle loan = new LoanedBottle();
            loan.setClient(clients.findById(clientId).orElseThrow());
            loan.setProduct(water);
            loan.setQuantity(2);
            return loanService.save(loan).getId();
        });
        var debt = service.save(request(5, null));
        service.complete(debt.id());
        assertClientBalancesUnchanged();
        tx.executeWithoutResult(status -> {
            assertThat(loanService.findPendingByClient(clientId, PageRequest.of(0, 10)))
                    .extracting(LoanedBottle::getId).containsExactly(loanId);
            assertThat(loanService.findPendingReturns(PageRequest.of(0, 100)).getContent())
                    .anyMatch(loan -> loan.getId().equals(loanId));
        });
        loanService.registerReturn(loanId);
        tx.executeWithoutResult(status -> {
            LoanedBottle loan = loans.findById(loanId).orElseThrow();
            assertThat(loan.getLoanStatus()).isEqualTo(LoanStatus.RETURNED);
            assertThat(loan.getReturnDate()).isNotNull();
            assertThat(loanService.findPendingByClient(clientId, PageRequest.of(0, 10))).isEmpty();
            assertThat(stocks.findByProduct(loan.getProduct()).orElseThrow().getQuantityInStock()).isEqualTo(10);
            assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("12.50");
        });
        assertThatThrownBy(() -> loanService.registerReturn(loanId)).isInstanceOf(BusinessException.class);
        assertThat(service.findById(debt.id()).status()).isEqualTo(BottleDebtStatus.COMPLETED);
    }

    private DepotBottleDebtRequestDTO request(Integer quantity, String notes) {
        return new DepotBottleDebtRequestDTO(clientId, quantity, date, notes);
    }

    private Client client(String name) {
        Client client = new Client();
        client.setName(name);
        client.setActive(true);
        client.setClientType(ClientType.RETAIL);
        client.setClientStatus(ClientStatus.PAID);
        client.setBalance(new BigDecimal("12.50"));
        client.setBottlesDebt(3);
        return clients.saveAndFlush(client);
    }

    private void assertClientBalancesUnchanged() {
        Client client = clients.findById(clientId).orElseThrow();
        assertThat(client.getBalance()).isEqualByComparingTo("12.50");
        assertThat(client.getBottlesDebt()).isEqualTo(3);
    }
}
