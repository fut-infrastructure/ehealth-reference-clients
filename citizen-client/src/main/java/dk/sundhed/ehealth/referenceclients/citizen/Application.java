package dk.sundhed.ehealth.referenceclients.citizen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "dk.sundhed.ehealth.referenceclients.citizen",
                "dk.sundhed.ehealth.referenceclients.common"
        })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
