package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.service.character.CharacterStateService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterPlanningNodeTest {

    @Mock private ChatModelProvider chatModelProvider;
    @Mock private ChatModel chatModel;
    @Mock private CharacterStateService characterStateService;

    private CharacterPlanningNode node;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(chatModelProvider.getStructuredModel(any())).thenReturn(chatModel);
        node = new CharacterPlanningNode(chatModelProvider, characterStateService, objectMapper);
    }

    @Test
    void validatesBehaviorWithFourParams() throws Exception {
        when(characterStateService.formatForPrompt("n1")).thenReturn("角色状态");
        when(chatModel.chat(anyString()))
                .thenReturn("[{\"characterName\":\"张三\",\"characterId\":\"c1\"," +
                        "\"role\":\"PROTAGONIST\",\"goal\":\"寻宝\",\"emotionalState\":\"兴奋\"," +
                        "\"keyAction\":\"拔剑\",\"interactions\":[\"与李四对话\"]}]");
        when(characterStateService.isBehaviorAllowed("n1", "c1", "拔剑", 1)).thenReturn(true);

        NovelAgentState input = NovelAgentState.builder()
                .novelId("n1").chapterNum(1)
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals(1, result.getChapterPlan().getCharacterActions().size());
        assertEquals("张三", result.getChapterPlan().getCharacterActions().get(0).getCharacterName());
        assertEquals("拔剑", result.getChapterPlan().getCharacterActions().get(0).getKeyAction());
    }

    @Test
    void marksRejectedBehaviorWithPrefix() throws Exception {
        when(characterStateService.formatForPrompt("n1")).thenReturn("角色状态");
        when(chatModel.chat(anyString()))
                .thenReturn("[{\"characterName\":\"张三\",\"characterId\":\"c1\"," +
                        "\"role\":\"PROTAGONIST\",\"goal\":\"寻宝\",\"emotionalState\":\"兴奋\"," +
                        "\"keyAction\":\"飞天\",\"interactions\":[]}]");
        when(characterStateService.isBehaviorAllowed("n1", "c1", "飞天", 1)).thenReturn(false);

        NovelAgentState input = NovelAgentState.builder()
                .novelId("n1").chapterNum(1)
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        assertEquals("[需审查] 飞天",
                result.getChapterPlan().getCharacterActions().get(0).getKeyAction());
    }
}
