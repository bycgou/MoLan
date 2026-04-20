package com.novel.system.service.novel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.dto.request.CreateNovelRequest;
import com.novel.system.dto.request.GenerateChapterRequest;
import com.novel.system.dto.request.UpdateNovelRequest;
import com.novel.system.dto.request.UpdateTimelineRequest;
import com.novel.system.dto.response.GenerationResultResponse;
import com.novel.system.dto.response.NovelResponse;
import com.novel.system.dto.response.OutlineResponse;
import com.novel.system.entity.mongo.NovelGraph;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mysql.WorldClock;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.mapper.WorldClockMapper;
import com.novel.system.repository.mongo.NovelGraphRepository;
import com.novel.system.service.chapter.ChapterGenerationRequest;
import com.novel.system.service.chapter.ChapterGenerationResponse;
import com.novel.system.service.chapter.ChapterGenerationService;
import com.novel.system.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 小说管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelServiceImpl implements NovelService {

    private final NovelBaseMapper novelBaseMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final WorldClockMapper worldClockMapper;
    private final ChapterGenerationService chapterGenerationService;
    private final NovelGraphRepository novelGraphRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public NovelResponse createNovel(CreateNovelRequest request, String userId) {
        log.info("创建小说: {}", request.getNovelName());

        NovelBase novel = new NovelBase();
        novel.setNovelId(UUID.randomUUID().toString().replace("-", ""));
        novel.setNovelName(request.getNovelName());
        novel.setUserId(userId);
        novel.setGenre(request.getGenre() != null ? request.getGenre() : "玄幻");
        novel.setTotalOutline(request.getTotalOutline());
        novel.setWorldSetting(request.getWorldSetting());
        novel.setStatus(0);
        novel.setCreateTime(LocalDateTime.now());
        novel.setUpdateTime(LocalDateTime.now());

        novelBaseMapper.insert(novel);

        return convertToNovelResponse(novel);
    }

    @Override
    public Map<String, String> autoGenerateOutline(String novelName, String genre, String userPrompt) {
        if (novelName == null || novelName.trim().isEmpty()) {
            throw new IllegalArgumentException("小说名称不能为空");
        }

        String systemPrompt = """
            你是一个畅销网络小说主编。请根据用户提供的小说名称、题材和核心创意，自动生成详细的【小说总纲】和【世界观设定】。
            请严格返回以下JSON格式：
            {
              "totalOutline": "详细的小说总纲，包含主线剧情、卷划分、高潮节点、结局等（至少500字）",
              "worldSetting": "详细的世界观设定，包含力量体系、地理环境、社会势力等（至少300字）"
            }
            """;

        String userMessage = String.format("小说名称：《%s》\n小说题材：%s\n核心创意/用户要求：%s",
                novelName,
                genre != null ? genre : "未指定",
                userPrompt != null ? userPrompt : "请自由发挥，写一部符合该题材套路的爽文");

        try {
            String response = llmService.generateWithUsage(systemPrompt, userMessage, "outline");

            String cleanJson = response.trim();
            if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
            else if (cleanJson.startsWith("```")) cleanJson = cleanJson.substring(3);
            if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            cleanJson = cleanJson.trim();

            JsonNode node = objectMapper.readTree(cleanJson);

            Map<String, String> result = new HashMap<>();
            result.put("totalOutline", node.has("totalOutline") ? node.get("totalOutline").asText() : "");
            result.put("worldSetting", node.has("worldSetting") ? node.get("worldSetting").asText() : "");

            return result;

        } catch (Exception e) {
            log.error("自动生成大纲失败", e);
            throw new RuntimeException("生成大纲失败，请稍后重试或尝试手动输入");
        }
    }

    @Override
    public List<NovelResponse> getNovelList(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户未认证");
        }
        List<NovelBase> novels = novelBaseMapper.selectByUserId(userId);
        return novels.stream()
                .map(this::convertToNovelResponse)
                .toList();
    }

    @Override
    public NovelResponse getNovel(String novelId) {
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel != null) {
            return convertToNovelResponse(novel);
        }
        throw new NoSuchElementException("小说不存在");
    }

    @Override
    public NovelResponse updateNovel(String novelId, UpdateNovelRequest request) {
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel == null) {
            throw new NoSuchElementException("小说不存在");
        }

        if (request.getNovelName() != null) novel.setNovelName(request.getNovelName());
        if (request.getGenre() != null) novel.setGenre(request.getGenre());
        if (request.getTotalOutline() != null) novel.setTotalOutline(request.getTotalOutline());
        if (request.getWorldSetting() != null) novel.setWorldSetting(request.getWorldSetting());
        if (request.getStatus() != null) novel.setStatus(request.getStatus());
        novel.setUpdateTime(LocalDateTime.now());

        novelBaseMapper.updateById(novel);
        return convertToNovelResponse(novel);
    }

    @Override
    public void deleteNovel(String novelId) {
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel != null) {
            novelBaseMapper.softDeleteById(novelId);
        } else {
            throw new NoSuchElementException("小说不存在");
        }
    }

    @Override
    public void restoreNovel(String novelId) {
        int result = novelBaseMapper.restoreById(novelId);
        if (result <= 0) {
            throw new NoSuchElementException("小说不存在");
        }
    }

    @Override
    public void hardDeleteNovel(String novelId) {
        novelBaseMapper.deleteById(novelId);
    }

    @Override
    public ResponseEntity<byte[]> exportNovelTxt(String novelId) {
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel == null) {
            return ResponseEntity.notFound().build();
        }

        List<NovelChapter> chapters = novelChapterMapper.selectByNovelId(novelId);

        StringBuilder sb = new StringBuilder();
        sb.append(novel.getNovelName()).append("\n\n");
        sb.append("简介：\n").append(novel.getTotalOutline() != null ? novel.getTotalOutline() : "无").append("\n\n");
        sb.append("==============================\n\n");

        for (NovelChapter chapter : chapters) {
            sb.append(chapter.getChapterTitle()).append("\n\n");
            String cleanContent = chapter.getContent() != null ? chapter.getContent()
                    .replaceAll("</p>", "\n")
                    .replaceAll("<br>", "\n")
                    .replaceAll("<[^>]+>", "") : "";
            sb.append(cleanContent).append("\n\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        String encodedFilename = URLEncoder.encode(novel.getNovelName() + ".txt", StandardCharsets.UTF_8);
        headers.setContentDispositionFormData("attachment", encodedFilename);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @Override
    public NovelGraph getNovelGraph(String novelId) {
        return novelGraphRepository.findById(novelId)
                .orElseGet(() -> {
                    NovelGraph emptyGraph = new NovelGraph();
                    emptyGraph.setNovelId(novelId);
                    emptyGraph.setNodes(new ArrayList<>());
                    emptyGraph.setEdges(new ArrayList<>());
                    return emptyGraph;
                });
    }

    @Override
    public WorldClock getTimeline(String novelId) {
        WorldClock clock = worldClockMapper.selectByNovelId(novelId);
        if (clock != null) {
            return clock;
        }
        WorldClock emptyClock = new WorldClock();
        emptyClock.setNovelId(novelId);
        return emptyClock;
    }

    @Override
    public WorldClock updateTimeline(String novelId, UpdateTimelineRequest request) {
        WorldClock clock = worldClockMapper.selectByNovelId(novelId);
        if (clock == null) {
            clock = new WorldClock();
            clock.setClockId(UUID.randomUUID().toString().replace("-", ""));
            clock.setNovelId(novelId);
        }

        if (request.getCurrentTime() != null) clock.setCurrentTime(request.getCurrentTime());
        if (request.getCurrentDate() != null) clock.setCurrentDate(request.getCurrentDate());
        if (request.getSeason() != null) clock.setSeason(request.getSeason());
        if (request.getWeather() != null) clock.setWeather(request.getWeather());
        if (request.getChapterDay() != null) clock.setChapterDay(request.getChapterDay());
        if (request.getTotalDays() != null) clock.setTotalDays(request.getTotalDays());
        clock.setUpdateTime(LocalDateTime.now());

        if (clock.getClockId() != null && worldClockMapper.selectByNovelId(novelId) != null) {
            worldClockMapper.updateById(clock);
        } else {
            worldClockMapper.insert(clock);
        }
        return clock;
    }

    @Override
    public OutlineResponse generateOutline(String novelId, Integer chapterNum, String userIntent, String llmConfigId, String modelType) {
        log.info("生成章纲，小说ID: {}, 章节号: {}", novelId, chapterNum);

        ChapterGenerationRequest request = new ChapterGenerationRequest();
        request.setNovelId(novelId);
        request.setChapterNum(chapterNum != null ? chapterNum : getNextChapterNum(novelId));
        request.setUserIntent(userIntent);
        request.setModelType(modelType);
        request.setLlmConfigId(llmConfigId);

        ChapterGenerationResponse response = chapterGenerationService.generateOutline(request);

        if (response.getSuccess()) {
            OutlineResponse outlineResponse = new OutlineResponse();
            outlineResponse.setNovelId(novelId);
            outlineResponse.setChapterNum(request.getChapterNum());
            outlineResponse.setOutline(response.getChapterOutline());
            outlineResponse.setImplementationIdeas(response.getImplementationIdeas());
            return outlineResponse;
        } else {
            throw new RuntimeException(response.getErrorMessage());
        }
    }

    @Override
    public GenerationResultResponse generateChapter(String novelId, GenerateChapterRequest request) {
        log.info("生成章节，小说ID: {}", novelId);

        ChapterGenerationRequest genRequest = new ChapterGenerationRequest();
        genRequest.setNovelId(novelId);
        genRequest.setChapterNum(request.getChapterNum() != null ?
                request.getChapterNum() : getNextChapterNum(novelId));
        genRequest.setUserIntent(request.getUserIntent());
        genRequest.setSelectedIdeaIndex(request.getSelectedIdeaIndex());
        genRequest.setModelType(request.getModelType() != null ? request.getModelType() : "qwen");
        genRequest.setLlmConfigId(request.getLlmConfigId());

        ChapterGenerationResponse response = chapterGenerationService.generateChapter(genRequest);

        GenerationResultResponse result = new GenerationResultResponse();
        result.setSuccess(response.getSuccess());
        result.setChapterId(response.getChapterId());
        result.setChapterTitle(response.getChapterTitle());
        result.setContent(response.getContent());
        result.setWordCount(response.getWordCount());
        result.setPlotFingerprint(response.getPlotFingerprint());
        result.setStatus(response.getStatus() != null ? response.getStatus().name() : "UNKNOWN");
        result.setErrorMessage(response.getErrorMessage());

        if (!response.getSuccess()) {
            throw new RuntimeException(response.getErrorMessage());
        }
        return result;
    }

    @Override
    public String generateChapterAsync(String novelId, GenerateChapterRequest request) {
        ChapterGenerationRequest genRequest = new ChapterGenerationRequest();
        genRequest.setNovelId(novelId);
        genRequest.setChapterNum(request.getChapterNum() != null ?
                request.getChapterNum() : getNextChapterNum(novelId));
        genRequest.setUserIntent(request.getUserIntent());
        genRequest.setSelectedIdeaIndex(request.getSelectedIdeaIndex());
        genRequest.setModelType(request.getModelType() != null ? request.getModelType() : "qwen");
        genRequest.setLlmConfigId(request.getLlmConfigId());

        return chapterGenerationService.generateChapterAsync(genRequest);
    }

    @Override
    public GenerationResultResponse getGenerationStatus(String novelId, String taskId) {
        ChapterGenerationResponse response = chapterGenerationService.getGenerationStatus(taskId);

        GenerationResultResponse result = new GenerationResultResponse();
        if (response != null) {
            result.setSuccess(response.getSuccess());
            result.setChapterId(response.getChapterId());
            result.setChapterTitle(response.getChapterTitle());
            result.setContent(response.getContent());
            result.setWordCount(response.getWordCount());
            result.setPlotFingerprint(response.getPlotFingerprint());
            result.setStatus(response.getStatus() != null ? response.getStatus().name() : "UNKNOWN");
            result.setErrorMessage(response.getErrorMessage());
        } else {
            result.setStatus("UNKNOWN");
            result.setErrorMessage("任务不存在或已过期");
        }
        return result;
    }

    private Integer getNextChapterNum(String novelId) {
        NovelBase novel = novelBaseMapper.selectById(novelId);
        return (novel != null ? novel.getChapterCount() : 0) + 1;
    }

    private NovelResponse convertToNovelResponse(NovelBase novel) {
        NovelResponse response = new NovelResponse();
        response.setNovelId(novel.getNovelId());
        response.setNovelName(novel.getNovelName());
        response.setGenre(novel.getGenre());
        response.setTotalOutline(novel.getTotalOutline());
        response.setWorldSetting(novel.getWorldSetting());
        response.setWordCount(novel.getWordCount());
        response.setChapterCount(novel.getChapterCount());
        response.setStatus(novel.getStatus());
        response.setCreateTime(novel.getCreateTime());
        response.setUpdateTime(novel.getUpdateTime());
        return response;
    }
}
