package com.sun.note.client;

import java.util.List;

public record OllamaChatRequest(String model, List<OllamaMessage> messages, boolean stream) {
}
