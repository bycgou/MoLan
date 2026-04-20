package com.novel.system.service.novel;

import com.novel.system.dto.request.CreateNovelRequest;
import com.novel.system.dto.request.GenerateChapterRequest;
import com.novel.system.dto.request.UpdateNovelRequest;
import com.novel.system.dto.request.UpdateTimelineRequest;
import com.novel.system.dto.response.NovelResponse;
import com.novel.system.dto.response.OutlineResponse;
import com.novel.system.dto.response.GenerationResultResponse;
import com.novel.system.entity.mongo.NovelGraph;
import com.novel.system.entity.mysql.WorldClock;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

/**
 * 小说管理服务接口
 */
public interface NovelService {

    NovelResponse createNovel(CreateNovelRequest request, String userId);

    Map<String, String> autoGenerateOutline(String novelName, String genre, String userPrompt);

    List<NovelResponse> getNovelList(String userId);

    NovelResponse getNovel(String novelId);

    NovelResponse updateNovel(String novelId, UpdateNovelRequest request);

    void deleteNovel(String novelId);

    void restoreNovel(String novelId);

    void hardDeleteNovel(String novelId);

    ResponseEntity<byte[]> exportNovelTxt(String novelId);

    NovelGraph getNovelGraph(String novelId);

    WorldClock getTimeline(String novelId);

    WorldClock updateTimeline(String novelId, UpdateTimelineRequest request);

    OutlineResponse generateOutline(String novelId, Integer chapterNum, String userIntent, String llmConfigId, String modelType);

    GenerationResultResponse generateChapter(String novelId, GenerateChapterRequest request);

    String generateChapterAsync(String novelId, GenerateChapterRequest request);

    GenerationResultResponse getGenerationStatus(String novelId, String taskId);
}
