package local.pepal.journal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PepalJournalApplication {
    public static void main(String[] args) {
        SpringApplication.run(PepalJournalApplication.class, args);
    }
}
