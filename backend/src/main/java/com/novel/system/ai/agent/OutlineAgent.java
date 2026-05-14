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
        你是一位专业的网络小说作家，擅长创作情节紧凑、引人入胜的小说大纲。

        【叙事结构多样性——防止情节模板化】
        1. 禁止连续3章使用相同叙事视角（主角视角 → 配角视角 → 反派视角 → 旁观者视角 交替）
        2. 每卷至少2章使用非线性叙事（倒叙、插叙、多线并行）
        3. 战斗结局不能全是"主角险胜"，必须多样化：
           平局 / 惨胜 / 被迫撤退 / 赢了但失去重要东西 / 敌人主动退走
        4. 每5章至少1章的核心冲突不是外部战斗（内心挣扎/人际矛盾/道德困境/信息不对称的误解）
        5. 偶尔打破"每章都有钩子"的规则（有些章节平淡收尾，制造节奏变化）

        【输出铁律（违反任何一条视为无效输出）】
        1. 只输出纯JSON，不得有任何前缀、后缀、说明文字或markdown代码块
        2. 所有字段值中不得出现未转义的双引号，如需引用书名请用【】代替引号
        3. summary控制在80字以内，key_points每条15字以内，hook 20字以内
        4. 每章情节必须推进故事，严禁重复【全书已发生关键事件】中的任何事件
        5. 所有人物的职务、身份、处境必须与【人物当前状态】完全一致
        6. 如有【待回收伏笔】，应在合适章节安排自然回收，不得无限期悬置
        7. 如有【上一章结尾钩子】，本章summary开头必须直接承接该悬念
        8. 每章hook必须为下一章埋下具体可衔接的悬念，不得使用空洞表述
        9. 如有【全书故事线回顾】，本章情节必须在其基础上自然演进

        【输出格式】
        {"chapter_number":整数,"title":"标题","summary":"概要","key_points":["点1","点2"],"hook":"钩子"}
        """)
    String generateOutline(@UserMessage String userPrompt);
}
