package org.springframework.ai.autoconfigure.agents;

import java.nio.file.Path;
import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdActivePath;
import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdAdvisorParams;
import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdTargetPathResolver;
import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdSystemAdvisor;
import org.springframework.ai.autoconfigure.agents.advisor.DefaultAgentsMdTargetPathResolver;
import org.springframework.ai.autoconfigure.agents.config.AgentsMdProperties;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolver;
import org.springframework.ai.autoconfigure.agents.discovery.FilesystemAgentsMdResolver;
import org.springframework.ai.autoconfigure.agents.parser.AgentsMdParser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ResourceLoader;

/**
 * Auto-configuration for loading AGENTS.md and making its instructions available to
 * Spring AI.
 */
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(AgentsMdProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.agents-md", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentsMdAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AgentsMdParser agentsMdParser() {
		return new AgentsMdParser();
	}

	@Bean
	@ConditionalOnMissingBean
	AgentsMdResolver agentsMdResolver(AgentsMdParser parser, AgentsMdProperties properties,
			ResourceLoader resourceLoader) {
		return new FilesystemAgentsMdResolver(parser, resourceLoader, properties.getLocation(),
				properties.getFallbackLocation(), workingDirectory(), properties.getMaxDepth(),
				properties.getMaxDocuments(), properties.getMaxDocumentSize().toBytes(),
				properties.getMaxTotalSize().toBytes());
	}

	@Bean
	@ConditionalOnMissingBean
	AgentsMdTargetPathResolver agentsMdTargetPathResolver() {
		return new DefaultAgentsMdTargetPathResolver(workingDirectory());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "spring.ai.agents-md", name = "inject-into-system-prompt", havingValue = "true",
			matchIfMissing = true)
	AgentsMdSystemAdvisor agentsMdSystemAdvisor(AgentsMdResolver resolver,
			AgentsMdTargetPathResolver targetPathResolver, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<MeterRegistry> meterRegistry, ApplicationEventPublisher eventPublisher) {
		return new AgentsMdSystemAdvisor(resolver, targetPathResolver,
				observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP), meterRegistry.getIfAvailable(),
				eventPublisher);
	}

	@Bean
	@ConditionalOnMissingBean(name = "agentsMdChatClientBuilderCustomizer")
	@ConditionalOnProperty(prefix = "spring.ai.agents-md", name = "inject-into-system-prompt", havingValue = "true",
			matchIfMissing = true)
	ChatClientBuilderCustomizer agentsMdChatClientBuilderCustomizer(AgentsMdSystemAdvisor advisor) {
		return builder -> {
			AgentsMdActivePath activePath = new AgentsMdActivePath();
			builder
				.defaultAdvisors(spec -> spec.advisors(advisor).param(AgentsMdAdvisorParams.ACTIVE_PATH, activePath));
			builder.defaultToolContext(Map.of(AgentsMdAdvisorParams.ACTIVE_PATH, activePath));
		};
	}

	private static Path workingDirectory() {
		return Path.of(System.getProperty("user.dir"));
	}

}
