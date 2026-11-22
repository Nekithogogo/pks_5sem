package ru.mirea.autosalon.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.mirea.autosalon.api.domain.Role;
import ru.mirea.autosalon.api.domain.UserAccount;
import ru.mirea.autosalon.api.repository.AuditEventRepository;
import ru.mirea.autosalon.api.repository.UserAccountRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired AuditEventRepository audit;
    @Autowired PasswordEncoder passwords;

    @Test
    void reusedRefreshTokenRevokesItsReplacementAndLoginRecordsActor() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "refresh-" + userId + "@example.org";
        UserAccount user = new UserAccount(userId, email, "Проверка", passwords.encode("Test!Password2026"), Set.of(Role.VIEWER));
        user.setPasswordChangeRequired(false);
        users.saveAndFlush(user);

        JsonNode login = send("/api/auth/login", Map.of("email", email, "password", "Test!Password2026", "clientType", "NATIVE"), 200);
        String first = login.path("refreshToken").asText();
        JsonNode rotated = send("/api/auth/refresh", Map.of("refreshToken", first), 200);
        String second = rotated.path("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        send("/api/auth/refresh", Map.of("refreshToken", first), 401);
        send("/api/auth/refresh", Map.of("refreshToken", second), 401);
        assertThat(audit.findAll()).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("LOGIN_SUCCESS");
            assertThat(event.getActorId()).isEqualTo(userId);
            assertThat(event.getActorEmail()).isEqualTo(email);
        });
    }

    private JsonNode send(String url, Object body, int expected) throws Exception {
        String result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }
}
