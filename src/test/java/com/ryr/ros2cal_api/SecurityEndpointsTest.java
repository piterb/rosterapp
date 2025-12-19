package com.ryr.ros2cal_api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void meReturnsClaimsWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> {
            jwt.subject("user-1");
            jwt.claim("email", "user@example.com");
            jwt.issuer("https://issuer.example.com");
        })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("user-1"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }
}
