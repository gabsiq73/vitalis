package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.GasSettlementReportDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.GasSettlementMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasSettlementServiceTest {

    @Mock private GasSettlementRepository repository;

    @Mock private GasSettlementMapper mapper;

    @InjectMocks
    private GasSettlementService gasSettlementService;

    private OrderItem orderItem;
    private GasSupplier supplier;

    @BeforeEach
    void setUp() {
        supplier = new GasSupplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Distribuidora Sul");

        Product gasProduct = new Product();
        gasProduct.setId(UUID.randomUUID());

        orderItem = new OrderItem();
        orderItem.setId(UUID.randomUUID());
        orderItem.setProduct(gasProduct);
        orderItem.setUnitPrice(new BigDecimal("120.00"));
        orderItem.setQuantity(1);
        orderItem.setGasSupplier(supplier);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // createAutomatedSettlement — a regra mais crítica do módulo financeiro
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createAutomatedSettlement")
    class CreateAutomatedSettlement {

        @Test
        @DisplayName("receivedByUs=true: depósito recebeu → tipo YOU_OWE com valor do custo")
        void shouldCreateYouOweSettlementWhenReceivedByUs() {
            // ARRANGE
            BigDecimal costPrice = new BigDecimal("90.00");

            // ACT
            gasSettlementService.createAutomatedSettlement(orderItem, true, costPrice);

            // ASSERT — captura o objeto salvo para inspecionar seus campos
            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository).save(captor.capture());

            GasSettlement saved = captor.getValue();
            assertThat(saved.getSettlementType()).isEqualTo(SettlementType.YOU_OWE);
            // Depósito recebeu → deve ao fornecedor o custo inteiro
            assertThat(saved.getAmount()).isEqualByComparingTo("90.00");
            assertThat(saved.getSettled()).isFalse();
            assertThat(saved.getGasSupplier()).isEqualTo(supplier);
        }

        @Test
        @DisplayName("receivedByUs=false: entregador recebeu → tipo SUPPLIER_OWE com valor do lucro")
        void shouldCreateSupplierOweSettlementWhenNotReceivedByUs() {
            // ARRANGE
            // Venda: R$120, Custo: R$90 → Lucro (comissão a receber): R$30
            BigDecimal costPrice = new BigDecimal("90.00");

            // ACT
            gasSettlementService.createAutomatedSettlement(orderItem, false, costPrice);

            // ASSERT
            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository).save(captor.capture());

            GasSettlement saved = captor.getValue();
            assertThat(saved.getSettlementType()).isEqualTo(SettlementType.SUPPLIER_OWE);
            // Entregador ficou com o dinheiro → fornecedor deve o lucro ao depósito
            assertThat(saved.getAmount()).isEqualByComparingTo("30.00"); // 120 - 90
            assertThat(saved.getSettled()).isFalse();
        }

        @Test
        @DisplayName("Lucro zero quando preço de venda e custo são iguais")
        void shouldCreateZeroProfitSettlementWhenPriceEqualsCost() {
            // Caso extremo: venda pelo preço de custo (sem margem)
            BigDecimal costPrice = new BigDecimal("120.00"); // igual ao unitPrice

            gasSettlementService.createAutomatedSettlement(orderItem, false, costPrice);

            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualByComparingTo("0.00");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // generateReportBySupplier
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("generateReportBySupplier")
    class GenerateReportBySupplier {

        private LocalDate start;
        private LocalDate end;

        @BeforeEach
        void setDates() {
            start = LocalDate.of(2025, 1, 1);
            end   = LocalDate.of(2025, 1, 31);
        }

        @Test
        @DisplayName("Deve lançar exceção quando não há acertos pendentes no período")
        void shouldThrowWhenNoSettlementsFound() {
            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    any(), any(), any())).thenReturn(List.of());

            assertThatThrownBy(() ->
                    gasSettlementService.generateReportBySupplier(supplier.getId(), start, end))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Não há acertos pendentes");
        }

        @Test
        @DisplayName("Deve calcular toPay e toReceive separadamente e o saldo líquido")
        void shouldCalculateToPayToReceiveAndNetBalance() {
            // ARRANGE — 2 acertos YOU_OWE (R$200 total) e 1 SUPPLIER_OWE (R$50)
            // saldo líquido = toPay - toReceive = 200 - 50 = 150 (devemos R$150 ao fornecedor)
            GasSettlement youOwe1 = buildSettlement(SettlementType.YOU_OWE, "120.00");
            GasSettlement youOwe2 = buildSettlement(SettlementType.YOU_OWE, "80.00");
            GasSettlement supplierOwes = buildSettlement(SettlementType.SUPPLIER_OWE, "50.00");

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    eq(supplier.getId()),
                    eq(start.atStartOfDay()),
                    eq(end.atTime(LocalTime.MAX))))
                    .thenReturn(List.of(youOwe1, youOwe2, supplierOwes));

            // ACT
            GasSettlementReportDTO report =
                    gasSettlementService.generateReportBySupplier(supplier.getId(), start, end);

            // ASSERT
            assertThat(report.totalToPay()).isEqualByComparingTo("200.00");
            assertThat(report.totalToReceive()).isEqualByComparingTo("50.00");
            assertThat(report.netBalance()).isEqualByComparingTo("150.00");
            assertThat(report.supplierName()).isEqualTo("Distribuidora Sul");
        }

        @Test
        @DisplayName("Deve usar startOfDay e endOfDay corretos para o intervalo de datas")
        void shouldQueryWithCorrectDateRange() {
            // Garante que o período pega do primeiro segundo de start ao último segundo de end
            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    any(), any(), any())).thenReturn(List.of());

            try {
                gasSettlementService.generateReportBySupplier(supplier.getId(), start, end);
            } catch (BusinessException ignored) { /* esperado, sem acertos */ }

            verify(repository).findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    eq(supplier.getId()),
                    eq(LocalDateTime.of(2025, 1, 1, 0, 0, 0)),
                    eq(LocalDate.of(2025, 1, 31).atTime(LocalTime.MAX))
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // markAsSettled
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("markAsSettled")
    class MarkAsSettled {

        @Test
        @DisplayName("Deve lançar exceção ao liquidar acerto já liquidado")
        void shouldThrowWhenAlreadySettled() {
            GasSettlement alreadySettled = new GasSettlement();
            alreadySettled.setSettled(true);

            when(repository.findById(any())).thenReturn(Optional.of(alreadySettled));

            assertThatThrownBy(() -> gasSettlementService.settleIndividual(UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("já foi liquidado");
        }

        @Test
        @DisplayName("Deve marcar como liquidado e definir a data do acerto")
        void shouldMarkAsSettledAndSetDate() {
            // ARRANGE
            GasSettlement pending = new GasSettlement();
            pending.setId(UUID.randomUUID());
            pending.setSettled(false);

            when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            gasSettlementService.settleIndividual(pending.getId());

            // ASSERT
            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository).save(captor.capture());

            GasSettlement saved = captor.getValue();
            assertThat(saved.getSettled()).isTrue();
            assertThat(saved.getSettledDate()).isNotNull();
            // Data do acerto deve ser recente (menos de 5 segundos atrás)
            assertThat(saved.getSettledDate())
                    .isAfter(LocalDateTime.now().minusSeconds(5));
        }

        @Test
        @DisplayName("Deve lançar exceção quando acerto não existe")
        void shouldThrowWhenSettlementNotFound() {
            when(repository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gasSettlementService.settleIndividual(UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("não encontrado");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // settledAllBySupplier
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("settledAllBySupplier")
    class SettledAllBySupplier {

        @Test
        @DisplayName("Deve lançar exceção quando não há acertos pendentes para liquidar")
        void shouldThrowWhenNothingToSettle() {
            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    any(), any(), any())).thenReturn(List.of());

            assertThatThrownBy(() ->
                    gasSettlementService.settleAllBySupplier(
                            supplier.getId(),
                            LocalDate.now().minusDays(30),
                            LocalDate.now()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Não há acertos pendentes");
        }

        @Test
        @DisplayName("Deve marcar todos os acertos do período como liquidados")
        void shouldMarkAllSettlementsAsSettled() {
            // ARRANGE
            GasSettlement s1 = new GasSettlement(); s1.setSettled(false);
            GasSettlement s2 = new GasSettlement(); s2.setSettled(false);
            GasSettlement s3 = new GasSettlement(); s3.setSettled(false);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    any(), any(), any())).thenReturn(List.of(s1, s2, s3));

            // ACT
            gasSettlementService.settleAllBySupplier(
                    supplier.getId(),
                    LocalDate.now().minusDays(30),
                    LocalDate.now());

            // ASSERT — saveAll chamado com todos marcados como settled
            ArgumentCaptor<List<GasSettlement>> captor = ArgumentCaptor.forClass(List.class);
            verify(repository).saveAll(captor.capture());

            captor.getValue().forEach(s -> {
                assertThat(s.getSettled()).isTrue();
                assertThat(s.getSettledDate()).isNotNull();
            });
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private GasSettlement buildSettlement(SettlementType type, String amount) {
        GasSettlement settlement = new GasSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setSettlementType(type);
        settlement.setAmount(new BigDecimal(amount));
        settlement.setSettled(false);
        settlement.setGasSupplier(supplier);
        return settlement;
    }
}