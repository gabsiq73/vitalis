package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.response.GasSettlementResponseDTO;
import com.vitalis.demo.dto.response.GasSettlementMovementDTO;
import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.SettlementType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", imports = {CashMovementDirection.class, SettlementType.class})
public interface GasSettlementMapper {

    @Mapping(source = "gasSupplier.name", target = "supplierName")
    @Mapping(source = "orderItem.id", target = "orderItemId")
    @Mapping(source = "orderItem.order.client.name", target = "clientName")
    @Mapping(source = "orderItem.product.name", target = "productName")
    @Mapping(source = "orderItem.quantity", target = "quantity")
    @Mapping(source = "orderItem.unitPrice", target = "salePrice")
    @Mapping(target = "costPrice", expression = "java(entity.getSettlementType() == SettlementType.YOU_OWE ? entity.getAmount() : entity.getOrderItem().getUnitPrice().subtract(entity.getAmount()))")
    GasSettlementResponseDTO toResponseDTO(GasSettlement entity);

    List<GasSettlementResponseDTO> toResponseDTOList(List<GasSettlement> entities);

    @Mapping(source = "gasSupplier.name", target = "supplierName")
    @Mapping(source = "settlementType", target = "type")
    @Mapping(target = "direction", expression = "java(entity.getSettlementType() == SettlementType.SUPPLIER_OWE ? CashMovementDirection.IN : CashMovementDirection.OUT)")
    GasSettlementMovementDTO toMovementDTO(GasSettlement entity);
}
