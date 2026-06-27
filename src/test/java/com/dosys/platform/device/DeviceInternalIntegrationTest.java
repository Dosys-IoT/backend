package com.dosys.platform.device;

import com.dosys.platform.medication.infrastructure.DeviceHeartbeatRepository;
import com.dosys.platform.medication.infrastructure.DeviceRepository;
import com.dosys.platform.medication.infrastructure.DeviceStockEventRepository;
import com.dosys.platform.medication.infrastructure.EnvironmentReadingRepository;
import com.dosys.platform.medication.infrastructure.IntakeRecordRepository;
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
    private static final String EDGE_SERVICE_KEY = "dosys-local-edge-service-key-change-me-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private EnvironmentReadingRepository environmentReadingRepository;
    @Autowired private DeviceHeartbeatRepository heartbeatRepository;
    @Autowired private IntakeRecordRepository intakeRecordRepository;
    @Autowired private DeviceStockEventRepository stockEventRepository;

    private String token;
    private long deviceId;
    private long scheduleId;

    @BeforeEach
    void setUp() throws Exception {
        stockEventRepository.deleteAll();
        heartbeatRepository.deleteAll();
        intakeRecordRepository.deleteAll();
        environmentReadingRepository.deleteAll();

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

        mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/{containerNumber}", deviceId, 1)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "medicationName":"Ibuprofen",
                          "dosageLabel":"200mg",
                          "remainingPills":20,
                          "isEnabled":true
                        }
                        """)).andExpect(status().isOk());

        String scheduleResponse = mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "containerNumber":1,
                                  "time":"08:00:00",
                                  "daysOfWeek":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
                                  "isActive":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        scheduleId = objectMapper.readTree(scheduleResponse).get("id").asLong();
    }

    @Test
    void runtimeConfigWithValidEdgeServiceKey() throws Exception {
        mockMvc.perform(get("/api/v1/device/internal/{deviceId}/runtime-config", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(String.valueOf(deviceId)))
                .andExpect(jsonPath("$.containers.length()").value(5))
                .andExpect(jsonPath("$.schedules.length()").value(1))
                .andExpect(jsonPath("$.environmentThresholds.temperatureWarning").value(28))
                .andExpect(jsonPath("$.environmentThresholds.humidityCritical").value(80));
    }

    @Test
    void rejectRuntimeConfigWithInvalidEdgeServiceKey() throws Exception {
        mockMvc.perform(get("/api/v1/device/internal/{deviceId}/runtime-config", deviceId)
                        .header("X-Edge-Service-Key", "wrong-service-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ingestEnvironmentReadingAndCalculateRisk() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/environment-readings", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"env-1",
                                  "temperature":27.8,
                                  "humidity":60.2,
                                  "recordedAt":"2026-06-27T12:00:00",
                                  "firmwareVersion":"1.0.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskStatus").value("NORMAL"));

        Assertions.assertTrue(environmentReadingRepository.findByDeviceIdAndEventId(deviceId, "env-1").isPresent());
    }

    @Test
    void registerHeartbeatFromEsp32() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/heartbeats", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"hb-1",
                                  "rtcTime":"2026-06-27T12:00:00",
                                  "wifiConnected":true,
                                  "mqttConnected":true,
                                  "rtcOk":true,
                                  "sht3xOk":true,
                                  "dfPlayerOk":true,
                                  "sdCardOk":true,
                                  "switchOk":true,
                                  "buttonPin":15,
                                  "freeHeap":180000,
                                  "rssi":-55,
                                  "deviceStatus":"ONLINE",
                                  "firmwareVersion":"1.0.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));

        Assertions.assertEquals("ONLINE", deviceRepository.findById(deviceId).orElseThrow().getLastKnownStatus());
        Assertions.assertTrue(heartbeatRepository.findByDeviceIdAndEventId(deviceId, "hb-1").isPresent());
    }

    @Test
    void exposeDeviceStatusFromLastHeartbeat() throws Exception {
        registerHeartbeatFromEsp32();

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/status", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(String.valueOf(deviceId)))
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andExpect(jsonPath("$.buttonPin").value(15))
                .andExpect(jsonPath("$.firmwareVersion").value("1.0.0"));
    }

    @Test
    void ingestTakenIntakeFromPhysicalButtonPin15() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"intake-1",
                                  "scheduleId":"%d",
                                  "containerNumber":1,
                                  "scheduledAt":"2026-06-27T08:00:00",
                                  "confirmedAt":"2026-06-27T08:02:15",
                                  "status":"TAKEN",
                                  "source":"PHYSICAL_BUTTON",
                                  "buttonPin":15
                                }
                                """.formatted(scheduleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PHYSICAL_BUTTON"))
                .andExpect(jsonPath("$.buttonPin").value(15));

        Assertions.assertTrue(intakeRecordRepository.findByDeviceIdAndEventId(deviceId, "intake-1").isPresent());
    }

    @Test
    void preventDuplicateIntakeEventByEventId() throws Exception {
        String body = """
                {
                  "eventId":"intake-dup",
                  "scheduleId":"%d",
                  "containerNumber":1,
                  "scheduledAt":"2026-06-27T08:00:00",
                  "confirmedAt":"2026-06-27T08:02:15",
                  "status":"TAKEN",
                  "source":"PHYSICAL_BUTTON",
                  "buttonPin":15
                }
                """.formatted(scheduleId);

        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Assertions.assertTrue(intakeRecordRepository.findByDeviceIdAndEventId(deviceId, "intake-dup").isPresent());
    }

    @Test
    void updateStockFromStockEvent() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/stock-events", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"stock-1",
                                  "containerNumber":1,
                                  "remainingPills":19,
                                  "reportedAt":"2026-06-27T08:02:20",
                                  "reason":"INTAKE_CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingPills").value(19));

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/containers", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remainingPills").value(19));

        Assertions.assertTrue(stockEventRepository.findByDeviceIdAndEventId(deviceId, "stock-1").isPresent());
    }

    @Test
    void preventNegativeStock() throws Exception {
        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/stock-events", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"stock-neg",
                                  "containerNumber":1,
                                  "remainingPills":-1,
                                  "reportedAt":"2026-06-27T08:02:20",
                                  "reason":"INTAKE_CONFIRMED"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
