package br.com.carrim.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousApiRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRouteIsNotPublic() throws Exception {
        mockMvc.perform(get("/unexpected")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void authenticatedRequestIsDeniedUntilExplicitlyAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void mutationWithCsrfIsStillDenied() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void mutationWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/products")).andExpect(status().isForbidden());
    }
}
