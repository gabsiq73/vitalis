package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vitalis.demo.model.SystemUser;
import com.vitalis.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_registration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "logging.file.name=target/user-registration-test.log"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class UserRegistrationContractTest {
    private static final String PASSWORD = "registration-secret-12";
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @MockitoSpyBean PasswordEncoder encoder;

    @BeforeEach
    void emptyTestDatabase() {
        users.deleteAll();
    }

    @Test
    void firstAdminSellerAndAnotherAdminCanRegisterAnonymouslyAndAuthenticate(CapturedOutput output) throws Exception {
        assertThat(users.count()).isZero();
        http.perform(get("/users")).andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "SELLER", "ADMIN")) {
            String username = "operator" + users.count();
            ObjectNode request = request(username, username + "@example.test", role);
            JsonNode response = register(request, 201);
            assertThat(response.properties().stream().map(java.util.Map.Entry::getKey))
                    .containsExactlyInAnyOrder("id", "firstName", "lastName", "username", "email", "userRole");
            assertThat(response.get("userRole").asText()).isEqualTo(role);
            assertThat(response.get("lastName").isNull()).isTrue();
            SystemUser stored = users.findByUsername(username);
            assertThat(stored.getPassword()).startsWith("$2");
            assertThat(encoder.matches(PASSWORD, stored.getPassword())).isTrue();
            assertThat(output.getAll()).doesNotContain(PASSWORD, stored.getPassword());
            http.perform(get("/users/{id}", stored.getId()).with(httpBasic(username, PASSWORD)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.password").doesNotExist());
            http.perform(get("/config").with(httpBasic(username, PASSWORD))).andExpect(status().isOk());
            http.perform(get("/reports/performance").param("start", "2026-11-13").param("end", "2026-11-13")
                            .with(httpBasic(username, PASSWORD)))
                    .andExpect(role.equals("ADMIN") ? status().isOk() : status().isForbidden());
        }
        http.perform(get("/users").with(httpBasic("operator0", "wrong-password"))).andExpect(status().isUnauthorized());
        assertThat(users.count()).isEqualTo(3);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidFieldsWithoutPersisting(String field, String value) throws Exception {
        ObjectNode body = request("maria", "maria@example.test", "ADMIN");
        if (value == null) body.putNull(field); else body.put(field, value);
        JsonNode response = register(body, 422);
        assertThat(response.get("errors").findValuesAsText("field")).contains(field);
        assertThat(response.toString()).doesNotContain(PASSWORD);
        assertThat(users.count()).isZero();
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("firstName", null), Arguments.of("firstName", " "),
                Arguments.of("firstName", "M"), Arguments.of("firstName", "a".repeat(21)),
                Arguments.of("lastName", ""), Arguments.of("lastName", "S"), Arguments.of("lastName", "a".repeat(21)),
                Arguments.of("username", null), Arguments.of("username", " "),
                Arguments.of("username", "m"), Arguments.of("username", "a".repeat(31)),
                Arguments.of("email", null), Arguments.of("email", " "), Arguments.of("email", "invalid"),
                Arguments.of("email", "a".repeat(64) + "@" + "b".repeat(32) + ".com"),
                Arguments.of("password", null), Arguments.of("password", "      "), Arguments.of("password", "12345"),
                Arguments.of("userRole", null));
    }

    @Test
    void missingFieldsMalformedJsonAndInvalidRoleUseExistingHttpErrors() throws Exception {
        register(json.createObjectNode(), 422);
        for (String body : List.of("{", request("maria", "maria@example.test", "MANAGER").toString())) {
            http.perform(post("/users").contentType("application/json").content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.path").value("/users"))
                    .andExpect(jsonPath("$.message").value("JSON ou perfil de usuário inválido!"));
        }
        assertThat(users.count()).isZero();
    }

    @Test
    void invalidJsonDoesNotEchoPotentialCredentialsInResponsesOrLogs(CapturedOutput output) throws Exception {
        JsonNode response = register(request("maria", "maria@example.test", PASSWORD), 400);
        assertThat(response.toString()).doesNotContain(PASSWORD);
        assertThat(output.getAll()).doesNotContain(PASSWORD);
        assertThat(users.count()).isZero();
    }

    @Test
    void acceptsDocumentedLimitsAndOptionalSurname() throws Exception {
        var minimum = request("ab", "ab@example.test", "SELLER");
        minimum.put("firstName", "Ma").put("lastName", "Si").put("password", "123456");
        register(minimum, 201);
        var maximum = request("a".repeat(30), "a".repeat(64) + "@" + "b".repeat(31) + ".com", "ADMIN");
        maximum.put("firstName", "a".repeat(20)).put("lastName", "b".repeat(20));
        register(maximum, 201);
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void duplicateUsernameAndEmailReturnSpecificBusinessMessagesAndKeepOriginalAccount() throws Exception {
        register(request("maria", "maria@example.test", "ADMIN"), 201);
        String hash = users.findByUsername("maria").getPassword();
        JsonNode username = register(request("maria", "other@example.test", "SELLER"), 400);
        assertThat(username.get("message").asText()).isEqualTo("Nome de usuário já cadastrado!");
        JsonNode email = register(request("other", "maria@example.test", "SELLER"), 400);
        assertThat(email.get("message").asText()).isEqualTo("Email já cadastrado!");
        assertThat(email.get("error").asText()).isEqualTo("Erro de negócio");
        assertThat(email.get("path").asText()).isEqualTo("/users");
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findByUsername("maria").getPassword()).isEqualTo(hash);
    }

    @Test
    void keepsExistingCaseSensitiveComparisonAndDoesNotTrimIdentifiers() throws Exception {
        register(request("Maria", "Maria@example.test", "ADMIN"), 201);
        register(request("maria", "maria@example.test", "SELLER"), 201);
        JsonNode spaced = register(request(" maria ", "other@example.test", "SELLER"), 201);
        assertThat(spaced.get("username").asText()).isEqualTo(" maria ");
        assertThat(users.count()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentRegistrationPersistsOneAccountAndReturnsSpecific400(boolean sameUsername, CapturedOutput output) throws Exception {
        CyclicBarrier bothPassedPrechecks = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothPassedPrechecks.await(15, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(encoder).encode(any());
        var first = request("maria", "maria@example.test", "ADMIN");
        var second = request(sameUsername ? "maria" : "other",
                sameUsername ? "other@example.test" : "maria@example.test", "SELLER");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> submit(first));
            var right = executor.submit(() -> submit(second));
            var responses = List.of(left.get(25, TimeUnit.SECONDS), right.get(25, TimeUnit.SECONDS));
            assertThat(responses.stream().map(result -> result.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 400);
            for (var result : responses) {
                String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                assertThat(body).doesNotContain(PASSWORD, "user_password", "$2");
                if (result.getResponse().getStatus() == 400) {
                    assertThat(json.readTree(body).get("message").asText()).isEqualTo(sameUsername
                            ? "Nome de usuário já cadastrado!" : "Email já cadastrado!");
                }
            }
        }
        assertThat(users.count()).isEqualTo(1);
        String hash = users.findAll().getFirst().getPassword();
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(output.getAll()).doesNotContain(PASSWORD, hash);
    }

    private ObjectNode request(String username, String email, String role) {
        return json.createObjectNode().put("firstName", "Maria").put("username", username)
                .put("email", email).put("password", PASSWORD).put("userRole", role);
    }

    private MvcResult submit(ObjectNode request) throws Exception {
        return http.perform(post("/users").contentType("application/json").content(request.toString())).andReturn();
    }

    private JsonNode register(ObjectNode request, int expectedStatus) throws Exception {
        MvcResult result = http.perform(post("/users").contentType("application/json").content(request.toString()))
                .andExpect(status().is(expectedStatus)).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
