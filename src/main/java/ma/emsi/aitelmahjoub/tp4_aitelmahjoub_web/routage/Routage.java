package ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.routage;

import ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.llm.Assistant;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Routage {

    private static void configureLogger() {
        System.out.println("Configuring logger");
        Logger packageLogger = Logger.getLogger("dev.langchain4j");
        packageLogger.setLevel(Level.FINE);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINE);
        packageLogger.addHandler(handler);
    }

    public static void main(String[] args) {
        configureLogger();
        System.out.println("===  Routage ===");

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // Charger et segmenter les documents avec PDFBox
        List<TextSegment> segmentsIA = loadAndSplitPDF("src/document/RAG.pdf");
        List<TextSegment> segmentsSQL = loadAndSplitPDF("src/document/Optimisation des instructions SQL.pdf");

        System.out.println("Segments IA chargés : " + segmentsIA.size());
        System.out.println("Segments SQL chargés : " + segmentsSQL.size());

        // Créer les stores d'embeddings
        EmbeddingStore<TextSegment> storeIA = new InMemoryEmbeddingStore<>();
        EmbeddingStore<TextSegment> storeSQL = new InMemoryEmbeddingStore<>();

        // Créer et stocker les embeddings
        List<Embedding> embeddingsIA = embeddingModel.embedAll(segmentsIA).content();
        List<Embedding> embeddingsSQL = embeddingModel.embedAll(segmentsSQL).content();

        storeIA.addAll(embeddingsIA, segmentsIA);
        storeSQL.addAll(embeddingsSQL, segmentsSQL);

        System.out.println("Embeddings stockés avec succès");

        // Créer les retrievers
        var retrieverIA = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeIA)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        var retrieverSQL = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeSQL)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        // Configurer le modèle de chat
        String key = System.getenv("GEMINI_KEY");
        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(key)
                .modelName("gemini-2.5-flash")
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        // Configurer le routage avec descriptions
        Map<ContentRetriever, String> desc = new HashMap<>();
        desc.put(retrieverIA, "Documents de cours sur le RAG (Retrieval-Augmented Generation), le fine-tuning et l'intelligence artificielle");
        desc.put(retrieverSQL, "Document sur l'optimisation des instructions SQL, les requêtes de bases de données et les performances");

        var queryRouter = new LanguageModelQueryRouter(model, desc);

        var augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        // Créer l'assistant
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .retrievalAugmentor(augmentor)
                .build();

        System.out.println("\nAssistant configuré avec succès!");
        System.out.println("Posez vos questions (tapez 'exit' pour quitter)");

        // Boucle de conversation
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("\nVous : ");
            String question = sc.nextLine();
            if (question.equalsIgnoreCase("exit")) break;

            try {
                String reponse = assistant.chat(question);
                System.out.println("Gemini : " + reponse);
            } catch (Exception e) {
                System.out.println("Erreur : " + e.getMessage());
            }
        }

        sc.close();
        System.out.println("Au revoir !");
    }

    /**
     * Charge un PDF et le découpe en segments avec PDFBox
     */
    private static List<TextSegment> loadAndSplitPDF(String chemin) {
        Path pdfPath = Paths.get(chemin);
        File pdfFile = pdfPath.toFile();
        List<TextSegment> segments = new ArrayList<>();

        try (PDDocument pdDoc = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String pdfText = stripper.getText(pdDoc);

            // Découper le texte en segments (taille max 300 caractères, chevauchement 30)
            int maxLen = 300;
            int overlap = 30;
            int start = 0;

            while (start < pdfText.length()) {
                int end = Math.min(start + maxLen, pdfText.length());

                // Éviter de couper les mots
                if (end < pdfText.length()) {
                    int lastSpace = pdfText.lastIndexOf(' ', end);
                    if (lastSpace > start) {
                        end = lastSpace;
                    }
                }

                String chunk = pdfText.substring(start, end).trim();
                if (!chunk.isEmpty()) {
                    segments.add(TextSegment.from(chunk));
                }

                // Avancer avec chevauchement
                start = end - overlap;
                if (start < 0) start = 0;
            }

        } catch (IOException e) {
            System.err.println("Erreur lors de la lecture du PDF " + chemin + ": " + e.getMessage());
            throw new RuntimeException(e);
        }

        return segments;
    }
}