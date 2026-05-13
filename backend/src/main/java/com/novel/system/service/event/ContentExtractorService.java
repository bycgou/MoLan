package com.novel.system.service.event;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 程序化内容提取服务
 * 使用正则表达式从章节正文中提取位置、实力、物品、对话等信息。
 * 作为LLM提取的补充和兜底方案。
 */
@Slf4j
@Service
public class ContentExtractorService {

    /** 代词集合（排除代词避免误提取） */
    private static final Set<String> PRONOUNS = Set.of(
        "他", "她", "它", "我", "你", "谁", "人们", "众人", "大家",
        "对方", "那人", "这人", "某人", "别人", "自己", "咱们"
    );

    /** 位置变化模式 */
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
        "(?:来到|到达|进入|走进|走入|飞到|赶到|回到|抵达|跨入|踏入|步入|涌入|冲进|闯入|奔至|行至)([\\u4e00-\\u9fa5]{2,10})"
    );

    /** 实力变化模式 */
    private static final Pattern POWER_PATTERN = Pattern.compile(
        "(?:突破到|晋升为|达到|突破至|踏入|晋级|迈入|凝练出|领悟了|掌握了|获得了)([\\u4e00-\\u9fa5]{2,10})"
    );

    /** 物品获取模式 */
    private static final Pattern ITEM_PATTERN = Pattern.compile(
        "(?:获得|得到|捡到|发现|拿到|取出|拿出|掏出|收获|夺得|抢到|寻得|觅得)了?(?:一把|一枚|一件|一块|一本|一颗|一道|一只|一张)?([\\u4e00-\\u9fa5]{2,8})"
    );

    /** 对话模式：人名 + 说/道/问 + 引号内容 */
    private static final Pattern DIALOGUE_PATTERN = Pattern.compile(
        "([\\u4e00-\\u9fa5]{1,4}?)(?:说道|笑道|问道|喊道|答道|叫道|叹道|怒道|吼道|[说道喊问答笑叫叹吟喝怒吼冷哼轻哼])([：:\"]?[\u201c\"]([^\u201d\"]+)[\u201d\"])"
    );

    /** 时间推进模式 */
    private static final Pattern TIME_ADVANCE_PATTERN = Pattern.compile(
        "(\\d+|[一二三四五六七八九十百千半]+)[天日月年]后"
    );

    /**
     * 从正文中提取位置变化
     * @return 位置名称，未匹配返回null
     */
    public String extractLocation(String content) {
        if (content == null || content.isEmpty()) return null;
        Matcher matcher = LOCATION_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从正文中提取实力变化
     * @return 实力等级描述，未匹配返回null
     */
    public String extractPowerLevel(String content) {
        if (content == null || content.isEmpty()) return null;
        Matcher matcher = POWER_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从正文中提取获取的物品
     * @return 物品名称列表
     */
    public List<String> extractItems(String content) {
        List<String> items = new ArrayList<>();
        if (content == null || content.isEmpty()) return items;
        Matcher matcher = ITEM_PATTERN.matcher(content);
        while (matcher.find()) {
            String item = matcher.group(1);
            if (item != null && !item.isBlank() && item.length() >= 2) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 从正文中提取对话事件
     * @return 对话事件列表
     */
    public List<DialogueEvent> extractDialogues(String content) {
        List<DialogueEvent> events = new ArrayList<>();
        if (content == null || content.isEmpty()) return events;
        Matcher matcher = DIALOGUE_PATTERN.matcher(content);
        while (matcher.find()) {
            String speaker = matcher.group(1);
            String dialogueContent = matcher.group(3);
            if (speaker == null || PRONOUNS.contains(speaker)) continue;
            if (dialogueContent == null || dialogueContent.isBlank()) continue;
            DialogueEvent event = new DialogueEvent();
            event.setSpeaker(speaker);
            event.setContent(dialogueContent.trim());
            events.add(event);
        }
        return events;
    }

    /**
     * 从正文中提取时间推进（天数）
     * @return 推进的天数，未匹配返回0
     */
    public int extractTimeAdvance(String content) {
        if (content == null || content.isEmpty()) return 0;
        Matcher matcher = TIME_ADVANCE_PATTERN.matcher(content);
        if (matcher.find()) {
            String numStr = matcher.group(1);
            try {
                return Integer.parseInt(numStr);
            } catch (NumberFormatException e) {
                return chineseToNumber(numStr);
            }
        }
        return 0;
    }

    /**
     * 中文数字转阿拉伯数字（简化实现）
     */
    private int chineseToNumber(String chinese) {
        if (chinese == null || chinese.isEmpty()) return 1;
        return switch (chinese) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            case "半" -> 15; // 半月 ≈ 15天
            default -> 1;
        };
    }

    /**
     * 对话事件数据类
     */
    @Data
    public static class DialogueEvent {
        private String speaker;
        private String content;
    }
}
