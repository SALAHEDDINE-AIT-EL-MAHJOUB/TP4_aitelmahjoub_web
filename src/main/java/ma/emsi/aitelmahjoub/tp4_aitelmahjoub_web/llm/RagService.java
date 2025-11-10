package ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper service to build an EmbeddingStoreContentRetriever from local PDF files.
 * This is intentionally defensive: if creation fails (missing model, files, etc.) it
 * returns null so callers can fallback to a non-RAG mode.
 */
public class RagService {

    private static final Logger LOGGER = Logger.getLogger(RagService.class.getName());

    public static EmbeddingStoreContentRetriever createRetriever() {
        try {
            // PDF file list (relative to project root)
            List<String> pdfFiles = List.of(
                    "src/document/RAG.pdf",
                    "src/document/Optimisation des instructions SQL.pdf"
            );

            List<TextSegment> allSegments = new ArrayList<>();

            for (String pdfFilePath : pdfFiles) {
                Path pdfPath = Paths.get(pdfFilePath);
                File pdfFile = pdfPath.toFile();
                if (!pdfFile.exists()) {
                    LOGGER.log(Level.WARNING, "PDF not found: {0}", pdfFile.getAbsolutePath());
                    continue;
                }

                String pdfText;
                try (PDDocument pdDoc = Loader.loadPDF(pdfFile)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    pdfText = stripper.getText(pdDoc);
                }

                if (pdfText == null || pdfText.isBlank()) {
                    continue;
                }

                // simple chunking by characters (keeps words intact where possible)
                int maxLen = 500;
                int start = 0;
                while (start < pdfText.length()) {
                    int end = Math.min(start + maxLen, pdfText.length());
                    if (end < pdfText.length()) {
                        int lastSpace = pdfText.lastIndexOf(' ', end);
                        if (lastSpace > start) {
                            end = lastSpace;
                        }
                    }
                    String chunk = pdfText.substring(start, end).trim();
                    if (!chunk.isEmpty()) {
                        allSegments.add(TextSegment.from(chunk));
                    }
                    start = end;
                }
            }

            if (allSegments.isEmpty()) {
                LOGGER.info("No text segments extracted for RAG.");
                return null;
            }

            // Build embedding model and embeddings
            EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            List<Embedding> embeddings = embeddingModel.embedAll(allSegments).content();

            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            embeddingStore.addAll(embeddings, allSegments);

            EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(3)
                    .minScore(0.7)
                    .build();

            LOGGER.info("RAG retriever created successfully.");
            return retriever;

        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Error creating RAG retriever: {0}", ioe.getMessage());
            return null;
        } catch (Throwable t) {
            // defensive: ONNX model / native libs might throw runtime errors
            LOGGER.log(Level.WARNING, "Unexpected error creating RAG retriever: {0}", t.toString());
            return null;
        }
    }
}
