package com.dosys.platform.device;

import com.dosys.platform.medication.infrastructure.DeviceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceInternalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceRepository deviceRepository;

    private String token;
    private long deviceId;
    private String deviceKey;
    private long scheduleId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "device-internal-" + UUID.randomUUID() + "@test.com";
        String password = "StrongPass123";

        mockMvc.perform(post("/api/v1/access/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName":"Device",
                          "lastName":"Tester",
                          "email":"%s",
                          "password":"%s"
                        }
                        """.formatted(email, password))).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/v1/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(loginResponse).get("accessToken").asText();

        String createdDevice = mockMvc.perform(post("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dosys Device\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        deviceId = objectMapper.readTree(createdDevice).get("id").asLong();
        deviceKey = deviceRepository.findById(deviceId).orElseThrow().getDeviceKey();

        mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/{containerNumber}", deviceId, 1)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "medicationName":"Ibuprofen",
                          "dosageLabel":"200mg",
                          "remainingPills":12,
                          "isEnabled":true
                        }
                        """
                )).andExpect(status().isOk());

        String scheduleResponse = mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber":1,
                                  "time":"08:00:00",
                                  "daysOfWeek":["MONDAY"],
                                  "isActive":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        scheduleId = objectMapper.readTree(scheduleResponse).get("id").asLong();
    }

    @Test
    void runtimeConfigWithValidDeviceKey() throws Exception {
        mockMvc.perform(get("/api/v1/device/internal/{deviceId}/runtime-config", deviceId)
                        .header("X-Device-Key", deviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId))
                .andExpect(jsonPath("$.containers.length()").value(1))
                .andExpect(jsonPath("$.activeSchedules.length()").value(1));
    }

    @Test
    void runtimeConfigWithoutDeviceKey() throws Exception {
        mockMvc.perform(get("/api/v1/device/internal/{deviceId}/runtime-config", deviceId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void ingestValidIntakeEvent() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleId": %d,
                                  "containerNumber": 1,
                                  "scheduledAt": "2026-05-04T08:00:00Z",
                                  "confirmedAt": "2026-05-04T08:01:00Z",
                                  "status": "TAKEN"
                                }
                                """.formatted(scheduleId)))
                .andExpect(status().isOk());
    }

    @Test
    void preventIntakeDuplicateByUpsertBehavior() throws Exception {
        String body = """
                {
                  "scheduleId": %d,
                  "containerNumber": 1,
                  "scheduledAt": "2026-05-04T08:00:00Z",
                  "confirmedAt": "2026-05-04T08:01:00Z",
                  "status": "MISSED"
                }
                """.formatted(scheduleId);

        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("MISSED", "TAKEN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/adherence/calendar", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].items.length()").value(1));
    }

    @Test
    void preventIntakeWithInconsistentContainer() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleId": %d,
                                  "containerNumber": 2,
                                  "scheduledAt": "2026-05-04T08:00:00Z",
                                  "confirmedAt": "2026-05-04T08:01:00Z",
                                  "status": "TAKEN"
                                }
                                """.formatted(scheduleId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestEnvironmentReadingAndCalculateRisk() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/environment-readings", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "temperature": 35.0,
                                  "humidity": 72.0,
                                  "recordedAt": "2026-05-04T10:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/environment/latest", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskStatus").value("CRITICAL"));
    }

    @Test
    void updateStock() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/stock-events", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber": 1,
                                  "remainingPills": 7,
                                  "recordedAt": "2026-05-04T11:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/containers", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remainingPills").value(7));
    }

    @Test
    void preventNegativeStock() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/stock-events", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber": 1,
                                  "remainingPills": -2,
                                  "recordedAt": "2026-05-04T11:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerHeartbeat() throws Exception {
        String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/heartbeats", deviceId)
                        .header("X-Device-Key", deviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordedAt": "%s",
                                  "rtcTime": "%s",
                                  "wifiConnected": true,
                                  "deviceStatus": "ONLINE"
                                }
                                """.formatted(now, now)))
                .andExpect(status().isOk());

        Assertions.assertEquals("ONLINE", deviceRepository.findById(deviceId).orElseThrow().getLastKnownStatus());
    }
}
