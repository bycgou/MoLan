package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.config.JwtConfig;
import com.novel.system.dto.style.StyleCardVO;
import com.novel.system.dto.style.StyleCheckResult;
import com.novel.system.entity.mysql.StyleCard;
import com.novel.system.service.style.StyleCardService;
import com.novel.system.service.style.StyleCheckerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/style-card")
@RequiredArgsConstructor
public class StyleCardController {
    
    private final StyleCardService styleCardService;
    private final StyleCheckerService styleCheckerService;
    private final JwtConfig jwtConfig;
    
    @PostMapping("/create")
    public ApiResponse<StyleCardVO> createFromText(
            @RequestBody CreateStyleCardRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = getUserId(httpRequest);
            if (userId == null) {
                return ApiResponse.error("未登录");
            }
            
            if (request.getNovelName() == null || request.getNovelName().isEmpty()) {
                return ApiResponse.error("小说名称不能为空");
            }
            
            if (request.getText() == null || request.getText().length() < 100) {
                return ApiResponse.error("文本内容太短，至少需要100字");
            }
            
            StyleCard card = styleCardService.createFromNovel(userId, request.getNovelName(), request.getText(), request.getConfigId());
            return ApiResponse.success(styleCardService.toVO(card));
        } catch (Exception e) {
            log.error("创建文风卡片失败", e);
            return ApiResponse.error("创建失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/upload")
    public ApiResponse<StyleCardVO> createFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("novelName") String novelName,
            @RequestParam(value = "configId", required = false) String configId,
            HttpServletRequest httpRequest) {
        try {
            String userId = getUserId(httpRequest);
            if (userId == null) {
                return ApiResponse.error("未登录");
            }

            if (file.isEmpty()) {
                return ApiResponse.error("文件为空");
            }

            String text;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                text = sb.toString();
            }

            if (text.length() < 100) {
                return ApiResponse.error("文件内容太短，至少需要100字");
            }

            if (text.length() > 50_000_000) {
                return ApiResponse.error("文件太大，最大支持50MB");
            }

            StyleCard card = styleCardService.createFromNovel(userId, novelName, text, configId);
            return ApiResponse.success(styleCardService.toVO(card));
        } catch (Exception e) {
            log.error("上传文件创建文风卡片失败", e);
            return ApiResponse.error("上传失败：" + e.getMessage());
        }
    }
    
    @GetMapping("/list")
    public ApiResponse<List<StyleCardVO>> getUserCards(HttpServletRequest httpRequest) {
        try {
            String userId = getUserId(httpRequest);
            if (userId == null) {
                return ApiResponse.error("未登录");
            }
            
            List<StyleCard> cards = styleCardService.getUserCards(userId);
            return ApiResponse.success(styleCardService.toVOList(cards));
        } catch (Exception e) {
            log.error("获取用户卡片列表失败", e);
            return ApiResponse.error("获取失败：" + e.getMessage());
        }
    }
    
    @GetMapping("/public")
    public ApiResponse<List<StyleCardVO>> getPublicCards(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<StyleCard> cards = styleCardService.getPublicCards(page, size);
            return ApiResponse.success(styleCardService.toVOList(cards));
        } catch (Exception e) {
            log.error("获取公开卡片列表失败", e);
            return ApiResponse.error("获取失败：" + e.getMessage());
        }
    }
    
    @GetMapping("/{cardId}")
    public ApiResponse<StyleCardVO> getCardDetail(@PathVariable String cardId) {
        try {
            StyleCard card = styleCardService.getCardById(cardId);
            if (card == null) {
                return ApiResponse.error("卡片不存在");
            }
            return ApiResponse.success(styleCardService.toVO(card));
        } catch (Exception e) {
            log.error("获取卡片详情失败", e);
            return ApiResponse.error("获取失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/bind")
    public ApiResponse<Void> bindNovelToCard(
            @RequestBody BindNovelRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = getUserId(httpRequest);
            if (userId == null) {
                return ApiResponse.error("未登录");
            }
            
            if (request.getNovelId() == null || request.getNovelId().isEmpty()) {
                return ApiResponse.error("小说ID不能为空");
            }
            
            if (request.getCardId() == null || request.getCardId().isEmpty()) {
                return ApiResponse.error("卡片ID不能为空");
            }
            
            styleCardService.bindNovelToCard(request.getNovelId(), request.getCardId());
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("绑定小说到文风卡片失败", e);
            return ApiResponse.error("绑定失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/unbind")
    public ApiResponse<Void> unbindNovel(
            @RequestBody UnbindNovelRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = getUserId(httpRequest);
            if (userId == null) {
                return ApiResponse.error("未登录");
            }
            
            if (request.getNovelId() == null || request.getNovelId().isEmpty()) {
                return ApiResponse.error("小说ID不能为空");
            }

            styleCardService.unbindNovel(request.getNovelId());
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("解除绑定失败", e);
            return ApiResponse.error("解除失败：" + e.getMessage());
        }
    }
    
    @GetMapping("/binding/{novelId}")
    public ApiResponse<String> getBoundCardId(@PathVariable String novelId) {
        try {
            String cardId = styleCardService.getBoundCardId(novelId);
            return ApiResponse.success(cardId);
        } catch (Exception e) {
            log.error("获取绑定卡片失败", e);
            return ApiResponse.error("获取失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/check/quick")
    public ApiResponse<StyleCheckResult> quickCheck(
            @RequestBody CheckRequest request) {
        try {
            if (request.getText() == null || request.getText().isEmpty()) {
                return ApiResponse.error("文本不能为空");
            }
            
            if (request.getCardId() == null || request.getCardId().isEmpty()) {
                return ApiResponse.error("卡片ID不能为空");
            }
            
            StyleCheckResult result = styleCheckerService.quickCheck(request.getText(), request.getCardId());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("快速检查失败", e);
            return ApiResponse.error("检查失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/check/deep")
    public ApiResponse<StyleCheckResult> deepCheck(
            @RequestBody CheckRequest request) {
        try {
            if (request.getText() == null || request.getText().isEmpty()) {
                return ApiResponse.error("文本不能为空");
            }
            
            if (request.getCardId() == null || request.getCardId().isEmpty()) {
                return ApiResponse.error("卡片ID不能为空");
            }
            
            StyleCheckResult result = styleCheckerService.deepCheck(request.getText(), request.getCardId());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("深度检查失败", e);
            return ApiResponse.error("检查失败：" + e.getMessage());
        }
    }
    
    @DeleteMapping("/{cardId}")
    public ApiResponse<Void> deleteCard(
            @PathVariable String cardId,
            HttpServletRequest httpRequest) {
        try {
            String userId = getUserId(httpRequest);
            if (userId == null) {
                return ApiResponse.error("未登录");
            }
            
            styleCardService.deleteCard(cardId, userId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除卡片失败", e);
            return ApiResponse.error("删除失败：" + e.getMessage());
        }
    }
    
    @PutMapping("/{cardId}/public")
    public ApiResponse<Void> updatePublicStatus(
            @PathVariable String cardId,
            @RequestBody UpdatePublicRequest request,
            HttpServletRequest httpRequest) {
        try {
            String userId = getUserId(httpRequest);
            if (userId == null) {
                return ApiResponse.error("未登录");
            }
            
            styleCardService.updatePublicStatus(cardId, userId, request.isPublic());
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("更新公开状态失败", e);
            return ApiResponse.error("更新失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/admin/fix-double-encoding")
    public ApiResponse<Map<String, Object>> fixDoubleEncoding() {
        try {
            Map<String, Object> result = styleCardService.fixDoubleEncodedCards();
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("修复双重编码失败", e);
            return ApiResponse.error("修复失败：" + e.getMessage());
        }
    }

    private String getUserId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                return jwtConfig.getUserIdFromToken(token);
            }
        } catch (Exception e) {
            log.warn("获取用户ID失败: {}", e.getMessage());
        }
        return null;
    }
    
    @lombok.Data
    public static class CreateStyleCardRequest {
        private String novelName;
        private String text;
        private String configId;
    }
    
    @lombok.Data
    public static class BindNovelRequest {
        private String novelId;
        private String cardId;
    }
    
    @lombok.Data
    public static class UnbindNovelRequest {
        private String novelId;
    }
    
    @lombok.Data
    public static class CheckRequest {
        private String text;
        private String cardId;
    }
    
    @lombok.Data
    public static class UpdatePublicRequest {
        private boolean isPublic;
    }
}
