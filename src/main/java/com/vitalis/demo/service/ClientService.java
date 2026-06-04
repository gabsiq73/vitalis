package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.ClientUpdateDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.mapper.ClientMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.ClientRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.validator.ClientValidator;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ClientService {

    private final ClientRepository repository;
    private final OrderRepository orderRepository;
    private final ClientMapper clientMapper;
    private final ClientValidator validator;

    public Client findById(UUID id) {
        return findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + id + " não encontrado"));
    }

    @Transactional(readOnly = true)
    public Optional<Client> findByIdOptional(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Client> listClient(String name, ClientType type, Pageable pageable){
        if(name != null && type != null) return repository.findByNameContainingIgnoreCaseAndClientType(pageable, name, type);
        if(name != null) return repository.findByNameContainingIgnoreCaseAndClientTypeNot(pageable, name, ClientType.AVULSO);
        if(type != null) return repository.findByClientType(pageable, type);

        return repository.findByClientTypeNot(pageable, ClientType.AVULSO);
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
    public void addCreditBalance(UUID clientId, BigDecimal amount) {
        Client client = findById(clientId);

        BigDecimal currentBalance = (client.getBalance() != null) ? client.getBalance() : BigDecimal.ZERO;
        client.setBalance(currentBalance.add(amount));
        repository.save(client);
    }

    @Transactional
    public void consumeCreditBalance(UUID clientId, BigDecimal amount){
        Client client = findById(clientId);
        BigDecimal currentBalance = (client.getBalance() != null) ? client.getBalance() : BigDecimal.ZERO;

        if(amount.compareTo(currentBalance) > 0){
            throw new BusinessException("Saldo insuficiente! O cliente possui apenas R$ " + currentBalance);
        }

        client.setBalance(currentBalance.subtract(amount));
        repository.save(client);
    }

    @Transactional
    public BigDecimal calculateDebtBalance(UUID clientId){
        Client client = repository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado!"));

        // Busca todos os pedidos que ja foram entregues
        List<Order> orders = orderRepository.findByClientAndStatus(client, OrderStatus.DELIVERED);


        //Soma de todos os pedidos DELIVERED do cliente
        BigDecimal totalBought = orders.stream()
                .map(this::sumOrderItems)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //Pega o total pago pelo cliente
        BigDecimal totalPaid = orders.stream()
                .flatMap(order -> order.getPayments().stream())
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcula o resultado final = comprado - pago
        BigDecimal outstandingBalance = totalBought.subtract(totalPaid);

        // Persiste a dívida como saldo negativo para leitura direta no frontend.
        // Saldo positivo (crédito) só é ajustado pelo fluxo de pagamento.
        if (outstandingBalance.compareTo(BigDecimal.ZERO) > 0) {
            client.setBalance(outstandingBalance.negate());
        } else if (outstandingBalance.compareTo(BigDecimal.ZERO) == 0) {
            // Sem dívida: zera o saldo negativo, mas não toca crédito existente
            BigDecimal current = client.getBalance() != null ? client.getBalance() : BigDecimal.ZERO;
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

        BigDecimal totalBought = orders.stream()
                .map(this::sumOrderItems)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = orders.stream()
                .flatMap(order -> order.getPayments().stream())
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalBought.subtract(totalPaid);
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

    @Transactional
    public void addPointsFidelity(UUID id, Integer points){
        Client client = findById(id);
        client.getFidelity().addPoints(points);
    }

    private BigDecimal sumOrderItems(Order order){
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
