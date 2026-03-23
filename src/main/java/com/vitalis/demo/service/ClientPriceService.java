package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.repository.ClientPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ClientPriceService {

    private final ClientPriceRepository clientPriceRepository;

    @Transactional(readOnly = true)
    public BigDecimal calculateEffectivePrice(Client client, Product product){
        return clientPriceRepository.findByClientAndProduct(client, product)
                .map(ClientPrice::getPrice)
                .filter(price -> price != null)
                .orElse(product.getBasePrice());
    }
}
