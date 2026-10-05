package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.response.OrderItemResponseDTO;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class OrderItemMapper {

    @Autowired
    protected GasSettlementRepository gasSettlementRepository;

    @Mapping(target = "order", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "gasSupplier", ignore = true)
    public abstract OrderItem toEntity(OrderItemRequestDTO dto);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "supplierId", source = "gasSupplier.id")
    @Mapping(target = "supplierName", source = "gasSupplier.name")
    @Mapping(target = "subTotal", expression = "java(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    @Mapping(target = "receivedByUs", expression = "java(receivedByUs(item))")
    @Mapping(target = "gasPaymentMethod", expression = "java(gasPaymentMethod(item))")
    public abstract OrderItemResponseDTO toResponseDTO(OrderItem item);

    protected Boolean receivedByUs(OrderItem item) {
        if (item.getProduct().getType() != ProductType.GAS) return null;
        return gasSettlementRepository.findByOrderItem(item)
                .map(settlement -> settlement.getSettlementType() == SettlementType.YOU_OWE)
                .orElse(null);
    }

    protected Method gasPaymentMethod(OrderItem item) {
        if (item.getProduct().getType() != ProductType.GAS || !Boolean.TRUE.equals(receivedByUs(item))) return null;
        return item.getOrder().getPayments().stream()
                .filter(Payment::isAutomaticGas).findFirst()
                .or(() -> item.getOrder().getPayments().stream().filter(payment -> !payment.isAutomaticGas()).findFirst())
                .map(Payment::getMethod).orElse(null);
    }
}
