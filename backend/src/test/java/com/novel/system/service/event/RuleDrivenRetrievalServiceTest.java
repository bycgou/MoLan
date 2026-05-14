package com.novel.system.service.event;

import com.novel.system.entity.mongo.EventNode;
import com.novel.system.entity.mysql.ClueBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuleDrivenRetrievalServiceTest {

    @Mock
    private EventGraphService eventGraphService;

    private RuleDrivenRetrievalService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RuleDrivenRetrievalService(eventGraphService);
    }

    @Test
    void retrieveByClueRecycle_returnsEventsWhenDeadlineNear() {
        ClueBook clue = new ClueBook();
        clue.setClueContent("神秘玉佩的秘密");
        clue.setBuryChapter(5);
        clue.setDeadline(50);
        clue.setRelatedCharacters("张三");
        clue.setStatus(0);

        EventNode buryEvent = new EventNode();
        buryEvent.setEventId("evt1");
        buryEvent.setChapter(5);
        buryEvent.setContent("张三在古墓中发现了一枚神秘玉佩");
        buryEvent.setType("foreshadow");

        when(eventGraphService.getEventsByChapterAndTag("novel1", 5, "伏笔埋设"))
            .thenReturn(List.of(buryEvent));
        when(eventGraphService.getEventsByCharacter("novel1", "张三"))
            .thenReturn(Collections.emptyList());

        List<EventNode> results = service.retrieveByClueRecycle("novel1", 48, List.of(clue));
        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
    }

    @Test
    void retrieveByClueRecycle_returnsEmptyWhenDeadlineFar() {
        ClueBook clue = new ClueBook();
        clue.setClueContent("远古遗迹的线索");
        clue.setBuryChapter(5);
        clue.setDeadline(100);
        clue.setStatus(0);

        List<EventNode> results = service.retrieveByClueRecycle("novel1", 48, List.of(clue));
        assertTrue(results.isEmpty());
    }

    @Test
    void retrieveByCharacterReunion_returnsLastAppearanceWhenLongAbsent() {
        EventNode lastAppearance = new EventNode();
        lastAppearance.setEventId("evt2");
        lastAppearance.setChapter(10);
        lastAppearance.setContent("李四与主角告别，前往北方");
        lastAppearance.setType("dialogue");

        when(eventGraphService.getLastAppearance("novel1", "李四"))
            .thenReturn(lastAppearance);

        List<EventNode> results = service.retrieveByCharacterReunion("novel1", 50, "李四再次出现在城门口");
        assertFalse(results.isEmpty());
    }

    @Test
    void retrieveByCharacterReunion_returnsEmptyWhenRecentlySeen() {
        EventNode recentAppearance = new EventNode();
        recentAppearance.setEventId("evt3");
        recentAppearance.setChapter(48);
        recentAppearance.setContent("李四在酒楼喝酒");

        when(eventGraphService.getLastAppearance("novel1", "李四"))
            .thenReturn(recentAppearance);

        List<EventNode> results = service.retrieveByCharacterReunion("novel1", 50, "李四再次出现");
        assertTrue(results.isEmpty());
    }

    @Test
    void retrieveByLocation_returnsImportantEventsAtLocation() {
        EventNode locationEvent = new EventNode();
        locationEvent.setEventId("evt4");
        locationEvent.setChapter(3);
        locationEvent.setContent("主角在青云山获得了传承");
        locationEvent.setImportance(8);

        when(eventGraphService.getImportantEventsByLocation("novel1", "青云山", 7))
            .thenReturn(List.of(locationEvent));

        List<EventNode> results = service.retrieveByLocation("novel1", 50, "主角再次来到青云山");
        assertFalse(results.isEmpty());
    }

    @Test
    void retrieveByLocation_returnsEmptyWhenNoLocationMentioned() {
        List<EventNode> results = service.retrieveByLocation("novel1", 50, "今天天气不错");
        assertTrue(results.isEmpty());
    }
}
