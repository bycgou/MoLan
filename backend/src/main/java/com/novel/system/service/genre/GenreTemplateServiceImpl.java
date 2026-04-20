package com.novel.system.service.genre;

import com.novel.system.service.genre.GenreTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 题材模板服务实现 - 支持动态加载 Markdown 文件
 */
@Slf4j
@Service
public class GenreTemplateServiceImpl implements GenreTemplateService {
    
    private final Map<String, GenreTemplate> templates = new HashMap<>();
    
    public GenreTemplateServiceImpl() {
        initTemplates();
        loadMarkdownTemplates();
    }
    
    private void loadMarkdownTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:genres/**/*.md");
            log.info("从资源目录扫描到 {} 个题材配置文件", resources.length);
            for (Resource resource : resources) {
                // 暂不解析复杂的Markdown为结构化数据，仅作为日志记录
                // 未来可结合 LlmConfig 读取 Markdown 动态注入 Prompt
                String filename = resource.getFilename();
                log.debug("加载了题材文件: {}", filename);
            }
        } catch (Exception e) {
            log.error("加载题材Markdown配置文件失败", e);
        }
    }
    
    private void initTemplates() {
        // 玄幻
        registerGenre("xuanhuan", "玄幻", "男频", 
            "升级打怪、争霸天下、秘境探险",
            Arrays.asList(
                "废柴逆袭：主角从底层崛起",
                "退婚流：被退婚后逆天改命",
                "系统流：获得金手指系统",
                "重生流：重生后改变命运",
                "宗门争霸：从底层弟子到宗主"
            ),
            Arrays.asList(
                "避免主角过于被动",
                "金手指不能太逆天",
                "升级要有代价",
                "配角不能太弱智"
            ),
            Map.of(
                "主角", "性格坚韧、有成长弧线",
                "女主", "独立强大、不拖后腿",
                "反派", "有明确动机、不是纯恶",
                "导师", "神秘背景、适时出现"
            ),
            0.6, 0.15, 0.25, 5, 12, 15
        );
        
        // 都市
        registerGenre("dushi", "都市", "男频",
            "现实背景、职场逆袭、商业争霸",
            Arrays.asList(
                "职场逆袭：从小职员到CEO",
                "重生都市：重生后利用信息差",
                "透视异能：获得特殊能力",
                "神医流：医术救人",
                "保镖流：保护重要人物"
            ),
            Arrays.asList(
                "避免过于脱离现实",
                "职场描写要专业",
                "感情线要有深度",
                "金手指要有限制"
            ),
            Map.of(
                "主角", "低调务实、能力出众",
                "女主", "职场精英或青梅竹马",
                "对手", "商业对手或职场敌人",
                "兄弟", "患难与共的伙伴"
            ),
            0.55, 0.3, 0.15, 4, 8, 18
        );
        
        // 仙侠
        registerGenre("xianxia", "仙侠", "男频",
            "修仙问道、飞升成仙、仙界争霸",
            Arrays.asList(
                "凡人修仙：从凡人到仙人",
                "重生修仙：带着记忆重修",
                "系统修仙：系统辅助修炼",
                "宗门修仙：从小宗门到仙门",
                "散修流：独自修炼成仙"
            ),
            Arrays.asList(
                "境界体系要清晰",
                "法宝功法要详细",
                "仙界设定要完整",
                "避免境界通胀"
            ),
            Map.of(
                "主角", "心性坚定、悟性极高",
                "道侣", "仙子或妖女",
                "宗门长辈", "护短或严厉",
                "仙界势力", "各大仙门"
            ),
            0.65, 0.1, 0.25, 6, 15, 12
        );
        
        // 言情
        registerGenre("yanqing", "言情", "女频",
            "爱情主线、甜宠或虐恋、情感成长",
            Arrays.asList(
                "甜宠文：全程撒糖",
                "虐恋情深：先虐后甜",
                "破镜重圆：分手后复合",
                "假戏真做：假装情侣变真爱",
                "霸道总裁：强势男主爱上我"
            ),
            Arrays.asList(
                "感情发展要自然",
                "误会不能太狗血",
                "配角不能太恶毒",
                "结局要HE"
            ),
            Map.of(
                "女主", "独立或软萌、有成长",
                "男主", "深情专一、有魅力",
                "闺蜜", "助攻或情敌",
                "长辈", "开明或反对"
            ),
            0.3, 0.6, 0.1, 3, 5, 20
        );
        
        // 悬疑
        registerGenre("xuanyi", "悬疑", "通用",
            "推理破案、惊悚恐怖、反转不断",
            Arrays.asList(
                "本格推理：逻辑推理破案",
                "社会派：关注社会问题",
                "灵异怪谈：超自然事件",
                "密室逃脱：解谜逃生",
                "心理悬疑：心理博弈"
            ),
            Arrays.asList(
                "伏笔要埋好",
                "凶手不能太明显",
                "反转要合理",
                "结局要圆满"
            ),
            Map.of(
                "侦探", "聪明冷静、观察力强",
                "助手", "衬托主角、提供线索",
                "凶手", "有作案动机",
                "受害者", "有故事背景"
            ),
            0.7, 0.1, 0.2, 7, 20, 10
        );
        
        // 科幻
        registerGenre("kehuan", "科幻", "男频",
            "未来科技、星际文明、末日求生",
            Arrays.asList(
                "星际争霸：宇宙战争",
                "机甲流：机甲战斗",
                "末日求生：灾难后生存",
                "赛博朋克：高科技低生活",
                "时间循环：重复同一天"
            ),
            Arrays.asList(
                "科技设定要自洽",
                "世界观要完整",
                "避免科技万能",
                "人文思考要深入"
            ),
            Map.of(
                "主角", "理性或热血、有理想",
                "科学家", "技术担当",
                "军人", "战斗担当",
                "AI", "人工智能角色"
            ),
            0.6, 0.15, 0.25, 5, 15, 12
        );
        
        // 游戏
        registerGenre("youxi", "游戏", "男频",
            "游戏世界、电竞比赛、虚拟现实",
            Arrays.asList(
                "电竞流：职业选手",
                "游戏穿越：穿越到游戏世界",
                "网游流：游戏内发展",
                "直播流：游戏主播",
                "全息网游：沉浸式游戏"
            ),
            Arrays.asList(
                "游戏设定要详细",
                "战斗描写要精彩",
                "职业平衡要合理",
                "避免外挂太强"
            ),
            Map.of(
                "主角", "技术流或战术流",
                "队友", "各有特色",
                "对手", "强大但可战胜",
                "粉丝", "提供爽点"
            ),
            0.55, 0.2, 0.25, 4, 10, 15
        );
        
        // 历史
        registerGenre("lishi", "历史", "男频",
            "古代背景、争霸天下、权谋斗争",
            Arrays.asList(
                "争霸流：统一战争",
                "权谋流：朝堂争斗",
                "种田流：发展势力",
                "架空历史：平行世界",
                "穿越历史：改变历史"
            ),
            Arrays.asList(
                "历史背景要考究",
                "政治斗争要合理",
                "军事描写要专业",
                "避免现代思维"
            ),
            Map.of(
                "主角", "有谋略、有人格魅力",
                "谋士", "出谋划策",
                "武将", "征战四方",
                "对手", "枭雄或奸臣"
            ),
            0.65, 0.15, 0.2, 6, 15, 12
        );
        
        // 末世
        registerGenre("moshi", "末世", "男频",
            "丧尸危机、资源争夺、重建文明",
            Arrays.asList(
                "丧尸流：对抗丧尸",
                "异能流：获得异能",
                "系统流：末世系统",
                "基地流：建立基地",
                "进化流：生物进化"
            ),
            Arrays.asList(
                "生存压力要真实",
                "人性描写要深刻",
                "资源设定要合理",
                "避免主角太圣母"
            ),
            Map.of(
                "主角", "冷静理智、有领导力",
                "伙伴", "各有专长",
                "反派", "人性黑暗面",
                "幸存者", "不同立场"
            ),
            0.6, 0.2, 0.2, 5, 10, 15
        );
    }
    
    private void registerGenre(String code, String name, String category, String description,
                               List<String> tropes, List<String> pitfalls,
                               Map<String, String> characterArchetypes,
                               double questRatio, double fireRatio, double constellationRatio,
                               int questMaxConsecutive, int fireMaxGap, int constellationMaxGap) {
        GenreTemplate template = new GenreTemplate();
        template.setCode(code);
        template.setName(name);
        template.setDescription(description);
        template.setTropes(tropes);
        template.setPitfalls(pitfalls);
        template.setCharacterArchetypes(characterArchetypes);
        
        GenreRhythmConfig rhythmConfig = new GenreRhythmConfig();
        rhythmConfig.setQuestRatio(questRatio);
        rhythmConfig.setFireRatio(fireRatio);
        rhythmConfig.setConstellationRatio(constellationRatio);
        rhythmConfig.setQuestMaxConsecutive(questMaxConsecutive);
        rhythmConfig.setFireMaxGap(fireMaxGap);
        rhythmConfig.setConstellationMaxGap(constellationMaxGap);
        template.setRhythmConfig(rhythmConfig);
        
        templates.put(code, template);
    }
    
    @Override
    public List<GenreInfo> getGenreList() {
        List<GenreInfo> list = new ArrayList<>();
        for (GenreTemplate template : templates.values()) {
            GenreInfo info = new GenreInfo();
            info.setCode(template.getCode());
            info.setName(template.getName());
            info.setDescription(template.getDescription());
            list.add(info);
        }
        return list;
    }
    
    @Override
    public GenreTemplate getGenreTemplate(String genreCode) {
        return templates.getOrDefault(genreCode.toLowerCase(), templates.get("xuanhuan"));
    }
    
    @Override
    public GenreRhythmConfig getGenreRhythmConfig(String genreCode) {
        GenreTemplate template = getGenreTemplate(genreCode);
        return template != null ? template.getRhythmConfig() : null;
    }
    
    @Override
    public String applyGenreTemplate(String basePrompt, String genreCode) {
        GenreTemplate template = getGenreTemplate(genreCode);
        if (template == null) {
            return basePrompt;
        }
        
        StringBuilder genreInfo = new StringBuilder();
        genreInfo.append("\n\n===题材指导（").append(template.getName()).append("）===\n");
        genreInfo.append("题材特点：").append(template.getDescription()).append("\n\n");
        
        genreInfo.append("常见套路：\n");
        for (String trope : template.getTropes()) {
            genreInfo.append("- ").append(trope).append("\n");
        }
        
        genreInfo.append("\n避坑指南：\n");
        for (String pitfall : template.getPitfalls()) {
            genreInfo.append("- ").append(pitfall).append("\n");
        }
        
        genreInfo.append("\n人物原型：\n");
        for (Map.Entry<String, String> entry : template.getCharacterArchetypes().entrySet()) {
            genreInfo.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
        }
        
        return basePrompt + genreInfo.toString();
    }
}
