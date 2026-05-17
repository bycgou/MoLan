package com.novel.system.ai.rag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    @Test
    void validateVector_throwsOnNull() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(null));
    }

    @Test
    void validateVector_throwsOnEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(new float[0]));
    }

    @Test
    void validateVector_throwsOnAllZeros() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(new float[1536]));
    }

    @Test
    void validateVector_throwsOnWrongDimension() {
        float[] wrongDim = new float[768];
        for (int i = 0; i < 768; i++) wrongDim[i] = 0.01f;
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(wrongDim));
    }

    @Test
    void validateVector_passesOnValidVector() {
        float[] valid = new float[1536];
        for (int i = 0; i < 1536; i++) valid[i] = 0.01f;
        assertDoesNotThrow(() -> EmbeddingService.validateVector(valid));
    }
}