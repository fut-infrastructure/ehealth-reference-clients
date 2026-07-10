package dk.sundhed.ehealth.referenceclients.citizen.app;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the {@code fragments/activity-group :: day} fragment with the real Thymeleaf engine to
 * confirm the multi-line submit link produces well-formed query parameters (no whitespace leaks
 * into parameter names) and picks up {@link ActivityView#slotStartParam()} / {@link
 * ActivityView#slotEndParam()}.
 */
class ActivityGroupFragmentTest {

    private TemplateEngine engine;
    private ServletContext servletContext;
    private JakartaServletWebApplication webApplication;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        servletContext = new MockServletContext();
        webApplication = JakartaServletWebApplication.buildApplication(servletContext);
    }

    @Test
    void submitLinkRendersAllParametersFromTheActivity() {
        ActivityView activity = new ActivityView(
                "Puls",
                LocalDateTime.of(2026, 4, 15, 10, 0),
                LocalDateTime.of(2026, 4, 15, 10, 30),
                "Resolved",
                "1/3",
                "9",
                "2",
                "https://careplan.example/fhir/ServiceRequest/657450",
                "https://careplan.example/fhir/EpisodeOfCare/5");

        String html = render(activity);

        assertThat(html).contains("/measurements/new");
        // Parameter names must be intact (no leading whitespace turning into %20name).
        assertThat(html).contains("timingType=Resolved");
        assertThat(html).contains("srVersionId=2");
        // Colon in the ISO time is URL-encoded, so assert on the stable prefix.
        assertThat(html).contains("slotStart=2026-04-15T10");
        assertThat(html).contains("slotEnd=2026-04-15T10");
        assertThat(html).doesNotContain("%20");
    }

    @Test
    void unscheduledActivityOmitsSlotValues() {
        ActivityView activity = new ActivityView(
                "Puls", null, null, "Adhoc", null, "9", "2",
                "https://careplan.example/fhir/ServiceRequest/657450",
                "https://careplan.example/fhir/EpisodeOfCare/5");

        String html = render(activity);

        assertThat(html).contains("timingType=Adhoc");
        assertThat(html).doesNotContain("slotStart=2026");
    }

    private String render(ActivityView activity) {
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        IWebExchange exchange = webApplication.buildExchange(request, response);

        WebContext context = new WebContext(exchange);
        context.setVariable("day", new DayView(LocalDate.of(2026, 4, 15), List.of(activity)));
        context.setVariable("today", LocalDate.of(2026, 4, 15));
        return engine.process("fragments/activity-group", Set.of("day"), context);
    }
}
