package com.campuseventhub.security;

import com.campuseventhub.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicEventsEndpointAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void actuatorHealthAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void createEventRequiresAuthentication() throws Exception {
        String payload = """
                {
                  "title": "Unauthorized Event",
                  "description": "Should fail",
                  "location": "Hall",
                  "startTime": "%s",
                  "endTime": "%s",
                  "capacity": 10,
                  "categoryIds": [1]
                }
                """.formatted(OffsetDateTime.now().plusDays(6).withNano(0), OffsetDateTime.now().plusDays(6).plusHours(1).withNano(0));

        mockMvc.perform(post("/api/v1/events")
                        .header("Idempotency-Key", "unauth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void categoriesCreateForbiddenForUserAndAllowedForAdmin() throws Exception {
        String userToken = login("student1", "Student@123");
        String adminToken = login("admin", "Admin@123");

        String categoryName = "Music-" + System.currentTimeMillis();
        String payload = """
                {
                  "name": "%s",
                  "description": "Live music and open mic"
                }
                """.formatted(categoryName);

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("token").asText();
    }
}
