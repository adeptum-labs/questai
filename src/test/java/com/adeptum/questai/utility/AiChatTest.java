/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai.utility;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatTest {

	@Test
	void askReturnsTrimmedResponse() {
		final OpenAiChatModel chatModel = mockModelReturning("  Edric Stone  ");

		assertEquals("Edric Stone",
			AiChat.ask(chatModel, "prompt", "fallback"));
	}

	@Test
	void askReturnsFallbackOnBlankResponse() {
		final OpenAiChatModel chatModel = mockModelReturning("   ");

		assertEquals("fallback",
			AiChat.ask(chatModel, "prompt", "fallback"));
	}

	private OpenAiChatModel mockModelReturning(final String text) {
		final OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
		final ChatResponse response = ChatResponse.builder()
			.aiMessage(AiMessage.from(text))
			.build();
		when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);
		return chatModel;
	}
}
