package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.UserMapper;
import com.vitalis.demo.model.SystemUser;
import com.vitalis.demo.model.enums.Role;
import com.vitalis.demo.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {
    @Mock UserRepository repository;
    @Mock UserMapper mapper;
    @Mock PasswordEncoder encoder;
    @InjectMocks UserService service;

    @Test
    void duplicateUsernameIsRejectedBeforeEncodingOrSaving() {
        when(repository.existsByUsername("maria")).thenReturn(true);
        assertThatThrownBy(() -> service.save(user())).isInstanceOf(BusinessException.class)
                .hasMessage("Nome de usuário já cadastrado!");
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void duplicateEmailIsRejectedBeforeEncodingOrSaving() {
        when(repository.existsByEmail("maria@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.save(user())).isInstanceOf(BusinessException.class)
                .hasMessage("Email já cadastrado!");
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(encoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {SystemUser.USERNAME_UNIQUE_CONSTRAINT, SystemUser.EMAIL_UNIQUE_CONSTRAINT})
    void concurrentKnownUniqueViolationBecomesSpecificBusinessError(String constraint) {
        when(encoder.encode("registration-secret")).thenReturn("encoded");
        when(repository.saveAndFlush(any())).thenThrow(failure("23505", constraint));
        String message = constraint.equals(SystemUser.USERNAME_UNIQUE_CONSTRAINT)
                ? "Nome de usuário já cadastrado!" : "Email já cadastrado!";
        assertThatThrownBy(() -> service.save(user())).isInstanceOf(BusinessException.class).hasMessage(message);
        // Não consulta o banco depois que o PostgreSQL aborta a transação.
        verify(repository).existsByUsername("maria");
        verify(repository).existsByEmail("maria@example.test");
        verify(repository).saveAndFlush(any());
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tb_user_pkey", "another_unique_constraint", ""})
    void unrelatedUniqueViolationsAreNotDisguisedAsRegistrationConflicts(String constraint) {
        when(encoder.encode(any())).thenReturn("encoded");
        var exception = failure("23505", constraint);
        when(repository.saveAndFlush(any())).thenThrow(exception);
        assertThatThrownBy(() -> service.save(user())).isSameAs(exception);
    }

    @Test
    void nonUniqueIntegrityFailureKeepsItsOriginalMeaning() {
        when(encoder.encode(any())).thenReturn("encoded");
        var exception = failure("23502", SystemUser.EMAIL_UNIQUE_CONSTRAINT);
        when(repository.saveAndFlush(any())).thenThrow(exception);
        assertThatThrownBy(() -> service.save(user())).isSameAs(exception);
    }

    @Test
    void missingConstraintOrHibernateCauseDoesNotMaskDatabaseFailure() {
        when(encoder.encode(any())).thenReturn("encoded");
        var missing = failure("23505", null);
        var other = new DataIntegrityViolationException("Other persistence failure");
        when(repository.saveAndFlush(any())).thenThrow(missing).thenThrow(other);
        assertThatThrownBy(() -> service.save(user())).isSameAs(missing);
        assertThatThrownBy(() -> service.save(user())).isSameAs(other);
    }

    @Test
    void passwordIsEncodedBeforeFlushAndExcludedFromEntityString() {
        SystemUser user = user();
        assertThat(user.toString()).doesNotContain("registration-secret");
        when(encoder.encode("registration-secret")).thenReturn("encoded-password");
        when(repository.saveAndFlush(user)).thenReturn(user);
        assertThat(service.save(user)).isSameAs(user);
        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.toString()).doesNotContain("encoded-password");
    }

    private DataIntegrityViolationException failure(String sqlState, String constraint) {
        return new DataIntegrityViolationException("Persistence error",
                new ConstraintViolationException("Constraint error", new SQLException("Constraint error", sqlState),
                        "insert into tb_user (...) values (...) ", constraint));
    }

    private SystemUser user() {
        SystemUser user = new SystemUser();
        user.setFirstName("Maria");
        user.setUsername("maria");
        user.setEmail("maria@example.test");
        user.setPassword("registration-secret");
        user.setUserRole(Role.ADMIN);
        return user;
    }
}
