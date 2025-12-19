package com.ryr.ros2cal_api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RosterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void convertRosterRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/roster/convert"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void convertRosterReturnsSampleStringWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/roster/convert").with(jwt().jwt(jwt -> jwt.subject("user-123"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Sample roster conversion"));
    }
}
