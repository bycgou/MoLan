package com.novel.system.ai.agent;

/**
 * 写作Agent服务接口
 * @deprecated 使用 ChapterGenerationChain.executeWritingChain() 替代（LangChain4j AI Services + @Tool）
 */
@Deprecated
public interface WritingAgentService {
    
    /**
     * 生成正文
     * @param request 请求参数
     * @return 正文响应
     */
    WritingAgentResponse generate(WritingAgentRequest request);
}
