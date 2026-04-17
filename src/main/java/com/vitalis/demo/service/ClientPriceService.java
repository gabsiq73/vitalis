package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.repository.ClientPriceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientPriceService {

    private final ClientPriceRepository clientPriceRepository;
    private final ClientService clientService;
    private final ProductService productService;

    @Transactional
    public ClientPrice save(UUID clientId, UUID productId, BigDecimal customPrice) {
        Client client = clientService.findById(clientId);
        Product product = productService.findById(productId);

        // Busca se já existe um preço especial para esse par Cliente/Produto
        ClientPrice cp = clientPriceRepository.findByClientAndProduct(client, product)
                .orElse(new ClientPrice()); // Se não existir, cria um novo

        cp.setClient(client);
        cp.setProduct(product);
        cp.setPrice(customPrice);

        return clientPriceRepository.save(cp);
    }

    @Transactional(readOnly = true)
    public Optional<ClientPrice> findByControllerId(UUID id){
        return clientPriceRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public ClientPrice findById(UUID id){
        return findByControllerId(id)
                .orElseThrow(() -> new EntityNotFoundException("Preço especial de cliente não encontrado!"));
    }

    @Transactional(readOnly = true)
    public List<ClientPrice> findAllByClientId(UUID clientId){
        clientService.findById(clientId);
        return clientPriceRepository.findByClientId(clientId);
    }

    @Transactional
    public void delete(UUID id){
        ClientPrice clientPrice = findById(id);
        clientPriceRepository.delete(clientPrice);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateEffectivePrice(Client client, Product product){
        return clientPriceRepository.findByClientAndProduct(client, product)
                .map(ClientPrice::getPrice)
                .filter(price -> price != null)
                .orElse(product.getBasePrice());
    }
}
