package com.novel.system.mcp.server.tool;

import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.mcp.server.McpServerConfig;
import com.novel.system.service.character.CharacterStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class CharacterMcpTools extends BaseMcpTools {

    private final CharacterStateService characterStateService;

    @Autowired
    public CharacterMcpTools(McpServerConfig mcpConfig, CharacterStateService characterStateService) {
        super(mcpConfig);
        this.characterStateService = characterStateService;
    }

    @Override
    public void registerAll() {
        addTool("moyun_list_characters",
            "查询小说中所有人物的基本信息，返回姓名、性格特点、说话风格",
            Map.of("novelId", Map.of("type", "string", "description", "小说ID")),
            List.of("novelId"),
            args -> listCharacters((String) args.get("novelId")));

        addTool("moyun_get_character",
            "查询指定人物的详细信息，包括状态、位置、关系等",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "characterId", Map.of("type", "string", "description", "人物ID")
            ),
            List.of("novelId", "characterId"),
            args -> getCharacterDetail(
                (String) args.get("novelId"),
                (String) args.get("characterId")));

        addTool("moyun_create_character",
            "创建新人物，需提供小说ID、人物名称、性格特点、说话风格等",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "characterName", Map.of("type", "string", "description", "人物名称"),
                "coreTraits", Map.of("type", "string", "description", "核心特质，逗号分隔"),
                "voiceStyle", Map.of("type", "string", "description", "说话风格"),
                "background", Map.of("type", "string", "description", "人物背景")
            ),
            List.of("novelId", "characterName"),
            args -> createCharacter(
                (String) args.get("novelId"),
                (String) args.get("characterName"),
                (String) args.get("coreTraits"),
                (String) args.get("voiceStyle"),
                (String) args.get("background")));

        addTool("moyun_check_ooc",
            "检查人物行为是否符合设定，防止OOC（人设崩塌）",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "characterId", Map.of("type", "string", "description", "人物ID"),
                "behavior", Map.of("type", "string", "description", "待检查的行为描述"),
                "chapterNum", Map.of("type", "integer", "description", "章节号")
            ),
            List.of("novelId", "characterId", "behavior", "chapterNum"),
            args -> checkBehavior(
                (String) args.get("novelId"),
                (String) args.get("characterId"),
                (String) args.get("behavior"),
                ((Number) args.get("chapterNum")).intValue()));

        addTool("moyun_get_characters_by_location",
            "查询位于指定地点的所有人物",
            Map.of(
                "novelId", Map.of("type", "string", "description", "小说ID"),
                "location", Map.of("type", "string", "description", "地点名称")
            ),
            List.of("novelId", "location"),
            args -> getCharactersByLocation(
                (String) args.get("novelId"),
                (String) args.get("location")));
    }

    private String listCharacters(String novelId) {
        List<CharacterState> characters = characterStateService.getCharactersByNovelId(novelId);
        if (characters.isEmpty()) return "该小说暂无人物设定";
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

    private String getCharacterDetail(String novelId, String characterId) {
        CharacterState c = characterStateService.getCharacter(novelId, characterId);
        if (c == null) return "未找到该人物";
        StringBuilder sb = new StringBuilder();
        sb.append("姓名: ").append(c.getCharacterName()).append("\n");
        if (c.getCoreTraits() != null) sb.append("核心特质: ").append(String.join(",", c.getCoreTraits())).append("\n");
        if (c.getVoiceStyle() != null) sb.append("说话风格: ").append(c.getVoiceStyle()).append("\n");
        if (c.getBackground() != null) sb.append("背景: ").append(c.getBackground()).append("\n");
        if (c.getCurrentStatus() != null) {
            if (c.getCurrentStatus().getLocation() != null)
                sb.append("当前位置: ").append(c.getCurrentStatus().getLocation()).append("\n");
            if (c.getCurrentStatus().getStatus() != null)
                sb.append("当前状态: ").append(c.getCurrentStatus().getStatus()).append("\n");
        }
        return sb.toString();
    }

    private String createCharacter(String novelId, String characterName,
                                    String coreTraits, String voiceStyle, String background) {
        CharacterState character = new CharacterState();
        character.setCharacterId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        character.setNovelId(novelId);
        character.setCharacterName(characterName);
        if (coreTraits != null && !coreTraits.isEmpty()) {
            character.setCoreTraits(Arrays.asList(coreTraits.split(",")));
        }
        character.setVoiceStyle(voiceStyle);
        character.setBackground(background);

        CharacterState.CurrentStatus status = new CharacterState.CurrentStatus();
        status.setHealth(100);
        status.setEmotion("平静");
        status.setLocation("未知");
        character.setCurrentStatus(status);

        characterStateService.createCharacter(character);
        return "人物创建成功: " + characterName;
    }

    private String checkBehavior(String novelId, String characterId, String behavior, int chapterNum) {
        boolean allowed = characterStateService.isBehaviorAllowed(
            novelId, characterId, behavior, chapterNum);
        return allowed ? "该行为符合人物设定" : "警告：该行为可能不符合人物设定，存在OOC风险";
    }

    private String getCharactersByLocation(String novelId, String location) {
        List<CharacterState> characters = characterStateService.getCharactersByLocation(novelId, location);
        if (characters.isEmpty()) return "该地点暂无人物";
        StringBuilder sb = new StringBuilder();
        for (CharacterState c : characters) {
            sb.append(c.getCharacterName()).append(" ");
        }
        return "位于" + location + "的人物: " + sb.toString().trim();
    }
}
