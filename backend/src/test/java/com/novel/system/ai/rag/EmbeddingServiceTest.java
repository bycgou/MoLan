package com.novel.system.ai.rag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    private static final int DIM = 1536;

    @Test
    void validateVector_throwsOnNull() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(null, DIM));
    }

    @Test
    void validateVector_throwsOnEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(new float[0], DIM));
    }

    @Test
    void validateVector_throwsOnAllZeros() {
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(new float[DIM], DIM));
    }

    @Test
    void validateVector_throwsOnWrongDimension() {
        float[] wrongDim = new float[768];
        for (int i = 0; i < 768; i++) wrongDim[i] = 0.01f;
        assertThrows(IllegalArgumentException.class, () ->
            EmbeddingService.validateVector(wrongDim, DIM));
    }

    @Test
    void validateVector_passesOnValidVector() {
        float[] valid = new float[DIM];
        for (int i = 0; i < DIM; i++) valid[i] = 0.01f;
        assertDoesNotThrow(() -> EmbeddingService.validateVector(valid, DIM));
    }
}
