package io.github.springaicommunity.agentsmd.codingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RepositoryStewardProperties.class)
public class CodingAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodingAgentApplication.class, args);
	}

}
