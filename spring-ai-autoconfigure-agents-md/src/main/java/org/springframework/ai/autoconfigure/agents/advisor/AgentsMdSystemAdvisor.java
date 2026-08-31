package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 */
public class AgentsMdSystemAdvisor implements CallAdvisor, StreamAdvisor {

	private static final String INJECTED_CONTEXT = "spring.ai.agents-md.injected-context";

	private final AgentsMdResolver resolver;

	private final AgentsMdTargetPathResolver targetPathResolver;

	private final ObservationRegistry observationRegistry;

	private final @Nullable DistributionSummary contextSize;

	private final @Nullable ApplicationEventPublisher eventPublisher;

	public AgentsMdSystemAdvisor(AgentsMdDocument document) {
		this(document, ObservationRegistry.NOOP);
	}

	public AgentsMdSystemAdvisor(AgentsMdDocument document, ObservationRegistry observationRegistry) {
		Assert.notNull(document, "AgentsMdDocument must not be null");
		this.resolver = target -> new AgentsMdResolution(target,
				List.of(new AgentsMdResource("provided AGENTS.md", document)));
		this.targetPathResolver = new DefaultAgentsMdTargetPathResolver(Path.of(System.getProperty("user.dir")));
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
		this.observationRegistry = observationRegistry;
		this.contextSize = null;
		this.eventPublisher = null;
	}

	public AgentsMdSystemAdvisor(AgentsMdResolver resolver, AgentsMdTargetPathResolver targetPathResolver,
			ObservationRegistry observationRegistry) {
		this(resolver, targetPathResolver, observationRegistry, null);
	}

	public AgentsMdSystemAdvisor(AgentsMdResolver resolver, AgentsMdTargetPathResolver targetPathResolver,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry) {
		this(resolver, targetPathResolver, observationRegistry, meterRegistry, null);
	}

	public AgentsMdSystemAdvisor(AgentsMdResolver resolver, AgentsMdTargetPathResolver targetPathResolver,
			ObservationRegistry observationRegistry, @Nullable MeterRegistry meterRegistry,
			@Nullable ApplicationEventPublisher eventPublisher) {
		Assert.notNull(resolver, "AgentsMdResolver must not be null");
		Assert.notNull(targetPathResolver, "AgentsMdTargetPathResolver must not be null");
		Assert.notNull(observationRegistry, "ObservationRegistry must not be null");
		this.resolver = resolver;
		this.targetPathResolver = targetPathResolver;
		this.observationRegistry = observationRegistry;
		this.contextSize = meterRegistry == null ? null
				: DistributionSummary.builder(AgentsMdObservations.CONTEXT_SIZE)
					.description("Size of AGENTS.md context added to the system prompt")
					.baseUnit("characters")
					.register(meterRegistry);
		this.eventPublisher = eventPublisher;
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
		String previousContext = request.context().get(INJECTED_CONTEXT) instanceof String value ? value : "";
		Prompt prompt = request.prompt()
			.augmentSystemMessage(systemMessage -> replace(systemMessage, previousContext, context));
		Map<String, Object> requestContext = new HashMap<>(request.context());
		if (context.isBlank()) {
			requestContext.remove(INJECTED_CONTEXT);
		}
		else {
			requestContext.put(INJECTED_CONTEXT, context);
		}
		return request.mutate().prompt(prompt).context(requestContext).build();
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

	private SystemMessage replace(SystemMessage systemMessage, String previousContext, String context) {
		String existing = systemMessage.getText() == null ? "" : systemMessage.getText();
		if (!previousContext.isBlank() && existing.endsWith(previousContext)) {
			existing = existing.substring(0, existing.length() - previousContext.length());
			if (existing.endsWith("\n\n")) {
				existing = existing.substring(0, existing.length() - 2);
			}
		}
		String separator = existing.isBlank() || context.isBlank() ? "" : "\n\n";
		return systemMessage.mutate().text(existing + separator + context).build();
	}

}
