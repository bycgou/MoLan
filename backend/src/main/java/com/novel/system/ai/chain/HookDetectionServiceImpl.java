package com.novel.system.ai.chain;

import com.novel.system.ai.chain.HookDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

/**
 * 钩子强度检测服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HookDetectionServiceImpl implements HookDetectionService {
    
    // 爽点关键词
    private static final String[] COOL_POINT_KEYWORDS = {
        "震惊", "不可思议", "不可能", "天才", "碾压", "秒杀", "逆天", "突破",
        "觉醒", "进化", "升级", "获得", "击败", "胜利", "王者", "至尊"
    };
    
    // 悬念关键词
    private static final String[] SUSPENSE_KEYWORDS = {
        "但是", "然而", "突然", "竟然", "居然", "难道", "难道是",
        "真相", "秘密", "阴谋", "隐藏", "背后", "谜团"
    };
    
    // 情绪关键词
    private static final String[] EMOTION_KEYWORDS = {
        "愤怒", "悲伤", "绝望", "希望", "感动", "震撼", "恐惧",
        "喜悦", "激动", "心痛", "欣慰", "不甘", "释然"
    };
    
    // 钩子结尾模式
    private static final Pattern HOOK_ENDING_PATTERN = Pattern.compile(
        ".*(?:但是|然而|突然|就在这时|下一刻|紧接着|与此同时|震惊的是|令所有人没想到的是|谁也没想到).*[。！？]$",
        Pattern.DOTALL
    );
    
    @Override
    public HookAnalysis analyzeHook(String chapterContent) {
        log.info("开始分析章节钩子强度");
        
        HookAnalysis analysis = new HookAnalysis();
        
        if (chapterContent == null || chapterContent.isEmpty()) {
            analysis.setOpeningAttraction(0);
            analysis.setSuspenseSetup(0);
            analysis.setEmotionTrigger(0);
            analysis.setEndingHook(0);
            analysis.setCoolPointDensity(0);
            analysis.setSuggestion("章节内容为空");
            analysis.calculateScore();
            return analysis;
        }
        
        // 1. 分析开头吸引力（前200字）
        String opening = chapterContent.substring(0, Math.min(200, chapterContent.length()));
        analysis.setOpeningAttraction(analyzeOpening(opening));
        
        // 2. 分析悬念设置
        analysis.setSuspenseSetup(analyzeSuspense(chapterContent));
        
        // 3. 分析情绪调动
        analysis.setEmotionTrigger(analyzeEmotion(chapterContent));
        
        // 4. 分析结尾钩子（最后200字）
        String ending = chapterContent.substring(Math.max(0, chapterContent.length() - 200));
        analysis.setEndingHook(analyzeEnding(ending));
        
        // 5. 分析爽点密度
        analysis.setCoolPointDensity(analyzeCoolPoints(chapterContent));
        
        // 计算总分
        analysis.calculateScore();
        
        // 生成改进建议
        analysis.setSuggestion(generateSuggestion(analysis));
        
        log.info("钩子分析完成，总分: {}, 等级: {}", analysis.getTotalScore(), analysis.getPullLevel());
        
        return analysis;
    }
    
    /**
     * 分析开头吸引力
     */
    private int analyzeOpening(String opening) {
        int score = 5; // 基础分
        
        // 检查是否有动作/冲突开场
        if (opening.contains("战斗") || opening.contains("攻击") || opening.contains("危机")) {
            score += 2;
        }
        
        // 检查是否有悬念开场
        for (String keyword : SUSPENSE_KEYWORDS) {
            if (opening.contains(keyword)) {
                score += 1;
                break;
            }
        }
        
        // 检查是否有情绪开场
        for (String keyword : EMOTION_KEYWORDS) {
            if (opening.contains(keyword)) {
                score += 1;
                break;
            }
        }
        
        // 检查开场是否太慢（以描述开头）
        if (opening.startsWith("这是一个") || opening.startsWith("在那")) {
            score -= 1;
        }
        
        return Math.max(0, Math.min(10, score));
    }
    
    /**
     * 分析悬念设置
     */
    private int analyzeSuspense(String content) {
        int score = 3; // 基础分
        
        int suspenseCount = 0;
        for (String keyword : SUSPENSE_KEYWORDS) {
            int count = countOccurrences(content, keyword);
            suspenseCount += count;
        }
        
        // 根据悬念词数量评分
        if (suspenseCount >= 10) {
            score = 9;
        } else if (suspenseCount >= 7) {
            score = 7;
        } else if (suspenseCount >= 4) {
            score = 5;
        }
        
        return Math.min(10, score);
    }
    
    /**
     * 分析情绪调动
     */
    private int analyzeEmotion(String content) {
        int score = 3; // 基础分
        
        int emotionCount = 0;
        for (String keyword : EMOTION_KEYWORDS) {
            int count = countOccurrences(content, keyword);
            emotionCount += count;
        }
        
        // 根据情绪词数量评分
        if (emotionCount >= 8) {
            score = 9;
        } else if (emotionCount >= 5) {
            score = 7;
        } else if (emotionCount >= 3) {
            score = 5;
        }
        
        return Math.min(10, score);
    }
    
    /**
     * 分析结尾钩子
     */
    private int analyzeEnding(String ending) {
        int score = 3; // 基础分
        
        // 检查是否有钩子结尾模式
        if (HOOK_ENDING_PATTERN.matcher(ending).matches()) {
            score += 3;
        }
        
        // 检查是否有问号结尾（悬念）
        if (ending.trim().endsWith("？")) {
            score += 2;
        }
        
        // 检查是否有省略号结尾（留白）
        if (ending.trim().endsWith("……") || ending.trim().endsWith("...")) {
            score += 1;
        }
        
        // 检查是否有转折词
        for (String keyword : new String[]{"但是", "然而", "突然", "就在这时"}) {
            if (ending.contains(keyword)) {
                score += 2;
                break;
            }
        }
        
        return Math.max(0, Math.min(10, score));
    }
    
    /**
     * 分析爽点密度
     */
    private int analyzeCoolPoints(String content) {
        int score = 3; // 基础分
        
        int coolCount = 0;
        for (String keyword : COOL_POINT_KEYWORDS) {
            int count = countOccurrences(content, keyword);
            coolCount += count;
        }
        
        // 根据爽点词数量评分
        if (coolCount >= 6) {
            score = 9;
        } else if (coolCount >= 4) {
            score = 7;
        } else if (coolCount >= 2) {
            score = 5;
        }
        
        return Math.min(10, score);
    }
    
    /**
     * 生成改进建议
     */
    private String generateSuggestion(HookAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        
        if (analysis.getOpeningAttraction() < 5) {
            sb.append("开头吸引力不足，建议以动作、冲突或悬念开场；");
        }
        
        if (analysis.getSuspenseSetup() < 5) {
            sb.append("悬念设置不够，建议增加转折和未知元素；");
        }
        
        if (analysis.getEmotionTrigger() < 5) {
            sb.append("情绪调动不足，建议加强人物内心描写；");
        }
        
        if (analysis.getEndingHook() < 5) {
            sb.append("结尾钩子较弱，建议用转折或悬念结尾；");
        }
        
        if (analysis.getCoolPointDensity() < 5) {
            sb.append("爽点密度较低，建议增加打脸、逆袭等爽点；");
        }
        
        if (sb.length() == 0) {
            sb.append("钩子强度良好，继续保持！");
        }
        
        return sb.toString();
    }
    
    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }
}
