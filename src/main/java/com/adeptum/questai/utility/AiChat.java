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

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Utility for single-prompt chat model calls.
 */
public final class AiChat {

	private AiChat() {
	}

	/**
	 * Sends a single user prompt to the chat model and returns the trimmed
	 * response text, or the fallback when the response is empty.
	 */
	public static String ask(final OpenAiChatModel chatModel, final String prompt,
		final String fallback) {

		final ChatRequest request = ChatRequest.builder()
			.messages(UserMessage.from(prompt))
			.build();
		final String text = chatModel.chat(request).aiMessage().text().trim();
		return text.isEmpty() ? fallback : text;
	}
}
