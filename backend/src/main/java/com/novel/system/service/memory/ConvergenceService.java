package com.novel.system.service.memory;

public interface ConvergenceService {

    String formatForPrompt(String novelId);

    void updateConvergenceState(String novelId, int currentChapter, int totalChapters);

    String getPhase(String novelId);
}
