package ma.aquaops.assets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AssetsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AssetsServiceApplication.class, args);
	}

}
