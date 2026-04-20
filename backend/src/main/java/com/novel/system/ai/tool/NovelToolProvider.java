package com.novel.system.ai.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 小说工具提供器 - 为Agent提供所有可用工具
 * 
 * 【核心作用】
 * 这是Agent的"工具箱管理员"，负责收集和提供所有可用的工具。
 * Agent在创建时，需要通过此工具提供器获取所有可用的工具。
 * 
 * 【设计模式】
 * 采用"工厂模式"，集中管理所有工具实例。
 * 
 * 【工具分类】
 * 1. CharacterTool：人物工具（查询人物信息、校验人物行为）
 * 2. ClueTool：伏笔工具（查询伏笔信息）
 * 3. NovelTool：小说工具（查询小说、章节、卷等信息）
 * 
 * 【使用方式】
 * <pre>
 * // 在ChapterGenerationChain中：
 * OutlineAgent agent = AiServices.builder(OutlineAgent.class)
 *     .chatLanguageModel(model)
 *     .tools(toolProvider.getAllTools())  // 获取所有工具
 *     .build();
 * </pre>
 * 
 * 【为什么需要工具提供器？】
 * 1. 集中管理：所有工具在一个地方管理
 * 2. 依赖注入：使用Spring的依赖注入，避免手动创建工具实例
 * 3. 灵活扩展：添加新工具时，只需在此类中添加
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see CharacterTool 人物工具
 * @see ClueTool 伏笔工具
 * @see NovelTool 小说工具
 * @see ChapterGenerationChain 使用此工具提供器的地方
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NovelToolProvider {
    
    /**
     * 人物工具
     * 提供人物查询和校验功能
     */
    private final CharacterTool characterTool;
    
    /**
     * 伏笔工具
     * 提供伏笔查询功能
     */
    private final ClueTool clueTool;
    
    /**
     * 小说工具
     * 提供小说、章节、卷等查询功能
     */
    private final NovelTool novelTool;
    
    /**
     * 获取所有工具对象，用于AI Service构建
     * 
     * 【返回格式】
     * 返回Object数组，包含所有工具实例：
     * - CharacterTool：人物工具
     * - ClueTool：伏笔工具
     * - NovelTool：小说工具
     * 
     * 【使用方式】
     * <pre>
     * Object[] tools = toolProvider.getAllTools();
     * OutlineAgent agent = AiServices.builder(OutlineAgent.class)
     *     .chatLanguageModel(model)
     *     .tools(tools)
     *     .build();
     * </pre>
     * 
     * @return 工具对象数组
     */
    public Object[] getAllTools() {
        return new Object[]{characterTool, clueTool, novelTool};
    }
    
    /**
     * 获取人物工具
     * 
     * @return CharacterTool实例
     */
    public CharacterTool getCharacterTool() {
        return characterTool;
    }
    
    /**
     * 获取伏笔工具
     * 
     * @return ClueTool实例
     */
    public ClueTool getClueTool() {
        return clueTool;
    }
    
    /**
     * 获取小说工具
     * 
     * @return NovelTool实例
     */
    public NovelTool getNovelTool() {
        return novelTool;
    }
}
