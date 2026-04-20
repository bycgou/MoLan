package com.novel.system.service.chapter;

/**
 * 章节生成服务接口
 */
public interface ChapterGenerationService {
    
    /**
     * 生成章纲
     * @param request 请求参数
     * @return 包含章纲和实现思路的响应
     */
    ChapterGenerationResponse generateOutline(ChapterGenerationRequest request);
    
    /**
     * 生成完整章节
     * @param request 请求参数（需包含选择的实现思路）
     * @return 生成的章节响应
     */
    ChapterGenerationResponse generateChapter(ChapterGenerationRequest request);
    
    /**
     * 重新生成章节
     * @param request 请求参数
     * @return 生成的章节响应
     */
    ChapterGenerationResponse regenerateChapter(ChapterGenerationRequest request);
    
    /**
     * 异步生成章节（不阻塞主线程）
     * @param request 请求参数
     * @return 生成任务ID
     */
    String generateChapterAsync(ChapterGenerationRequest request);
    
    /**
     * 获取生成状态
     * @param taskId 任务ID
     * @return 生成状态
     */
    ChapterGenerationResponse getGenerationStatus(String taskId);
    /**
     * 手动提取摘要（供前端半自动模式调用）
     * @param request 请求参数（包含手写的章节内容）
     * @return 包含摘要、人物、地点的响应
     */
    com.novel.system.ai.agent.SummaryAgentResponse extractSummaryOnly(ChapterGenerationRequest request, String content);
    
    /**
     * 保存手动撰写的章节和摘要
     * @param request 包含小说ID、章节号等信息
     * @param content 章节内容
     * @param summaryResponse 前端确认后的摘要信息
     * @return 章节响应
     */
    ChapterGenerationResponse saveManualChapter(ChapterGenerationRequest request, String content, com.novel.system.ai.agent.SummaryAgentResponse summaryResponse);
}
