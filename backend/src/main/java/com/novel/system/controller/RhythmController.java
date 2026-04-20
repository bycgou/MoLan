package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.service.novel.RhythmService;
import com.novel.system.service.novel.StrandRhythmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 节奏系统控制器
 */
@Slf4j
@RestController
@RequestMapping("/novel/{novelId}/rhythm")
@RequiredArgsConstructor
public class RhythmController {
    
    private final RhythmService rhythmService;
    private final StrandRhythmService strandRhythmService;
    
    /**
     * 获取节奏状态
     */
    @GetMapping("/status")
    public ApiResponse<StrandRhythmService.RhythmStatus> getRhythmStatus(
            @PathVariable String novelId) {
        StrandRhythmService.RhythmStatus status = strandRhythmService.getRhythmStatus(novelId);
        return ApiResponse.success(status);
    }
    
    /**
     * 获取推荐的下一个Strand类型
     */
    @GetMapping("/recommend")
    public ApiResponse<String> getRecommendedStrand(@PathVariable String novelId) {
        StrandRhythmService.StrandType recommended = strandRhythmService.recommendNextStrand(novelId);
        return ApiResponse.success(recommended.name().toLowerCase());
    }
    
    /**
     * 检查节奏违规
     */
    @GetMapping("/check")
    public ApiResponse<StrandRhythmService.RhythmViolation> checkViolation(
            @PathVariable String novelId,
            @RequestParam String strandType) {
        StrandRhythmService.StrandType type = StrandRhythmService.StrandType.valueOf(strandType.toUpperCase());
        StrandRhythmService.RhythmViolation violation = strandRhythmService.checkRhythmViolation(novelId, type);
        return ApiResponse.success(violation);
    }
    
    /**
     * 分析章节追读力
     */
    @PostMapping("/read-pull")
    public ApiResponse<RhythmService.ReadPullAnalysis> analyzeReadPull(
            @PathVariable String novelId,
            @RequestParam int chapterNum,
            @RequestBody String chapterContent) {
        log.info("分析章节追读力，小说ID: {}, 章节: {}", novelId, chapterNum);
        
        RhythmService.ReadPullAnalysis analysis = rhythmService.analyzeReadPull(
                chapterContent, novelId, chapterNum);
        
        return ApiResponse.success("追读力分析完成", analysis);
    }
    
    /**
     * 获取小说追读力趋势
     */
    @GetMapping("/trend")
    public ApiResponse<RhythmService.ReadPullTrend> getReadPullTrend(
            @PathVariable String novelId) {
        log.info("获取小说追读力趋势，小说ID: {}", novelId);
        
        RhythmService.ReadPullTrend trend = rhythmService.calculateReadPullTrend(novelId);
        
        return ApiResponse.success("获取追读力趋势成功", trend);
    }
    
    /**
     * 获取追读力优化建议
     */
    @GetMapping("/suggestion")
    public ApiResponse<String> getOptimizationSuggestion(
            @PathVariable String novelId) {
        log.info("获取追读力优化建议，小说ID: {}", novelId);
        
        String suggestion = rhythmService.generateOptimizationSuggestion(novelId);
        
        return ApiResponse.success("获取优化建议成功", suggestion);
    }
}
