package dk.sundhed.ehealth.referenceclients.clinician;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "dk.sundhed.ehealth.referenceclients.clinician",
                "dk.sundhed.ehealth.referenceclients.common"
        })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
