package com.novel.system.service.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContentExtractorServiceTest {

    private ContentExtractorService service;

    @BeforeEach
    void setUp() {
        service = new ContentExtractorService();
    }

    @Test
    void extractLocation_matchesChineseLocationPattern() {
        String content = "他来到青云山，抬头望去，只见云雾缭绕。";
        String location = service.extractLocation(content);
        assertEquals("青云山", location);
    }

    @Test
    void extractLocation_returnsNullWhenNoMatch() {
        String content = "今天天气不错。";
        String location = service.extractLocation(content);
        assertNull(location);
    }

    @Test
    void extractLocation_matchesMultiplePatterns() {
        String content1 = "走进大殿之中";
        String content2 = "飞到天际城";
        String content3 = "赶到战场";
        assertEquals("大殿之中", service.extractLocation(content1));
        assertEquals("天际城", service.extractLocation(content2));
        assertEquals("战场", service.extractLocation(content3));
    }

    @Test
    void extractPowerLevel_matchesBreakthroughPattern() {
        String content = "他终于突破到金丹期，全身气息暴涨。";
        String level = service.extractPowerLevel(content);
        assertEquals("金丹期", level);
    }

    @Test
    void extractPowerLevel_matchesPromotionPattern() {
        String content = "经过三年苦修，她晋升为内门弟子。";
        String level = service.extractPowerLevel(content);
        assertEquals("内门弟子", level);
    }

    @Test
    void extractItems_findsAcquiredItems() {
        String content = "他获得了一把青锋剑，又在洞府中发现了三枚筑基丹。";
        List<String> items = service.extractItems(content);
        assertTrue(items.contains("一把青锋剑"));
        assertTrue(items.contains("三枚筑基丹"));
    }

    @Test
    void extractItems_returnsEmptyListWhenNoMatch() {
        String content = "今天天气不错。";
        List<String> items = service.extractItems(content);
        assertTrue(items.isEmpty());
    }

    @Test
    void extractDialogues_findsSpeakerAndContent() {
        String content = "张三说道：\u201c你可知此地凶险？\u201d李四笑道：\u201c无妨。\u201d";
        List<ContentExtractorService.DialogueEvent> dialogues = service.extractDialogues(content);
        assertEquals(2, dialogues.size());
        assertEquals("张三", dialogues.get(0).getSpeaker());
        assertEquals("你可知此地凶险？", dialogues.get(0).getContent());
        assertEquals("李四", dialogues.get(1).getSpeaker());
        assertEquals("无妨。", dialogues.get(1).getContent());
    }

    @Test
    void extractDialogues_excludesPronouns() {
        String content = "他说：\u201c走吧。\u201d她答：\u201c好。\u201d";
        List<ContentExtractorService.DialogueEvent> dialogues = service.extractDialogues(content);
        // "他" and "她" are pronouns, should be excluded
        assertTrue(dialogues.isEmpty());
    }

    @Test
    void extractTimeAdvance_findsDaysLater() {
        String content = "三日后，他终于到达了目的地。";
        int days = service.extractTimeAdvance(content);
        assertEquals(3, days);
    }

    @Test
    void extractTimeAdvance_handlesCompositeChineseNumbers() {
        assertEquals(15, service.extractTimeAdvance("十五天后"));
        assertEquals(23, service.extractTimeAdvance("二十三天后"));
        assertEquals(30, service.extractTimeAdvance("三十天后"));
    }

    @Test
    void extractTimeAdvance_findsNumericDays() {
        String content = "15天后，秘境开启。";
        int days = service.extractTimeAdvance(content);
        assertEquals(15, days);
    }

    @Test
    void extractTimeAdvance_returnsZeroWhenNoMatch() {
        String content = "他走进了大殿。";
        int days = service.extractTimeAdvance(content);
        assertEquals(0, days);
    }

    @Test
    void extractLocation_handlesLongLocationNames() {
        String content = "来到天玄宗后山禁地深处";
        String location = service.extractLocation(content);
        assertEquals("天玄宗后山禁地深处", location);
    }
}
