package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.ClientUpdateDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.ClientMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.ClientRepository;
import com.vitalis.demo.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
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

    @Transactional(readOnly = true)
    public Optional<Client> findByIdController(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Client> listClient(Pageable pageable){
        return repository.findAll(pageable);
    }

    @Transactional
    public void delete(UUID id){
        Client client = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("This Client ID doesnt exist"));
        repository.delete(client);
    }

    @Transactional
    public Client save(Client client) {
       return repository.save(client);
    }

    @Transactional
    public void update(UUID id, ClientUpdateDTO dto) {
        Client client = findById(id);
        clientMapper.updateEntityFromDto(dto, client);
        repository.save(client);
    }

    @Transactional
    public void calculateDebt(Client client, BigDecimal debt){

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

    public BigDecimal processCustomerDebitBalance(UUID clientId){
        Client client = repository.findById(clientId)
                .orElseThrow(() -> new BusinessException("Cliente não encontrado!"));

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

        this.calculateDebt(client, outstandingBalance);

        return outstandingBalance;
    }

    private BigDecimal sumOrderItems(Order order){
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

//    Método atalho para uso interno no Service (Update, Delete, etc)
    public Client findById(UUID id) {
        return findByIdController(id).orElseThrow(() ->
                new EntityNotFoundException("Cliente com ID " + id + " não encontrado"));
    }

}
