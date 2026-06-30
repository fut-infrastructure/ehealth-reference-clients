package dk.sundhed.ehealth.referenceclients.common.infrastructure.connect;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Wires {@link EHealthContextOptionsClient} as an {@link HttpServiceProxyFactory} proxy with
 * {@code ${ehealth.issuer-uri}} as the base URL.
 */
@Configuration
public class EHealthConnectConfiguration {

    @Bean
    public EHealthContextOptionsClient eHealthContextOptionsClient(
            RestClient.Builder restClientBuilder, @Value("${ehealth.issuer-uri}") String issuerUri) {
        RestClient restClient = restClientBuilder.baseUrl(issuerUri).build();
        HttpServiceProxyFactory factory =
                HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
        return factory.createClient(EHealthContextOptionsClient.class);
    }
}
