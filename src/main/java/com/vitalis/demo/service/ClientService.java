package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.ClientRequestDTO;
import com.vitalis.demo.dto.response.ClientResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.ClientRepository;
import com.vitalis.demo.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ClientService {

    private final ClientRepository repository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Client findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Page<ClientResponseDTO> listClient(Pageable pageable){

        return repository.findAll(pageable)
                .map(ClientResponseDTO::fromEntity);
    }

    @Transactional
    public void delete(UUID id){
        Client client = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("This Client ID doesnt exist"));
        repository.delete(client);
    }

    @Transactional
    public ClientResponseDTO save(ClientRequestDTO requestDTO) {
        Client clientToSave = requestDTO.toModel();
        Client savedClient = repository.save(clientToSave);
        return ClientResponseDTO.fromEntity(savedClient);
    }

    @Transactional
    public void update(UUID id,ClientRequestDTO dto){
        Client clientUpdated = this.findById(id);

        if(dto.name() != null && !dto.name().isBlank()){
            clientUpdated.setName(dto.name());
        }
        if(dto.address() != null && !dto.address().isBlank()){
            clientUpdated.setAddress(dto.address());
        }
        if(dto.notes() != null && !dto.notes().isBlank()){
            clientUpdated.setNotes(dto.notes());
        }
        if(dto.clientType() != null) {
            clientUpdated.setClientType(dto.clientType() );
        }
        if(dto.clientStatus() != null){
            clientUpdated.setClientStatus(dto.clientStatus());
        }

        repository.save(clientUpdated);

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

}
