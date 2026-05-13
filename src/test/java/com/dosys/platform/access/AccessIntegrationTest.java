package com.dosys.platform.access;

import com.dosys.platform.access.infrastructure.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanData() {
        userRepository.deleteAll();
    }

    @Test
    void registerSuccess() throws Exception {
        String body = """
                {
                  "firstName": "Ana",
                  "lastName": "Lopez",
                  "email": "ana@test.com",
                  "password": "StrongPass123"
                }
                """;

        mockMvc.perform(post("/api/v1/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@test.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerDuplicateEmail() throws Exception {
        String body = """
                {
                  "firstName": "Ana",
                  "lastName": "Lopez",
                  "email": "ana@test.com",
                  "password": "StrongPass123"
                }
                """;

        mockMvc.perform(post("/api/v1/access/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        String body = """
                {
                  "firstName": "Ana",
                  "lastName": "Lopez",
                  "email": "invalid-email",
                  "password": "StrongPass123"
                }
                """;

        mockMvc.perform(post("/api/v1/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerRejectsWeakOrBlankPassword() throws Exception {
        String weakPassword = """
                {
                  "firstName": "Ana",
                  "lastName": "Lopez",
                  "email": "ana2@test.com",
                  "password": "123"
                }
                """;
        String blankPassword = """
                {
                  "firstName": "Ana",
                  "lastName": "Lopez",
                  "email": "ana3@test.com",
                  "password": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weakPassword))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/access/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankPassword))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSuccess() throws Exception {
        registerDefaultUser();

        String login = """
                {
                  "email": "ana@test.com",
                  "password": "StrongPass123"
                }
                """;

        mockMvc.perform(post("/api/v1/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("ana@test.com"));
    }

    @Test
    void loginInvalidPassword() throws Exception {
        registerDefaultUser();

        String login = """
                {
                  "email": "ana@test.com",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/v1/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void loginRejectsUnknownEmail() throws Exception {
        String login = """
                {
                  "email": "unknown@test.com",
                  "password": "StrongPass123"
                }
                """;

        mockMvc.perform(post("/api/v1/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void meWithValidToken() throws Exception {
        registerDefaultUser();
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/v1/access/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@test.com"));
    }

    @Test
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/access/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meRejectsMalformedBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/access/me")
                        .header("Authorization", "Bearer malformed.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void registerDefaultUser() throws Exception {
        String body = """
                {
                  "firstName": "Ana",
                  "lastName": "Lopez",
                  "email": "ana@test.com",
                  "password": "StrongPass123"
                }
                """;

        mockMvc.perform(post("/api/v1/access/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated());
    }

    private String loginAndGetToken() throws Exception {
        String login = """
                {
                  "email": "ana@test.com",
                  "password": "StrongPass123"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        return jsonNode.get("accessToken").asText();
    }
}
