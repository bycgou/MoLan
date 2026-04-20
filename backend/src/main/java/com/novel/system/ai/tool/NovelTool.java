package com.novel.system.ai.tool;

import dev.langchain4j.agent.tool.Tool;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mysql.WorldClock;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.mapper.WorldClockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 小说工具 - Agent可调用的小说和章节查询工具
 * 
 * 【核心作用】
 * 这是AI Agent的"工具箱"，提供查询小说数据的各种方法。
 * Agent在生成内容时，可以自动调用这些工具获取所需信息。
 * 
 * 【工具使用原理】
 * 1. LangChain4j通过@Tool注解识别可调用的方法
 * 2. Agent根据上下文判断需要调用哪个工具
 * 3. 框架自动执行工具方法，将结果返回给Agent
 * 4. Agent根据工具返回的信息生成更准确的内容
 * 
 * 【工具列表】
 * 1. getNovelInfo - 查询小说基本信息
 * 2. getChapterSummary - 查询章节摘要
 * 3. getLastChapterFingerprint - 查询上一章摘要
 * 4. getArcInfo - 查询卷信息
 * 5. getWorldClock - 查询世界时间线
 * 
 * 【使用示例】
 * <pre>
 * // 在OutlineAgent的系统指令中：
 * "使用工具查询人物、伏笔等信息辅助生成"
 * 
 * // Agent会自动判断需要调用哪个工具
 * // 例如：需要了解上一章内容时，会自动调用getLastChapterFingerprint
 * </pre>
 * 
 * @author AI Assistant
 * @since 1.0.0
 * @see CharacterTool 人物工具
 * @see ClueTool 伏笔工具
 * @see NovelToolProvider 工具提供器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NovelTool {
    
    /**
     * 小说基础信息Mapper
     * 用于查询小说基本信息
     */
    private final NovelBaseMapper novelBaseMapper;
    
    /**
     * 章节Mapper
     * 用于查询章节信息
     */
    private final NovelChapterMapper novelChapterMapper;
    
    /**
     * 卷/弧线Mapper
     * 用于查询卷信息
     */
    private final NovelArcMapper novelArcMapper;
    
    /**
     * 世界时钟Mapper
     * 用于查询世界时间线
     */
    private final WorldClockMapper worldClockMapper;
    
    /**
     * 查询小说基本信息
     * 
     * 【使用场景】
     * Agent在生成章纲或正文时，需要了解小说的整体情况：
     * - 小说名称和题材
     * - 总字数和章节数
     * - 小说总纲（最重要的参考信息）
     * 
     * 【返回内容】
     * - 小说名：xxx
     * - 题材：玄幻/言情/悬疑等
     * - 字数：xxx
     * - 章节数：xxx
     * - 总纲：xxx（如果超过2000字会截断）
     * 
     * @param novelId 小说ID
     * @return 格式化的小说信息字符串
     */
    @Tool("查询小说基本信息，包括名称、题材、总纲、世界观设定等")
    public String getNovelInfo(String novelId) {
        log.info("[Tool] 查询小说信息: novelId={}", novelId);
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel == null) {
            return "未找到该小说";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("小说名: ").append(novel.getNovelName()).append("\n");
        sb.append("题材: ").append(novel.getGenre()).append("\n");
        sb.append("字数: ").append(novel.getWordCount()).append("\n");
        sb.append("章节数: ").append(novel.getChapterCount()).append("\n");
        if (novel.getTotalOutline() != null) {
            String outline = novel.getTotalOutline();
            if (outline.length() > 2000) outline = outline.substring(0, 2000) + "...(已截断)";
            sb.append("总纲: ").append(outline).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 查询指定章节的内容摘要
     * 
     * 【使用场景】
     * Agent在生成新章节时，需要了解之前章节的内容：
     * - 查询特定章节的剧情
     * - 了解章节的节奏类型
     * - 获取章节的剧情指纹
     * 
     * 【注意】
     * 只返回摘要信息，不返回完整正文，避免上下文过长
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @return 格式化的章节摘要字符串
     */
    @Tool("查询指定章节的内容摘要，包括标题、字数、节奏标签。不返回完整正文，只返回摘要信息。")
    public String getChapterSummary(String novelId, int chapterNum) {
        log.info("[Tool] 查询章节摘要: novelId={}, chapter={}", novelId, chapterNum);
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter == null) {
            return "未找到第" + chapterNum + "章";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("第").append(chapter.getChapterNum()).append("章: ").append(chapter.getChapterTitle()).append("\n");
        sb.append("字数: ").append(chapter.getWordCount()).append("\n");
        if (chapter.getRhythmTag() != null) sb.append("节奏类型: ").append(chapter.getRhythmTag()).append("\n");
        if (chapter.getPlotFingerprint() != null) {
            sb.append("剧情摘要: ").append(chapter.getPlotFingerprint()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 查询上一章的剧情摘要
     * 
     * 【使用场景】
     * Agent在生成新章节时，必须了解上一章的内容，确保剧情连贯：
     * - 保持人物状态一致
     * - 延续剧情发展
     * - 回收上一章埋下的伏笔
     * 
     * 【特殊处理】
     * - 如果是第一章，返回"无（首章）"
     * - 如果上一章不存在，返回"无"
     * 
     * @param novelId 小说ID
     * @param currentChapterNum 当前章节号
     * @return 上一章的剧情指纹，如果没有则返回"无"
     */
    @Tool("查询上一章的剧情摘要，用于保持上下文连贯性")
    public String getLastChapterFingerprint(String novelId, int currentChapterNum) {
        log.info("[Tool] 查询上一章摘要: novelId={}, currentChapter={}", novelId, currentChapterNum);
        if (currentChapterNum <= 1) {
            return "无（首章）";
        }
        NovelChapter prev = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, currentChapterNum - 1);
        if (prev == null) {
            return "无";
        }
        return prev.getPlotFingerprint() != null ? prev.getPlotFingerprint() : "无";
    }
    
    /**
     * 查询当前章节所属卷（弧线）的名称和卷纲
     * 
     * 【使用场景】
     * Agent在生成章纲时，需要了解当前章节属于哪一卷：
     * - 卷名称（如"第一卷：初入江湖"）
     * - 卷章节范围（如第1-50章）
     * - 卷纲（卷的整体剧情规划）
     * 
     * 【卷的作用】
     * 卷是小说的中层结构，介于总纲和章纲之间：
     * - 总纲：整本书的剧情规划
     * - 卷纲：某一卷的剧情规划
     * - 章纲：某一章的剧情规划
     * 
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @return 格式化的卷信息字符串
     */
    @Tool("查询当前章节所属卷（弧线）的名称和卷纲")
    public String getArcInfo(String novelId, int chapterNum) {
        log.info("[Tool] 查询卷信息: novelId={}, chapter={}", novelId, chapterNum);
        NovelArc arc = novelArcMapper.findByChapterNum(novelId, chapterNum);
        if (arc == null) {
            return "未配置卷信息";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("卷名: ").append(arc.getArcName()).append("\n");
        sb.append("章节范围: 第").append(arc.getStartChapter()).append("-");
        sb.append(arc.getEndChapter() != null ? arc.getEndChapter() : "?").append("章\n");
        if (arc.getArcOutline() != null) {
            String outline = arc.getArcOutline();
            if (outline.length() > 1000) outline = outline.substring(0, 1000) + "...";
            sb.append("卷纲: ").append(outline).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 查询小说的世界时间线状态
     * 
     * 【使用场景】
     * Agent在生成内容时，需要了解当前的世界状态：
     * - 当前时间（如"午时三刻"）
     * - 当前日期（如"天元三年五月初五"）
     * - 当前季节（如"春"）
     * - 当前天气（如"晴朗"）
     * 
     * 【世界时间线的作用】
     * - 保持时间逻辑一致
     * - 避免出现"昨天还在冬天，今天就到夏天"的错误
     * - 为场景描写提供背景信息
     * 
     * @param novelId 小说ID
     * @return 格式化的时间线信息字符串
     */
    @Tool("查询小说的世界时间线状态，包括时间、季节、天气等")
    public String getWorldClock(String novelId) {
        log.info("[Tool] 查询世界时间线: novelId={}", novelId);
        WorldClock clock = worldClockMapper.selectByNovelId(novelId);
        if (clock == null) {
            return "未设定世界时间线";
        }
        
        StringBuilder sb = new StringBuilder();
        if (clock.getCurrentTime() != null) sb.append("时间: ").append(clock.getCurrentTime()).append("\n");
        if (clock.getCurrentDate() != null) sb.append("日期: ").append(clock.getCurrentDate()).append("\n");
        if (clock.getSeason() != null) sb.append("季节: ").append(clock.getSeason()).append("\n");
        if (clock.getWeather() != null) sb.append("天气: ").append(clock.getWeather()).append("\n");
        return sb.length() > 0 ? sb.toString() : "未设定世界时间线";
    }
}
