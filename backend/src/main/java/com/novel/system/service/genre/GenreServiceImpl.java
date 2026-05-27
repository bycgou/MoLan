package com.novel.system.service.genre;

import com.novel.system.dto.genre.GenreInfo;
import com.novel.system.dto.genre.GenreRhythmConfig;
import com.novel.system.dto.genre.GenreTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Service
public class GenreServiceImpl implements GenreService {

    private static final Map<String, String> LEGACY_CODE_MAP = Map.of(
        "yanqing", "dog-blood-romance",
        "xuanyi", "rules-mystery",
        "lishi", "period-drama"
    );

    private final Map<String, GenreTemplate> templates = new HashMap<>();

    public GenreServiceImpl() {
        loadFromMarkdown();
        initTemplatesForMissing();
    }

    @Override
    public List<GenreInfo> getGenreList() {
        return templates.values().stream().map(t -> {
            GenreInfo info = new GenreInfo();
            info.setCode(t.getCode());
            info.setName(t.getName());
            info.setCategory(t.getCategory() != null ? t.getCategory() : "通用");
            info.setDescription(t.getDescription());
            return info;
        }).collect(Collectors.toList());
    }

    @Override
    public GenreTemplate getGenreTemplate(String code) {
        if (code == null) return null;
        String normalized = code.toLowerCase().trim();
        // Direct match
        GenreTemplate result = templates.get(normalized);
        if (result != null) return result;
        // Legacy code fallback
        String mapped = LEGACY_CODE_MAP.get(normalized);
        if (mapped != null) return templates.get(mapped);
        return null;
    }

    @Override
    public GenreRhythmConfig getGenreRhythmConfig(String code) {
        GenreTemplate template = getGenreTemplate(code);
        return template != null ? template.getRhythmConfig() : null;
    }

    @Override
    public String getGenreGuide(String code) {
        String folder = mapGenreToFolder(code);
        if (folder == null) return "";
        return loadMarkdownGuide(folder);
    }

    @Override
    public List<String> getGenreDetailFiles(String code) {
        String folder = mapGenreToFolder(code);
        if (folder == null) return Collections.emptyList();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:genres/" + folder + "/*.md");
            return Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to list genre detail files for {}", code, e);
            return Collections.emptyList();
        }
    }

    // --- Private helpers ---

    private String mapGenreToFolder(String genre) {
        if (genre == null) return null;
        String g = genre.toLowerCase().trim();
        // Exact code match
        if (templates.containsKey(g)) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources("classpath:genres/" + g + "/*.md");
                if (resources.length > 0) return g;
            } catch (Exception ignored) {}
        }
        // Keyword match (migrated from PromptTemplateServiceImpl.mapGenreToFolder)
        if (g.contains("玄幻") || g.equals("xuanhuan")) return "xuanhuan";
        if (g.contains("现实") || g.equals("realistic")) return "realistic";
        if (g.contains("规则") || g.contains("推理") || g.contains("悬疑") || g.equals("rules-mystery")) return "rules-mystery";
        if (g.contains("古") || g.contains("宫斗") || g.contains("朝堂") || g.equals("period-drama")) return "period-drama";
        if (g.contains("言情") || g.contains("甜") || g.contains("虐") || g.equals("dog-blood-romance")) return "dog-blood-romance";
        if (g.contains("知乎") || g.contains("短") || g.equals("zhihu-short")) return "zhihu-short";
        return null;
    }

    private String loadMarkdownGuide(String folder) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:genres/" + folder + "/*.md");
            StringBuilder sb = new StringBuilder();
            int totalChars = 0;
            int maxPerFile = 2000;
            int maxTotal = 8000;
            for (Resource resource : resources) {
                if (totalChars >= maxTotal) break;
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (content.length() > maxPerFile) {
                    content = content.substring(0, maxPerFile);
                }
                sb.append(content).append("\n\n");
                totalChars += content.length();
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to load markdown guide for folder: {}", folder, e);
            return "";
        }
    }

    // --- Front matter parsing ---

    private Map<String, Object> parseFrontMatter(String content) {
        if (content == null || !content.startsWith("---")) return Collections.emptyMap();
        int end = content.indexOf("---", 3);
        if (end < 0) return Collections.emptyMap();
        String yaml = content.substring(3, end).trim();
        try {
            Yaml parser = new Yaml();
            Map<String, Object> result = parser.load(yaml);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to parse front matter", e);
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List) {
            return ((List<?>) v).stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getStringMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Map) {
            Map<String, String> result = new HashMap<>();
            ((Map<String, Object>) v).forEach((k, val) -> result.put(k, val != null ? val.toString() : ""));
            return result;
        }
        return Collections.emptyMap();
    }

    private double getDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }

    private int getInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private GenreTemplate mapFrontMatterToTemplate(Map<String, Object> fm) {
        GenreTemplate t = new GenreTemplate();
        t.setCode(getString(fm, "code"));
        t.setName(getString(fm, "name"));
        t.setCategory(getString(fm, "category"));
        t.setDescription(getString(fm, "description"));
        t.setTropes(getStringList(fm, "tropes"));
        t.setPitfalls(getStringList(fm, "pitfalls"));
        t.setCharacterArchetypes(getStringMap(fm, "characterArchetypes"));
        Object rhythmObj = fm.get("rhythmConfig");
        if (rhythmObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rhythm = (Map<String, Object>) rhythmObj;
            GenreRhythmConfig rc = new GenreRhythmConfig();
            rc.setQuestRatio(getDouble(rhythm, "questRatio"));
            rc.setFireRatio(getDouble(rhythm, "fireRatio"));
            rc.setConstellationRatio(getDouble(rhythm, "constellationRatio"));
            rc.setQuestMaxConsecutive(getInt(rhythm, "questMaxConsecutive"));
            rc.setFireMaxGap(getInt(rhythm, "fireMaxGap"));
            rc.setConstellationMaxGap(getInt(rhythm, "constellationMaxGap"));
            t.setRhythmConfig(rc);
        }
        return t;
    }

    private void loadFromMarkdown() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] indexes = resolver.getResources("classpath:genres/*/index.md");
            for (Resource index : indexes) {
                String content = new String(index.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> frontMatter = parseFrontMatter(content);
                if (frontMatter.isEmpty()) continue;
                GenreTemplate template = mapFrontMatterToTemplate(frontMatter);
                if (template.getCode() != null) {
                    templates.put(template.getCode(), template);
                    log.info("Loaded genre from index.md: {} ({})", template.getName(), template.getCode());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load genre index.md files, falling back to hardcoded data", e);
        }
    }

    private void initTemplatesForMissing() {
        if (!templates.containsKey("dushi")) {
            registerGenre("dushi", "都市", "男频", "现实背景、职场逆袭、商业争霸",
                    List.of("职场逆袭：从小职员到CEO", "重生都市：重生后利用信息差", "透视异能：获得特殊能力", "神医流：医术救人", "保镖流：保护重要人物"),
                    List.of("避免过于脱离现实", "职场描写要专业", "感情线要有深度", "金手指要有限制"),
                    Map.of("主角", "低调务实、能力出众", "女主", "职场精英或青梅竹马", "对手", "商业对手或职场敌人", "兄弟", "患难与共的伙伴"),
                    0.55, 0.3, 0.15, 4, 8, 18);
        }
        if (!templates.containsKey("xianxia")) {
            registerGenre("xianxia", "仙侠", "男频", "修仙问道、飞升成仙、仙界争霸",
                    List.of("凡人修仙：从凡人到仙人", "重生修仙：带着记忆重修", "系统修仙：系统辅助修炼", "宗门修仙：从小宗门到仙门", "散修流：独自修炼成仙"),
                    List.of("境界体系要清晰", "法宝功法要详细", "仙界设定要完整", "避免境界通胀"),
                    Map.of("主角", "心性坚定、悟性极高", "道侣", "仙子或妖女", "宗门长辈", "护短或严厉", "仙界势力", "各大仙门"),
                    0.65, 0.1, 0.25, 6, 15, 12);
        }
        // yanqing and xuanyi now have index.md (as dog-blood-romance and rules-mystery), so skip them
        // lishi now has index.md (as period-drama), so skip it
        if (!templates.containsKey("kehuan")) {
            registerGenre("kehuan", "科幻", "男频", "未来科技、星际文明、末日求生",
                    List.of("星际争霸：宇宙战争", "机甲流：机甲战斗", "末日求生：灾难后生存", "赛博朋克：高科技低生活", "时间循环：重复同一天"),
                    List.of("科技设定要自洽", "世界观要完整", "避免科技万能", "人文思考要深入"),
                    Map.of("主角", "理性或热血、有理想", "科学家", "技术担当", "军人", "战斗担当", "AI", "人工智能角色"),
                    0.6, 0.15, 0.25, 5, 15, 12);
        }
        if (!templates.containsKey("youxi")) {
            registerGenre("youxi", "游戏", "男频", "游戏世界、电竞比赛、虚拟现实",
                    List.of("电竞流：职业选手", "游戏穿越：穿越到游戏世界", "网游流：游戏内发展", "直播流：游戏主播", "全息网游：沉浸式游戏"),
                    List.of("游戏设定要详细", "战斗描写要精彩", "职业平衡要合理", "避免外挂太强"),
                    Map.of("主角", "技术流或战术流", "队友", "各有特色", "对手", "强大但可战胜", "粉丝", "提供爽点"),
                    0.55, 0.2, 0.25, 4, 10, 15);
        }
        if (!templates.containsKey("lishi")) {
            registerGenre("lishi", "历史", "男频", "古代背景、争霸天下、权谋斗争",
                    List.of("争霸流：统一战争", "权谋流：朝堂争斗", "种田流：发展势力", "架空历史：平行世界", "穿越历史：改变历史"),
                    List.of("历史背景要考究", "政治斗争要合理", "军事描写要专业", "避免现代思维"),
                    Map.of("主角", "有谋略、有人格魅力", "谋士", "出谋划策", "武将", "征战四方", "对手", "枭雄或奸臣"),
                    0.65, 0.15, 0.2, 6, 15, 12);
        }
        if (!templates.containsKey("moshi")) {
            registerGenre("moshi", "末世", "男频", "丧尸危机、资源争夺、重建文明",
                    List.of("丧尸流：对抗丧尸", "异能流：获得异能", "系统流：末世系统", "基地流：建立基地", "进化流：生物进化"),
                    List.of("生存压力要真实", "人性描写要深刻", "资源设定要合理", "避免主角太圣母"),
                    Map.of("主角", "冷静理智、有领导力", "伙伴", "各有专长", "反派", "人性黑暗面", "幸存者", "不同立场"),
                    0.6, 0.2, 0.2, 5, 10, 15);
        }
    }

    private void registerGenre(String code, String name, String category, String description,
                               List<String> tropes, List<String> pitfalls,
                               Map<String, String> characterArchetypes,
                               double questRatio, double fireRatio, double constellationRatio,
                               int questMaxConsecutive, int fireMaxGap, int constellationMaxGap) {
        GenreTemplate template = new GenreTemplate();
        template.setCode(code);
        template.setName(name);
        template.setCategory(category);
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
}
