package dk.sundhed.ehealth.referenceclients.citizen.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Wires {@link CitizenEHealthContextArgumentResolver} into Spring MVC.
 */
@Configuration
public class CitizenWebMvcConfig implements WebMvcConfigurer {

    private final CitizenEHealthContextArgumentResolver resolver;

    public CitizenWebMvcConfig(CitizenEHealthContextArgumentResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}
