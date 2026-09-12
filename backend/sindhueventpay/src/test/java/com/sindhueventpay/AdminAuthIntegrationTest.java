package com.sindhueventpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sindhueventpay.dto.AuthRequest;
import com.sindhueventpay.enums.Role;
import com.sindhueventpay.models.User;
import com.sindhueventpay.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Assuming tests might use an in-memory DB or standard DB
@Transactional
public class AdminAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        // Since V3 migration might run, we just ensure a test user is present or create one.
        if (userRepository.findByEmail("admin_test@sindhurunners.com") == null) {
            User admin = new User();
            admin.setName("Test Admin");
            admin.setEmail("admin_test@sindhurunners.com");
            admin.setPhone("1234567890");
            admin.setPassword(passwordEncoder.encode("password123"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
    }

    @Test
    public void testPublicEndpoint_AccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk());
    }

    @Test
    public void testAdminEndpoint_UnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/events"))
                .andExpect(status().isForbidden()) // Due to Spring Security defaults, it could be 403 or 401. Let's accept 4xx
                .andExpect(status().is4xxClientError());
    }

    @Test
    public void testAdminLogin_SuccessReturnsToken() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setEmail("admin_test@sindhurunners.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    public void testAdminEndpoint_AccessibleWithValidToken() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setEmail("admin_test@sindhurunners.com");
        req.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        String responseString = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseString).get("data").get("token").asText();

        mockMvc.perform(get("/api/v1/admin/events")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
