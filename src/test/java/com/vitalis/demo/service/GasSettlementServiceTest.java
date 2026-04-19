package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.GasSettlementReportDTO;
import com.vitalis.demo.dto.response.GasSettlementResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.GasSettlementMapper;
import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GasSettlementService - Testes Unitários")
class GasSettlementServiceTest {

    @InjectMocks
    private GasSettlementService gasSettlementService;

    @Mock
    private GasSettlementRepository repository;

    @Mock
    private GasSettlementMapper mapper;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private GasSupplier supplier;
    private Product gasProduct;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        supplier = new GasSupplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Distribuidora Alpha");

        gasProduct = new Product();
        gasProduct.setId(UUID.randomUUID());
        gasProduct.setName("Gás P13");
        gasProduct.setType(ProductType.GAS);

        orderItem = new OrderItem();
        orderItem.setId(UUID.randomUUID());
        orderItem.setProduct(gasProduct);
        orderItem.setGasSupplier(supplier);
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(new BigDecimal("100.00"));
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("Deve retornar o acerto quando o ID existe")
        void shouldReturnSettlementWhenIdExists() {
            // Given
            UUID id = UUID.randomUUID();
            GasSettlement settlement = new GasSettlement();
            settlement.setId(id);
            when(repository.findById(id)).thenReturn(Optional.of(settlement));

            // When
            GasSettlement result = gasSettlementService.findById(id);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando o ID não existe")
        void shouldThrowWhenIdNotFound() {
            // Given
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> gasSettlementService.findById(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("não encontrado");
        }
    }

    // =========================================================================
    // createAutomatedSettlement
    // =========================================================================

    @Nested
    @DisplayName("createAutomatedSettlement - Criação de Acerto Automático")
    class CreateAutomatedSettlementTests {

        @Test
        @DisplayName("Deve criar acerto YOU_OWE com amount=costPrice quando receivedByUs=true (dinheiro no depósito)")
        void shouldCreateYouOweSettlementWhenMoneyReceivedByDeposit() {
            // Given
            BigDecimal costPrice = new BigDecimal("80.00");
            boolean receivedByUs = true;

            // When
            gasSettlementService.createAutomatedSettlement(orderItem, receivedByUs, costPrice);

            // Then
            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository, times(1)).save(captor.capture());

            GasSettlement saved = captor.getValue();
            assertThat(saved.getSettlementType()).isEqualTo(SettlementType.YOU_OWE);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(saved.getGasSupplier()).isEqualTo(supplier);
            assertThat(saved.getOrderItem()).isEqualTo(orderItem);
            assertThat(saved.getSettled()).isFalse();
        }

        @Test
        @DisplayName("Deve criar acerto SUPPLIER_OWE com amount=lucro quando receivedByUs=false (dinheiro com entregador)")
        void shouldCreateSupplierOweSettlementWhenMoneyWithDeliveryPerson() {
            // Given
            BigDecimal costPrice = new BigDecimal("80.00");
            // unitPrice = 100,00 → lucro = 100 - 80 = 20,00
            boolean receivedByUs = false;

            // When
            gasSettlementService.createAutomatedSettlement(orderItem, receivedByUs, costPrice);

            // Then
            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository, times(1)).save(captor.capture());

            GasSettlement saved = captor.getValue();
            assertThat(saved.getSettlementType()).isEqualTo(SettlementType.SUPPLIER_OWE);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(saved.getSettled()).isFalse();
        }

        @Test
        @DisplayName("Deve criar acerto SUPPLIER_OWE com lucro zero quando venda a custo (unitPrice == costPrice)")
        void shouldCreateZeroProfitSettlementWhenSoldAtCost() {
            // Given
            BigDecimal costPrice = new BigDecimal("100.00"); // igual ao unitPrice
            orderItem.setUnitPrice(new BigDecimal("100.00"));

            // When
            gasSettlementService.createAutomatedSettlement(orderItem, false, costPrice);

            // Then
            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository).save(captor.capture());

            assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(captor.getValue().getSettlementType()).isEqualTo(SettlementType.SUPPLIER_OWE);
        }

        @Test
        @DisplayName("Deve associar o fornecedor correto do item no acerto")
        void shouldLinkCorrectSupplierFromOrderItem() {
            // Given
            GasSupplier anotherSupplier = new GasSupplier();
            anotherSupplier.setId(UUID.randomUUID());
            anotherSupplier.setName("Distribuidora Beta");
            orderItem.setGasSupplier(anotherSupplier);

            // When
            gasSettlementService.createAutomatedSettlement(orderItem, true, new BigDecimal("80.00"));

            // Then
            ArgumentCaptor<GasSettlement> captor = ArgumentCaptor.forClass(GasSettlement.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getGasSupplier()).isEqualTo(anotherSupplier);
        }
    }

    // =========================================================================
    // settleAllBySupplier
    // =========================================================================

    @Nested
    @DisplayName("settleAllBySupplier - Liquidação em Lote por Fornecedor")
    class SettleAllBySupplierTests {

        private final LocalDate startDate = LocalDate.of(2024, 1, 1);
        private final LocalDate endDate = LocalDate.of(2024, 1, 31);

        @Test
        @DisplayName("Deve liquidar todos os acertos pendentes de um fornecedor no período")
        void shouldSettleAllPendingSettlementsForSupplier() {
            // Given
            GasSettlement s1 = buildPendingSettlement();
            GasSettlement s2 = buildPendingSettlement();
            List<GasSettlement> pending = List.of(s1, s2);

            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(pending);

            // When
            gasSettlementService.settleAllBySupplier(supplier.getId(), startDate, endDate);

            // Then
            assertThat(s1.getSettled()).isTrue();
            assertThat(s1.getSettledDate()).isNotNull();
            assertThat(s2.getSettled()).isTrue();
            assertThat(s2.getSettledDate()).isNotNull();
            verify(repository, times(1)).saveAll(pending);
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando não há acertos pendentes no período")
        void shouldThrowWhenNoPendingSettlementsFound() {
            // Given
            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(Collections.emptyList());

            // When / Then
            assertThatThrownBy(() -> gasSettlementService.settleAllBySupplier(
                    supplier.getId(), startDate, endDate))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("pendentes");

            verify(repository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Deve registrar a data e hora do acerto em cada settlement liquidado")
        void shouldRecordSettlementDateTimeForEachSettledItem() {
            // Given
            GasSettlement settlement = buildPendingSettlement();
            LocalDateTime beforeCall = LocalDateTime.now().minusSeconds(1);

            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(List.of(settlement));

            // When
            gasSettlementService.settleAllBySupplier(supplier.getId(), startDate, endDate);

            // Then
            assertThat(settlement.getSettledDate()).isAfterOrEqualTo(beforeCall);
        }
    }

    // =========================================================================
    // settleIndividual
    // =========================================================================

    @Nested
    @DisplayName("settleIndividual - Liquidação Individual")
    class SettleIndividualTests {

        @Test
        @DisplayName("Deve liquidar um acerto individual com sucesso")
        void shouldSettleIndividualSettlement() {
            // Given
            UUID id = UUID.randomUUID();
            GasSettlement settlement = buildPendingSettlement();
            settlement.setId(id);
            when(repository.findById(id)).thenReturn(Optional.of(settlement));

            // When
            gasSettlementService.settleIndividual(id);

            // Then
            assertThat(settlement.getSettled()).isTrue();
            assertThat(settlement.getSettledDate()).isNotNull();
            verify(repository, times(1)).save(settlement);
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando o acerto não é encontrado")
        void shouldThrowWhenSettlementNotFound() {
            // Given
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> gasSettlementService.settleIndividual(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("não encontrado");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando o acerto já foi liquidado")
        void shouldThrowWhenSettlementAlreadySettled() {
            // Given
            UUID id = UUID.randomUUID();
            GasSettlement settlement = buildPendingSettlement();
            settlement.setSettled(true);
            settlement.setSettledDate(LocalDateTime.now().minusDays(1));
            when(repository.findById(id)).thenReturn(Optional.of(settlement));

            // When / Then
            assertThatThrownBy(() -> gasSettlementService.settleIndividual(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("liquidado");

            verify(repository, never()).save(any());
        }
    }

    // =========================================================================
    // generateReportBySupplier
    // =========================================================================

    @Nested
    @DisplayName("generateReportBySupplier - Relatório de Acertos")
    class GenerateReportTests {

        private final LocalDate startDate = LocalDate.of(2024, 1, 1);
        private final LocalDate endDate = LocalDate.of(2024, 1, 31);

        @Test
        @DisplayName("Deve gerar relatório com saldo líquido correto (toPay - toReceive)")
        void shouldGenerateReportWithCorrectNetBalance() {
            // Given
            GasSettlement youOwe = buildSettlementOfType(SettlementType.YOU_OWE, new BigDecimal("150.00"));
            GasSettlement supplierOwes = buildSettlementOfType(SettlementType.SUPPLIER_OWE, new BigDecimal("40.00"));
            List<GasSettlement> settlements = List.of(youOwe, supplierOwes);

            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(settlements);
            when(mapper.toResponseDTOList(settlements)).thenReturn(
                    List.of(stubGasSettlementResponseDTO(), stubGasSettlementResponseDTO()));

            // When
            GasSettlementReportDTO report = gasSettlementService.generateReportBySupplier(
                    supplier.getId(), startDate, endDate);

            // Then
            assertThat(report.supplierName()).isEqualTo(supplier.getName());
            assertThat(report.totalToPay()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(report.totalToReceive()).isEqualByComparingTo(new BigDecimal("40.00"));
            // netBalance = 150 - 40 = 110
            assertThat(report.netBalance()).isEqualByComparingTo(new BigDecimal("110.00"));
            assertThat(report.details()).hasSize(2);
        }

        @Test
        @DisplayName("Deve gerar relatório com saldo líquido negativo quando fornecedor deve mais do que o depósito")
        void shouldGenerateReportWithNegativeNetBalance() {
            // Given
            GasSettlement youOwe = buildSettlementOfType(SettlementType.YOU_OWE, new BigDecimal("30.00"));
            GasSettlement supplierOwes = buildSettlementOfType(SettlementType.SUPPLIER_OWE, new BigDecimal("100.00"));

            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(List.of(youOwe, supplierOwes));
            when(mapper.toResponseDTOList(anyList())).thenReturn(List.of());

            // When
            GasSettlementReportDTO report = gasSettlementService.generateReportBySupplier(
                    supplier.getId(), startDate, endDate);

            // Then: netBalance = 30 - 100 = -70
            assertThat(report.netBalance()).isEqualByComparingTo(new BigDecimal("-70.00"));
        }

        @Test
        @DisplayName("Deve gerar relatório apenas com acertos YOU_OWE (sem SUPPLIER_OWE)")
        void shouldGenerateReportWithOnlyYouOweEntries() {
            // Given
            GasSettlement s1 = buildSettlementOfType(SettlementType.YOU_OWE, new BigDecimal("50.00"));
            GasSettlement s2 = buildSettlementOfType(SettlementType.YOU_OWE, new BigDecimal("70.00"));

            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(List.of(s1, s2));
            when(mapper.toResponseDTOList(anyList())).thenReturn(List.of());

            // When
            GasSettlementReportDTO report = gasSettlementService.generateReportBySupplier(
                    supplier.getId(), startDate, endDate);

            // Then
            assertThat(report.totalToPay()).isEqualByComparingTo(new BigDecimal("120.00"));
            assertThat(report.totalToReceive()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(report.netBalance()).isEqualByComparingTo(new BigDecimal("120.00"));
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando não há acertos pendentes no período para o relatório")
        void shouldThrowWhenNoSettlementsForReport() {
            // Given
            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(Collections.emptyList());

            // When / Then
            assertThatThrownBy(() -> gasSettlementService.generateReportBySupplier(
                    supplier.getId(), startDate, endDate))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("pendentes");
        }

        @Test
        @DisplayName("Deve usar o nome do fornecedor do primeiro acerto encontrado")
        void shouldUseSupplierNameFromFirstSettlement() {
            // Given
            GasSettlement settlement = buildSettlementOfType(SettlementType.YOU_OWE, new BigDecimal("100.00"));

            LocalDateTime startDt = startDate.atStartOfDay();
            LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

            when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                    supplier.getId(), startDt, endDt)).thenReturn(List.of(settlement));
            when(mapper.toResponseDTOList(anyList())).thenReturn(List.of());

            // When
            GasSettlementReportDTO report = gasSettlementService.generateReportBySupplier(
                    supplier.getId(), startDate, endDate);

            // Then
            assertThat(report.supplierName()).isEqualTo("Distribuidora Alpha");
        }
    }

    // =========================================================================
    // delete / deleteByOrderItem
    // =========================================================================

    @Nested
    @DisplayName("delete / deleteByOrderItem")
    class DeleteTests {

        @Test
        @DisplayName("delete deve remover o acerto quando encontrado pelo ID")
        void shouldDeleteSettlementById() {
            // Given
            UUID id = UUID.randomUUID();
            GasSettlement settlement = new GasSettlement();
            settlement.setId(id);
            when(repository.findById(id)).thenReturn(Optional.of(settlement));

            // When
            gasSettlementService.delete(id);

            // Then
            verify(repository, times(1)).delete(settlement);
        }

        @Test
        @DisplayName("delete deve lançar BusinessException quando o ID não existe")
        void shouldThrowWhenDeletingNonExistentId() {
            // Given
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> gasSettlementService.delete(id))
                    .isInstanceOf(BusinessException.class);

            verify(repository, never()).delete(any(GasSettlement.class));
        }

        @Test
        @DisplayName("deleteByOrderItem deve remover o acerto vinculado ao item")
        void shouldDeleteSettlementByOrderItem() {
            // Given
            GasSettlement settlement = buildPendingSettlement();
            when(repository.findByOrderItem(orderItem)).thenReturn(Optional.of(settlement));

            // When
            gasSettlementService.deleteByOrderItem(orderItem);

            // Then
            verify(repository, times(1)).delete(settlement);
        }

        @Test
        @DisplayName("deleteByOrderItem deve lançar BusinessException quando nenhum acerto está vinculado ao item")
        void shouldThrowWhenNoSettlementLinkedToOrderItem() {
            // Given
            when(repository.findByOrderItem(orderItem)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> gasSettlementService.deleteByOrderItem(orderItem))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("não encontrado");

            verify(repository, never()).delete(any(GasSettlement.class));
        }
    }

    // =========================================================================
    // findAll
    // =========================================================================

    @Test
    @DisplayName("findAll deve retornar todos os acertos do repositório")
    void shouldReturnAllSettlements() {
        // Given
        List<GasSettlement> allSettlements = List.of(new GasSettlement(), new GasSettlement());
        when(repository.findAll()).thenReturn(allSettlements);

        // When
        List<GasSettlement> result = gasSettlementService.findAll();

        // Then
        assertThat(result).hasSize(2);
        verify(repository, times(1)).findAll();
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    /**
     * Cria um GasSettlementResponseDTO válido com todos os campos nulos.
     * Necessário pois record não possui construtor vazio.
     */
    private GasSettlementResponseDTO stubGasSettlementResponseDTO() {
        return new GasSettlementResponseDTO(null, null, null, null, null, null, null);
    }

    private GasSettlement buildPendingSettlement() {
        GasSettlement s = new GasSettlement();
        s.setId(UUID.randomUUID());
        s.setGasSupplier(supplier);
        s.setOrderItem(orderItem);
        s.setSettled(false);
        s.setAmount(new BigDecimal("80.00"));
        s.setSettlementType(SettlementType.YOU_OWE);
        return s;
    }

    private GasSettlement buildSettlementOfType(SettlementType type, BigDecimal amount) {
        GasSettlement s = buildPendingSettlement();
        s.setSettlementType(type);
        s.setAmount(amount);
        s.setGasSupplier(supplier);
        return s;
    }
}