package com.novel.system.controller;

import com.novel.system.dto.request.CreateClueRequest;
import com.novel.system.dto.response.ApiResponse;
import com.novel.system.dto.response.ClueResponse;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.service.clue.ClueBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 伏笔管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/novel/{novelId}/clue")
@RequiredArgsConstructor
public class ClueController {
    
    private final ClueBookService clueBookService;
    
    /**
     * 创建伏笔
     */
    @PostMapping
    public ApiResponse<ClueResponse> createClue(
            @PathVariable String novelId,
            @RequestBody CreateClueRequest request) {
        log.info("创建伏笔，小说ID: {}", novelId);
        
        ClueBook clue = new ClueBook();
        clue.setClueId(UUID.randomUUID().toString().replace("-", ""));
        clue.setNovelId(novelId);
        clue.setClueContent(request.getClueContent());
        clue.setPriority(request.getPriority() != null ? request.getPriority() : 2);
        clue.setBuryChapter(request.getBuryChapter());
        clue.setRelatedCharacters(request.getRelatedCharacters());
        clue.setRelatedLocations(request.getRelatedLocations());
        clue.setStatus(0); // 未回收
        clue.setCreateTime(LocalDateTime.now());
        
        clue = clueBookService.createClue(clue);
        
        return ApiResponse.success("伏笔创建成功", convertToClueResponse(clue));
    }
    
    /**
     * 获取小说所有伏笔
     */
    @GetMapping("/list")
    public ApiResponse<List<ClueResponse>> getClues(
            @PathVariable String novelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        List<ClueBook> clues = clueBookService.getCluesByNovelId(novelId);
        List<ClueResponse> responses = clues.stream()
            .map(this::convertToClueResponse)
            .collect(Collectors.toList());
        if (size > 0) {
            int from = Math.min(page * size, responses.size());
            int to = Math.min(from + size, responses.size());
            return ApiResponse.success(responses.subList(from, to));
        }
        return ApiResponse.success(responses);
    }
    
    /**
     * 获取未回收伏笔
     */
    @GetMapping("/unrecycled")
    public ApiResponse<List<ClueResponse>> getUnrecycledClues(@PathVariable String novelId) {
        List<ClueBook> clues = clueBookService.getUnrecycledClues(novelId);
        List<ClueResponse> responses = clues.stream()
            .map(this::convertToClueResponse)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
    
    /**
     * 回收伏笔
     */
    @PutMapping("/{clueId}/recycle")
    public ApiResponse<Void> recycleClue(
            @PathVariable String novelId,
            @PathVariable String clueId,
            @RequestParam Integer recycleChapter) {
        clueBookService.recycleClue(clueId, recycleChapter);
        return ApiResponse.success("伏笔回收成功", null);
    }
    
    /**
     * 批量回收伏笔
     */
    @PutMapping("/batch-recycle")
    public ApiResponse<Void> batchRecycleClues(
            @PathVariable String novelId,
            @RequestBody List<String> clueIds,
            @RequestParam Integer recycleChapter) {
        clueBookService.batchRecycleClues(clueIds, recycleChapter);
        return ApiResponse.success("批量回收成功", null);
    }
    
    /**
     * 获取在某章埋下的伏笔
     */
    @GetMapping("/bury/{chapterNum}")
    public ApiResponse<List<ClueResponse>> getCluesByburyChapter(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum) {
        List<ClueBook> clues = clueBookService.getCluesByburyChapter(novelId, chapterNum);
        List<ClueResponse> responses = clues.stream()
            .map(this::convertToClueResponse)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
    
    /**
     * 获取在某章回收的伏笔
     */
    @GetMapping("/recycle/{chapterNum}")
    public ApiResponse<List<ClueResponse>> getRecycledCluesByChapter(
            @PathVariable String novelId,
            @PathVariable Integer chapterNum) {
        List<ClueBook> clues = clueBookService.getRecycledCluesByChapter(novelId, chapterNum);
        List<ClueResponse> responses = clues.stream()
            .map(this::convertToClueResponse)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
    
    private ClueResponse convertToClueResponse(ClueBook clue) {
        ClueResponse response = new ClueResponse();
        response.setClueId(clue.getClueId());
        response.setNovelId(clue.getNovelId());
        response.setClueContent(clue.getClueContent());
        response.setPriority(clue.getPriority());
        response.setBuryChapter(clue.getBuryChapter());
        response.setRecycleChapter(clue.getRecycleChapter());
        response.setStatus(clue.getStatus());
        response.setCreateTime(clue.getCreateTime());
        return response;
    }
}
