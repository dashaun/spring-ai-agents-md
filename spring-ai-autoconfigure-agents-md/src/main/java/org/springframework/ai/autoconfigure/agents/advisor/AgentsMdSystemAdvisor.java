package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolution;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolutionOutcome;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolver;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResource;
import org.springframework.ai.autoconfigure.agents.observation.AgentsMdObservations;
import org.springframework.ai.autoconfigure.agents.observation.AgentsMdLimitReachedEvent;
import org.springframework.ai.autoconfigure.agents.parser.AgentsMdDocument;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

/**
 * Adds parsed AGENTS.md instructions to a Spring AI system message.
 *
 * <p>
 * Create instances with {@link #builder()} or the single-document convenience
 * {@link #of(AgentsMdDocument)}.
 */
public class AgentsMdSystemAdvisor implements CallAdvisor, StreamAdvisor {

	private static final String CONTEXT_START = "<!-- spring-ai-agents-md:start -->";

	private static final String CONTEXT_END = "<!-- spring-ai-agents-md:end -->";

	private final AgentsMdResolver resolver;

	private final AgentsMdTargetPathResolver targetPathResolver;

	private final ObservationRegistry observationRegistry;

	private final @Nullable DistributionSummary contextSize;

	private final @Nullable ApplicationEventPublisher eventPublisher;

	/**
	 * Create an advisor that always injects a single document.
	 * @param document the document to inject
	 * @deprecated use {@link #of(AgentsMdDocument)}
	 */
	@Deprecated
	public AgentsMdSystemAdvisor(AgentsMdDocument document) {
		this(document, ObservationRegistry.NOOP);
	}

	/**
	 * Create an advisor that always injects a single document.
	 * @param document the document to inject
	 * @param observationRegistry registry used for advisor observations
	 * @deprecated use {@link #of(AgentsMdDocument, ObservationRegistry)}
	 */
	@Deprecated
	public AgentsMdSystemAdvisor(AgentsMdDocument document, ObservationRegistry observationRegistry) {
		this(builder().resolver(documentResolver(document))
			.targetPathResolver(defaultTargetPathResolver())
			.observationRegistry(observationRegistry));
	}

	/**
	 * Create an advisor that resolves instructions through a resolver.
	 * @param resolver resolves applicable documents for a target
	 * @param targetPathResolver resolves the request target
	 * @param observationRegistry registry used for advisor observations
	 * @deprecated use {@link #builder()}
	 */
	@Deprecated
	public AgentsMdSystemAdvisor(AgentsMdResolver resolver, AgentsMdTargetPathResolver targetPathResolver,
			ObservationRegistry observationRegistry) {
		this(builder().resolver(resolver)
			.targetPathResolver(targetPathResolver)
			.observationRegistry(observationRegistry));
	}

	/**
	 * Create an advisor that resolves instructions through a resolver.
	 * @param resolver resolves applicable documents for a target
	 * @param targetPathResolver resolves the request target
	 * @param observationRegistry registry used for advisor observations
	 * @param meterRegistry registry used for the context-size summary, or {@code null}
	 * @deprecated use {@link #builder()}
	 */
	@Deprecated
	public AgentsMdSystemAdvisor(AgentsMdResolver resolver, AgentsMdTargetPathResolver targetPathResolver,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry) {
		this(builder().resolver(resolver)
			.targetPathResolver(targetPathResolver)
			.observationRegistry(observationRegistry)
			.meterRegistry(meterRegistry));
	}

	/**
	 * Create an advisor that resolves instructions through a resolver.
	 * @param resolver resolves applicable documents for a target
	 * @param targetPathResolver resolves the request target
	 * @param observationRegistry registry used for advisor observations
	 * @param meterRegistry registry used for the context-size summary, or {@code null}
	 * @param eventPublisher publisher for limit-reached events, or {@code null}
	 * @deprecated use {@link #builder()}
	 */
	@Deprecated
	public AgentsMdSystemAdvisor(AgentsMdResolver resolver, AgentsMdTargetPathResolver targetPathResolver,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry,
			@Nullable ApplicationEventPublisher eventPublisher) {
		this(builder().resolver(resolver)
			.targetPathResolver(targetPathResolver)
			.observationRegistry(observationRegistry)
			.meterRegistry(meterRegistry)
			.eventPublisher(eventPublisher));
	}

	private AgentsMdSystemAdvisor(Builder builder) {
		Assert.notNull(builder.resolver, "AgentsMdResolver must not be null");
		Assert.notNull(builder.targetPathResolver, "AgentsMdTargetPathResolver must not be null");
		Assert.notNull(builder.observationRegistry, "ObservationRegistry must not be null");
		this.resolver = builder.resolver;
		this.targetPathResolver = builder.targetPathResolver;
		this.observationRegistry = builder.observationRegistry;
		this.contextSize = builder.meterRegistry == null ? null
				: DistributionSummary.builder(AgentsMdObservations.CONTEXT_SIZE)
					.description("Size of AGENTS.md context added to the system prompt")
					.baseUnit("characters")
					.register(builder.meterRegistry);
		this.eventPublisher = builder.eventPublisher;
	}

	/**
	 * Create an advisor that always injects a single document.
	 * @param document the document to inject
	 * @return a new advisor
	 */
	public static AgentsMdSystemAdvisor of(AgentsMdDocument document) {
		return of(document, ObservationRegistry.NOOP);
	}

	/**
	 * Create an advisor that always injects a single document.
	 * @param document the document to inject
	 * @param observationRegistry registry used for advisor observations
	 * @return a new advisor
	 */
	public static AgentsMdSystemAdvisor of(AgentsMdDocument document, ObservationRegistry observationRegistry) {
		Assert.notNull(document, "AgentsMdDocument must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
		return builder().resolver(documentResolver(document))
			.targetPathResolver(defaultTargetPathResolver())
			.observationRegistry(observationRegistry)
			.build();
	}

	/**
	 * Start building an advisor that resolves instructions through a resolver.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	private static AgentsMdResolver documentResolver(AgentsMdDocument document) {
		return target -> new AgentsMdResolution(target, List.of(new AgentsMdResource("provided AGENTS.md", document)));
	}

	private static AgentsMdTargetPathResolver defaultTargetPathResolver() {
		return new DefaultAgentsMdTargetPathResolver(Path.of(System.getProperty("user.dir")));
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest advisedRequest, CallAdvisorChain chain) {
		Assert.notNull(advisedRequest, "ChatClientRequest must not be null");
		Assert.notNull(chain, "CallAdvisorChain must not be null");
		return chain.nextCall(observeAugmentation(advisedRequest));
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest advisedRequest, StreamAdvisorChain chain) {
		Assert.notNull(advisedRequest, "ChatClientRequest must not be null");
		Assert.notNull(chain, "StreamAdvisorChain must not be null");
		return chain.nextStream(observeAugmentation(advisedRequest));
	}

	@Override
	public String getName() {
		return AgentsMdSystemAdvisor.class.getSimpleName();
	}

	@Override
	public int getOrder() {
		return ToolCallingAdvisor.DEFAULT_ORDER + 10;
	}

	private ChatClientRequest augment(ChatClientRequest request, String context) {
		Prompt prompt = request.prompt().augmentSystemMessage(systemMessage -> replace(systemMessage, context));
		return request.mutate().prompt(prompt).build();
	}

	private ChatClientRequest observeAugmentation(ChatClientRequest request) {
		Path target = this.targetPathResolver.resolve(request);
		AgentsMdResolution resolution = this.resolver.resolve(target);
		String context = resolution.toSystemPromptContext();
		String documentState = context.isBlank() ? AgentsMdObservations.DOCUMENT_EMPTY
				: AgentsMdObservations.DOCUMENT_PRESENT;
		String documentCount = documentCount(resolution.resources().size());
		if (this.contextSize != null) {
			this.contextSize.record(context.length());
		}
		publishLimitEvent(resolution, context);
		return Observation.createNotStarted(AgentsMdObservations.ADVISOR, this.observationRegistry)
			.contextualName(AgentsMdObservations.ADVISOR_CONTEXTUAL_NAME)
			.lowCardinalityKeyValue(AgentsMdObservations.DOCUMENT_STATE, documentState)
			.lowCardinalityKeyValue(AgentsMdObservations.DOCUMENT_COUNT, documentCount)
			.lowCardinalityKeyValue(AgentsMdObservations.RESOLUTION_OUTCOME, resolution.outcome().tagValue())
			.observe(() -> augment(request, context));
	}

	private void publishLimitEvent(AgentsMdResolution resolution, String context) {
		if (this.eventPublisher == null || resolution.outcome() == AgentsMdResolutionOutcome.COMPLETE) {
			return;
		}
		this.eventPublisher.publishEvent(
				new AgentsMdLimitReachedEvent(resolution.target(), resolution.outcome(), resolution.resources().size(),
						context.getBytes(StandardCharsets.UTF_8).length, resolution.configuredLimit()));
	}

	private String documentCount(int count) {
		if (count == 0) {
			return AgentsMdObservations.DOCUMENT_COUNT_ZERO;
		}
		return count == 1 ? AgentsMdObservations.DOCUMENT_COUNT_ONE : AgentsMdObservations.DOCUMENT_COUNT_MULTIPLE;
	}

	private SystemMessage replace(SystemMessage systemMessage, String context) {
		String existing = systemMessage.getText() == null ? "" : systemMessage.getText();
		int start = existing.indexOf(CONTEXT_START);
		int end = existing.indexOf(CONTEXT_END);
		if (start >= 0 && end > start) {
			existing = existing.substring(0, start) + existing.substring(end + CONTEXT_END.length());
		}
		if (context.isBlank()) {
			return systemMessage.mutate().text(existing.stripTrailing()).build();
		}
		String separator = existing.isBlank() ? "" : "\n\n";
		String wrapped = CONTEXT_START + "\n" + context + "\n" + CONTEXT_END;
		return systemMessage.mutate().text(existing + separator + wrapped).build();
	}

	/**
	 * Builder for {@link AgentsMdSystemAdvisor}.
	 */
	public static class Builder {

		private @Nullable AgentsMdResolver resolver;

		private @Nullable AgentsMdTargetPathResolver targetPathResolver;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		private @Nullable MeterRegistry meterRegistry;

		private @Nullable ApplicationEventPublisher eventPublisher;

		private Builder() {
		}

		/**
		 * Set the resolver that resolves applicable documents for a target.
		 * @param resolver the resolver
		 * @return this builder
		 */
		public Builder resolver(AgentsMdResolver resolver) {
			Assert.notNull(resolver, "AgentsMdResolver must not be null");
			this.resolver = resolver;
			return this;
		}

		/**
		 * Set the resolver that resolves the request target.
		 * @param targetPathResolver the target-path resolver
		 * @return this builder
		 */
		public Builder targetPathResolver(AgentsMdTargetPathResolver targetPathResolver) {
			Assert.notNull(targetPathResolver, "AgentsMdTargetPathResolver must not be null");
			this.targetPathResolver = targetPathResolver;
			return this;
		}

		/**
		 * Set the registry used for advisor observations.
		 * @param observationRegistry the observation registry
		 * @return this builder
		 */
		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Set the registry used for the context-size summary.
		 * @param meterRegistry the meter registry, or {@code null} to disable the summary
		 * @return this builder
		 */
		public Builder meterRegistry(@Nullable MeterRegistry meterRegistry) {
			this.meterRegistry = meterRegistry;
			return this;
		}

		/**
		 * Set the publisher for limit-reached events.
		 * @param eventPublisher the event publisher, or {@code null} to disable events
		 * @return this builder
		 */
		public Builder eventPublisher(@Nullable ApplicationEventPublisher eventPublisher) {
			this.eventPublisher = eventPublisher;
			return this;
		}

		/**
		 * Build the advisor.
		 * @return a new advisor
		 */
		public AgentsMdSystemAdvisor build() {
			return new AgentsMdSystemAdvisor(this);
		}

	}

}
