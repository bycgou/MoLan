package com.novel.system.service.chapter;

import com.novel.system.dto.request.UpdateChapterRequest;
import com.novel.system.dto.response.ChapterResponse;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mysql.NovelChapterVersion;

import java.util.List;

/**
 * 章节管理服务接口
 */
public interface ChapterService {

    com.novel.system.ai.agent.SummaryAgentResponse extractSummary(String novelId, Integer chapterNum, String content);

    ChapterResponse saveManualChapter(String novelId, Integer chapterNum, String chapterTitle, String content, com.novel.system.ai.agent.SummaryAgentResponse summaryResponse);

    List<ChapterResponse> getChapters(String novelId);

    ChapterResponse getChapter(String novelId, Integer chapterNum);

    List<ChapterResponse> getRecentChapters(String novelId, Integer count);

    ChapterResponse updateChapter(String novelId, Integer chapterNum, UpdateChapterRequest request);

    void deleteChapter(String novelId, Integer chapterNum);

    List<ChapterResponse> getDeletedChapters(String novelId);

    void restoreChapter(String novelId, String chapterId);

    void hardDeleteChapter(String novelId, String chapterId);

    List<NovelChapterVersion> getChapterVersions(String novelId, Integer chapterNum);

    ChapterResponse rollbackChapter(String novelId, Integer chapterNum, String versionId);
}
