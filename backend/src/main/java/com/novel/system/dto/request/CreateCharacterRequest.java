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
    
    /** 当前所在位置 */
    private String currentLocation;
    
    /** 禁止行为列表（防止OOC的硬性规则） */
    private List<String> forbiddenBehaviors;
    
    /** 前端发送 role → 映射到 background */
    @JsonAlias({"role"})
    public void setRole(String role) {
        if (role != null && this.background == null) {
            this.background = "角色: " + role;
        }
    }
    
    /** 前端发送 description → 映射到 background */
    @JsonAlias({"description"})
    public void setDescription(String description) {
        if (description != null) {
            if (this.background == null) {
                this.background = description;
            } else {
                this.background = this.background + "\n" + description;
            }
        }
    }
}
