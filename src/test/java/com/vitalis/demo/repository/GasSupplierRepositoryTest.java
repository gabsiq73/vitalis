package com.vitalis.demo.repository;

import com.vitalis.demo.model.GasSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class GasSupplierRepositoryTest {

    @Autowired
    private GasSupplierRepository repository;

    @Test
    void shouldSaveAndFind(){
        GasSupplier supplier = new GasSupplier();
        supplier.setName("Ultragas");
        supplier.setNotes("Distribuidora laranja");

        repository.save(supplier);

        if(supplier.getId() == null){
            throw new RuntimeException("Supplier was not saved");
        }

        Optional<GasSupplier> optional = repository.findById(supplier.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("Cannot findById");
        }

        GasSupplier found = optional.get();

        if(!found.getName().equals("Ultragas")){
            throw new RuntimeException("Wrong Supplier found");
        }

        if(!found.getNotes().equals("Distribuidora laranja")){
            throw new RuntimeException("Wrong Supplier notes found");
        }

        System.out.println(found);
    }

    @Test
    void shouldFindByName(){
        GasSupplier supplier = new GasSupplier();
        supplier.setName("Liquigas");
        supplier.setNotes("Distribuidora verde");

        repository.save(supplier);

        if(supplier.getId() == null){
            throw new RuntimeException("Supplier was not saved");
        }

        Optional<GasSupplier> optional = repository.findByName("Liquigas");

        if(optional.isEmpty()){
            throw new RuntimeException("Cannot findByName");
        }

        GasSupplier found = optional.get();

        if(!found.getName().equals("Liquigas")){
            throw new RuntimeException("Wrong name found!");
        }

        if(!found.getNotes().equals("Distribuidora verde")){
            throw new RuntimeException("Wrong notes found!");
        }

        System.out.println(found);

        Optional<GasSupplier> notFound = repository.findByName("Inexistente");

        if(notFound.isPresent()){
            throw new RuntimeException("Should not find supplier");
        }

    }


}
