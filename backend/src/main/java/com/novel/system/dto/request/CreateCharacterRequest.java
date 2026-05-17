package com.novel.system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 创建人物请求DTO - 兼容前后端字段名
 */
@Data
public class CreateCharacterRequest {
    
    /** 人物名称 */
    @NotBlank(message = "人物名称不能为空")
    @JsonAlias({"name"})
    private String characterName;
    
    /** 核心特质列表（如：坚韧、善良） */
    private List<String> coreTraits;
    
    /**
     * 前端发送 personality 为字符串时，自动按逗号分隔转为列表
     * 支持中文逗号"，"和英文逗号","分隔
     */
    @JsonAlias({"personality"})
    public void setPersonality(Object personality) {
        if (personality instanceof String str) {
            this.coreTraits = java.util.Arrays.stream(str.split("[，,]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } else if (personality instanceof java.util.List<?> list) {
            this.coreTraits = list.stream()
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.toList());
        }
    }
    
    /** 说话风格（如：沉稳、幽默、毒舌） */
    private String voiceStyle;

    /** 人物背景故事 */
    private String background;

    /** 人物描述 */
    private String description;

    /** 角色分类：主角/配角/反派/龙套 */
    private String role;

    /** 当前所在位置 */
    private String currentLocation;

    /** 禁止行为列表（防止OOC的硬性规则） */
    private List<String> forbiddenBehaviors;
}
