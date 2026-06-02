package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.ai.chain.ChapterGenerationChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SummaryNodeTest {

    @Mock
    private ChapterGenerationChain chain;

    private SummaryNode node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        node = new SummaryNode(chain, objectMapper);
    }

    @Test
    void parseStructuredSummary_validJson_extractsAllFields() {
        String json = """
                {
                    "strand_type": "quest",
                    "fingerprint": "主角发现古老卷轴的秘密",
                    "core_events": ["发现卷轴", "解读文字", "遭遇守护者"],
                    "characters": ["林风", "苏瑶", "长老"],
                    "locations": ["藏书阁", "禁地"],
                    "keywords": ["卷轴", "禁术", "守护者"],
                    "hook_level": "高"
                }
                """;

        Map<String, Object> result = node.parseStructuredSummary(json);

        assertEquals("主角发现古老卷轴的秘密", result.get("fingerprint"));
        assertEquals("quest", result.get("strand_type"));
        assertEquals("高", result.get("hook_level"));

        List<String> coreEvents = (List<String>) result.get("core_events");
        assertEquals(3, coreEvents.size());
        assertEquals("发现卷轴", coreEvents.get(0));
        assertEquals("解读文字", coreEvents.get(1));
        assertEquals("遭遇守护者", coreEvents.get(2));

        List<String> characters = (List<String>) result.get("characters");
        assertEquals(3, characters.size());
        assertTrue(characters.contains("林风"));
        assertTrue(characters.contains("苏瑶"));

        List<String> locations = (List<String>) result.get("locations");
        assertEquals(2, locations.size());
        assertTrue(locations.contains("藏书阁"));

        List<String> keywords = (List<String>) result.get("keywords");
        assertEquals(3, keywords.size());
        assertTrue(keywords.contains("卷轴"));
    }

    @Test
    void parseStructuredSummary_missingFields_returnsDefaults() {
        String json = """
                {
                    "fingerprint": "一句话描述"
                }
                """;

        Map<String, Object> result = node.parseStructuredSummary(json);

        assertEquals("一句话描述", result.get("fingerprint"));
        assertEquals("", result.get("strand_type"));
        assertEquals("", result.get("hook_level"));

        List<String> coreEvents = (List<String>) result.get("core_events");
        assertNotNull(coreEvents);
        assertTrue(coreEvents.isEmpty());

        List<String> characters = (List<String>) result.get("characters");
        assertNotNull(characters);
        assertTrue(characters.isEmpty());

        List<String> locations = (List<String>) result.get("locations");
        assertNotNull(locations);
        assertTrue(locations.isEmpty());

        List<String> keywords = (List<String>) result.get("keywords");
        assertNotNull(keywords);
        assertTrue(keywords.isEmpty());
    }

    @Test
    void parseStructuredSummary_nullInput_returnsEmptyMap() {
        Map<String, Object> result = node.parseStructuredSummary(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseStructuredSummary_invalidJson_returnsEmptyMap() {
        Map<String, Object> result = node.parseStructuredSummary("this is not json at all {{{");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
