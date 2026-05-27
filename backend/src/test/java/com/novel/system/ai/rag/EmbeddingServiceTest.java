package com.novel.system.ai.rag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    private static final int DIM = 1024;

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
            EmbeddingService.validateVector(new float[DIM]));
    }

    @Test
    void validateVector_passesOnValidVector() {
        float[] valid = new float[DIM];
        for (int i = 0; i < DIM; i++) valid[i] = 0.01f;
        assertDoesNotThrow(() -> EmbeddingService.validateVector(valid));
    }
}
