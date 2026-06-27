package com.dosys.platform.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

public class StepDefinitions {
    private static final String EDGE_SERVICE_KEY = "dosys-local-edge-service-key-change-me-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String caregiverEmail;
    private String caregiverPassword = "StrongPass123";
    private String caregiverToken;
    private String secondCaregiverToken;
    private Long deviceId;
    private String deviceKey;
    private Long scheduleId;
    private MvcResult lastResult;

    @Before
    public void init() {
        caregiverEmail = "acc-" + UUID.randomUUID() + "@test.com";
        caregiverToken = null;
        secondCaregiverToken = null;
        deviceId = null;
        deviceKey = null;
        scheduleId = null;
        lastResult = null;
    }

    @Given("a new caregiver with valid account data")
    public void aNewCaregiver() {}

    @When("the caregiver registers an account")
    public void registerCaregiver() throws Exception {
        lastResult = register(caregiverEmail, caregiverPassword, "Care", "Giver");
    }

    @And("the caregiver logs in with the same credentials")
    public void loginCaregiver() throws Exception {
        lastResult = login(caregiverEmail, caregiverPassword);
        caregiverToken = read(lastResult).get("accessToken").asText();
    }

    @Then("the backend returns a Bearer access token")
    public void backendReturnsBearer() throws Exception {
        JsonNode json = read(lastResult);
        Assertions.assertEquals(200, lastResult.getResponse().getStatus());
        Assertions.assertEquals("Bearer", json.get("tokenType").asText());
        Assertions.assertFalse(json.get("accessToken").asText().isBlank());
    }

    @And("the authenticated profile can be retrieved")
    public void meWorks() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/access/me").header("Authorization", "Bearer " + caregiverToken)).andReturn();
        Assertions.assertEquals(200, result.getResponse().getStatus());
    }

    @Given("a registered caregiver")
    public void registeredCaregiver() throws Exception {
        register(caregiverEmail, caregiverPassword, "Care", "Giver");
    }

    @When("the caregiver logs in with an invalid password")
    public void loginInvalidPassword() throws Exception {
        lastResult = login(caregiverEmail, "wrong-password");
    }

    @Then("the backend rejects the request with unauthorized status")
    public void unauthorizedStatus() {
        Assertions.assertEquals(401, lastResult.getResponse().getStatus());
    }

    @Given("an authenticated caregiver")
    public void authenticatedCaregiver() throws Exception {
        register(caregiverEmail, caregiverPassword, "Care", "Giver");
        caregiverToken = read(login(caregiverEmail, caregiverPassword)).get("accessToken").asText();
    }

    @When("the caregiver creates a device")
    public void createDevice() throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/medication/devices")
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"name\":\"Acceptance Device\"}"))
                .andReturn();
        JsonNode node = read(lastResult);
        deviceId = node.get("id").asLong();
        deviceKey = node.get("deviceKey").asText();
    }

    @And("the caregiver configures container 1 with a medication")
    public void configureContainer() throws Exception {
        lastResult = mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/1", deviceId)
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"medicationName\":\"Metformin\"," +
                                "\"dosageLabel\":\"500mg\"," +
                                "\"remainingPills\":20," +
                                "\"isEnabled\":true}"))
                .andReturn();
    }

    @And("the caregiver creates a schedule for container 1")
    public void createSchedule() throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/medication/devices/{deviceId}/schedules", deviceId)
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"containerNumber\":1," +
                                "\"time\":\"08:00:00\"," +
                                "\"daysOfWeek\":[\"MONDAY\"]," +
                                "\"isActive\":true}"))
                .andReturn();
        scheduleId = read(lastResult).get("id").asLong();
    }

    @Then("the backend stores the schedule")
    public void scheduleStored() {
        Assertions.assertEquals(201, lastResult.getResponse().getStatus());
    }

    @And("the device runtime configuration contains the scheduled dose")
    public void runtimeContainsSchedule() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/device/internal/{deviceId}/runtime-config", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY))
                .andReturn();
        Assertions.assertEquals(200, result.getResponse().getStatus());
        Assertions.assertEquals(1, read(result).get("schedules").size());
    }

    @Given("two authenticated caregivers")
    public void twoCaregivers() throws Exception {
        authenticatedCaregiver();
        String email2 = "acc2-" + UUID.randomUUID() + "@test.com";
        register(email2, caregiverPassword, "Other", "Caregiver");
        secondCaregiverToken = read(login(email2, caregiverPassword)).get("accessToken").asText();
    }

    @And("the first caregiver owns a device")
    public void firstOwnsDevice() throws Exception { createDevice(); }

    @When("the second caregiver tries to access that device medication data")
    public void secondAccessesFirstDevice() throws Exception {
        lastResult = mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/containers", deviceId)
                        .header("Authorization", "Bearer " + secondCaregiverToken))
                .andReturn();
    }

    @Then("the backend rejects or hides the unauthorized data")
    public void unauthorizedDataHidden() {
        Assertions.assertTrue(lastResult.getResponse().getStatus() == 404 || lastResult.getResponse().getStatus() == 403);
    }

    @Given("an authenticated caregiver with a device")
    public void caregiverWithDevice() throws Exception {
        authenticatedCaregiver();
        createDevice();
    }

    @When("the caregiver tries to set negative remaining pills")
    public void setNegativePills() throws Exception {
        lastResult = mockMvc.perform(put("/api/v1/medication/devices/{deviceId}/containers/1", deviceId)
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"medicationName\":\"Metformin\"," +
                                "\"dosageLabel\":\"500mg\"," +
                                "\"remainingPills\":-1," +
                                "\"isEnabled\":true}"))
                .andReturn();
    }

    @Then("the backend rejects the request with bad request status")
    public void badRequestStatus() {
        Assertions.assertEquals(400, lastResult.getResponse().getStatus());
    }

    @Given("an authenticated caregiver with a configured device and schedule")
    public void configuredDeviceAndSchedule() throws Exception {
        caregiverWithDevice();
        configureContainer();
        createSchedule();
    }

    @Given("an authenticated caregiver with a configured device")
    public void configuredDevice() throws Exception {
        caregiverWithDevice();
        configureContainer();
    }

    @When("the device requests runtime configuration using a valid device key")
    public void deviceRequestsRuntime() throws Exception {
        lastResult = mockMvc.perform(get("/api/v1/device/internal/{deviceId}/runtime-config", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY))
                .andReturn();
    }

    @Then("the backend returns the runtime configuration")
    public void runtimeReturned() {
        Assertions.assertEquals(200, lastResult.getResponse().getStatus());
    }

    @And("the configuration includes the active schedule")
    public void configIncludesActiveSchedule() throws Exception {
        Assertions.assertEquals(1, read(lastResult).get("schedules").size());
    }

    @When("the device posts temperature and humidity using a valid internal key")
    public void postEnvironment() throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/device/internal/{deviceId}/environment-readings", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"eventId\":\"env-1\"," +
                                "\"temperature\":31.0," +
                                "\"humidity\":71.0," +
                                "\"recordedAt\":\"2026-05-04T10:00:00\"," +
                                "\"firmwareVersion\":\"1.0.0\"}"))
                .andReturn();
    }

    @Then("the backend stores the environmental reading")
    public void environmentStored() {
        Assertions.assertEquals(200, lastResult.getResponse().getStatus());
    }

    @And("the caregiver can query the latest environmental reading")
    public void queryLatestEnvironment() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/environment/latest", deviceId)
                        .header("Authorization", "Bearer " + caregiverToken))
                .andReturn();
        Assertions.assertEquals(200, result.getResponse().getStatus());
        Assertions.assertNotNull(read(result).get("riskStatus"));
    }

    @When("the device posts an intake event with status TAKEN")
    public void postIntakeTaken() throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/device/internal/{deviceId}/intake-events", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"eventId\":\"intake-1\"," +
                                "\"scheduleId\":" + scheduleId + "," +
                                "\"containerNumber\":1," +
                                "\"scheduledAt\":\"2026-05-04T08:00:00\"," +
                                "\"confirmedAt\":\"2026-05-04T08:01:00\"," +
                                "\"status\":\"TAKEN\"," +
                                "\"source\":\"PHYSICAL_BUTTON\"," +
                                "\"buttonPin\":15}"))
                .andReturn();
    }

    @Then("the backend stores the intake event")
    public void intakeStored() {
        Assertions.assertEquals(200, lastResult.getResponse().getStatus());
    }

    @And("the adherence calendar includes the taken dose")
    public void adherenceIncludesTaken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/adherence/calendar", deviceId)
                        .header("Authorization", "Bearer " + caregiverToken)
                        .param("month", "2026-05"))
                .andReturn();
        Assertions.assertEquals(200, result.getResponse().getStatus());
        Assertions.assertTrue(read(result).get("days").size() > 0);
    }

    @When("the device posts a stock event for container 1")
    public void postStock() throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/device/internal/{deviceId}/stock-events", deviceId)
                        .header("X-Edge-Service-Key", EDGE_SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"eventId\":\"stock-1\"," +
                                "\"containerNumber\":1," +
                                "\"remainingPills\":9," +
                                "\"reportedAt\":\"2026-05-04T10:00:00\"," +
                                "\"reason\":\"INTAKE_CONFIRMED\"}"))
                .andReturn();
    }

    @Then("the backend updates the remaining pills for container 1")
    public void stockUpdated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/medication/devices/{deviceId}/containers", deviceId)
                        .header("Authorization", "Bearer " + caregiverToken))
                .andReturn();
        Assertions.assertEquals(9, read(result).get(0).get("remainingPills").asInt());
    }

    @When("the device calls an internal endpoint with an invalid key")
    public void callInternalWithInvalidKey() throws Exception {
        lastResult = mockMvc.perform(get("/api/v1/device/internal/{deviceId}/runtime-config", deviceId)
                        .header("X-Edge-Service-Key", "bad-key"))
                .andReturn();
    }

    @Then("the backend rejects the request with unauthorized or forbidden status")
    public void unauthorizedOrForbidden() {
        Assertions.assertTrue(lastResult.getResponse().getStatus() == 401 || lastResult.getResponse().getStatus() == 403);
    }

    private MvcResult register(String email, String password, String firstName, String lastName) throws Exception {
        return mockMvc.perform(post("/api/v1/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"firstName\":\"" + firstName + "\"," +
                                "\"lastName\":\"" + lastName + "\"," +
                                "\"email\":\"" + email + "\"," +
                                "\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"email\":\"" + email + "\"," +
                                "\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
