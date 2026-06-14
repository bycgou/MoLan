package com.novel.system.service.memory;

public interface EmotionLedgerService {

    String formatForPrompt(String novelId, int currentChapterNum);

    void extractFromChapter(String novelId, int chapterNum, String chapterContent);
}
