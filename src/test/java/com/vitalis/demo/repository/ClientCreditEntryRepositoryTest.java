package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.BaseEntity;
import com.vitalis.demo.model.ClientCreditEntry;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:credit_entry_test;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class ClientCreditEntryRepositoryTest {
    @Autowired private ClientRepository clientRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ClientCreditEntryRepository creditEntryRepository;

    @Test
    void sumsOnlyCashPaymentsAndEntriesAndDeletesEntryWithSourcePayment() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        Client client = new Client();
        client.setName("Cliente");
        client.setActive(true);
        client.setClientType(ClientType.RETAIL);
        client.setClientStatus(ClientStatus.PAID);
        client.setFidelity(null);
        audit(client);
        client = clientRepository.saveAndFlush(client);

        Order order = new Order();
        order.setClient(client);
        order.setStatus(OrderStatus.DELIVERED);
        audit(order);
        order = orderRepository.saveAndFlush(order);

        Payment cash = paymentRepository.saveAndFlush(payment(order, "70.00", Method.PIX, day.atTime(9, 0)));
        paymentRepository.saveAndFlush(payment(order, "30.00", Method.SALDO, day.atTime(11, 0)));

        ClientCreditEntry entry = new ClientCreditEntry();
        entry.setClient(client);
        entry.setAmount(new BigDecimal("30.00"));
        entry.setMethod(Method.PIX);
        entry.setDate(day.atTime(9, 0));
        entry.setSourcePayment(cash);
        audit(entry);
        creditEntryRepository.saveAndFlush(entry);
        ClientCreditEntry previousDay = new ClientCreditEntry();
        previousDay.setClient(client);
        previousDay.setAmount(new BigDecimal("40.00"));
        previousDay.setMethod(Method.DINHEIRO);
        previousDay.setDate(day.minusDays(1).atTime(15, 0));
        audit(previousDay);
        creditEntryRepository.saveAndFlush(previousDay);

        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        assertThat(paymentRepository.sumTotalReceived(start, end)).isEqualByComparingTo("70.00");
        assertThat(creditEntryRepository.sumAmountBetween(start, end)).isEqualByComparingTo("30.00");
        assertThat(creditEntryRepository.sumAmountBetween(day.minusDays(1).atStartOfDay(),
                day.minusDays(1).atTime(LocalTime.MAX))).isEqualByComparingTo("40.00");
        assertThat(paymentRepository.findByDateBetweenAndMethodNotOrderByDateDesc(start, end, Method.SALDO))
                .containsExactly(cash);
        assertThat(creditEntryRepository.findByDateBetweenOrderByDateDesc(start, end))
                .containsExactly(entry);

        creditEntryRepository.deleteBySourcePaymentIn(List.of(cash));
        creditEntryRepository.flush();
        assertThat(creditEntryRepository.sumAmountBetween(start, end)).isNull();
        assertThat(creditEntryRepository.findAll()).containsExactly(previousDay);
    }

    private Payment payment(Order order, String amount, Method method, LocalDateTime date) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal(amount));
        payment.setMethod(method);
        payment.setDate(date);
        audit(payment);
        return payment;
    }

    private void audit(BaseEntity entity) {
        entity.setCreateDate(LocalDateTime.of(2026, 10, 5, 8, 0));
        entity.setLastModifiedDate(LocalDateTime.of(2026, 10, 5, 8, 0));
    }
}
