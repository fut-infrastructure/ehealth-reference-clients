package dk.sundhed.ehealth.referenceclients.clinician.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.sundhed.ehealth.referenceclients.clinician.Application;
import dk.sundhed.ehealth.referenceclients.common.infrastructure.connect.CareTeamOption;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test that drives {@code POST /citizens} through the full clinician-client Spring MVC
 * pipeline against a live FHIR server, exercising the argument resolver, the selected-context
 * interceptor, the request-scoped {@code EHealthUser}, {@code FhirClientFactory}, and the real
 * {@code $createPatient} call.
 *
 * <p>Credential resolution mirrors {@code CreatePatientIT}: env vars override the
 * {@code src/test/resources/application.yaml} defaults. The test skips when no usable ROPC
 * credentials are configured.
 *
 * <p>The refresh-token grant inside {@code DefaultEHealthUser} is intentionally not exercised: this
 * test installs an {@code OAuth2AuthorizedClient} without a refresh token, which short-circuits the
 * context-aware refresh path and reuses the ROPC access token as-is. A separate test could cover
 * the refresh extension once a Keycloak-side fixture for context-scoped grants exists.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@EnabledIf("hasUsableConfig")
class CreatePatientFlowIT {

    /**
     * Gates the test at discovery time so the Spring context is not loaded when ROPC credentials
     * and/or the issuer URI are missing. Env vars override the test-yaml defaults; if neither is
     * present the test is skipped silently rather than failing during context load.
     */
    @SuppressWarnings("unused")
    static boolean hasUsableConfig() {
        return resolve("EHEALTH_TEST_TOKEN_URL", "token-url") != null
                && resolve("EHEALTH_TEST_CLIENT_ID", "client-id") != null
                && resolve("EHEALTH_TEST_USERNAME", "username") != null
                && resolve("EHEALTH_TEST_PASSWORD", "password") != null
                && resolve("EHEALTH_TEST_PATIENT_BASE_URL", "patient-base-url") != null;
    }

    private static final Map<String, Object> DEFAULTS = loadTestDefaults();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String tokenUrl = resolve("EHEALTH_TEST_TOKEN_URL", "token-url");
        String issuerUri = tokenUrl == null
                ? ""
                : tokenUrl.replaceFirst("/protocol/openid-connect/token$", "");
        String patientBaseUrl = orEmpty(resolve("EHEALTH_TEST_PATIENT_BASE_URL", "patient-base-url"));
        String clientId = orEmpty(resolve("EHEALTH_TEST_CLIENT_ID", "client-id"));

        registry.add("spring.security.oauth2.client.provider.clinician.issuer-uri", () -> issuerUri);
        registry.add("spring.security.oauth2.client.registration.clinician.client-id", () -> clientId);
        registry.add("spring.security.oauth2.client.registration.clinician.client-secret",
                () -> orEmpty(System.getenv("EHEALTH_TEST_CLIENT_SECRET")));
        registry.add("ehealth.issuer-uri", () -> issuerUri);
        registry.add("fhir.server.patient", () -> patientBaseUrl);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClientRegistrationRepository registrations;
    @Autowired
    private OAuth2AuthorizedClientRepository authorizedClientRepository;

    @Test
    void postCitizens_createsCitizenThroughFullMvcPipeline() throws Exception {
        String accessTokenValue = fetchRopcAccessToken();
        Assumptions.assumeTrue(accessTokenValue != null,
                "Skipping: ROPC token exchange did not return an access token.");

        String cpr = resolve("EHEALTH_TEST_CPR", "cpr");
        String username = resolve("EHEALTH_TEST_USERNAME", "username");

        ClientRegistration registration = registrations.findByRegistrationId("clinician");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                accessTokenValue,
                Instant.now(),
                Instant.now().plus(5, ChronoUnit.MINUTES));
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                registration, username, accessToken);

        OidcIdToken idToken = OidcIdToken.withTokenValue(accessTokenValue)
                .subject(username)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();
        OidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oidcUser, oidcUser.getAuthorities(), "clinician");

        MockHttpSession session = new MockHttpSession();
        String carePlanBase = orEmpty(System.getenv("FHIR_SERVER_CARE_PLAN"));
        if (carePlanBase.isBlank()) {
            carePlanBase = resolve("EHEALTH_TEST_PATIENT_BASE_URL", "patient-base-url")
                    .replace("/fhir", "").replace("patient.", "careplan.") + "/fhir";
        }
        CareTeamOption selectedContext = new CareTeamOption(
                carePlanBase + "/CareTeam/integration-test-care-team",
                "Integration test care team",
                null,
                List.of(),
                List.of());
        session.setAttribute(SelectContextController.SELECTED_CONTEXT_ATTRIBUTE, selectedContext);

        // Save authorized client into the session so DefaultEHealthUser can load it via the
        // session-backed OAuth2AuthorizedClientRepository on the controller dispatch.
        MockHttpServletRequest primingRequest = new MockHttpServletRequest();
        primingRequest.setSession(session);
        authorizedClientRepository.saveAuthorizedClient(
                authorizedClient, authentication, primingRequest, new MockHttpServletResponse());

        mockMvc.perform(post("/citizens")
                        .session(session)
                        .param("cpr", cpr)
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/patients/*"));
    }

    private static String fetchRopcAccessToken() throws Exception {
        String tokenUrl = resolve("EHEALTH_TEST_TOKEN_URL", "token-url");
        String clientId = resolve("EHEALTH_TEST_CLIENT_ID", "client-id");
        String username = resolve("EHEALTH_TEST_USERNAME", "username");
        String password = resolve("EHEALTH_TEST_PASSWORD", "password");
        String scope = resolve("EHEALTH_TEST_SCOPE", "scope");
        if (tokenUrl == null || clientId == null || username == null || password == null) {
            return null;
        }
        StringBuilder form = new StringBuilder("grant_type=password");
        form.append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        form.append("&username=").append(URLEncoder.encode(username, StandardCharsets.UTF_8));
        form.append("&password=").append(URLEncoder.encode(password, StandardCharsets.UTF_8));
        if (scope != null) {
            form.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
        }
        String clientSecret = System.getenv("EHEALTH_TEST_CLIENT_SECRET");
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
        }

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new ObjectMapper().readValue(response.body(), Map.class);
        Object accessToken = body.get("access_token");
        return accessToken == null ? null : accessToken.toString();
    }

    private static String resolve(String envVarName, String yamlKey) {
        String env = System.getenv(envVarName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        Object yamlValue = DEFAULTS.get(yamlKey);
        return yamlValue == null ? null : yamlValue.toString();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadTestDefaults() {
        try (InputStream inputStream = CreatePatientFlowIT.class.getClassLoader()
                .getResourceAsStream("application.yaml")) {
            if (inputStream == null) {
                return Map.of();
            }
            Map<String, Object> root = new Yaml().load(inputStream);
            Map<String, Object> ehealth = (Map<String, Object>) root.getOrDefault("ehealth", Map.of());
            return (Map<String, Object>) ehealth.getOrDefault("test", Map.of());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load test-scope application.yaml", exception);
        }
    }
}
