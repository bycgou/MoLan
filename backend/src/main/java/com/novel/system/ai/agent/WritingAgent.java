package com.novel.system.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 写作Agent接口 - LangChain4j AI Service
 * 
 * 【核心作用】
 * 这是整个系统的"小说作家"，负责根据章纲生成小说正文。
 * 
 * 【工作原理】
 * 1. 接收章纲和上下文信息
 * 2. 根据系统指令分析章纲内容
 * 3. 可能调用工具查询人物设定、伏笔等信息
 * 4. 生成符合要求的小说正文
 * 
 * 【与OutlineAgent的区别】
 * - OutlineAgent：生成章纲（剧情规划）
 * - WritingAgent：生成正文（具体内容）
 * 
 * 【写作要求】
 * 1. 严格遵循章纲：不能偏离核心情节
 * 2. 人物一致性：言行必须符合人物设定
 * 3. 逻辑连贯：与前文保持一致
 * 4. 世界状态一致：时间、地点、环境等不能矛盾
 * 5. 追读力：章节结尾要有钩子，吸引读者继续阅读
 * 
 * 【防幻觉三定律】
 * 与OutlineAgent相同，防止AI胡编乱造：
 * - 定律一：大纲即法律 - 严格遵循章纲内容
 * - 定律二：设定即物理 - 人物言行必须符合设定
 * - 定律三：发明需识别 - 创造新元素必须标注
 * 
 * 【工具使用】
 * Agent可以调用工具查询：
 * - 人物设定：确保人物对话和行为符合设定
 * - 伏笔信息：确保伏笔得到合理安排
 * - 小说基本信息：了解整体情况
 * 
 * 【输出格式】
 * 直接输出小说正文，不要添加任何说明。
 * 
 * 【使用示例】
 * <pre>
 * WritingAgent agent = AiServices.builder(WritingAgent.class)
 *     .chatLanguageModel(model)
 *     .tools(toolProvider.getAllTools())
 *     .build();
 * 
 * String content = agent.generateChapter("小说ID: xxx\n章节号: 第1章\n章纲: ...\n上下文: ...");
 * </pre>
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see ChapterGenerationChain#executeWritingChain 调用此Agent的地方
 * @see OutlineAgent 章纲生成Agent
 * @see NovelTool Agent可使用的工具
 */
public interface WritingAgent {
    
    /**
     * 生成小说正文
     * 
     * 【执行流程】
     * 1. 接收用户消息，包含小说ID、章节号、章纲、上下文信息
     * 2. 根据系统指令分析章纲内容
     * 3. 可能调用工具查询额外信息（如人物设定、伏笔等）
     * 4. 生成符合要求的小说正文
     * 
     * 【系统指令详解】
     * - "你是专业的小说写作Agent" - 定义角色
     * - "防幻觉三定律" - 约束AI行为
     * - "写作规则" - 具体的写作要求
     * - "追读力要求" - 章节结尾要有钩子
     * - "输出要求" - 直接输出正文，不要添加说明
     * 
     * @param userPrompt 用户消息，包含：
     *                   - novelId: 小说ID
     *                   - chapterNum: 章节号
     *                   - 章纲: OutlineAgent生成的章纲
     *                   - 上下文信息: 总纲、人物状态、世界状态等
     * @return 生成的小说正文
     * @see WritingAgentResponse 用于解析返回的内容
     */
    @SystemMessage("""
        你是专业的小说写作Agent。你的任务是根据章纲生成小说正文。
        
        ===防幻觉三定律===
        【定律一：大纲即法律】严格遵循章纲内容，不能偏离核心情节
        【定律二：设定即物理】人物言行必须符合设定，世界状态保持一致
        【定律三：发明需识别】创造新元素必须标注 [新设定：XXX]
        
        ===写作规则===
        1. 严格遵循章纲内容，不能偏离核心情节
        2. 人物对话和行为必须符合人物设定
        3. 保持与前文的连贯性，不能出现逻辑矛盾
        4. 注意世界状态的一致性
        5. 目标字数：1500字左右
        6. 段落清晰，每段不超过150字
        
        ===追读力要求===
        - 章节结尾必须有钩子，吸引读者继续阅读
        - 适当制造悬念或反转
        - 控制节奏，避免平淡
        
        ===输出要求===
        直接输出小说正文，不要添加任何说明。使用工具查询人物设定以确保人物一致性。""")
    String generateChapter(@UserMessage String userPrompt);
}
