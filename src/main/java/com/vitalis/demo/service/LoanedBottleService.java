package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.ProductResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.LoanedBottle;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.LoanStatus;
import com.vitalis.demo.repository.LoanedBottleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanedBottleService {

    private final LoanedBottleRepository repository;
    private final ClientService clientService;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public Optional<LoanedBottle> findByIdController(UUID id){
        return repository.findById(id);
    }

    public LoanedBottle findById(UUID id){
        return findByIdController(id)
                .orElseThrow(() -> new EntityNotFoundException("Registro de vasilhame com ID: "+ id + " não encontrado!"));
    }

    @Transactional
    public void save(LoanedBottle loanedBottle){
        if(loanedBottle.getQuantity() != null && loanedBottle.getQuantity() <= 0){
            throw new BusinessException("A quantidade deve ser maior que zero!");
        }

        if(loanedBottle.getId() == null){
            loanedBottle.setLoanStatus(LoanStatus.LOANED);
            if(loanedBottle.getLoanDate() == null){
                loanedBottle.setLoanDate(LocalDateTime.now());
            }
        }

        repository.save(loanedBottle);
    }

    @Transactional
    public void registerReturn(UUID id){
        LoanedBottle lb = findById(id);

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
