package dk.sundhed.ehealth.referenceclients.common.infrastructure.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the FHIR R4 {@link FhirContext} singleton.
 *
 * <p>The {@code hapi-fhir-spring-boot-starter} declares its auto-configuration via the legacy
 * {@code META-INF/spring.factories} mechanism, which Spring Boot 3 no longer reads. We therefore
 * declare the bean explicitly.
 *
 * <p>Disables the default CapabilityStatement preflight: HAPI's restful client factory defaults to
 * {@link ServerValidationModeEnum#ONCE}, which issues a {@code GET /metadata} on first use of each
 * base URL. We talk to known, trusted FUT servers; the preflight just adds latency.
 *
 * <p>Raises socket read timeout to 60s. HAPI's default 10s is too short for FUT operations like
 * {@code Patient/$createPatient}, which fans out to an upstream CPR lookup on first invocation.
 */
@Configuration
public class FhirConfiguration {

    @Bean
    public FhirContext fhirContext() {
        FhirContext ctx = FhirContext.forR4();
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        ctx.getRestfulClientFactory().setSocketTimeout(60_000);
        return ctx;
    }
}
