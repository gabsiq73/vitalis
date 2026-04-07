package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.ProductRequestDTO;
import com.vitalis.demo.dto.response.PaymentResponseDTO;
import com.vitalis.demo.dto.response.ProductResponseDTO;
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
    Payment toEntity(ProductRequestDTO dto);
}
