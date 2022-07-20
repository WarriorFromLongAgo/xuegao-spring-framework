package xuegao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestConfig {

	@Bean
	public String name() {
		return "Hello World Spring.";
	}
}