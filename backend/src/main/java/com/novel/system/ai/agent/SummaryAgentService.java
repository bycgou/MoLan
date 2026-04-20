package com.novel.system.ai.agent;

/**
 * 摘要Agent服务接口
 * @deprecated 使用 ChapterGenerationChain.executeSummaryChain() 替代（LangChain4j AI Services + @Tool）
 */
@Deprecated
public interface SummaryAgentService {
    
    /**
     * 生成剧情指纹
     * @param request 请求参数
     * @return 摘要响应
     */
    SummaryAgentResponse generate(SummaryAgentRequest request);
}
