package ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.rag;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.llm.Assistant;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;

public class Rag {

    private static void configureLogger() {
        // Configure le logger sous-jacent (java.util.logging)
        Logger packageLogger = Logger.getLogger("dev.langchain4j");
        packageLogger.setLevel(Level.FINE); // Ajuster niveau
        // Ajouter un handler pour la console pour faire afficher les logs
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINE);
        packageLogger.addHandler(handler);
    }
    public static void main(String[] args) {
        // configure logger early so LangChain4j request/response logs are printed
        configureLogger();
        try {
            /**
             * Phase 1 : Enregistrement des embeddings
             */


/**
 * Phase 1 : Enregistrement des embeddings
 */

            System.out.println("=== Phase 1 : Enregistrement des embeddings ===");

// 1. Liste des fichiers PDF à traiter
            List<String> pdfFiles = List.of(
                    "src/document/RAG.pdf",
                    "src/document/Optimisation des instructions SQL.pdf"
            );

// 2. Liste pour stocker tous les segments de tous les PDFs
            List<TextSegment> allSegments = new ArrayList<>();

// 3. Traiter chaque fichier PDF
            for (String pdfFilePath : pdfFiles) {
                Path pdfPath = Paths.get(pdfFilePath);
                System.out.println("Chargement du fichier : " + pdfPath);

                // Extraction du texte du PDF avec PDFBox
                String pdfText;
                File pdfFile = pdfPath.toFile();

                try (PDDocument pdDoc = Loader.loadPDF(pdfFile)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    pdfText = stripper.getText(pdDoc);
                } catch (IOException ioe) {
                    System.err.println("Erreur lors de la lecture du PDF " + pdfFilePath + ": " + ioe.getMessage());
                    continue; // Passer au fichier suivant en cas d'erreur
                }

                System.out.println("Document chargé : " + pdfFile.getName());

                // Découpage du texte en segments (taille max 500 caractères)
                int maxLen = 500;
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
                        // Ajouter le nom du fichier source comme métadonnée (optionnel)
                        TextSegment segment = TextSegment.from(chunk);
                        allSegments.add(segment);
                    }
                    start = end;
                }

                System.out.println("  → " + "Segments extraits de " + pdfFile.getName());
            }

            System.out.println("\nTotal : " + allSegments.size() + " segments extraits de tous les documents");

// 4. Création du modèle d'embedding
            EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

// 5. Création des embeddings pour tous les segments
            List<Embedding> embeddings = embeddingModel.embedAll(allSegments).content();
            System.out.println(embeddings.size() + " embeddings créés");

// 6. Stockage dans un magasin d'embeddings en mémoire
            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            embeddingStore.addAll(embeddings, allSegments);
            System.out.println("Embeddings stockés avec succès");

            System.out.println("------------------Phase 1 terminée -----------------------");
            /**
             * Phase 2 : Utilisation des embeddings pour répondre aux questions
             */

            System.out.println("\n-------- Phase 2 : Configuration de l'assistant RAG ");

            // Création du ChatModel
            ChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey(System.getenv("GEMINI_KEY"))
                    .modelName("gemini-2.5-flash")
                    .logRequestsAndResponses(true)
                    .temperature(0.2)
                    .build();

            // Création du ContentRetriever avec EmbeddingStoreContentRetriever (builder)
            EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(3)
                    .minScore(0.7)
                    .build();

            // Ajout d'une mémoire pour 10 messages
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            // Création de l'assistant via AiServices
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .chatMemory(chatMemory)
                    .contentRetriever(retriever)
                    .build();


            System.out.println("Assistant  configuré avec succès!");
            System.out.println("poser vos questions");

            // Boucle de questions-réponses
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("\nPosez votre question (ou 'FIN' pour quitter) : ");
                String question = scanner.nextLine();

                if (question.equalsIgnoreCase("FIN")) {
                    break;
                }

                if (question.trim().isEmpty()) {
                    continue;
                }

                try {
                    String response = assistant.chat(question);
                    System.out.println("\n Réponse : " + response);
                } catch (Exception e) {
                    System.out.println("\n Erreur lors de la génération de la réponse : " + e.getMessage());
                }
            }

            scanner.close();
            System.out.println("Au revoir !");

        } catch (Exception e) {
            System.err.println(" Erreur lors de l'exécution : " + e.getMessage());
            e.printStackTrace();
        }
    }
}