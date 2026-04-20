package com.novel.system.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 章纲Agent接口 - LangChain4j AI Service
 * 
 * 【核心作用】
 * 这是整个系统的"大纲规划师"，负责根据小说总纲和当前状态生成章纲。
 * 
 * 【工作原理】
 * 1. LangChain4j通过动态代理技术，将这个接口转换为可执行的AI服务
 * 2. @SystemMessage定义AI的角色和行为规则
 * 3. @UserMessage是用户传入的请求内容
 * 4. AI会根据系统指令处理用户消息，返回符合格式的JSON响应
 * 
 * 【防幻觉三定律】
 * 这是项目的核心设计思想，防止AI胡编乱造：
 * - 定律一：大纲即法律 - 必须严格按照总纲生成，不能擅自发挥
 * - 定律二：设定即物理 - 世界观设定不可违反，人物特质固定不变
 * - 定律三：发明需识别 - 创造新元素必须标注[新设定：XXX]
 * 
 * 【工具使用】
 * Agent可以在生成过程中自动调用工具查询小说数据，如：
 * - 查询人物信息（CharacterTool）
 * - 查询伏笔信息（ClueTool）
 * - 查询小说基本信息（NovelTool）
 * 
 * 【输出格式】
 * 返回JSON格式，包含：
 * - strand_type: 剧情线类型（quest/fire/constellation）
 * - outline: 章纲内容
 * - ideas: 实现思路列表
 * 
 * 【使用示例】
 * <pre>
 * OutlineAgent agent = AiServices.builder(OutlineAgent.class)
 *     .chatLanguageModel(model)
 *     .tools(toolProvider.getAllTools())
 *     .build();
 * 
 * String json = agent.generateOutline("小说ID: xxx\n章节号: 第1章\n...");
 * </pre>
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see ChapterGenerationChain#executeOutlineChain 调用此Agent的地方
 * @see NovelTool Agent可使用的工具
 */
public interface OutlineAgent {
    
    /**
     * 生成章纲
     * 
     * 【执行流程】
     * 1. 接收用户消息，包含小说ID、章节号、上下文信息
     * 2. 根据系统指令分析上下文
     * 3. 可能调用工具查询额外信息（如人物状态、伏笔等）
     * 4. 生成符合格式的JSON响应
     * 
     * 【系统指令详解】
     * - "你是专业的小说大纲规划Agent" - 定义角色
     * - "防幻觉三定律" - 约束AI行为，防止胡编乱造
     * - "生成规则" - 具体的执行步骤
     * - "输出格式" - 定义JSON结构
     * 
     * @param userPrompt 用户消息，包含：
     *                   - novelId: 小说ID
     *                   - chapterNum: 章节号
     *                   - 上下文信息：总纲、卷纲、人物状态、世界状态等
     * @return JSON格式的章纲，包含outline和ideas
     * @see OutlineAgentResponse 用于解析返回的JSON
     */
    @SystemMessage("""
        你是专业的小说大纲规划Agent。你的任务是根据小说总纲和当前状态生成章纲。
        
        ===防幻觉三定律===
        【定律一：大纲即法律】必须严格按照总纲和卷纲内容生成，不能擅自发挥
        【定律二：设定即物理】世界观设定不可违反，人物特质固定不变
        【定律三：发明需识别】创造新元素必须标注 [新设定：XXX]
        
        ===生成规则===
        1. 首先分析总纲，确定本章应该发生什么剧情
        2. 参考当前卷的剧情规划，确保本章符合卷的发展节奏
        3. 如果总纲明确说明了本章内容，必须严格按照总纲生成
        4. 使用工具查询人物、伏笔等信息辅助生成
        5. 注意承上启下，与上一章剧情连贯
        6. 合理安排伏笔的埋下和回收
        
        ===输出格式（JSON）===
        {
          "strand_type": "quest/fire/constellation",
          "outline": "章纲内容，包含场景、情节、冲突、结尾、钩子",
          "ideas": ["实现思路1", "实现思路2"]
        }
        """)
    String generateOutline(@UserMessage String userPrompt);
}
