package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.repository.ClientPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientPriceService {

    private final ClientPriceRepository clientPriceRepository;
    private final ClientService service;
    private final ProductService productService;

    @Transactional
    public ClientPrice save(UUID clientId, UUID productId, BigDecimal customPrice) {
        Client client = service.findById(clientId);
        Product product = productService.findEntityById(productId);

        // Busca se já existe um preço especial para esse par Cliente/Produto
        ClientPrice cp = clientPriceRepository.findByClientAndProduct(client, product)
                .orElse(new ClientPrice()); // Se não existir, cria um novo

        cp.setClient(client);
        cp.setProduct(product);
        cp.setPrice(customPrice);

        return clientPriceRepository.save(cp);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateEffectivePrice(Client client, Product product){
        return clientPriceRepository.findByClientAndProduct(client, product)
                .map(ClientPrice::getPrice)
                .filter(price -> price != null)
                .orElse(product.getBasePrice());
    }
}
