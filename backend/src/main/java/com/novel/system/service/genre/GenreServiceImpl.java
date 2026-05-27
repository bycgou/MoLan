package com.novel.system.service.genre;

import com.novel.system.dto.genre.GenreInfo;
import com.novel.system.dto.genre.GenreRhythmConfig;
import com.novel.system.dto.genre.GenreTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${genre.guide.max-chars-per-file:2000}")
    private int maxCharsPerFile;

    @Value("${genre.guide.max-total-chars:8000}")
    private int maxTotalChars;

    private final Map<String, GenreTemplate> templates = new HashMap<>();

    public GenreServiceImpl() {
        loadFromMarkdown();
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

    @Override
    public String getGenreGuideFile(String code, String filename) {
        if (filename == null || filename.contains("..")) return null;
        String folder = mapGenreToFolder(code);
        if (folder == null) return null;
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("classpath:genres/" + folder + "/" + filename);
            if (!resource.exists()) return null;
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to load genre guide file: {}/{}", code, filename, e);
            return null;
        }
    }

    @Override
    public synchronized void reload() {
        templates.clear();
        loadFromMarkdown();
        log.info("Genre templates reloaded, {} genres loaded", templates.size());
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
        if (g.contains("都市") || g.equals("dushi")) return "dushi";
        if (g.contains("仙侠") || g.equals("xianxia")) return "xianxia";
        if (g.contains("科幻") || g.equals("kehuan")) return "kehuan";
        if (g.contains("游戏") || g.equals("youxi")) return "youxi";
        if (g.contains("末世") || g.equals("moshi")) return "moshi";
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
            int maxPerFile = maxCharsPerFile;
            int maxTotal = maxTotalChars;
            for (Resource resource : resources) {
                if (totalChars >= maxTotal) break;
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                content = smartTruncate(content, maxPerFile);
                sb.append(content).append("\n\n");
                totalChars += content.length();
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to load markdown guide for folder: {}", folder, e);
            return "";
        }
    }

    private String smartTruncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) return content;
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        int accumulated = 0;
        for (String line : lines) {
            if (line.startsWith("## ") && accumulated > maxChars) {
                break;
            }
            accumulated += line.length() + 1; // +1 for newline
            result.append(line).append("\n");
        }
        // Fallback: if a single section exceeds maxChars, hard-cut
        if (result.length() > maxChars) {
            return result.substring(0, maxChars);
        }
        return result.toString().trim();
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

}
