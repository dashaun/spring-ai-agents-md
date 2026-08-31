package io.github.springaicommunity.agentsmd.example;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private final ChatClient chatClient;

	ChatController(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	@PostMapping("/chat")
	ChatResponse chat(@RequestBody ChatRequest request) {
		Assert.hasText(request.message(), "message must not be blank");
		return new ChatResponse(this.chatClient.prompt().user(request.message()).call().content());
	}

	record ChatRequest(String message) {
	}

	record ChatResponse(String content) {
	}

}
