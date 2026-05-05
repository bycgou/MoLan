package com.novel.system.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 弧段摘要 Agent 接口 - LangChain4j AI Service
 * 
 * 【核心作用】
 * 这是整个系统的"小说编辑"，负责分析一段故事线，生成简洁的弧段摘要。
 * 
 * 【工作原理】
 * 1. 接收一段故事线内容
 * 2. 分析主要情节、人物变化、伏笔埋设和回收
 * 3. 生成200-400字的简洁摘要
 * 
 * 【触发时机】
 * 每写完10-20章，自动触发生成弧段摘要
 * 
 * 【输出格式】
 * 直接输出摘要文本，不要JSON或其他格式
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see ContextBuilderService 使用弧段摘要的服务
 */
public interface ArcSummaryAgent {
    
    /**
     * 生成弧段摘要
     * 
     * @param prompt 包含故事线内容的提示
     * @return 200-400字的弧段摘要
     */
    @SystemMessage("""
        你是专业的小说编辑。分析一段故事线，生成简洁的弧段摘要。
        
        【输出要求】
        1. 200-400字
        2. 包含主要情节、人物变化、伏笔埋设和回收
        3. 只输出摘要文本，不要JSON或其他格式
        4. 保持客观，不要添加个人评论
        """)
    String generateArcSummary(@UserMessage String prompt);
}