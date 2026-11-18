package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.ClientUpdateDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.mapper.ClientMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.ClientRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.util.SearchText;
import com.vitalis.demo.validator.ClientValidator;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.text.Normalizer;
import java.util.Locale;

@Service
@AllArgsConstructor
public class ClientService {

    private final ClientRepository repository;
    private final OrderRepository orderRepository;
    private final ClientMapper clientMapper;
    private final ClientValidator validator;
    private final OrderBalanceService orderBalanceService;

    public Client findById(UUID id) {
        return findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + id + " não encontrado"));
    }

    @Transactional(readOnly = true)
    public Optional<Client> findByIdOptional(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Client findByIdIncludingInactive(UUID id) {
        return repository.findByIdIncludingInactive(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + id + " não encontrado"));
    }

    @Transactional(readOnly = true)
    public Page<Client> listClient(String name, ClientType type, ClientStatus status,
                                   boolean positiveBalance, Pageable pageable) {
        return repository.findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.isTrue(root.get("isActive")));
            filters.add(type == null
                    ? cb.notEqual(root.get("clientType"), ClientType.AVULSO)
                    : cb.equal(root.get("clientType"), type));
            if (name != null && !name.isBlank()) {
                filters.add(cb.like(SearchText.normalized(cb, root.get("name")), SearchText.pattern(name), '\\'));
            }
            if (status != null) {
                filters.add(cb.equal(root.get("clientStatus"), status));
            }
            if (positiveBalance) {
                filters.add(cb.greaterThan(root.<BigDecimal>get("balance"), BigDecimal.ZERO));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, pageable);
    }

    @Transactional
    public Client save(Client client) {
       client.setActive(true);
       validator.validate(client);
       return repository.save(client);
    }

    @Transactional
    public Client saveAvulso(String name) {
        Client client = new Client();
        client.setName(name != null && !name.isBlank() ? name : "Avulso");
        client.setClientType(ClientType.AVULSO);
        client.setClientStatus(ClientStatus.PAID);
        client.setActive(true);
        return repository.save(client);
    }

    @Transactional
    public void update(UUID id, ClientUpdateDTO dto) {
        Client client = findById(id);
        clientMapper.updateEntityFromDto(dto, client);
        validator.validate(client);

        repository.save(client);
    }

    @Transactional
    public void delete(UUID id){
        Client client = findById(id);
        client.setActive(false);
        repository.save(client);
    }

    @Transactional
    public BigDecimal correctBalance(UUID id, BigDecimal expectedBalance, BigDecimal balance) {
        Client client = repository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        if (client.getClientType() == ClientType.AVULSO) {
            throw new BusinessException("A correção de saldo exige um cliente cadastrado.");
        }

        BigDecimal current = client.getBalance() == null ? BigDecimal.ZERO : client.getBalance();
        if (current.signum() < 0 || getOutstandingDebt(id).signum() > 0) {
            throw new BusinessException("Corrija primeiro o valor dos pedidos em aberto.");
        }
        if (current.compareTo(balance) == 0) {
            return current;
        }
        if (current.compareTo(expectedBalance) != 0) {
            throw new BusinessException("O saldo mudou. Feche a correção e atualize a ficha do cliente.");
        }

        client.setBalance(balance);
        return balance;
    }

    @Transactional
    public void addCreditBalance(UUID clientId, BigDecimal amount) {
        Client client = findByIdIncludingInactive(clientId);

        BigDecimal currentBalance = (client.getBalance() != null) ? client.getBalance() : BigDecimal.ZERO;
        client.setBalance(currentBalance.add(amount));
        repository.save(client);
    }

    @Transactional
    public void consumeCreditBalance(UUID clientId, BigDecimal amount){
        Client client = findByIdIncludingInactive(clientId);
        BigDecimal currentBalance = (client.getBalance() != null) ? client.getBalance() : BigDecimal.ZERO;

        if(amount.compareTo(currentBalance) > 0){
            throw new BusinessException("Saldo insuficiente! O cliente possui apenas R$ " + currentBalance);
        }

        client.setBalance(currentBalance.subtract(amount));
        repository.save(client);
    }

    @Transactional
    public BigDecimal calculateDebtBalance(UUID clientId){
        Client client = findByIdIncludingInactive(clientId);

        // Busca todos os pedidos que ja foram entregues
        List<Order> orders = orderRepository.findByClientAndStatus(client, OrderStatus.DELIVERED);


        BigDecimal outstandingBalance = orders.stream()
                .map(orderBalanceService::calculateRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Persiste o saldo líquido: crédito existente abatido pela dívida dos pedidos entregues.
        BigDecimal current = client.getBalance() != null ? client.getBalance() : BigDecimal.ZERO;
        BigDecimal credit = current.compareTo(BigDecimal.ZERO) > 0 ? current : BigDecimal.ZERO;
        if (outstandingBalance.compareTo(BigDecimal.ZERO) > 0) {
            client.setBalance(credit.subtract(outstandingBalance));
        } else if (outstandingBalance.compareTo(BigDecimal.ZERO) == 0) {
            // Sem dívida: zera saldo negativo, mas preserva crédito
            if (current.compareTo(BigDecimal.ZERO) < 0) {
                client.setBalance(BigDecimal.ZERO);
            }
        }

        this.updateStatusByDebt(client, outstandingBalance);

        return outstandingBalance;
    }

    @Transactional(readOnly = true)
    public BigDecimal getOutstandingDebt(UUID clientId) {
        Client client = findById(clientId);
        List<Order> orders = orderRepository.findByClientAndStatus(client, OrderStatus.DELIVERED);

        return orders.stream()
                .map(orderBalanceService::calculateRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void updateStatusByDebt(Client client, BigDecimal debt){

        if(debt != null && debt.compareTo(BigDecimal.ZERO) < 0){
            throw new BusinessException("O saldo devedor não pode ser menor que zero!");
        }

        BigDecimal currentDebt = (debt != null)? debt : BigDecimal.ZERO;

        if(currentDebt.compareTo(BigDecimal.ZERO) == 0){
            client.setClientStatus(ClientStatus.PAID);
        }
        else if(currentDebt.compareTo(BigDecimal.ZERO) > 0){
            client.setClientStatus(ClientStatus.OVERDUE);
        }

        repository.save(client);
    }

    public void updateBottleBalance(UUID clientId, Integer quantity){
        Client client = findById(clientId);

        Integer newDebt = client.getBottlesDebt() + quantity;

        if(quantity < 0){
            throw new BusinessException("O cliente não pode devolver mais garrafões!");
        }

        client.setBottlesDebt(newDebt);
        repository.save(client);
    }



}
