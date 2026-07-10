package dk.sundhed.ehealth.referenceclients.common.infrastructure.web;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class FhirErrorAdvice {

    @ExceptionHandler(ForbiddenOperationException.class)
    public String handleForbidden(ForbiddenOperationException forbiddenException, Model model) {
        model.addAttribute("message", forbiddenException.getMessage());
        return "error/forbidden";
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public String handleUnprocessable(UnprocessableEntityException unprocessableException, Model model) {
        model.addAttribute("message", unprocessableException.getMessage());
        return "error/unprocessable";
    }

    @ExceptionHandler(BaseServerResponseException.class)
    public String handleFhirError(BaseServerResponseException fhirException, Model model) {
        model.addAttribute("status", fhirException.getStatusCode());
        model.addAttribute("message", fhirException.getMessage());
        return "error/fhir-error";
    }
}
