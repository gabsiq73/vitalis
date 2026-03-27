package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.GasSettlement;

import java.math.BigDecimal;
import java.util.List;

public record GasSupplierReportDTO(
        String supplierName,
        BigDecimal totalToPay,  // Soma de todos os acertos com status de YOU_OWE
        BigDecimal totalToReceive, // Soma de todos os acertos com status de SUPPLIER_OWE
        BigDecimal netBalance, // totalToPay - totalToReceive
        List<GasSettlement> details // Lista detalhada para conferencia
) {
}
