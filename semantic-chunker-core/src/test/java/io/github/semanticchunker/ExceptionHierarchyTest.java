package io.github.semanticchunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.semanticchunker.chunker.ChunkingException;
import io.github.semanticchunker.extraction.ExtractionException;
import io.github.semanticchunker.model.ModelException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The typed, unchecked exception hierarchy: every concrete exception shares the same construction
 * and subtyping contract, so the cases are verified uniformly across the three of them.
 */
class ExceptionHierarchyTest {

    static Stream<Arguments> exceptions() {
        return Stream.of(
                arguments(
                        "ExtractionException",
                        (Function<String, SemanticChunkerException>) ExtractionException::new,
                        (BiFunction<String, Throwable, SemanticChunkerException>)
                                ExtractionException::new),
                arguments(
                        "ChunkingException",
                        (Function<String, SemanticChunkerException>) ChunkingException::new,
                        (BiFunction<String, Throwable, SemanticChunkerException>)
                                ChunkingException::new),
                arguments(
                        "ModelException",
                        (Function<String, SemanticChunkerException>) ModelException::new,
                        (BiFunction<String, Throwable, SemanticChunkerException>)
                                ModelException::new));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exceptions")
    void isAnUncheckedSemanticChunkerException(
            String name,
            Function<String, SemanticChunkerException> messageForm,
            BiFunction<String, Throwable, SemanticChunkerException> causeForm) {
        SemanticChunkerException exception = messageForm.apply("boom");

        assertThat(exception)
                .isInstanceOf(SemanticChunkerException.class)
                .isInstanceOf(RuntimeException.class);
        assertThat(exception).hasMessage("boom");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exceptions")
    void carriesMessageAndCause(
            String name,
            Function<String, SemanticChunkerException> messageForm,
            BiFunction<String, Throwable, SemanticChunkerException> causeForm) {
        Throwable cause = new IllegalStateException("root");

        SemanticChunkerException exception = causeForm.apply("wrapped", cause);

        assertThat(exception).hasMessage("wrapped").hasCause(cause);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exceptions")
    void survivesSerializationRoundTrip(
            String name,
            Function<String, SemanticChunkerException> messageForm,
            BiFunction<String, Throwable, SemanticChunkerException> causeForm) {
        SemanticChunkerException original = messageForm.apply("persisted");

        assertThatCode(
                        () -> {
                            SemanticChunkerException restored = roundTrip(original);
                            assertThat(restored).hasMessage("persisted");
                            assertThat(restored).isInstanceOf(original.getClass());
                        })
                .doesNotThrowAnyException();
    }

    private static SemanticChunkerException roundTrip(SemanticChunkerException exception)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(exception);
        }
        try (ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (SemanticChunkerException) in.readObject();
        }
    }
}
