package com.novel.system.service.novel;

import com.novel.system.service.novel.RhythmService;
import com.novel.system.ai.chain.HookDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.entity.mysql.NovelChapter;

/**
 * 节奏系统服务实现 - 包括追读力系统
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RhythmServiceImpl implements RhythmService {
    
    private final HookDetectionService hookDetectionService;
    private final NovelChapterMapper novelChapterMapper;
    
    private static final String[] COOL_POINT_PATTERNS = {
        "击败.*对手", "战胜.*敌人", "获得.*宝物", "突破.*境界",
        "觉醒.*能力", "秒杀.*敌人", "碾压.*对手", "打脸.*反派",
        "逆袭.*成功", "王者.*归来", "至尊.*实力", "震惊.*全场"
    };
    
    private static final String[] DEBT_PATTERNS = {
        "谁.*", "为什么.*", "到底.*", "真相.*", "秘密.*",
        "背后.*", "阴谋.*", "隐藏.*", "未知.*", "神秘.*"
    };
    
    private static final String[] FULFILLMENT_PATTERNS = {
        "终于.*", "成功.*", "实现.*", "达成.*", "完成.*",
        "获得.*", "得到.*", "解决.*", "克服.*", "战胜.*"
    };
    
    @Override
    public ReadPullAnalysis analyzeReadPull(String chapterContent, String novelId, int chapterNum) {
        log.info("开始分析章节追读力，小说ID: {}, 章节: {}", novelId, chapterNum);
        
        ReadPullAnalysis analysis = new ReadPullAnalysis();
        
        if (chapterContent == null || chapterContent.isEmpty()) {
            analysis.setHookScore(0);
            analysis.setCoolPointScore(0);
            analysis.setMicroFulfillment(0);
            analysis.setDebtScore(0);
            analysis.setCoolPoints(new ArrayList<>());
            analysis.setHooks(new ArrayList<>());
            analysis.setDebts(new ArrayList<>());
            analysis.setFulfillments(new ArrayList<>());
            analysis.setSuggestion("章节内容为空");
            analysis.calculateScore();
            return analysis;
        }
        
        HookDetectionService.HookAnalysis hookAnalysis = hookDetectionService.analyzeHook(chapterContent);
        analysis.setHookScore(hookAnalysis.getEndingHook());
        
        List<String> coolPoints = analyzeCoolPoints(chapterContent, chapterNum);
        analysis.setCoolPoints(coolPoints);
        analysis.setCoolPointScore(Math.min(10, Math.max(0, coolPoints.size() * 2)));
        
        List<MicroFulfillment> fulfillments = analyzeMicroFulfillments(chapterContent, chapterNum);
        analysis.setFulfillments(fulfillments);
        int fulfillmentScore = fulfillments.stream().mapToInt(MicroFulfillment::getSatisfaction).sum() / 
                              (fulfillments.isEmpty() ? 1 : fulfillments.size());
        analysis.setMicroFulfillment(Math.min(10, fulfillmentScore));
        
        List<Debt> debts = analyzeDebts(chapterContent, chapterNum);
        analysis.setDebts(debts);
        analysis.setDebtScore(Math.min(10, Math.max(0, debts.size() * 2)));
        
        List<String> hooks = identifyHooks(chapterContent);
        analysis.setHooks(hooks);
        
        analysis.calculateScore();
        analysis.setSuggestion(generateSuggestion(analysis));
        
        log.info("追读力分析完成，总分: {}, 等级: {}", analysis.getTotalScore(), analysis.getReadPullLevel());
        
        return analysis;
    }
    
    @Override
    public ReadPullTrend calculateReadPullTrend(String novelId) {
        ReadPullTrend trend = new ReadPullTrend();
        List<NovelChapter> chapters = novelChapterMapper.selectByNovelId(novelId);
        List<Integer> nums = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        List<String> levels = new ArrayList<>();
        int total = 0;
        for (NovelChapter ch : chapters) {
            int score = 0;
            if (ch.getWordCount() != null && ch.getWordCount() > 0) {
                score = ch.getWordCount() / 20;
            } else if (ch.getContent() != null) {
                score = ch.getContent().length() / 50;
            } else {
                score = 20;
            }
            score = Math.max(5, Math.min(60, score));
            nums.add(ch.getChapterNum());
            scores.add(score);
            levels.add(score >= 40 ? "高" : score >= 25 ? "中" : "低");
            total += score;
        }
        trend.setChapterNumbers(nums);
        trend.setReadPullScores(scores);
        trend.setReadPullLevels(levels);
        trend.setAverageScore(chapters.isEmpty() ? 0 : ((double) total / chapters.size()));
        trend.setTrendAnalysis(chapters.isEmpty() ? "无数据" : "基于字数与内容密度的趋势分析");
        return trend;
    }
    
    @Override
    public String generateOptimizationSuggestion(String novelId) {
        return "建议在低谷章节增加爽点密度，保持每3-5章设置一个大的钩子，合理控制债务的产生和回收节奏。";
    }
    
    private List<String> analyzeCoolPoints(String content, int chapterNum) {
        List<String> coolPoints = new ArrayList<>();
        String[] coolKeywords = {"震惊", "碾压", "秒杀", "突破", "觉醒", "升级"};
        for (String keyword : coolKeywords) {
            if (content.contains(keyword)) {
                coolPoints.add("包含关键词: " + keyword);
            }
        }
        return coolPoints;
    }
    
    private List<MicroFulfillment> analyzeMicroFulfillments(String content, int chapterNum) {
        List<MicroFulfillment> fulfillments = new ArrayList<>();
        String[] fulfillKeywords = {"终于", "成功", "实现", "达成", "完成", "获得"};
        for (String keyword : fulfillKeywords) {
            if (content.contains(keyword)) {
                MicroFulfillment fulfillment = new MicroFulfillment();
                fulfillment.setDescription("实现了目标: " + keyword);
                fulfillment.setChapterNum(chapterNum);
                fulfillment.setSatisfaction(8);
                fulfillments.add(fulfillment);
            }
        }
        return fulfillments;
    }
    
    private List<Debt> analyzeDebts(String content, int chapterNum) {
        List<Debt> debts = new ArrayList<>();
        String[] debtKeywords = {"谁", "为什么", "到底", "真相", "秘密", "背后", "阴谋"};
        for (String keyword : debtKeywords) {
            if (content.contains(keyword)) {
                Debt debt = new Debt();
                debt.setDescription("产生悬念: " + keyword);
                debt.setChapterNum(chapterNum);
                debt.setPriority(3);
                debt.setResolved(false);
                debts.add(debt);
            }
        }
        
        Pattern questionPattern = Pattern.compile(".*\\?.*");
        if (questionPattern.matcher(content).find()) {
            Debt debt = new Debt();
            debt.setDescription("提出疑问");
            debt.setChapterNum(chapterNum);
            debt.setPriority(2);
            debt.setResolved(false);
            debts.add(debt);
        }
        
        return debts;
    }
    
    private List<String> identifyHooks(String content) {
        List<String> hooks = new ArrayList<>();
        String ending = content.substring(Math.max(0, content.length() - 200));
        if (ending.contains("但是") || ending.contains("然而") || ending.contains("突然")) {
            hooks.add("结尾转折钩子");
        }
        if (ending.trim().endsWith("？")) {
            hooks.add("结尾疑问钩子");
        }
        if (ending.trim().endsWith("……")) {
            hooks.add("结尾留白钩子");
        }
        return hooks;
    }
    
    private String generateSuggestion(ReadPullAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        if (analysis.getHookScore() < 5) {
            sb.append("钩子强度不足，建议在章节结尾设置转折或悬念；");
        }
        if (analysis.getCoolPointScore() < 5) {
            sb.append("爽点不足，建议增加打脸、逆袭等情节；");
        }
        if (analysis.getMicroFulfillment() < 5) {
            sb.append("微兑现不足，建议适当满足读者期待；");
        }
        if (analysis.getDebtScore() < 5) {
            sb.append("债务设置不足，建议增加悬念和未解之谜；");
        }
        if (sb.length() == 0) {
            sb.append("追读力表现良好，继续保持！");
        }
        return sb.toString();
    }
}
