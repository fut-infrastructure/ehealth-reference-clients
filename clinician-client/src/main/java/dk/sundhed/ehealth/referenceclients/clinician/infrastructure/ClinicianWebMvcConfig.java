package dk.sundhed.ehealth.referenceclients.clinician.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Wires the {@link EHealthContextArgumentResolver} and {@link SelectedContextInterceptor} into
 * Spring MVC.
 */
@Configuration
public class ClinicianWebMvcConfig implements WebMvcConfigurer {

    private final EHealthContextArgumentResolver contextArgumentResolver;

    public ClinicianWebMvcConfig(EHealthContextArgumentResolver contextArgumentResolver) {
        this.contextArgumentResolver = contextArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(contextArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SelectedContextInterceptor());
    }
}
