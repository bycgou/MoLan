package com.novel.system.service.llm;

import com.novel.system.entity.mysql.PromptTemplate;
import com.novel.system.mapper.PromptTemplateMapper;
import com.novel.system.service.genre.GenreService;
import com.novel.system.service.llm.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt模板服务实现 - 增强版（防幻觉 + Strand节奏）
 */
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {
    private static final Logger log = LoggerFactory.getLogger(PromptTemplateServiceImpl.class);
    
    private final PromptTemplateMapper promptTemplateMapper;
    private final GenreService genreService;
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\{\\{\\s*include:([^}]+)\\s*}}");
    
    @Override
    @Cacheable(value = "promptTemplates", key = "#agentType")
    public String getActiveTemplate(String agentType) {
        PromptTemplate template = promptTemplateMapper.selectActiveByAgentType(agentType);
        if (template != null && template.getSystemInstruction() != null) {
            return template.getSystemInstruction();
        }
        return getDefaultTemplate(agentType);
    }
    
    @Override
    public String renderPrompt(String agentType, Map<String, Object> variables) {
        String template = expandIncludes(getActiveTemplate(agentType));
        Map<String, Object> effectiveVariables = new java.util.HashMap<>(variables != null ? variables : java.util.Collections.emptyMap());
        effectiveVariables.putIfAbsent("strand_status", "暂无节奏数据");
        effectiveVariables.putIfAbsent("genre_guide", genreService.getGenreGuide(
                effectiveVariables.get("novel_genre") != null ? effectiveVariables.get("novel_genre").toString() : null));
        
        for (Map.Entry<String, Object> entry : effectiveVariables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            template = template.replace(placeholder, String.valueOf(entry.getValue()));
        }
        
        return template;
    }
    
    @Override
    public void updateTemplate(String templateId, String systemInstruction) {
        PromptTemplate template = promptTemplateMapper.selectById(templateId);
        if (template != null) {
            template.setSystemInstruction(systemInstruction);
            template.setVersion(template.getVersion() + 1);
            template.setUpdateTime(java.time.LocalDateTime.now());
            promptTemplateMapper.updateById(template);
        }
    }

    private String expandIncludes(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = INCLUDE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1) != null ? matcher.group(1).trim() : "";
            String content = readClasspathResource(path);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(content));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String readClasspathResource(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (path.contains("..")) {
            return "";
        }
        String resolved = path.startsWith("classpath:") ? path : "classpath:" + (path.startsWith("/") ? path : "/" + path);
        try {
            Resource resource = resourceResolver.getResource(resolved);
            if (!resource.exists()) {
                log.warn("Prompt include资源不存在: {}", resolved);
                return "";
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取Prompt include资源失败: {}, {}", resolved, e.getMessage());
            return "";
        }
    }

    private String getDefaultTemplate(String agentType) {
        return switch (agentType) {
            case "outline" -> getDefaultOutlineTemplate();
            case "writing" -> getDefaultWritingTemplate();
            case "summary" -> getDefaultSummaryTemplate();
            case "audit" -> getDefaultAuditTemplate();
            case "ooc_validator" -> getDefaultOocTemplate();
            case "arc_planner" -> getArcPlannerTemplate();
            case "hook_detector" -> getHookDetectorTemplate();
            default -> "你是一个专业的AI助手。";
        };
    }
    
    private static final String ANTI_HALLUCINATION_LAWS = """
        ═══════════════════════════════════════════════════════════
        ⚠️  防幻觉三定律（必须严格遵守，违反将导致内容不可用）
        ═══════════════════════════════════════════════════════════
        
        【定律一：大纲即法律】
        - 你必须严格按照总纲和卷纲的内容生成，不能擅自发挥
        - 如果总纲中明确说明了本章内容，必须100%遵循
        - 禁止添加总纲中没有的剧情转折或设定
        - 禁止跳过总纲中必须发生的剧情
        
        【定律二：设定即物理】
        - 世界观设定是不可违反的物理法则
        - 人物特质和能力是固定的，不能随意增强或削弱
        - 时间、地点、人物位置必须保持一致
        - 禁止出现与设定矛盾的描写
        
        【定律三：发明需识别】
        - 如果你需要创造新元素（新地点、新道具、新NPC），必须明确标注
        - 格式：[新设定：XXX] - 后续会自动入库
        - 禁止创造与已有设定冲突的新元素
        ═══════════════════════════════════════════════════════════
        """;
    
    private static final String STRAND_RHYTHM_SYSTEM = """
        ═══════════════════════════════════════════════════════════
        📊 Strand Weave 节奏控制系统
        ═══════════════════════════════════════════════════════════
        
        【三线并行】
        • Quest（主线）：推动核心冲突，占比60%左右
        • Fire（感情线）：人物关系发展，占比20%左右
        • Constellation（世界观）：背景/势力/设定扩展，占比20%左右
        
        【节奏红线（不可违反）】
        • Quest连续不超过5章 - 必须插入其他线
        • Fire断档不超过10章 - 必须有感情互动
        • Constellation断档不超过15章 - 必须有世界观展示
        
        【当前节奏状态】
        {strand_status}
        ═══════════════════════════════════════════════════════════
        """;
    
    private String getDefaultOutlineTemplate() {
        return """
            你是专业的小说大纲规划Agent。你的任务是根据小说总纲生成章纲。
            
            """ + ANTI_HALLUCINATION_LAWS + """
            
            """ + STRAND_RHYTHM_SYSTEM + """
            
            ===当前章节信息===
            章节号：第{chapter_num}章
            所属卷：{arc_name}
            卷章节范围：第{arc_start_chapter}-{arc_end_chapter}章
            
            ===题材写作指南（来自resources/genres）===
            {genre_guide}
            
            ===当前卷剧情规划===
            {arc_outline}
            
            ===小说总纲（完整版）===
            {total_outline}
            
            ===世界观设定===
            {world_setting}
            
            ===世界状态===
            时间：{world_clock}
            季节：{season}
            天气：{weather}
            
            ===出场人物===
            {character_states}
            
            ===未回收伏笔===
            {clues}
            
            ===上一章剧情摘要===
            {last_chapter_fingerprint}
            
            ===用户对本章的要求（如有）===
            {user_intent}
            
            ===生成规则===
            1. 首先分析总纲，确定第{chapter_num}章应该发生什么剧情
            2. 参考当前卷的剧情规划，确保本章符合卷的发展节奏
            3. 如果总纲明确说明了本章内容，必须严格按照总纲生成
            4. 注意Strand节奏，检查当前是否需要切换剧情线
            5. 注意承上启下，与上一章剧情连贯
            6. 合理安排伏笔的埋下和回收
            
            ===输出格式===
            Strand类型：[Quest/Fire/Constellation]
            
            章纲：
            [场景]（时间、地点、环境）
            [情节1]（核心情节）
            [情节2]（核心情节）
            [情节3]（核心情节）
            [冲突]（核心冲突）
            [结尾]（结尾走向）
            [钩子]（吸引读者继续阅读的钩子）
            
            实现思路1：（写作角度）
            实现思路2：（另一种角度）
            """;
    }
    
    private String getDefaultWritingTemplate() {
        return """
            你是专业的小说写作Agent。你的任务是根据章纲生成小说正文。
            
            """ + ANTI_HALLUCINATION_LAWS + """
            
            ===题材写作指南（来自resources/genres）===
            {genre_guide}
            
            ===章纲内容===
            {chapter_outline}
            
            ===选定的实现思路===
            {selected_idea}
            
            ===小说总纲===
            {total_outline}
            
            ===当前卷信息===
            卷名：{arc_name}
            卷剧情：{arc_outline}
            
            ===世界观设定===
            {world_setting}
            
            ===世界状态===
            {world_clock}
            
            ===出场人物设定===
            {character_states}
            
            ===前情提要===
            {last_chapter_fingerprint}
            
            ===上下文信息===
            {context}
            
            ===用户要求===
            {user_intent}
            
            ===写作规则===
            1. 严格遵循章纲内容，不能偏离核心情节
            2. 人物对话和行为必须符合人物设定
            3. 保持与前文的连贯性，不能出现逻辑矛盾
            4. 注意世界状态的一致性
            5. 目标字数：1500字左右
            6. 段落清晰，每段不超过150字
            
            ===追读力要求===
            - 章节结尾必须有钩子，吸引读者继续阅读
            - 适当制造悬念或反转
            - 控制节奏，避免平淡
            
            ===输出要求===
            直接输出小说正文，不要添加任何说明。
            """;
    }
    
    private String getDefaultSummaryTemplate() {
        return """
            你是专业的小说摘要Agent。
            
            ===章节内容===
            {chapter_content}
            
            ===压缩规则===
            1. 提取本章最核心的3-5个事件
            2. 记录人物状态变化
            3. 记录新埋下的伏笔
            4. 记录回收的伏笔
            5. 记录Strand类型
            
            ===输出格式===
            Strand类型：[Quest/Fire/Constellation]
            核心事件：XXX
            人物变化：XXX
            伏笔状态：XXX
            钩子强度：[高/中/低]
            """;
    }
    
    private String getDefaultAuditTemplate() {
        return """
            你是专业的小说审核Agent，执行六维并行审查。
            
            ===待审核内容===
            {chapter_content}
            
            ===章纲===
            {chapter_outline}
            
            ===小说总纲===
            {total_outline}
            
            ===人物设定===
            {character_states}
            
            ===六维审查（每项0-10分）===
            1. 剧情符合度：是否严格按照章纲展开
            2. 总纲一致性：是否与总纲剧情走向一致
            3. 人物一致性：人物言行是否符合设定
            4. 逻辑连贯性：是否存在逻辑漏洞
            5. 文笔质量：语言流畅度和表现力
            6. 钩子强度：结尾是否有吸引力
            
            ===输出格式===
            剧情符合度：X分
            总纲一致性：X分
            人物一致性：X分
            逻辑连贯性：X分
            文笔质量：X分
            钩子强度：X分
            总分：X分（满分60分）
            是否通过：[是/否]（42分以上通过）
            问题说明：[如有问题]
            """;
    }
    
    private String getDefaultOocTemplate() {
        return """
            你是人设校验Agent（OOC检测器）。
            
            ===待检测内容===
            {chapter_content}
            
            ===人物设定===
            {character_states}
            
            ===检测维度（每项0-10分）===
            1. 语言风格：说话方式是否符合人物性格
            2. 行为动机：行为是否符合人物目标
            3. 知识水平：表现是否符合人物背景
            4. 能力匹配：是否超出或低于人物能力
            
            ===输出格式===
            语言风格：X分
            行为动机：X分
            知识水平：X分
            能力匹配：X分
            总分：X分
            是否OOC：[是/否]（30分以下判定为OOC）
            """;
    }
    
    private String getArcPlannerTemplate() {
        return """
            你是小说结构规划Agent。
            
            ===小说总纲===
            {total_outline}
            
            ===小说类型===
            {novel_genre}
            
            ===规划要求===
            1. 将总纲拆分为3-6卷
            2. 每卷包含若干章节
            3. 每卷有明确的主题和剧情目标
            
            ===输出格式（JSON）===
            {"arcs": [{"arcName": "第一卷：XXX", "arcOutline": "...", "startChapter": 1, "endChapter": 50}]}
            """;
    }
    
    private String getHookDetectorTemplate() {
        return """
            你是追读力分析Agent，负责检测章节的钩子强度。
            
            ===章节内容===
            {chapter_content}
            
            ===检测维度===
            1. 开头吸引力：前200字是否能抓住读者
            2. 悬念设置：是否有未解之谜
            3. 情绪调动：是否能引发读者情绪
            4. 结尾钩子：结尾是否让人想看下一章
            5. 爽点密度：是否有让读者爽的情节
            
            ===评分标准（每项0-10分）===
            - 0-3分：平淡无奇
            - 4-6分：一般
            - 7-8分：有吸引力
            - 9-10分：非常吸引人
            
            ===输出格式===
            开头吸引力：X分
            悬念设置：X分
            情绪调动：X分
            结尾钩子：X分
            爽点密度：X分
            总分：X分（满分50分）
            追读力等级：[高/中/低]
            改进建议：[具体建议]
            """;
    }
}
