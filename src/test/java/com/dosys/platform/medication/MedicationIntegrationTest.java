package com.dosys.platform.medication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "medication-" + UUID.randomUUID() + "@test.com";
        String password = "StrongPass123";

        mockMvc.perform(post("/api/v1/access/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName":"Medi",
                          "lastName":"Tester",
                          "email":"%s",
                          "password":"%s"
                        }
                        """.formatted(email, password)));

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
    }

    @Test
    void createInitialDevice() throws Exception {
        mockMvc.perform(post("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dosys Home Device"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.configVersion").value(1))
                .andExpect(jsonPath("$.deviceKey").isNotEmpty());
    }

    @Test
    void allowMultipleDevicesForSameUser() throws Exception {
        mockMvc.perform(post("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Primary Device"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Another Device"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void listDevicesForUser() throws Exception {
        createDevice();
        createDevice();

        mockMvc.perform(get("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNumber());
    }

    @Test
    void createFiveContainersAutomatically() throws Exception {
        long deviceId = createDevice();

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/containers", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].containerNumber").value(1))
                .andExpect(jsonPath("$[4].containerNumber").value(5));
    }

    @Test
    void updateContainer() throws Exception {
        long deviceId = createDevice();

        mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/{containerNumber}", deviceId, 1)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicationName":"Metformin",
                                  "dosageLabel":"500mg",
                                  "remainingPills":20,
                                  "isEnabled":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicationName").value("Metformin"))
                .andExpect(jsonPath("$.remainingPills").value(20))
                .andExpect(jsonPath("$.isEnabled").value(true));
    }

    @Test
    void preventNegativeRemainingPills() throws Exception {
        long deviceId = createDevice();

        mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/{containerNumber}", deviceId, 2)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicationName":"Aspirin",
                                  "dosageLabel":"100mg",
                                  "remainingPills":-1,
                                  "isEnabled":true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createValidSchedule() throws Exception {
        long deviceId = createDevice();
        enableContainer(deviceId, 1);

        mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber":1,
                                  "time":"08:30:00",
                                  "daysOfWeek":["MONDAY","WEDNESDAY"],
                                  "isActive":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.containerNumber").value(1));
    }

    @Test
    void preventScheduleForDisabledContainer() throws Exception {
        long deviceId = createDevice();

        mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber":1,
                                  "time":"08:30:00",
                                  "daysOfWeek":["MONDAY"],
                                  "isActive":true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void listSchedules() throws Exception {
        long deviceId = createDevice();
        enableContainer(deviceId, 1);
        createSchedule(deviceId, 1);

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deleteSchedule() throws Exception {
        long deviceId = createDevice();
        enableContainer(deviceId, 1);
        long scheduleId = createSchedule(deviceId, 1);

        mockMvc.perform(delete("/api/v1/medication/devices/{deviceId}/schedules/{scheduleId}", deviceId, scheduleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getEmptyAdherenceCalendar() throws Exception {
        long deviceId = createDevice();

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/adherence/calendar", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-05"))
                .andExpect(jsonPath("$.days.length()").value(0));
    }

    @Test
    void getLatestEnvironmentWithoutData() throws Exception {
        long deviceId = createDevice();

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/environment/latest", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void getEdgeCredentialsWithValidJwt() throws Exception {
        long deviceId = createDevice();

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/edge-credentials", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId))
                .andExpect(jsonPath("$.deviceKey").isNotEmpty());
    }

    @Test
    void preventEdgeCredentialsFromAnotherUser() throws Exception {
        long deviceId = createDevice();
        String anotherToken = registerAndLogin("other-" + System.nanoTime() + "@test.com", "StrongPass123");

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/edge-credentials", deviceId)
                        .header("Authorization", "Bearer " + anotherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedMedicationEndpointsRejectMissingJwt() throws Exception {
        mockMvc.perform(get("/api/v1/medication/devices")).andExpect(status().isUnauthorized());
    }

    @Test
    void crossUserIsolationForDevicesContainersSchedulesAndEnvironment() throws Exception {
        long ownDeviceId = createDevice();
        enableContainer(ownDeviceId, 1);
        long ownScheduleId = createSchedule(ownDeviceId, 1);
        String anotherToken = registerAndLogin("other2-" + System.nanoTime() + "@test.com", "StrongPass123");

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/containers", ownDeviceId)
                        .header("Authorization", "Bearer " + anotherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/schedules", ownDeviceId)
                        .header("Authorization", "Bearer " + anotherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/environment/latest", ownDeviceId)
                        .header("Authorization", "Bearer " + anotherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/environment/history", ownDeviceId)
                        .header("Authorization", "Bearer " + anotherToken)
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-05-31T23:59:59Z"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/medication/devices/{deviceId}/schedules/{scheduleId}", ownDeviceId, ownScheduleId)
                        .header("Authorization", "Bearer " + anotherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidContainerNumbersAreRejected() throws Exception {
        long deviceId = createDevice();
        String payload = """
                {
                  "medicationName":"Aspirin",
                  "dosageLabel":"100mg",
                  "remainingPills":10,
                  "isEnabled":true
                }
                """;

        mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/0", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/6", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createScheduleRejectsInvalidTimeFormat() throws Exception {
        long deviceId = createDevice();
        enableContainer(deviceId, 1);

        mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber":1,
                                  "time":"25:99:00",
                                  "daysOfWeek":["MONDAY"],
                                  "isActive":true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createScheduleRejectsInvalidDaysOfWeek() throws Exception {
        long deviceId = createDevice();
        enableContainer(deviceId, 1);

        mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber":1,
                                  "time":"08:00:00",
                                  "daysOfWeek":["FUNDAY"],
                                  "isActive":true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private long createDevice() throws Exception {
        String response = mockMvc.perform(post("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dosys Home Device"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("id").asLong();
    }

    private void enableContainer(long deviceId, int containerNumber) throws Exception {
        mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/{containerNumber}", deviceId, containerNumber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "medicationName":"Vitamin C",
                                  "dosageLabel":"1 tablet",
                                  "remainingPills":10,
                                  "isEnabled":true
                                }
                                """))
                .andExpect(status().isOk());
    }

    private long createSchedule(long deviceId, int containerNumber) throws Exception {
        String response = mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber":%d,
                                  "time":"09:00:00",
                                  "daysOfWeek":["MONDAY","TUESDAY"],
                                  "isActive":true
                                }
                                """.formatted(containerNumber)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private String registerAndLogin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/access/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName":"Other",
                          "lastName":"User",
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

        return objectMapper.readTree(loginResponse).get("accessToken").asText();
    }
}
