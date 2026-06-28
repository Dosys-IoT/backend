package com.dosys.platform.medication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dosys.platform.medication.infrastructure.DeviceHeartbeatRepository;
import com.dosys.platform.medication.infrastructure.DeviceRepository;
import com.dosys.platform.medication.infrastructure.DeviceStockEventRepository;
import com.dosys.platform.medication.infrastructure.EnvironmentReadingRepository;
import com.dosys.platform.medication.infrastructure.IntakeRecordRepository;
import com.dosys.platform.medication.infrastructure.MedicationContainerRepository;
import com.dosys.platform.medication.infrastructure.MedicationScheduleRepository;
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
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private MedicationContainerRepository containerRepository;
    @Autowired
    private MedicationScheduleRepository scheduleRepository;
    @Autowired
    private IntakeRecordRepository intakeRecordRepository;
    @Autowired
    private EnvironmentReadingRepository environmentReadingRepository;
    @Autowired
    private DeviceHeartbeatRepository heartbeatRepository;
    @Autowired
    private DeviceStockEventRepository stockEventRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        stockEventRepository.deleteAll();
        heartbeatRepository.deleteAll();
        intakeRecordRepository.deleteAll();
        environmentReadingRepository.deleteAll();
        scheduleRepository.deleteAll();
        containerRepository.deleteAll();
        deviceRepository.deleteAll();

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
    void linkExistingPhysicalDeviceToAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/v1/medication/devices/link")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId":"1",
                                  "deviceName":"device1",
                                  "deviceKey":""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("1"))
                .andExpect(jsonPath("$.status").value("LINKED"))
                .andExpect(jsonPath("$.linked").value(true));

        mockMvc.perform(get("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hardwareDeviceId").value(1))
                .andExpect(jsonPath("$[0].name").value("device1"));
    }

    @Test
    void linkDeviceIsIdempotentForSameUser() throws Exception {
        String payload = """
                {
                  "deviceId":"1",
                  "deviceName":"device1",
                  "deviceKey":""
                }
                """;

        mockMvc.perform(post("/api/v1/medication/devices/link")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/medication/devices/link")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("1"))
                .andExpect(jsonPath("$.linked").value(true));
    }

    @Test
    void unlinkDeviceClearsHardwareAssociationWithoutDeletingData() throws Exception {
        linkPhysicalDevice();

        mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/unlink", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.hardwareDeviceId").value(nullValue()));

        mockMvc.perform(get("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hardwareDeviceId").value(nullValue()));
    }

    @Test
    void rejectDeviceLinkedToAnotherUser() throws Exception {
        String payload = """
                {
                  "deviceId":"1",
                  "deviceName":"device1",
                  "deviceKey":""
                }
                """;

        mockMvc.perform(post("/api/v1/medication/devices/link")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        String anotherToken = registerAndLogin("linked-other-" + System.nanoTime() + "@test.com", "StrongPass123");

        mockMvc.perform(post("/api/v1/medication/devices/link")
                        .header("Authorization", "Bearer " + anotherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void linkedDeviceCanReadLatestEnvironment() throws Exception {
        linkPhysicalDevice();

        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/environment-readings", 1L)
                        .header("X-Edge-Service-Key", "dosys-local-edge-service-key-change-me-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"env-linked-1",
                                  "temperature":26.4,
                                  "humidity":61.2,
                                  "recordedAt":"2026-06-27T12:00:00",
                                  "firmwareVersion":"1.0.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskStatus").value("NORMAL"));

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/environment/latest", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperature").value(26.4))
                .andExpect(jsonPath("$.humidity").value(61.2));
    }

    @Test
    void linkedDeviceCanReadStatus() throws Exception {
        linkPhysicalDevice();

        mockMvc.perform(post("/api/v1/device/internal/{deviceId}/heartbeats", 1L)
                        .header("X-Edge-Service-Key", "dosys-local-edge-service-key-change-me-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"hb-linked-1",
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

        mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/status", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("1"))
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andExpect(jsonPath("$.buttonPin").value(15));
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

    private void linkPhysicalDevice() throws Exception {
        mockMvc.perform(post("/api/v1/medication/devices/link")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId":"1",
                                  "deviceName":"device1",
                                  "deviceKey":""
                                }
                                """))
                .andExpect(status().isOk());
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
