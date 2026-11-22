package ru.mirea.autosalon.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessControlTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousUserCannotReadRequests() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanReadButCannotArchiveOrAdminister() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isOk());
        mvc.perform(delete("/api/requests/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }
}
