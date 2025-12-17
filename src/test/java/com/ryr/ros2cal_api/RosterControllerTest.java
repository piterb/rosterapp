package com.ryr.ros2cal_api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RosterController.class)
class RosterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void convertRosterReturnsSampleString() throws Exception {
        mockMvc.perform(get("/roster/convert"))
                .andExpect(status().isOk())
                .andExpect(content().string("Sample roster conversion2"));
    }
}
