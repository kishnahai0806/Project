package com.campuseventhub.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullSubmitApproveBrowseFlow() throws Exception {
        String uniqueUsername = "user_" + System.currentTimeMillis();
        String registerPayload = """
                {
                  "username": "%s",
                  "email": "%s@example.com",
                  "password": "User12345"
                }
                """.formatted(uniqueUsername, uniqueUsername);

        String registerBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String userToken = objectMapper.readTree(registerBody).get("token").asText();
        String adminToken = login("admin", "Admin@123");

        String categoriesBody = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode categories = objectMapper.readTree(categoriesBody);
        long firstCategoryId = categories.get(0).get("id").asLong();

        OffsetDateTime startTime = OffsetDateTime.now().plusDays(10).withNano(0);
        OffsetDateTime endTime = startTime.plusHours(2);

        String createPayload = """
                {
                  "title": "Integration Test Event",
                  "description": "End-to-end verification",
                  "location": "Auditorium A",
                  "startTime": "%s",
                  "endTime": "%s",
                  "capacity": 90,
                  "categoryIds": [%d]
                }
                """.formatted(startTime, endTime, firstCategoryId);

        String createBody = mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", "Bearer " + userToken)
                        .header("Idempotency-Key", "integration-flow-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String eventId = objectMapper.readTree(createBody).get("id").asText();

        mockMvc.perform(post("/api/v1/events/{eventId}/approve", eventId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Approved in integration test\"}"))
                .andExpect(status().isOk());

        String eventsBody = mockMvc.perform(get("/api/v1/events")
                        .param("status", "APPROVED")
                        .param("q", "Integration Test Event"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode events = objectMapper.readTree(eventsBody).get("content");
        boolean found = false;
        for (JsonNode event : events) {
            if (eventId.equals(event.get("id").asText())) {
                found = true;
                break;
            }
        }

        assertThat(found).isTrue();

        String metricsBody = mockMvc.perform(get("/api/v1/events/metrics/weekly")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", startTime.minusDays(7).toString())
                        .param("to", endTime.plusDays(7).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode metrics = objectMapper.readTree(metricsBody);
        assertThat(metrics.isArray()).isTrue();
        assertThat(metrics.size()).isGreaterThan(0);
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }
}
