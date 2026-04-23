package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
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

    @Transactional(readOnly = true)
    public ClientPrice findById(UUID id){
        return findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Preço especial de cliente não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<ClientPrice> findByIdOptional(UUID id){
        return clientPriceRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ClientPrice> findByClientId(UUID clientId){
        clientService.findById(clientId);
        return clientPriceRepository.findByClientId(clientId);
    }

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

    @Transactional
    public ClientPrice update(UUID clientId, UUID id, BigDecimal newPrice) {
        ClientPrice cp = findById(id);

        if (!cp.getClient().getId().equals(clientId)) {
            throw new BusinessException("Operação inválida: Este preço especial pertence a outro cliente.");
        }

        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O preço especial deve ser maior que zero. Para brinde, use o fluxo de fidelidade.");
        }

        if (newPrice.compareTo(cp.getProduct().getBasePrice()) > 0) {
            throw new BusinessException("O preço especial tem que ser menor que o preço base do produto");
        }

        cp.setPrice(newPrice);
        return clientPriceRepository.save(cp);
    }

    @Transactional
    public void delete(UUID clientId, UUID id) {
        ClientPrice cp = findById(id);

        if (!cp.getClient().getId().equals(clientId)) {
            throw new BusinessException("Operação negada: Este preço não pertence ao cliente informado.");
        }

        clientPriceRepository.delete(cp);
    }

    @Transactional(readOnly = true)
    public BigDecimal findEffectivePrice(Client client, Product product){
        return clientPriceRepository.findByClientAndProduct(client, product)
                .map(ClientPrice::getPrice)
                .filter(price -> price != null)
                .orElse(product.getBasePrice());
    }
}
