package com.novel.system.service.chapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.agent.SummaryAgentResponse;
import com.novel.system.dto.request.UpdateChapterRequest;
import com.novel.system.dto.response.ChapterResponse;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mysql.NovelChapterVersion;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.mapper.NovelChapterVersionMapper;
import com.novel.system.service.chapter.ChapterGenerationRequest;
import com.novel.system.service.chapter.ChapterGenerationService;
import com.novel.system.service.chapter.ChapterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 章节管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    private final NovelChapterMapper novelChapterMapper;
    private final NovelChapterVersionMapper chapterVersionMapper;
    private final ChapterGenerationService chapterGenerationService;
    private final ObjectMapper objectMapper;

    @Override
    public SummaryAgentResponse extractSummary(String novelId, Integer chapterNum, String content) {
        ChapterGenerationRequest request = new ChapterGenerationRequest();
        request.setNovelId(novelId);
        request.setChapterNum(chapterNum);
        return chapterGenerationService.extractSummaryOnly(request, content);
    }

    @Override
    public ChapterResponse saveManualChapter(String novelId, Integer chapterNum, String chapterTitle, String content, SummaryAgentResponse summaryResponse) {
        ChapterGenerationRequest request = new ChapterGenerationRequest();
        request.setNovelId(novelId);
        request.setChapterNum(chapterNum);
        request.setChapterTitle(chapterTitle);

        chapterGenerationService.saveManualChapter(request, content, summaryResponse);

        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter != null) {
            return convertToChapterResponse(chapter);
        }
        throw new RuntimeException("保存失败");
    }

    @Override
    public List<ChapterResponse> getChapters(String novelId) {
        List<NovelChapter> chapters = novelChapterMapper.selectByNovelId(novelId);
        return chapters.stream()
                .map(this::convertToChapterResponse)
                .toList();
    }

    @Override
    public ChapterResponse getChapter(String novelId, Integer chapterNum) {
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter != null) {
            return convertToChapterResponse(chapter);
        }
        throw new java.util.NoSuchElementException("章节不存在");
    }

    @Override
    public List<ChapterResponse> getRecentChapters(String novelId, Integer count) {
        Integer maxChapter = novelChapterMapper.selectMaxChapterNum(novelId);
        if (maxChapter == null) {
            return List.of();
        }
        Integer startChapter = Math.max(1, maxChapter - count + 1);
        List<NovelChapter> chapters = novelChapterMapper.selectRecentChapters(novelId, startChapter, maxChapter + 1);
        return chapters.stream()
                .map(this::convertToChapterResponse)
                .toList();
    }

    @Override
    public ChapterResponse updateChapter(String novelId, Integer chapterNum, UpdateChapterRequest request) {
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter == null) {
            throw new java.util.NoSuchElementException("章节不存在");
        }

        // 保存旧版本
        if (chapter.getContent() != null && !chapter.getContent().isEmpty()) {
            NovelChapterVersion version = new NovelChapterVersion();
            version.setVersionId(UUID.randomUUID().toString().replace("-", ""));
            version.setChapterId(chapter.getChapterId());
            version.setNovelId(chapter.getNovelId());
            version.setChapterNum(chapter.getChapterNum());
            version.setChapterTitle(chapter.getChapterTitle());
            version.setContent(chapter.getContent());
            version.setWordCount(chapter.getWordCount());
            version.setVersionDesc("手动修改前保存");
            version.setCreateTime(LocalDateTime.now());
            chapterVersionMapper.insert(version);
        }

        if (request.getContent() != null) {
            chapter.setContent(request.getContent());
            chapter.setWordCount(countChineseChars(request.getContent().replaceAll("<[^>]+>", "")));
        }
        if (request.getChapterTitle() != null) {
            chapter.setChapterTitle(request.getChapterTitle());
        }
        if (request.getPlotFingerprint() != null) {
            chapter.setPlotFingerprint(request.getPlotFingerprint());
        }
        chapter.setUpdateTime(LocalDateTime.now());
        novelChapterMapper.updateById(chapter);
        return convertToChapterResponse(chapter);
    }

    @Override
    public void deleteChapter(String novelId, Integer chapterNum) {
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter == null) {
            throw new java.util.NoSuchElementException("章节不存在");
        }
        novelChapterMapper.softDeleteById(chapter.getChapterId());
    }

    @Override
    public List<ChapterResponse> getDeletedChapters(String novelId) {
        List<NovelChapter> chapters = novelChapterMapper.selectDeletedByNovelId(novelId);
        return chapters.stream()
                .map(this::convertToChapterResponse)
                .toList();
    }

    @Override
    public void restoreChapter(String novelId, String chapterId) {
        NovelChapter chapter = novelChapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getNovelId().equals(novelId)) {
            throw new java.util.NoSuchElementException("章节不存在");
        }
        chapter.setIsDeleted(false);
        chapter.setUpdateTime(LocalDateTime.now());
        novelChapterMapper.updateById(chapter);
    }

    @Override
    public void hardDeleteChapter(String novelId, String chapterId) {
        NovelChapter chapter = novelChapterMapper.selectById(chapterId);
        if (chapter == null || !chapter.getNovelId().equals(novelId)) {
            throw new java.util.NoSuchElementException("章节不存在");
        }
        novelChapterMapper.deleteById(chapterId);
    }

    @Override
    public List<NovelChapterVersion> getChapterVersions(String novelId, Integer chapterNum) {
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter == null) {
            throw new java.util.NoSuchElementException("章节不存在");
        }
        return chapterVersionMapper.selectByChapterId(chapter.getChapterId());
    }

    @Override
    public ChapterResponse rollbackChapter(String novelId, Integer chapterNum, String versionId) {
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter == null) {
            throw new java.util.NoSuchElementException("章节不存在");
        }
        NovelChapterVersion version = chapterVersionMapper.selectById(versionId);
        if (version == null) {
            throw new java.util.NoSuchElementException("版本不存在");
        }

        // 先保存当前版本
        if (chapter.getContent() != null && !chapter.getContent().isEmpty()) {
            NovelChapterVersion currentVersion = new NovelChapterVersion();
            currentVersion.setVersionId(UUID.randomUUID().toString().replace("-", ""));
            currentVersion.setChapterId(chapter.getChapterId());
            currentVersion.setNovelId(chapter.getNovelId());
            currentVersion.setChapterNum(chapter.getChapterNum());
            currentVersion.setChapterTitle(chapter.getChapterTitle());
            currentVersion.setContent(chapter.getContent());
            currentVersion.setWordCount(chapter.getWordCount());
            currentVersion.setVersionDesc("回滚前保存");
            currentVersion.setCreateTime(LocalDateTime.now());
            chapterVersionMapper.insert(currentVersion);
        }

        // 执行回滚
        chapter.setContent(version.getContent());
        chapter.setChapterTitle(version.getChapterTitle());
        chapter.setWordCount(version.getWordCount());
        chapter.setUpdateTime(LocalDateTime.now());
        novelChapterMapper.updateById(chapter);
        return convertToChapterResponse(chapter);
    }

    private int countChineseChars(String text) {
        if (text == null) return 0;
        return text.replaceAll("\\s+", "").length();
    }

    private ChapterResponse convertToChapterResponse(NovelChapter chapter) {
        ChapterResponse response = new ChapterResponse();
        response.setChapterId(chapter.getChapterId());
        response.setNovelId(chapter.getNovelId());
        response.setChapterNum(chapter.getChapterNum());
        response.setChapterTitle(chapter.getChapterTitle());
        response.setChapterOutline(chapter.getChapterOutline());
        response.setContent(chapter.getContent());
        response.setWordCount(chapter.getWordCount());
        response.setRhythmTag(chapter.getRhythmTag());
        response.setPlotFingerprint(chapter.getPlotFingerprint());
        response.setGenerationStatus(chapter.getGenerationStatus());
        response.setCreateTime(chapter.getCreateTime());
        return response;
    }
}
