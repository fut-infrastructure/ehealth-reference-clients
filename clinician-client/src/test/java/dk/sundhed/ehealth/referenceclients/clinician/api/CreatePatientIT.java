package dk.sundhed.ehealth.referenceclients.clinician.api;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real integration test for the {@code $createPatient} operation on {@code fut-patient}.
 *
 * <p>Targets a live FHIR server. Defaults (including the test credentials) come from
 * {@code src/test/resources/application.yaml}. Env vars
 * ({@code EHEALTH_TEST_PATIENT_BASE_URL}, {@code EHEALTH_TEST_BEARER_TOKEN},
 * {@code EHEALTH_TEST_USERNAME}, {@code EHEALTH_TEST_PASSWORD}, ...) override per run.
 *
 * <p>Two credential paths are supported:
 *
 * <ul>
 *   <li><b>Bearer token</b>: set {@code EHEALTH_TEST_BEARER_TOKEN} to a token obtained
 *       externally (e.g. copied from a browser session, or a {@code curl} round-trip).
 *       Use this for environments whose clinician client does not support direct password
 *       grants (ROPC).</li>
 *   <li><b>ROPC</b>: supply a username and password via {@code EHEALTH_TEST_USERNAME} and
 *       {@code EHEALTH_TEST_PASSWORD}. The test fetches a token by POSTing to the configured
 *       token URL. Requires a Keycloak client with {@code directAccessGrantsEnabled=true}.</li>
 * </ul>
 *
 * <p>When neither credential path produces a usable token, the test is skipped via JUnit's
 * {@link Assumptions} mechanism; it does not fail.
 *
 * <p>See {@code docs/onboarding/connect-to-test.md} for the env-overrides cheatsheet.
 */
class CreatePatientIT {

    private static final String CPR_SYSTEM = "urn:oid:1.2.208.176.1.2";

    private static final Map<String, Object> DEFAULTS = loadTestDefaults();

    @Test
    void createPatient_returnsPatientWrappedInParameters() throws Exception {
        String baseUrl = resolve("EHEALTH_TEST_PATIENT_BASE_URL", "patient-base-url");
        String cpr = resolve("EHEALTH_TEST_CPR", "cpr");
        String bearerToken = resolveBearerToken();

        Assumptions.assumeTrue(bearerToken != null,
                "Skipping: no usable credentials. Set EHEALTH_TEST_BEARER_TOKEN, or supply "
                        + "username + password (env vars override the application.yaml defaults).");

        FhirContext ctx = FhirContext.forR4();
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

        IGenericClient client = ctx.newRestfulGenericClient(baseUrl);
        client.registerInterceptor(new BearerTokenAuthInterceptor(bearerToken));

        Identifier cprIdentifier = new Identifier()
                .setUse(Identifier.IdentifierUse.OFFICIAL)
                .setSystem(CPR_SYSTEM)
                .setValue(cpr);

        Parameters requestParams = new Parameters();
        requestParams.addParameter().setName("crn").setValue(cprIdentifier);

        Object raw = client
                .operation()
                .onType(Patient.class)
                .named("$createPatient")
                .withParameters(requestParams)
                .execute();

        assertThat(raw)
                .as("$createPatient must return a Parameters resource")
                .isInstanceOf(Parameters.class);

        Parameters response = (Parameters) raw;

        Patient patient = response.getParameter().stream()
                .map(Parameters.ParametersParameterComponent::getResource)
                .filter(r -> r instanceof Patient)
                .map(r -> (Patient) r)
                .findFirst()
                .orElse(null);

        assertThat(patient)
                .as("Parameters must contain at least one Patient resource")
                .isNotNull();

        assertThat(patient.getName())
                .as("Patient must have at least one HumanName")
                .isNotEmpty();

        assertThat(patient.getName().get(0).getFamily())
                .as("Patient's first HumanName must have a non-blank family name")
                .isNotBlank();

        boolean hasCprIdentifier = patient.getIdentifier().stream().anyMatch(id ->
                CPR_SYSTEM.equals(id.getSystem()) &&
                        id.getValue() != null &&
                        id.getValue().replaceAll("[^0-9]", "").equals(cpr.replaceAll("[^0-9]", "")));

        assertThat(hasCprIdentifier)
                .as("Patient must have identifier system %s matching CPR %s", CPR_SYSTEM, cpr)
                .isTrue();
    }

    private static String resolveBearerToken() throws Exception {
        String direct = System.getenv("EHEALTH_TEST_BEARER_TOKEN");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String username = resolve("EHEALTH_TEST_USERNAME", "username");
        String password = resolve("EHEALTH_TEST_PASSWORD", "password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        String tokenUrl = resolve("EHEALTH_TEST_TOKEN_URL", "token-url");
        String clientId = resolve("EHEALTH_TEST_CLIENT_ID", "client-id");
        String scope = resolve("EHEALTH_TEST_SCOPE", "scope");
        String clientSecret = System.getenv("EHEALTH_TEST_CLIENT_SECRET");
        return fetchRopcToken(tokenUrl, clientId, clientSecret, username, password, scope);
    }

    private static String fetchRopcToken(
            String tokenUrl, String clientId, String clientSecret,
            String username, String password, String scope) throws Exception {
        StringBuilder form = new StringBuilder();
        form.append("grant_type=password");
        form.append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
        }
        form.append("&username=").append(URLEncoder.encode(username, StandardCharsets.UTF_8));
        form.append("&password=").append(URLEncoder.encode(password, StandardCharsets.UTF_8));
        form.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(
                    "ROPC token request to " + tokenUrl + " failed: HTTP " + resp.statusCode()
                            + ": " + resp.body());
        }
        Matcher m = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body());
        if (!m.find()) {
            throw new IllegalStateException("No access_token in ROPC response body: " + resp.body());
        }
        return m.group(1);
    }

    /** Env var wins if set; otherwise the matching key under {@code ehealth.test.*}. */
    private static String resolve(String envVarName, String yamlKey) {
        String v = System.getenv(envVarName);
        if (v != null && !v.isBlank()) {
            return v;
        }
        Object yamlValue = DEFAULTS.get(yamlKey);
        return yamlValue == null ? null : yamlValue.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadTestDefaults() {
        try (InputStream in = CreatePatientIT.class.getClassLoader()
                .getResourceAsStream("application.yaml")) {
            if (in == null) {
                return Map.of();
            }
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> ehealth = (Map<String, Object>) root.getOrDefault("ehealth", Map.of());
            Map<String, Object> test = (Map<String, Object>) ehealth.getOrDefault("test", Map.of());
            return test;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load test-scope application.yaml", e);
        }
    }
}
