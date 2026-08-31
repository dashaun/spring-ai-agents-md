package io.github.springaicommunity.agentsmd.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AiConfiguration {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}

}
