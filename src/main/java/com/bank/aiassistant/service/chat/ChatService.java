package com.bank.aiassistant.service.chat;

import com.bank.aiassistant.model.dto.chat.ChatRequest;
import com.bank.aiassistant.model.dto.chat.ChatResponse;
import com.bank.aiassistant.model.entity.Conversation;
import com.bank.aiassistant.model.entity.Message;
import com.bank.aiassistant.model.entity.User;
import com.bank.aiassistant.repository.ConversationRepository;
import com.bank.aiassistant.repository.UserRepository;
import com.bank.aiassistant.service.agent.AgentOrchestrator;
import com.bank.aiassistant.service.connector.ConnectorRegistry;
import com.bank.aiassistant.service.guardrails.GuardrailChain;
import com.bank.aiassistant.service.vector.VectorStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;

/**
 * Orchestrates the full chat turn:
 * <ol>
 *   <li>Guardrail input check</li>
 *   <li>Hybrid RAG retrieval (vector + live connectors)</li>
 *   <li>Prompt augmentation with retrieved context</li>
 *   <li>Agent loop (detect tool calls → execute → continue)</li>
 *   <li>Guardrail output check</li>
 *   <li>Persist and return</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ConversationMemoryService memoryService;
    private final VectorStoreService vectorStoreService;
    private final AgentOrchestrator agentOrchestrator;
    private final ConnectorRegistry connectorRegistry;
    private final GuardrailChain guardrailChain;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Blocking Chat
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ChatResponse chat(ChatRequest request, String userEmail) {
        long start = System.currentTimeMillis();
        User user = resolveUser(userEmail);

        // 1. Input guardrails
        String sanitizedInput = guardrailChain.checkInput(request.message(), userEmail);

        // 2. Get or create conversation
        Conversation conversation = resolveConversation(request, user);

        // 3. Save user message
        memoryService.saveUserMessage(conversation, sanitizedInput);

        // 4. RAG: retrieve relevant context
        String augmentedPrompt = buildAugmentedPrompt(sanitizedInput, request.connectorIds(), userEmail);

        // 5. Build message history
        var messages = memoryService.buildContext(conversation.getId(), augmentedPrompt, null);

        // 6. Agent orchestration (handles tool calls internally)
        AgentOrchestrator.AgentResult result = agentOrchestrator.run(messages, conversation.getId());

        // 7. Output guardrails
        String safeOutput = guardrailChain.checkOutput(result.content(), userEmail);

        // 8. Persist assistant message
        String metadataJson = buildMetadataJson(result);
        Message assistantMessage = memoryService.saveAssistantMessage(
                conversation, safeOutput, result.model(),
                result.outputTokens(), System.currentTimeMillis() - start, metadataJson);

        log.info("Chat completed for user={} conv={} latency={}ms tokens={}",
                userEmail, conversation.getId(), System.currentTimeMillis() - start, result.outputTokens());

        return new ChatResponse(
                assistantMessage.getId(),
                conversation.getId(),
                safeOutput,
                result.citations(),
                result.artifacts(),
                result.model(),
                result.inputTokens(),
                result.outputTokens(),
                System.currentTimeMillis() - start,
                Instant.now()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Streaming Chat
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Flux<String> chatStream(ChatRequest request, String userEmail) {
        User user = resolveUser(userEmail);
        String sanitizedInput = guardrailChain.checkInput(request.message(), userEmail);
        Conversation conversation = resolveConversation(request, user);
        memoryService.saveUserMessage(conversation, sanitizedInput);

        String augmentedPrompt = buildAugmentedPrompt(sanitizedInput, request.connectorIds(), userEmail);
        var messages = memoryService.buildContext(conversation.getId(), augmentedPrompt, null);

        return agentOrchestrator.runStream(messages, conversation.getId())
                .doOnError(ex -> log.error("Stream error for user={}", userEmail, ex));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildAugmentedPrompt(String userQuery, List<String> connectorIds, String userEmail) {
        StringBuilder context = new StringBuilder();

        // Vector search (static knowledge base)
        List<Document> vectorDocs = vectorStoreService.hybridSearch(userQuery,
                Map.of("user_id", userEmail), 6);
        if (!vectorDocs.isEmpty()) {
            context.append("\n## Retrieved Knowledge Base Context\n");
            vectorDocs.forEach(doc -> context.append("- ").append(doc.getText()).append("\n"));
        }

        // Live connector data
        if (connectorIds != null && !connectorIds.isEmpty()) {
            connectorIds.forEach(connectorId -> {
                try {
                    String liveData = connectorRegistry.query(connectorId, userQuery);
                    if (liveData != null && !liveData.isBlank()) {
                        context.append("\n## Live Data from connector [").append(connectorId).append("]\n");
                        context.append(liveData).append("\n");
                    }
                } catch (Exception ex) {
                    log.warn("Failed to query connector {}: {}", connectorId, ex.getMessage());
                }
            });
        }

        if (context.isEmpty()) return userQuery;
        return userQuery + "\n\n---\n" + context;
    }

    private Conversation resolveConversation(ChatRequest request, User user) {
        if (request.conversationId() != null) {
            return conversationRepository.findByIdAndUserId(request.conversationId(), user.getId())
                    .orElseThrow(() -> new RuntimeException("Conversation not found: " + request.conversationId()));
        }
        Conversation newConv = Conversation.builder()
                .user(user)
                .title(memoryService.generateTitle(request.message()))
                .build();
        return conversationRepository.save(newConv);
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }

    private String buildMetadataJson(AgentOrchestrator.AgentResult result) {
        try {
            Map<String, Object> meta = new HashMap<>();
            if (result.citations() != null) meta.put("citations", result.citations());
            if (result.artifacts() != null) meta.put("artifacts", result.artifacts());
            if (result.toolCalls() != null) meta.put("tool_calls", result.toolCalls());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return "{}";
        }
    }
}
