package org.springframework.ai.autoconfigure.agents;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdAdvisorParams;
import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdSystemAdvisor;
import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdTargetPathResolver;
import org.springframework.ai.autoconfigure.agents.config.AgentsMdProperties;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolver;
import org.springframework.ai.autoconfigure.agents.parser.AgentsMdParser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentsMdAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AgentsMdAutoConfiguration.class));

	@Test
	@SuppressWarnings("unchecked")
	void configuresResolverPropertiesAdvisorAndBuilderCustomizer() {
		this.contextRunner.withPropertyValues("spring.ai.agents-md.location=classpath:sample-agents.md")
			.run(context -> {
				assertThat(context).hasSingleBean(AgentsMdParser.class)
					.hasSingleBean(AgentsMdResolver.class)
					.hasSingleBean(AgentsMdTargetPathResolver.class)
					.hasSingleBean(AgentsMdProperties.class)
					.hasSingleBean(AgentsMdSystemAdvisor.class)
					.hasSingleBean(ChatClientBuilderCustomizer.class);

				ChatClient.Builder builder = mock(ChatClient.Builder.class);
				context.getBean(ChatClientBuilderCustomizer.class).customize(builder);
				@SuppressWarnings("unchecked")
				var customizer = org.mockito.ArgumentCaptor.forClass(Consumer.class);
				verify(builder).defaultAdvisors(customizer.capture());
				ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
				org.mockito.Mockito
					.when(advisorSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
					.thenReturn(advisorSpec);
				customizer.getValue().accept(advisorSpec);
				verify(advisorSpec).advisors(context.getBean(AgentsMdSystemAdvisor.class));
				verify(advisorSpec).param(org.mockito.ArgumentMatchers.eq(AgentsMdAdvisorParams.ACTIVE_PATH), any());
				verify(builder).defaultToolContext(org.mockito.ArgumentMatchers
					.<Map<String, Object>>argThat(map -> map.containsKey(AgentsMdAdvisorParams.ACTIVE_PATH)));
			});
	}

	@Test
	void backsOffWhenDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.agents-md.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(AgentsMdParser.class)
				.doesNotHaveBean(AgentsMdResolver.class)
				.doesNotHaveBean(AgentsMdSystemAdvisor.class));
	}

	@Test
	void backsOffWhenSpringAiChatClientIsAbsent() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(ChatClient.class))
			.run(context -> assertThat(context).hasNotFailed()
				.doesNotHaveBean(AgentsMdParser.class)
				.doesNotHaveBean(AgentsMdResolver.class)
				.doesNotHaveBean(AgentsMdSystemAdvisor.class));
	}

	@Test
	void doesNotAttachAdvisorWhenPromptInjectionIsDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.agents-md.inject-into-system-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(AgentsMdSystemAdvisor.class)
				.doesNotHaveBean(ChatClientBuilderCustomizer.class));
	}

	@Test
	void allowsOnlyTheAgentsMdCustomizerToBeOverridden() {
		this.contextRunner.withBean("otherCustomizer", ChatClientBuilderCustomizer.class, () -> builder -> {
		}).withBean("agentsMdChatClientBuilderCustomizer", ChatClientBuilderCustomizer.class, () -> builder -> {
		}).run(context -> assertThat(context).getBeans(ChatClientBuilderCustomizer.class).hasSize(2));
	}

	@Test
	void propertiesExposeDocumentedDefaults() {
		AgentsMdProperties properties = new AgentsMdProperties();

		assertThat(properties.isEnabled()).isTrue();
		assertThat(properties.getLocation()).isNull();
		assertThat(properties.getFallbackLocation()).isEqualTo("classpath:AGENTS.md");
		assertThat(properties.isInjectIntoSystemPrompt()).isTrue();
		assertThat(properties.getMaxDepth()).isEqualTo(32);
		assertThat(properties.getMaxDocuments()).isEqualTo(16);
		assertThat(properties.getMaxDocumentSize()).isEqualTo(DataSize.ofKilobytes(64));
		assertThat(properties.getMaxTotalSize()).isEqualTo(DataSize.ofKilobytes(256));
	}

	@Test
	void rejectsInvalidSafetyLimitsDuringConfigurationBinding() {
		this.contextRunner.withPropertyValues("spring.ai.agents-md.max-depth=0")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void usesJacksonThree() throws Exception {
		assertThat(Class.forName("tools.jackson.databind.ObjectMapper")).isNotNull();
	}

	@Test
	void generatesSpringBootConfigurationAndAutoConfigurationMetadata() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		String configurationMetadata = new String(
				Objects.requireNonNull(classLoader.getResourceAsStream("META-INF/spring-configuration-metadata.json"))
					.readAllBytes(),
				StandardCharsets.UTF_8);
		String autoConfigurationMetadata = new String(Objects
			.requireNonNull(classLoader.getResourceAsStream("META-INF/spring-autoconfigure-metadata.properties"))
			.readAllBytes(), StandardCharsets.UTF_8);

		assertThat(configurationMetadata).contains("spring.ai.agents-md.max-depth",
				"spring.ai.agents-md.max-total-size", "Maximum number of directories inspected",
				"Maximum UTF-8 byte size of the complete injected context");
		assertThat(autoConfigurationMetadata).contains(AgentsMdAutoConfiguration.class.getName(), "ConditionalOnClass");
	}

}
