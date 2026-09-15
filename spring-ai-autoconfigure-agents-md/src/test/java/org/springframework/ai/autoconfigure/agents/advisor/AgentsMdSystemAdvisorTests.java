package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.autoconfigure.agents.observation.AgentsMdObservations;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolution;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolver;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResource;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolutionOutcome;
import org.springframework.ai.autoconfigure.agents.observation.AgentsMdLimitReachedEvent;
import org.springframework.ai.autoconfigure.agents.parser.AgentsMdDocument;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentsMdSystemAdvisorTests {

	@Test
	void appendsTheCompleteDocumentWithoutRewritingIt() {
		String markdown = """
				# Project instructions

				## Testing

				Run `./mvnw clean test`.
				""";
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.of(new AgentsMdDocument(markdown));
		ChatClientRequest request = new ChatClientRequest(
				new Prompt(List.of(new SystemMessage("Existing instructions"), new UserMessage("Hello"))), Map.of());
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		ChatClientResponse response = response();
		when(chain.nextCall(any())).thenReturn(response);

		assertThat(advisor.adviseCall(request, chain)).isSameAs(response);

		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(chain).nextCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().prompt().getSystemMessage().getText())
			.startsWith("Existing instructions\n\n<!-- spring-ai-agents-md:start -->\n# AGENTS.md instructions")
			.contains("follow the explicit user instruction", markdown);
		assertThat(advisor.getOrder()).isEqualTo(ToolCallingAdvisor.DEFAULT_ORDER + 10);
		assertThat(advisor.getName()).isEqualTo("AgentsMdSystemAdvisor");
	}

	@Test
	void createsSystemMessageFromTheCompleteDocument() {
		String markdown = "# Instructions\n\nUse any headings that fit the project.";
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.of(new AgentsMdDocument(markdown));
		ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("Hello")), Map.of());
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request, chain);

		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(chain).nextCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().prompt().getSystemMessage().getText())
			.startsWith("<!-- spring-ai-agents-md:start -->\n# AGENTS.md instructions")
			.contains("follow the explicit user instruction", markdown);
	}

	@Test
	void appendsTheCompleteDocumentToStreamingRequests() {
		String markdown = "# Streaming instructions";
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.of(new AgentsMdDocument(markdown));
		ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("Hello")), Map.of());
		StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
		when(chain.nextStream(any())).thenReturn(Flux.just(response()));

		assertThat(advisor.adviseStream(request, chain).collectList().block()).hasSize(1);

		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(chain).nextStream(requestCaptor.capture());
		assertThat(requestCaptor.getValue().prompt().getSystemMessage().getText())
			.startsWith("<!-- spring-ai-agents-md:start -->\n# AGENTS.md instructions")
			.contains(markdown);
	}

	@Test
	void resolvesInstructionsForTheExplicitRequestTarget() {
		Path target = Path.of("module/src/Example.java");
		AgentsMdResolver resolver = mock(AgentsMdResolver.class);
		when(resolver.resolve(target)).thenReturn(new AgentsMdResolution(target,
				List.of(new AgentsMdResource("module/AGENTS.md", new AgentsMdDocument("# Module instructions")))));
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.builder()
			.resolver(resolver)
			.targetPathResolver(new DefaultAgentsMdTargetPathResolver(Path.of("workspace")))
			.observationRegistry(ObservationRegistry.NOOP)
			.build();
		ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("Hello")),
				Map.of(AgentsMdAdvisorParams.TARGET_PATH, target));
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request, chain);

		verify(resolver).resolve(target);
		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(chain).nextCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().prompt().getSystemMessage().getText())
			.contains("follow the explicit user instruction", "# Module instructions");
	}

	@Test
	void replacesPreviouslyInjectedInstructionsWhenAToolChangesTheActivePath() {
		Path firstTarget = Path.of("first/Example.java");
		Path secondTarget = Path.of("second/Example.java");
		AgentsMdResolver resolver = target -> new AgentsMdResolution(target,
				List.of(new AgentsMdResource(target.toString(), new AgentsMdDocument("# " + target.getName(0)))));
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.builder()
			.resolver(resolver)
			.targetPathResolver(new DefaultAgentsMdTargetPathResolver(Path.of("workspace")))
			.observationRegistry(ObservationRegistry.NOOP)
			.build();
		AgentsMdActivePath activePath = new AgentsMdActivePath();
		activePath.update(firstTarget);
		ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("Hello")),
				Map.of(AgentsMdAdvisorParams.ACTIVE_PATH, activePath));
		CallAdvisorChain firstChain = mock(CallAdvisorChain.class);
		when(firstChain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request, firstChain);
		var firstRequest = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(firstChain).nextCall(firstRequest.capture());
		activePath.update(secondTarget);
		CallAdvisorChain secondChain = mock(CallAdvisorChain.class);
		when(secondChain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(firstRequest.getValue(), secondChain);

		var secondRequest = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(secondChain).nextCall(secondRequest.capture());
		assertThat(secondRequest.getValue().prompt().getSystemMessage().getText())
			.endsWith("<!-- spring-ai-agents-md:end -->")
			.contains("# second")
			.doesNotContain("# first");
	}

	@Test
	void replacesPreviouslyInjectedInstructionsEvenWhenAnotherAdvisorAppendsText() {
		Path firstTarget = Path.of("first/Example.java");
		Path secondTarget = Path.of("second/Example.java");
		AgentsMdResolver resolver = target -> new AgentsMdResolution(target,
				List.of(new AgentsMdResource(target.toString(), new AgentsMdDocument("# " + target.getName(0)))));
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.builder()
			.resolver(resolver)
			.targetPathResolver(new DefaultAgentsMdTargetPathResolver(Path.of("workspace")))
			.observationRegistry(ObservationRegistry.NOOP)
			.build();
		AgentsMdActivePath activePath = new AgentsMdActivePath();
		activePath.update(firstTarget);
		ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("Hello")),
				Map.of(AgentsMdAdvisorParams.ACTIVE_PATH, activePath));
		CallAdvisorChain firstChain = mock(CallAdvisorChain.class);
		when(firstChain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request, firstChain);
		var firstRequest = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(firstChain).nextCall(firstRequest.capture());
		Prompt appendedPrompt = firstRequest.getValue()
			.prompt()
			.augmentSystemMessage(systemMessage -> systemMessage.mutate()
				.text(systemMessage.getText() + "\n\nAppended by another advisor")
				.build());
		ChatClientRequest appended = firstRequest.getValue().mutate().prompt(appendedPrompt).build();
		activePath.update(secondTarget);
		CallAdvisorChain secondChain = mock(CallAdvisorChain.class);
		when(secondChain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(appended, secondChain);

		var secondRequest = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(secondChain).nextCall(secondRequest.capture());
		assertThat(secondRequest.getValue().prompt().getSystemMessage().getText())
			.contains("# second", "Appended by another advisor")
			.doesNotContain("# first");
	}

	@Test
	void removesPreviouslyInjectedMarkersWhenNoDocumentsApply() {
		AtomicBoolean hasDocument = new AtomicBoolean(true);
		AgentsMdResolver resolver = target -> hasDocument.get()
				? new AgentsMdResolution(target,
						List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Root"))))
				: new AgentsMdResolution(target, List.of());
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.builder()
			.resolver(resolver)
			.targetPathResolver(new DefaultAgentsMdTargetPathResolver(Path.of("workspace")))
			.observationRegistry(ObservationRegistry.NOOP)
			.build();
		ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("Hello")), Map.of());
		CallAdvisorChain firstChain = mock(CallAdvisorChain.class);
		when(firstChain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request, firstChain);
		var firstRequest = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(firstChain).nextCall(firstRequest.capture());
		assertThat(firstRequest.getValue().prompt().getSystemMessage().getText())
			.contains("<!-- spring-ai-agents-md:start -->");
		hasDocument.set(false);
		CallAdvisorChain secondChain = mock(CallAdvisorChain.class);
		when(secondChain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(firstRequest.getValue(), secondChain);

		var secondRequest = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(secondChain).nextCall(secondRequest.capture());
		assertThat(secondRequest.getValue().prompt().getSystemMessage().getText())
			.doesNotContain("<!-- spring-ai-agents-md:start -->")
			.doesNotContain("<!-- spring-ai-agents-md:end -->");
	}

	@Test
	void observesPromptAugmentationWithLowCardinalityDocumentState() {
		ObservationRegistry registry = ObservationRegistry.create();
		RecordingObservationHandler handler = new RecordingObservationHandler();
		registry.observationConfig().observationHandler(handler);
		AgentsMdSystemAdvisor advisor = new AgentsMdSystemAdvisor(new AgentsMdDocument("# Instructions"), registry);
		ChatClientRequest request = new ChatClientRequest(new Prompt(new UserMessage("Hello")), Map.of());
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request, chain);

		assertThat(handler.started).isEqualTo(1);
		assertThat(handler.stopped).isEqualTo(1);
		assertThat(handler.name).isEqualTo(AgentsMdObservations.ADVISOR);
		assertThat(handler.contextualName).isEqualTo(AgentsMdObservations.ADVISOR_CONTEXTUAL_NAME);
		assertThat(handler.documentState).isEqualTo(AgentsMdObservations.DOCUMENT_PRESENT);
		assertThat(handler.documentCount).isEqualTo(AgentsMdObservations.DOCUMENT_COUNT_ONE);
		assertThat(handler.resolutionOutcome).isEqualTo("complete");
	}

	@Test
	void recordsDocumentCountBucketsAndInjectedContextSize() {
		ObservationRegistry observationRegistry = ObservationRegistry.create();
		RecordingObservationHandler handler = new RecordingObservationHandler();
		observationRegistry.observationConfig().observationHandler(handler);
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		AgentsMdResolver resolver = target -> target.endsWith("empty") ? new AgentsMdResolution(target, List.of())
				: new AgentsMdResolution(target,
						List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Root")),
								new AgentsMdResource("module/AGENTS.md", new AgentsMdDocument("# Module"))));
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.builder()
			.resolver(resolver)
			.targetPathResolver(request -> (Path) request.context().get(AgentsMdAdvisorParams.TARGET_PATH))
			.observationRegistry(observationRegistry)
			.meterRegistry(meterRegistry)
			.build();
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request(Map.of(AgentsMdAdvisorParams.TARGET_PATH, Path.of("empty"))), chain);
		advisor.adviseCall(request(Map.of(AgentsMdAdvisorParams.TARGET_PATH, Path.of("multiple"))), chain);

		assertThat(handler.documentCounts).containsExactly(AgentsMdObservations.DOCUMENT_COUNT_ZERO,
				AgentsMdObservations.DOCUMENT_COUNT_MULTIPLE);
		assertThat(meterRegistry.get(AgentsMdObservations.CONTEXT_SIZE).summary().count()).isEqualTo(2);
		assertThat(meterRegistry.get(AgentsMdObservations.CONTEXT_SIZE).summary().totalAmount()).isPositive();
	}

	@Test
	void publishesOneApplicationEventWhenAResolutionLimitIsReached() {
		Path target = Path.of("module/Example.java");
		AgentsMdResolver resolver = ignored -> new AgentsMdResolution(target,
				List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Root"))),
				AgentsMdResolutionOutcome.SIZE_LIMIT, 256 * 1024);
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		AgentsMdSystemAdvisor advisor = AgentsMdSystemAdvisor.builder()
			.resolver(resolver)
			.targetPathResolver(request -> target)
			.observationRegistry(ObservationRegistry.NOOP)
			.eventPublisher(publisher)
			.build();
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request(Map.of()), chain);

		var event = org.mockito.ArgumentCaptor.forClass(AgentsMdLimitReachedEvent.class);
		verify(publisher).publishEvent(event.capture());
		assertThat(event.getValue().target()).isEqualTo(target);
		assertThat(event.getValue().outcome()).isEqualTo(AgentsMdResolutionOutcome.SIZE_LIMIT);
		assertThat(event.getValue().acceptedDocumentCount()).isEqualTo(1);
		assertThat(event.getValue().contextSizeBytes()).isEqualTo(new AgentsMdResolution(target,
				List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Root"))))
			.toSystemPromptContext()
			.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
		assertThat(event.getValue().configuredLimit()).isEqualTo(256 * 1024);
	}

	@Test
	void builderRequiresAResolver() {
		assertThatThrownBy(
				() -> AgentsMdSystemAdvisor.builder().targetPathResolver(request -> Path.of("target")).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("AgentsMdResolver must not be null");
	}

	@Test
	void builderRequiresATargetPathResolver() {
		assertThatThrownBy(() -> AgentsMdSystemAdvisor.builder()
			.resolver(target -> new AgentsMdResolution(target, List.of()))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("AgentsMdTargetPathResolver must not be null");
	}

	@Test
	void deprecatedDocumentConstructorStillInjectsTheDocument() {
		AgentsMdSystemAdvisor advisor = new AgentsMdSystemAdvisor(new AgentsMdDocument("# Legacy"));
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request(Map.of()), chain);

		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(chain).nextCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().prompt().getSystemMessage().getText()).contains("# Legacy");
	}

	@Test
	void deprecatedDocumentAndRegistryConstructorStillInjectsTheDocument() {
		AgentsMdSystemAdvisor advisor = new AgentsMdSystemAdvisor(new AgentsMdDocument("# Legacy"),
				ObservationRegistry.NOOP);
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request(Map.of()), chain);

		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(chain).nextCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().prompt().getSystemMessage().getText()).contains("# Legacy");
	}

	@Test
	void deprecatedResolverConstructorStillResolves() {
		AgentsMdResolver resolver = target -> new AgentsMdResolution(target,
				List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Legacy"))));
		AgentsMdSystemAdvisor advisor = new AgentsMdSystemAdvisor(resolver,
				new DefaultAgentsMdTargetPathResolver(Path.of("workspace")), ObservationRegistry.NOOP);
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request(Map.of()), chain);

		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatClientRequest.class);
		verify(chain).nextCall(requestCaptor.capture());
		assertThat(requestCaptor.getValue().prompt().getSystemMessage().getText()).contains("# Legacy");
	}

	@Test
	void deprecatedResolverAndMeterConstructorStillResolves() {
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		AgentsMdResolver resolver = target -> new AgentsMdResolution(target,
				List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Legacy"))));
		AgentsMdSystemAdvisor advisor = new AgentsMdSystemAdvisor(resolver,
				new DefaultAgentsMdTargetPathResolver(Path.of("workspace")), ObservationRegistry.NOOP, meterRegistry);
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request(Map.of()), chain);

		assertThat(meterRegistry.get(AgentsMdObservations.CONTEXT_SIZE).summary().count()).isEqualTo(1);
	}

	@Test
	void deprecatedFullConstructorStillResolvesAndPublishesEvents() {
		Path target = Path.of("module/Example.java");
		AgentsMdResolver resolver = ignored -> new AgentsMdResolution(target,
				List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Legacy"))),
				AgentsMdResolutionOutcome.SIZE_LIMIT, 256 * 1024);
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		AgentsMdSystemAdvisor advisor = new AgentsMdSystemAdvisor(resolver, request -> target, ObservationRegistry.NOOP,
				null, publisher);
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		when(chain.nextCall(any())).thenReturn(response());

		advisor.adviseCall(request(Map.of()), chain);

		verify(publisher).publishEvent(any(AgentsMdLimitReachedEvent.class));
	}

	private static ChatClientResponse response() {
		return new ChatClientResponse(new ChatResponse(List.of()), Map.of());
	}

	private ChatClientRequest request(Map<String, Object> context) {
		return new ChatClientRequest(new Prompt(new UserMessage("Hello")), context);
	}

	private static final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

		private int started;

		private int stopped;

		private String name;

		private String contextualName;

		private String documentState;

		private String documentCount;

		private String resolutionOutcome;

		private final List<String> documentCounts = new java.util.ArrayList<>();

		@Override
		public void onStart(Observation.Context context) {
			this.started++;
		}

		@Override
		public void onStop(Observation.Context context) {
			this.stopped++;
			this.name = context.getName();
			this.contextualName = context.getContextualName();
			this.documentState = context.getLowCardinalityKeyValue(AgentsMdObservations.DOCUMENT_STATE).getValue();
			this.documentCount = context.getLowCardinalityKeyValue(AgentsMdObservations.DOCUMENT_COUNT).getValue();
			this.resolutionOutcome = context.getLowCardinalityKeyValue(AgentsMdObservations.RESOLUTION_OUTCOME)
				.getValue();
			this.documentCounts.add(this.documentCount);
		}

		@Override
		public boolean supportsContext(Observation.Context context) {
			return true;
		}

	}

}
