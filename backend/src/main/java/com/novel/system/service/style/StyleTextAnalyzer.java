package com.novel.system.service.style;

import com.novel.system.dto.style.StyleMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.novel.system.config.AntiAICliches;

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

        // ========== 反模式检测 ==========
        double sentenceDiversityIndex = calcSentenceDiversityIndex(cleanText);
        double directCutRatio = calcDirectCutRatio(cleanText);
        double transitionClicheRatio = calcTransitionClicheRatio(cleanText);
        double actionEndingRatio = calcActionEndingRatio(cleanText);
        double dialogueEndingRatio = calcDialogueEndingRatio(cleanText);
        double suspenseEndingRatio = calcSuspenseEndingRatio(cleanText);
        int maxConsecutiveDialogue = calcMaxConsecutiveDialogue(cleanText);
        double emptySceneRatio = calcEmptySceneRatio(cleanText);

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
                .sentenceDiversityIndex(sentenceDiversityIndex)
                .directCutRatio(directCutRatio)
                .transitionClicheRatio(transitionClicheRatio)
                .actionEndingRatio(actionEndingRatio)
                .dialogueEndingRatio(dialogueEndingRatio)
                .suspenseEndingRatio(suspenseEndingRatio)
                .maxConsecutiveDialogue(maxConsecutiveDialogue)
                .emptySceneRatio(emptySceneRatio)
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
    
    // ========== 反模式检测方法 ==========

    /**
     * 计算句式多样性指数（变异系数）
     * AI写作句长过于均匀，多样性指数低；真人写作长短句交错，多样性高
     * @return 0-1，越高越多样
     */
    private double calcSentenceDiversityIndex(String text) {
        String[] sentences = splitSentences(text);
        if (sentences.length < 3) {
            return 1.0;
        }
        double[] lengths = Arrays.stream(sentences).mapToDouble(String::length).toArray();
        double mean = Arrays.stream(lengths).average().orElse(0);
        if (mean == 0) return 0;
        double variance = Arrays.stream(lengths).map(l -> Math.pow(l - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        // 变异系数 = 标准差/均值，cap到1.0
        return Math.min(stdDev / mean, 1.0);
    }

    /**
     * 计算直接切场景比例（段落首句无过渡词）
     * 真人写作倾向直接切场景，AI倾向用过渡句连接
     */
    private double calcDirectCutRatio(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length < 2) {
            return 1.0;
        }
        int transitionCount = 0;
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            // 检查是否以过渡词开头
            for (String t : AntiAICliches.TRANSITION_CLICHES) {
                if (trimmed.startsWith(t) || trimmed.contains(t)) {
                    transitionCount++;
                    break;
                }
            }
        }
        return 1.0 - (double) transitionCount / paragraphs.length;
    }

    /**
     * 计算万能过渡句比例
     */
    private double calcTransitionClicheRatio(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length == 0) return 0;
        int count = 0;
        for (String p : paragraphs) {
            for (String t : AntiAICliches.TRANSITION_CLICHES) {
                if (p.contains(t)) {
                    count++;
                    break;
                }
            }
        }
        return (double) count / paragraphs.length;
    }

    /**
     * 计算章尾模式分布：动作结尾比例
     */
    private double calcActionEndingRatio(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length < 3) return 0;
        // 取最后3个非空段落
        List<String> tailParagraphs = new ArrayList<>();
        for (int i = paragraphs.length - 1; i >= 0 && tailParagraphs.size() < 3; i--) {
            if (!paragraphs[i].trim().isEmpty()) {
                tailParagraphs.add(paragraphs[i].trim());
            }
        }
        if (tailParagraphs.isEmpty()) return 0;
        String ending = tailParagraphs.get(0);
        // 动作结尾：以动词或动作描写结尾，且不是对话
        boolean isDialogue = ending.contains("“") || ending.contains("”");
        boolean endsWithVerb = ending.matches(".*[了着过地]\\s*[。！]$")
                || ending.matches(".*走[了过去]。$")
                || ending.matches(".*跑[了过去]。$")
                || ending.matches(".*转身.*[。！]$")
                || ending.matches(".*站[了起].*[。！]$");
        return (!isDialogue && endsWithVerb) ? 1.0 : 0.0;
    }

    /**
     * 计算章尾模式：对话结尾比例
     */
    private double calcDialogueEndingRatio(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length < 3) return 0;
        for (int i = paragraphs.length - 1; i >= 0; i--) {
            if (!paragraphs[i].trim().isEmpty()) {
                return isDialogueParagraph(paragraphs[i].trim()) ? 1.0 : 0.0;
            }
        }
        return 0;
    }

    /**
     * 计算章尾模式：悬念结尾比例（省略号、问号结尾）
     */
    private double calcSuspenseEndingRatio(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (lastChar == '…' || lastChar == '?' || lastChar == '？') return 1.0;
        // 检查最后5个字符是否含省略号
        String tail = trimmed.substring(Math.max(0, trimmed.length() - 10));
        return tail.contains("…") ? 1.0 : 0.0;
    }

    /**
     * 计算最长连续对话轮数（无动作/描写插入）
     * AI倾向于连续写大段对话不插入动作描写
     */
    private int calcMaxConsecutiveDialogue(String text) {
        String[] paragraphs = splitParagraphs(text);
        int maxConsecutive = 0;
        int currentConsecutive = 0;
        for (String p : paragraphs) {
            if (isDialogueParagraph(p.trim())) {
                currentConsecutive++;
                maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
            } else {
                currentConsecutive = 0;
            }
        }
        return maxConsecutive;
    }

    /**
     * 计算新场景感官细节覆盖率（反向指标：空场景比例）
     * 检测包含场景切换词的段落，看是否有感官细节
     */
    private double calcEmptySceneRatio(String text) {
        String[] paragraphs = splitParagraphs(text);
        if (paragraphs.length < 3) return 0;

        String[] sceneMarkers = {"走进", "来到", "进入", "推开", "跨进", "走出", "站在", "坐到", "回到"};
        int sceneCount = 0;
        int emptyCount = 0;

        for (String p : paragraphs) {
            String trimmed = p.trim();
            boolean isSceneStart = false;
            for (String marker : sceneMarkers) {
                if (trimmed.contains(marker)) {
                    isSceneStart = true;
                    break;
                }
            }
            if (isSceneStart) {
                sceneCount++;
                // 检查该段落是否包含感官细节
                boolean hasSensoryDetail = false;
                for (String w : VISUAL_WORDS) {
                    if (trimmed.contains(w)) { hasSensoryDetail = true; break; }
                }
                if (!hasSensoryDetail) {
                    for (String w : AUDITORY_WORDS) {
                        if (trimmed.contains(w)) { hasSensoryDetail = true; break; }
                    }
                }
                if (!hasSensoryDetail) {
                    for (String w : TACTILE_WORDS) {
                        if (trimmed.contains(w)) { hasSensoryDetail = true; break; }
                    }
                }
                if (!hasSensoryDetail) emptyCount++;
            }
        }
        return sceneCount > 0 ? (double) emptyCount / sceneCount : 0;
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
