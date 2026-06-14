package com.novel.system.service.memory;

import java.util.List;

public interface EchoRecallService {

    String formatForPrompt(String novelId, int currentChapterNum, List<String> characters);

    void extractVoiceSamples(String novelId, int chapterNum, String chapterContent);

    List<VoiceSearchResult> searchVoiceSamples(String novelId, String characterName,
                                                int currentChapterNum, int minAgeChapters);

    record VoiceSearchResult(String dialogue, String context, int chapterNum, double score) {}
}
