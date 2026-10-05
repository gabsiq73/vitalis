package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.PaymentRequestDTO;
import com.vitalis.demo.dto.response.DailyCashPaymentDTO;
import com.vitalis.demo.dto.response.PaymentResponseDTO;
import com.vitalis.demo.model.ClientCreditEntry;
import com.vitalis.demo.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(source = "date", target = "paymentDate")
    @Mapping(source = "method", target = "paymentMethod")
    @Mapping(source = "order.id", target = "orderId")
    PaymentResponseDTO toResponseDTO(Payment payment);

    @Mapping(source = "paymentDate", target = "date")
    @Mapping(source = "paymentMethod", target = "method")
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "id", ignore = true)
    Payment toEntity(PaymentRequestDTO dto);

    default DailyCashPaymentDTO toDailyCashPaymentDTO(Payment payment) {
        var order = payment.getOrder();
        var orderId = order.getId();
        return new DailyCashPaymentDTO(payment.getId(), orderId,
                "#" + orderId.toString().replace("-", "").substring(26).toUpperCase(),
                order.getClient().getName(), payment.getDate(), payment.getAmount(),
                payment.getMethod(), payment.getNotes(), "PAYMENT");
    }

    default DailyCashPaymentDTO toDailyCashPaymentDTO(ClientCreditEntry entry) {
        var source = entry.getSourcePayment();
        var orderId = source != null ? source.getOrder().getId() : null;
        String orderRef = orderId != null
                ? "#" + orderId.toString().replace("-", "").substring(26).toUpperCase()
                : "CRÉDITO";
        return new DailyCashPaymentDTO(entry.getId(), orderId, orderRef,
                entry.getClient().getName(), entry.getDate(), entry.getAmount(),
                entry.getMethod(), "Crédito gerado por pagamento excedente", "CLIENT_CREDIT");
    }
}
