package ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service d'accès centralisé au modèle de langage Gemini via LangChain4j.
 * Gère la mémoire de chat et le rôle système pour maintenir le contexte.
 */
@ApplicationScoped
public class LlmClient {

    private String systemRole;
    private Assistant assistant;
    private ChatMemory chatMemory;



    public LlmClient() {
        String apiKey = System.getenv("GEMINI_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Erreur : variable d'environnement GEMINI_KEY absente ou vide."
            );
        }


        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .logRequestsAndResponses(false)
                .temperature(0.2)
                .build();

       this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                    .build();

            // try to create a RAG retriever and, if available, rebuild assistant with it
            try {
                EmbeddingStoreContentRetriever retriever = RagService.createRetriever();
                if (retriever != null) {
                    this.assistant = AiServices.builder(Assistant.class)
                            .chatModel(model)
                            .chatMemory(chatMemory)
                            .contentRetriever(retriever)
                            .build();
                }
            } catch (Throwable t) {
                // If anything goes wrong, keep the non-RAG assistant. Log at debug level.
                Logger.getLogger(LlmClient.class.getName()).log(Level.FINE, "RAG not enabled: {0}", t.toString());
            }
    }


    public void setSystemRole(String role) {
        this.systemRole = role;

        chatMemory.clear();


        if (role != null && !role.trim().isEmpty()) {
            chatMemory.add(SystemMessage.from(role));
        }
    }


    public String ask(String prompt) {
        return assistant.chat(prompt);
    }



    public String getSystemRole() {
        return systemRole;
    }
}