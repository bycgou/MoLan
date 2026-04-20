package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.service.novel.StrandRhythmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 增强节奏系统控制器（Strand Weave）
 */
@Slf4j
@RestController
@RequestMapping("/novel/{novelId}/rhythm-enhanced")
@RequiredArgsConstructor
public class EnhancedRhythmController {
    
    private final StrandRhythmService strandRhythmService;
    
    @GetMapping("/status")
    public ApiResponse<StrandRhythmService.RhythmStatus> getRhythmStatus(@PathVariable String novelId) {
        StrandRhythmService.RhythmStatus status = strandRhythmService.getRhythmStatus(novelId);
        return ApiResponse.success(status);
    }
    
    @GetMapping("/recommend")
    public ApiResponse<String> getRecommendedStrand(@PathVariable String novelId) {
        StrandRhythmService.StrandType recommended = strandRhythmService.recommendNextStrand(novelId);
        return ApiResponse.success(recommended.name().toLowerCase());
    }
    
    @GetMapping("/check")
    public ApiResponse<StrandRhythmService.RhythmViolation> checkViolation(
            @PathVariable String novelId,
            @RequestParam String strandType) {
        StrandRhythmService.StrandType type = StrandRhythmService.StrandType.valueOf(strandType.toUpperCase());
        StrandRhythmService.RhythmViolation violation = strandRhythmService.checkRhythmViolation(novelId, type);
        return ApiResponse.success(violation);
    }
}
