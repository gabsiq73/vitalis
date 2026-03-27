package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.LoanedBottle;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.LoanStatus;
import com.vitalis.demo.repository.LoanedBottleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanedBottleService {

    private final LoanedBottleRepository repository;
    private final ClientService clientService;
    private final ProductService productService;

    @Transactional
    public void registerBottleLoan(UUID clientId, UUID productId, Integer quantity){
        if(quantity <= 0){
            throw new BusinessException("A quantidade deve ser maior que zero!");
        }

        Client client = clientService.findById(clientId);
        Product product = productService.findById(productId);

        LoanedBottle lb = new LoanedBottle();
        lb.setProduct(product);
        lb.setClient(client);
        lb.setQuantity(quantity);
        lb.setLoanDate(LocalDateTime.now());
        lb.setLoanStatus(LoanStatus.LOANED);

        repository.save(lb);
    }

    @Transactional
    public void registerBottleReturn(UUID loanedBottleId){
        LoanedBottle lb = repository.findById(loanedBottleId)
                .orElseThrow(() -> new BusinessException("Registro de empréstimo de garrafão não encontrado!"));

        if(lb.getLoanStatus() == LoanStatus.RETURNED){
            throw new BusinessException("O garrafão já foi devolvido!");
        }

        lb.setReturnDate(LocalDateTime.now());
        lb.setLoanStatus(LoanStatus.RETURNED);

        repository.save(lb);
    }

    // Lista os garrafões emprestados que um cliente especifico tem
    @Transactional(readOnly = true)
    public List<LoanedBottle> listPendingLoansByClient(UUID clientId){
        return repository.findByClient_IdAndLoanStatus(clientId, LoanStatus.LOANED);
    }

    // Lista de todos os garrafões emprestados
    @Transactional(readOnly = true)
    public List<LoanedBottle> findAllPendingReturns(){
        List<LoanedBottle> pendingBottles =  repository.findByReturnDateIsNull();
        if(pendingBottles.isEmpty()){
            return Collections.emptyList();
        }
        return pendingBottles;
    }

}
