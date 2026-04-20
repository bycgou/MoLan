package com.novel.system.ai.tool;

import dev.langchain4j.agent.tool.Tool;
import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.service.character.CharacterStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 人物工具 - Agent可调用的人物查询与校验工具
 * 
 * 【核心作用】
 * 这是AI Agent的"人物数据库"，提供查询人物信息和校验人物行为的功能。
 * Agent在生成内容时，可以自动调用这些工具确保人物一致性。
 * 
 * 【为什么需要人物工具？】
 * 1. 人物一致性：确保人物言行符合设定
 * 2. 防止OOC：OOC（Out Of Character）指人物行为不符合设定
 * 3. 提供上下文：让Agent了解人物的基本信息
 * 
 * 【工具列表】
 * 1. getCharactersByNovelId - 查询所有人物
 * 2. getCharacterDetail - 查询指定人物详情
 * 3. checkBehaviorAllowed - 检查人物行为是否合理
 * 4. getCharactersByLocation - 查询指定地点的人物
 * 
 * 【使用场景】
 * - WritingAgent生成正文时，查询人物设定确保对话和行为符合设定
 * - OutlineAgent生成章纲时，查询人物状态了解当前情况
 * - ValidationChain校验内容时，检查人物行为是否合理
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see NovelTool 小说工具
 * @see ClueTool 伏笔工具
 * @see CharacterStateService 人物状态服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterTool {
    
    /**
     * 人物状态服务
     * 负责查询和管理人物状态
     */
    private final CharacterStateService characterStateService;
    
    /**
     * 查询小说中所有人物的基本信息
     * 
     * 【使用场景】
     * Agent需要了解小说中有哪些人物，以及他们的基本设定。
     * 
     * 【返回内容】
     * - 人物姓名
     * - 性格特点
     * - 说话风格
     * 
     * @param novelId 小说ID
     * @return 格式化的人物列表字符串
     */
    @Tool("查询小说中所有人物的基本信息，返回人物列表包含姓名、角色、性格特点等")
    public String getCharactersByNovelId(String novelId) {
        log.info("[Tool] 查询小说人物: novelId={}", novelId);
        List<CharacterState> characters = characterStateService.getCharactersByNovelId(novelId);
        if (characters.isEmpty()) {
            return "该小说暂无人物设定";
        }
        
        StringBuilder sb = new StringBuilder();
        for (CharacterState c : characters) {
            sb.append("【").append(c.getCharacterName()).append("】");
            if (c.getCoreTraits() != null && !c.getCoreTraits().isEmpty()) {
                sb.append(" 性格:").append(String.join(",", c.getCoreTraits()));
            }
            if (c.getVoiceStyle() != null) sb.append(" 说话风格:").append(c.getVoiceStyle());
            sb.append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 查询指定人物的详细信息
     * 
     * 【使用场景】
     * Agent需要了解特定人物的详细信息，如：
     * - 当前位置
     * - 当前状态
     * - 修为等级
     * 
     * @param novelId 小说ID
     * @param characterId 人物ID
     * @return 格式化的人物详情字符串
     */
    @Tool("查询指定人物的详细信息，包括当前状态、位置、修为等级等")
    public String getCharacterDetail(String novelId, String characterId) {
        log.info("[Tool] 查询人物详情: novelId={}, characterId={}", novelId, characterId);
        CharacterState character = characterStateService.getCharacter(novelId, characterId);
        if (character == null) {
            return "未找到该人物";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("姓名: ").append(character.getCharacterName()).append("\n");
        if (character.getCoreTraits() != null && !character.getCoreTraits().isEmpty()) {
            sb.append("核心特质: ").append(String.join(",", character.getCoreTraits())).append("\n");
        }
        if (character.getVoiceStyle() != null) sb.append("说话风格: ").append(character.getVoiceStyle()).append("\n");
        if (character.getCurrentStatus() != null) {
            if (character.getCurrentStatus().getLocation() != null) {
                sb.append("当前位置: ").append(character.getCurrentStatus().getLocation()).append("\n");
            }
            if (character.getCurrentStatus().getStatus() != null) {
                sb.append("当前状态: ").append(character.getCurrentStatus().getStatus()).append("\n");
            }
        }
        return sb.toString();
    }
    
    /**
     * 检查人物行为是否符合设定
     * 
     * 【OOC检测】
     * OOC（Out Of Character）指人物行为不符合设定。
     * 例如：
     * - 一个胆小的人突然变得勇敢
     * - 一个冷漠的人突然变得热情
     * - 一个不懂医术的人突然开始治病
     * 
     * 【使用场景】
     * ValidationChain校验内容时，会调用此工具检查人物行为是否合理。
     * 
     * @param novelId 小说ID
     * @param characterId 人物ID
     * @param behavior 人物行为描述
     * @param chapterNum 章节号
     * @return 校验结果，true表示行为合理，false表示存在OOC风险
     */
    @Tool("检查人物在指定章节的行为是否符合设定，返回true表示行为合理，false表示OOC（人设崩塌）")
    public String checkBehaviorAllowed(String novelId, String characterId, String behavior, int chapterNum) {
        log.info("[Tool] 检查人物行为: novelId={}, characterId={}, behavior={}", novelId, characterId, behavior);
        boolean allowed = characterStateService.isBehaviorAllowed(novelId, characterId, behavior, chapterNum);
        if (allowed) {
            return "该行为符合人物设定";
        } else {
            return "警告：该行为可能不符合人物设定，存在OOC风险。建议调整为更符合人物性格的行为。";
        }
    }
    
    /**
     * 查询位于指定地点的所有人物
     * 
     * 【使用场景】
     * Agent在生成场景描写时，需要了解当前地点有哪些人物。
     * 例如：
     * - 主角进入一个房间，需要知道房间里有谁
     * - 发生战斗，需要知道双方有哪些人
     * 
     * @param novelId 小说ID
     * @param location 地点名称
     * @return 格式化的地点人物列表
     */
    @Tool("查询位于指定地点的所有人物")
    public String getCharactersByLocation(String novelId, String location) {
        log.info("[Tool] 查询地点人物: novelId={}, location={}", novelId, location);
        List<CharacterState> characters = characterStateService.getCharactersByLocation(novelId, location);
        if (characters.isEmpty()) {
            return "该地点暂无人物";
        }
        StringBuilder sb = new StringBuilder();
        for (CharacterState c : characters) {
            sb.append(c.getCharacterName()).append(" ");
        }
        return "位于" + location + "的人物: " + sb.toString().trim();
    }
}
