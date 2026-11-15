package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.datasource.url=jdbc:h2:mem:generic_loans;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "logging.file.name=target/generic-loans.log"})
@AutoConfigureMockMvc @WithMockUser(roles="ADMIN") @Transactional
class GenericBottleLoanTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json;
    @Autowired ClientRepository clients; @Autowired ProductRepository products;
    @Autowired LoanedBottleRepository loans; @Autowired OrderRepository orders;
    @Autowired StockRepository stocks; @Autowired BottleInventoryRepository inventory;
    Client client() { Client c=new Client(); c.setName("Empréstimo genérico"); c.setActive(true); c.setClientType(ClientType.RETAIL); c.setClientStatus(ClientStatus.PAID); c.setBalance(BigDecimal.ZERO); return clients.saveAndFlush(c); }
    JsonNode postLoan(Map<String,Object> body) throws Exception {
        return json.readTree(http.perform(post("/bottles").contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void createsListsEditsAndReturnsLoanUsingOnlyClientAndQuantity() throws Exception {
        Client c=client(); JsonNode loan=postLoan(Map.of("clientId",c.getId(),"quantity",2));
        assertThat(loan.get("productId").isNull()).isTrue(); assertThat(loan.get("productName").isNull()).isTrue();
        assertThat(loan.get("loanDate").asText()).isNotBlank(); assertThat(loan.get("status").asText()).isEqualTo("LOANED");
        http.perform(get("/bottles")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].quantity").value(2));
        http.perform(get("/bottles/client/"+c.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(loan.get("id").asText()));
        JsonNode edited=json.readTree(http.perform(put("/bottles/"+loan.get("id").asText()).contentType("application/json").content(json.writeValueAsString(Map.of("clientId",c.getId(),"quantity",3))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(edited.get("loanDate")).isEqualTo(loan.get("loanDate")); assertThat(edited.get("quantity").asInt()).isEqualTo(3);
        http.perform(patch("/bottles/"+loan.get("id").asText()+"/return")).andExpect(status().isNoContent());
        http.perform(get("/bottles/client/"+c.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(products.count()).isZero(); assertThat(stocks.count()).isZero(); assertThat(inventory.count()).isZero();
    }
    @Test void keepsLegacyProductAndDateWhenEditingWithoutProduct() throws Exception {
        Client c=client(); Product p=new Product(); p.setName("Água antiga"); p.setType(ProductType.WATER); p.setBasePrice(new BigDecimal("9")); p.setActive(true); products.saveAndFlush(p);
        JsonNode old=postLoan(Map.of("clientId",c.getId(),"productId",p.getId(),"quantity",1,"loanDate","2020-01-01T00:00:00"));
        http.perform(put("/bottles/"+old.get("id").asText()).contentType("application/json").content(json.writeValueAsString(Map.of("clientId",c.getId(),"quantity",2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productId").value(p.getId().toString())).andExpect(jsonPath("$.loanDate").value("2020-01-01T00:00:00"));
        assertThat(loans.count()).isEqualTo(1);
    }
    @Test void linksGenericLoanToOrderAndDeletesPendingLoan() throws Exception {
        Client c=client(); Order order=new Order(); order.setClient(c); order.setStatus(OrderStatus.PENDING); orders.saveAndFlush(order);
        JsonNode loan=postLoan(Map.of("clientId",c.getId(),"quantity",1,"orderId",order.getId()));
        http.perform(get("/bottles/order/"+order.getId())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(loan.get("id").asText()));
        http.perform(delete("/bottles/"+loan.get("id").asText())).andExpect(status().isNoContent()); assertThat(loans.count()).isZero();
    }
    @Test void stillRejectsMissingClientAndNonPositiveQuantity() throws Exception {
        Client c=client();
        for (String payload : new String[]{"{\"quantity\":1}","{\"clientId\":\""+c.getId()+"\",\"quantity\":0}","{\"clientId\":\""+c.getId()+"\",\"quantity\":-1}"})
            http.perform(post("/bottles").contentType("application/json").content(payload)).andExpect(status().isUnprocessableEntity());
    }
}
