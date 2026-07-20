package io.github.semanticchunker.chunker;

import io.github.semanticchunker.chunker.internal.ChunkingPipeline;
import io.github.semanticchunker.document.DocumentSource;
import io.github.semanticchunker.document.PreparedDocument;
import io.github.semanticchunker.extraction.DocumentExtractor;
import io.github.semanticchunker.model.ChunkingModel;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The single entry point to the library: a caller builds one of these with an extractor and a
 * model, then chunks documents (API.md, section 2).
 *
 * <p>A chunker is a settled thing once built. It carries the extractor and model it was given and
 * can chunk one document after another; it need not be rebuilt for each document, and — being
 * composed of immutable configuration and stateless collaborators — is safe to reuse across
 * threads.
 *
 * <p>This class is {@code final} and constructed only through its {@link #builder() builder}.
 *
 * <p><strong>Implementation status.</strong> The public shape, construction, builder validation,
 * and the internal pipeline orchestration are in place: this facade delegates to the wired internal
 * pipeline. The pipeline's algorithmic stages (window planning, prompt construction, response
 * validation, boundary merging, and chunk construction) are implemented in a later phase, so a call
 * to {@code chunk} runs the fixed stage sequence and stops at the first stage whose algorithm is
 * not yet available.
 */
public final class SemanticChunker {

    private final ChunkingPipeline pipeline;

    private SemanticChunker(Builder builder) {
        this.pipeline = ChunkingPipeline.create(builder.documentExtractor, builder.chunkingModel);
    }

    /**
     * Starts building a chunker.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Chunks a raw document, extracting and normalizing it internally before chunking.
     *
     * <p><strong>This method performs network I/O, blocks, and incurs monetary cost:</strong> it
     * calls a language model one or more times to decide semantic boundaries
     * (CONTRIBUTING_ARCHITECTURE.md, section 5.3.3).
     *
     * @param source the raw document to chunk; must not be {@code null}
     * @return the result of the run
     */
    public ChunkingResult chunk(DocumentSource source) {
        Objects.requireNonNull(source, "source");
        return pipeline.chunk(source);
    }

    /**
     * Chunks the document at the given path, reading it, extracting and normalizing it internally
     * before chunking.
     *
     * <p><strong>This method performs file and network I/O, blocks, and incurs monetary
     * cost:</strong> it reads the file and calls a language model one or more times
     * (CONTRIBUTING_ARCHITECTURE.md, section 5.3.3).
     *
     * @param path the path to the document to chunk; must not be {@code null}
     * @return the result of the run
     */
    public ChunkingResult chunk(Path path) {
        Objects.requireNonNull(path, "path");
        return pipeline.chunk(path);
    }

    /**
     * Chunks an already-prepared document, skipping extraction.
     *
     * <p><strong>This method performs network I/O, blocks, and incurs monetary cost:</strong> it
     * calls a language model one or more times to decide semantic boundaries
     * (CONTRIBUTING_ARCHITECTURE.md, section 5.3.3).
     *
     * @param preparedDocument the normalized document to chunk; must not be {@code null}
     * @return the result of the run
     */
    public ChunkingResult chunk(PreparedDocument preparedDocument) {
        Objects.requireNonNull(preparedDocument, "preparedDocument");
        return pipeline.chunk(preparedDocument);
    }

    /**
     * Builds a {@link SemanticChunker} from the required collaborators.
     *
     * <p>The builder fails fast: {@link #build()} throws if a required collaborator is missing,
     * naming what is absent (CONTRIBUTING_ARCHITECTURE.md, section 5.3.1). Optional refinements,
     * when they exist, are added as optional steps so that existing construction code is never
     * disturbed.
     */
    public static final class Builder {

        private DocumentExtractor documentExtractor;
        private ChunkingModel chunkingModel;

        private Builder() {}

        /**
         * Sets the extractor that turns documents into structured content. Required.
         *
         * @param documentExtractor the extractor to use; must not be {@code null}
         * @return this builder
         */
        public Builder documentExtractor(DocumentExtractor documentExtractor) {
            this.documentExtractor = Objects.requireNonNull(documentExtractor, "documentExtractor");
            return this;
        }

        /**
         * Sets the model through which the language model is reached. Required.
         *
         * @param chunkingModel the model to use; must not be {@code null}
         * @return this builder
         */
        public Builder chunkingModel(ChunkingModel chunkingModel) {
            this.chunkingModel = Objects.requireNonNull(chunkingModel, "chunkingModel");
            return this;
        }

        /**
         * Completes construction, verifying that every required collaborator is present.
         *
         * @return a new chunker
         * @throws IllegalStateException if the extractor or the model has not been supplied
         */
        public SemanticChunker build() {
            if (documentExtractor == null) {
                throw new IllegalStateException(
                        "documentExtractor is required and was not supplied to the builder");
            }
            if (chunkingModel == null) {
                throw new IllegalStateException(
                        "chunkingModel is required and was not supplied to the builder");
            }
            return new SemanticChunker(this);
        }
    }
}
