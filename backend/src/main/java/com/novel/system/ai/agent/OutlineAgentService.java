package com.novel.system.ai.agent;

/**
 * 大纲Agent服务接口
 * @deprecated 使用 ChapterGenerationChain.executeOutlineChain() 替代（LangChain4j AI Services + @Tool）
 */
@Deprecated
public interface OutlineAgentService {
    
    /**
     * 生成章纲
     * @param request 请求参数
     * @return 章纲响应
     */
    OutlineAgentResponse generate(OutlineAgentRequest request);
}
