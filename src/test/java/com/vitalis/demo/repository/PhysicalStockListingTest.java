package com.vitalis.demo.repository;

import com.vitalis.demo.mapper.StockMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.service.StockService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.datasource.url=jdbc:h2:mem:physical_stock;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(StockService.class)
class PhysicalStockListingTest {
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired StockService service;
    final StockMapper mapper = Mappers.getMapper(StockMapper.class);

    @Test void legacyGasCannotCreateAlertOrDistortWaterPagination() {
        add("A Gas legado", ProductType.GAS, 0, 5);
        Stock normal = add("B Agua normal", ProductType.WATER, 200, 5);
        Stock low = add("C Agua baixa", ProductType.WATER, 5, 5);
        var first = service.findAll(PageRequest.of(0, 1, Sort.by("product.name")));
        var second = service.findAll(PageRequest.of(1, 1, Sort.by("product.name")));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).containsExactly(normal);
        assertThat(second.getContent()).containsExactly(low);
        assertThat(mapper.toResponseDTO(first.getContent().getFirst()).status()).isEqualTo(StockStatus.NORMAL);
        assertThat(mapper.toResponseDTO(second.getContent().getFirst()).status()).isEqualTo(StockStatus.LOW_STOCK);
    }

    @Test void gasOnlyDatabaseHasNoPhysicalStock() {
        add("Gas legado", ProductType.GAS, 0, 5);
        var page = service.findAll(PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test void minimumIsInclusiveAndZeroAlwaysMeansOutOfStock() {
        var stock = new Stock(); stock.setMinimumStock(5);
        stock.setQuantityInStock(200); assertThat(mapper.calculateStatus(stock)).isEqualTo(StockStatus.NORMAL);
        stock.setQuantityInStock(5); assertThat(mapper.calculateStatus(stock)).isEqualTo(StockStatus.LOW_STOCK);
        stock.setQuantityInStock(4); assertThat(mapper.calculateStatus(stock)).isEqualTo(StockStatus.LOW_STOCK);
        stock.setQuantityInStock(0); assertThat(mapper.calculateStatus(stock)).isEqualTo(StockStatus.OUT_OF_STOCK);
        stock.setMinimumStock(0); assertThat(mapper.calculateStatus(stock)).isEqualTo(StockStatus.OUT_OF_STOCK);
    }

    private Stock add(String name, ProductType type, int quantity, int minimum) {
        var product = new Product(); product.setName(name); product.setType(type); product.setBasePrice(BigDecimal.TEN); audit(product); products.saveAndFlush(product);
        var stock = new Stock(); stock.setProduct(product); stock.setQuantityInStock(quantity); stock.setMinimumStock(minimum); audit(stock);
        return stocks.saveAndFlush(stock);
    }
    private void audit(BaseEntity entity) { entity.setCreateDate(LocalDateTime.now()); entity.setLastModifiedDate(LocalDateTime.now()); }
}
