package com.novel.system.service.style;

import com.novel.system.dto.style.StyleMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文风文本分析器
 * 
 * 【核心作用】
 * 程序化提取文本的量化指标，零LLM成本
 * 
 * 【分析维度】
 * 1. 句长分布：平均句长、短句占比、最长句
 * 2. 段落分布：平均段落长度、短段落占比
 * 3. 对话统计：对话比例、平均对白长度
 * 4. 标点习惯：省略号、破折号、感叹号
 * 5. 感官词密度：视觉、听觉、触觉
 * 
 * @see StyleMetrics
 */
@Slf4j
@Service
public class StyleTextAnalyzer {
    
    // ========== 感官词库 ==========
    
    // 视觉词
    private static final String[] VISUAL_WORDS = {
        "看见", "看到", "颜色", "光", "暗", "亮", "红", "白", "黑", "影",
        "闪烁", "刺眼", "昏暗", "明亮", "漆黑", "光芒", "阳光", "月光",
        "目光", "眼睛", "眼神", "视线", "注视", "凝视", "盯着", "望着"
    };
    
    // 听觉词
    private static final String[] AUDITORY_WORDS = {
        "听到", "声音", "响", "叫", "喊", "低语", "寂静", "吵", "安静",
        "噪音", "脚步声", "说话声", "笑声", "哭声", "叹气", "呼吸", "心跳"
    };
    
    // 触觉词
    private static final String[] TACTILE_WORDS = {
        "摸", "触", "冷", "热", "硬", "软", "光滑", "粗糙",
        "温暖", "冰凉", "疼痛", "舒服", "难受", "紧", "松", "轻", "重"
    };
    
    // 中文标点
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[\u3002\uff01\uff1f\u2026]+");
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n+");
    private static final Pattern DIALOGUE_PATTERN = Pattern.compile("[\u300c\u300d\u300e\u300f\u201c\u201d''\"]");
    
    /**
     * 分析文本，提取量化指标
     * 
     * @param text 待分析的文本
     * @return 量化指标
     */
    public StyleMetrics analyze(String text) {
        if (text == null || text.isEmpty()) {
            return StyleMetrics.builder().build();
        }
        
        // 清理文本
        String cleanText = text.trim();
        
        // 分析各项指标
        double avgSentenceLength = calcAvgSentenceLength(cleanText);
        double shortSentenceRatio = calcShortSentenceRatio(cleanText);
        int maxSentenceLength = calcMaxSentenceLength(cleanText);
        
        double avgParagraphLength = calcAvgParagraphLength(cleanText);
        double shortParagraphRatio = calcShortParagraphRatio(cleanText);
        
        double dialogueRatio = calcDialogueRatio(cleanText);
        double avgDialogueLength = calcAvgDialogueLength(cleanText);
        
        int ellipsisCount = countOccurrences(cleanText, "\u2026");
        int emDashCount = countOccurrences(cleanText, "\u2014\u2014");
        int exclamationCount = countOccurrences(cleanText, "\uff01");
        int questionMarkCount = countOccurrences(cleanText, "\uff1f");
        
        double visualDensity = calcSenseDensity(cleanText, VISUAL_WORDS);
        double auditoryDensity = calcSenseDensity(cleanText, AUDITORY_WORDS);
        double tactileDensity = calcSenseDensity(cleanText, TACTILE_WORDS);
        
        int totalWords = cleanText.length();
        int totalSentences = countSentences(cleanText);
        int totalParagraphs = countParagraphs(cleanText);
        int dialogueParagraphs = countDialogueParagraphs(cleanText);
        
        List<String> distinctiveWords = extractDistinctiveWords(cleanText);
        
        return StyleMetrics.builder()
                .avgSentenceLength(avgSentenceLength)
                .shortSentenceRatio(shortSentenceRatio)
                .maxSentenceLength(maxSentenceLength)
                .avgParagraphLength(avgParagraphLength)
                .shortParagraphRatio(shortParagraphRatio)
                .dialogueRatio(dialogueRatio)
                .avgDialogueLength(avgDialogueLength)
                .ellipsisCount(ellipsisCount)
                .emDashCount(emDashCount)
                .exclamationCount(exclamationCount)
                .questionMarkCount(questionMarkCount)
                .visualDensity(visualDensity)
                .auditoryDensity(auditoryDensity)
                .tactileDensity(tactileDensity)
                .distinctiveWords(distinctiveWords)
                .totalWords(totalWords)
                .totalSentences(totalSentences)
                .totalParagraphs(totalParagraphs)
                .dialogueParagraphs(dialogueParagraphs)
                .build();
    }
    
    /**
     * 计算平均句长
     */
    private double calcAvgSentenceLength(String text) {
        String[] sentences = splitSentences(text);
        if (sentences.length == 0) {
            return 0;
        }
        int totalLength = Arrays.stream(sentences).mapToInt(String::length).sum();
        return (double) totalLength / sentences.length;
    }
    
    /**
     * 计算短句占比（<15字）
     */
    private double calcShortSentenceRatio(String text) {
        String[] sentences = splitSentences(text);
        if (sentences.length == 0) {
            return 0;
        }
        long shortCount = Arrays.stream(sentences).filter(s -> s.length() < 15).count();
        return (double) shortCount / sentences.length;
    }
    
    /**
     * 计算最长句长度
     */
    private int calcMaxSentenceLength(String text) {
        String[] sentences = splitSentences(text);
        return Arrays.stream(sentences).mapToInt(String::length).max().orElse(0);
    }
    
    /**
     * 计算平均段落长度
     */
    private double calcAvgParagraphLength(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length == 0) {
            return 0;
        }
        int totalLength = Arrays.stream(paragraphs).mapToInt(String::length).sum();
        return (double) totalLength / paragraphs.length;
    }
    
    /**
     * 计算短段落占比（<60字）
     */
    private double calcShortParagraphRatio(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length == 0) {
            return 0;
        }
        long shortCount = Arrays.stream(paragraphs).filter(p -> p.length() < 60).count();
        return (double) shortCount / paragraphs.length;
    }
    
    /**
     * 计算对话比例
     */
    private double calcDialogueRatio(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length == 0) {
            return 0;
        }
        long dialogueCount = Arrays.stream(paragraphs)
                .filter(this::isDialogueParagraph)
                .count();
        return (double) dialogueCount / paragraphs.length;
    }
    
    /**
     * 计算平均对白长度
     */
    private double calcAvgDialogueLength(String text) {
        List<String> dialogues = extractDialogues(text);
        if (dialogues.isEmpty()) {
            return 0;
        }
        int totalLength = dialogues.stream().mapToInt(String::length).sum();
        return (double) totalLength / dialogues.size();
    }
    
    /**
     * 计算感官词密度（每千字）
     */
    private double calcSenseDensity(String text, String[] words) {
        int count = 0;
        for (String word : words) {
            count += countOccurrences(text, word);
        }
        return (double) count / text.length() * 1000;
    }
    
    /**
     * 提取高频特色词
     */
    private List<String> extractDistinctiveWords(String text) {
        // 简单实现：提取2-4字的高频词
        Map<String, Integer> wordCount = new HashMap<>();
        
        // 提取2字词
        for (int i = 0; i < text.length() - 1; i++) {
            String word = text.substring(i, i + 2);
            if (isChineseWord(word)) {
                wordCount.merge(word, 1, Integer::sum);
            }
        }
        
        // 提取3字词
        for (int i = 0; i < text.length() - 2; i++) {
            String word = text.substring(i, i + 3);
            if (isChineseWord(word)) {
                wordCount.merge(word, 1, Integer::sum);
            }
        }
        
        // 过滤常见词，返回高频词
        Set<String> commonWords = Set.of("一个", "没有", "什么", "怎么", "这个", "那个", "可以", "因为", "所以", "但是");
        return wordCount.entrySet().stream()
                .filter(e -> e.getValue() >= 3 && !commonWords.contains(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 分割句子
     */
    private String[] splitSentences(String text) {
        return text.split("[\u3002\uff01\uff1f\u2026]+");
    }
    
    /**
     * 分割段落
     */
    private String[] splitParagraphs(String text) {
        return text.split("\\n+");
    }
    
    /**
     * 计算句子数量
     */
    private int countSentences(String text) {
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return Math.max(count, 1);
    }
    
    /**
     * 计算段落数量
     */
    private int countParagraphs(String text) {
        String[] paragraphs = splitParagraphs(text);
        return paragraphs.length;
    }
    
    /**
     * 计算包含对话的段落数
     */
    private int countDialogueParagraphs(String text) {
        String[] paragraphs = splitParagraphs(text);
        return (int) Arrays.stream(paragraphs).filter(this::isDialogueParagraph).count();
    }
    
    /**
     * 判断是否为对话段落
     */
    private boolean isDialogueParagraph(String paragraph) {
        return paragraph.contains("\u201c") || paragraph.contains("\u201d") || 
               paragraph.contains("\u300c") || paragraph.contains("\u300d") ||
               paragraph.contains("\"");
    }
    
    /**
     * 提取对话内容
     */
    private List<String> extractDialogues(String text) {
        List<String> dialogues = new ArrayList<>();
        Pattern pattern = Pattern.compile("[\u300c\u300e\"]([^\u300d\u300f\"]+)[\u300d\u300f\"]");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            dialogues.add(matcher.group(1));
        }
        return dialogues;
    }
    
    /**
     * 统计字符串出现次数
     */
    private int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }
    
    /**
     * 判断是否为中文词
     */
    private boolean isChineseWord(String word) {
        for (char c : word.toCharArray()) {
            if (c < '\u4e00' || c > '\u9fff') {
                return false;
            }
        }
        return true;
    }
}
