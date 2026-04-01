package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.response.StockResponseDTO;
import com.vitalis.demo.model.Stock;
import com.vitalis.demo.model.enums.StockStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StockMapper {

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    // O expression permite que voce escreva código java puro dentro da anotação de um mapeamento
    @Mapping(target = "status", expression = "java(calculateStatus(stock))")
    StockResponseDTO toResponseDTO(Stock stock);

    List<StockResponseDTO> toResponseList(List<Stock> stocks);

    default StockStatus calculateStatus(Stock stock) {
        if(stock.getQuantityInStock() <= 0) return StockStatus.OUT_OF_STOCK;
        if(stock.getQuantityInStock() <= stock.getMinimumStock()) return StockStatus.LOW_STOCK;
        return StockStatus.NORMAL;
    }

}
