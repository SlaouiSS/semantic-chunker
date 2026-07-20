package io.github.semanticchunker.chunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.model.ChunkingModel;
import org.junit.jupiter.api.Test;

/**
 * Tests the public construction contract of the {@link SemanticChunker} builder. The chunking
 * pipeline itself is out of scope for this phase and is not exercised here.
 */
class SemanticChunkerBuilderTest {

    private final DocumentExtractor extractor = mock(DocumentExtractor.class);
    private final ChunkingModel model = mock(ChunkingModel.class);

    @Test
    void buildsWhenBothCollaboratorsAreSupplied() {
        SemanticChunker chunker =
                SemanticChunker.builder().documentExtractor(extractor).chunkingModel(model).build();

        assertThat(chunker).isNotNull();
    }

    @Test
    void failsFastWhenExtractorIsMissing() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SemanticChunker.builder().chunkingModel(model).build())
                .withMessageContaining("documentExtractor");
    }

    @Test
    void failsFastWhenModelIsMissing() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SemanticChunker.builder().documentExtractor(extractor).build())
                .withMessageContaining("chunkingModel");
    }

    @Test
    void rejectsNullExtractor() {
        assertThatNullPointerException()
                .isThrownBy(() -> SemanticChunker.builder().documentExtractor(null));
    }

    @Test
    void rejectsNullModel() {
        assertThatNullPointerException()
                .isThrownBy(() -> SemanticChunker.builder().chunkingModel(null));
    }
}
