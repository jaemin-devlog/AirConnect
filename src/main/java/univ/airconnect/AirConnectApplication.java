package univ.airconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AirConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(AirConnectApplication.class, args);
    }

}
