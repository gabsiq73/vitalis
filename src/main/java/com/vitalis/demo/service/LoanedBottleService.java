package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.model.LoanedBottle;
import com.vitalis.demo.model.enums.LoanStatus;
import com.vitalis.demo.repository.LoanedBottleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanedBottleService {

    private final LoanedBottleRepository repository;
    private final ClientService clientService;
    private final ProductService productService;

    public LoanedBottle findById(UUID id){
        return findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de vasilhame com ID: "+ id + " não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<LoanedBottle> findByIdOptional(UUID id){
        return repository.findById(id);
    }

    // Lista os garrafões emprestados que um cliente especifico tem
    @Transactional(readOnly = true)
    public Page<LoanedBottle> findPendingByClient(UUID clientId, Pageable pageable){
        return repository.findByClient_IdAndLoanStatus(clientId, LoanStatus.LOANED, pageable);
    }

    @Transactional(readOnly = true)
    public List<LoanedBottle> findByOrderId(UUID orderId){
        return repository.findByOrder_Id(orderId);
    }

    // Lista de todos os garrafões emprestados
    @Transactional(readOnly = true)
    public Page<LoanedBottle> findPendingReturns(Pageable pageable){
        return repository.findByReturnDateIsNullOrderByLoanDateAsc(pageable);
    }

    @Transactional
    public LoanedBottle save(LoanedBottle loanedBottle){
        if(loanedBottle.getQuantity() != null && loanedBottle.getQuantity() <= 0){
            throw new BusinessException("A quantidade deve ser maior que zero!");
        }

        if(loanedBottle.getId() == null){
            loanedBottle.setLoanStatus(LoanStatus.LOANED);
            if(loanedBottle.getLoanDate() == null){
                loanedBottle.setLoanDate(LocalDateTime.now());
            }
        }

        return repository.save(loanedBottle);
    }

    @Transactional
    public void registerReturn(UUID id){
        LoanedBottle lb = findById(id);

        if(lb.getLoanStatus() == LoanStatus.RETURNED){
            throw new BusinessException("O garrafão já foi devolvido!");
        }

        lb.setReturnDate(LocalDateTime.now());
        lb.setLoanStatus(LoanStatus.RETURNED);
        clientService.updateBottleBalance(lb.getClient().getId(), lb.getQuantity());

        repository.save(lb);
    }


    @Transactional
    public void delete(UUID id){
        LoanedBottle lb = findById(id);

        if(lb.getLoanStatus() == LoanStatus.RETURNED){
            throw new BusinessException("Não é possível deletar um registro que já foi devolvido!");
        }

        repository.delete(lb);
    }

}
