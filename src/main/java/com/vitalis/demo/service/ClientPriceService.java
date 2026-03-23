package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ClientPriceService {

    @Transactional(readOnly = true)
    public BigDecimal calculateEffectivePrice(Client client, Product product){
        return clientPriceRepository.findByClientAndProduct(client, product)
                .map(ClientPrice::getPrice)
                .orElse(product.getBasePrice());
    }
}
