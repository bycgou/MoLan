package com.novel.system.service.memory;

public interface CompletedBeatsService {

    String formatForPrompt(String novelId);

    void extractFromChapter(String novelId, int chapterNum, String chapterContent);
}
