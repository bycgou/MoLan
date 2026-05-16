package com.novel.system.service.orchestration;

import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mongo.ArcOrchestration;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.entity.mysql.NovelArc;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelArcMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.repository.mongo.ArcOrchestrationRepository;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.llm.LlmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArcOrchestrationServiceImplTest {

    @Mock private NovelBaseMapper novelBaseMapper;
    @Mock private NovelArcMapper novelArcMapper;
    @Mock private ClueBookService clueBookService;
    @Mock private CharacterStateService characterStateService;
    @Mock private ArcOrchestrationRepository orchestrationRepository;
    @Mock private LangChainConfig langChainConfig;
    @Mock private LlmConfigService llmConfigService;

    private ArcOrchestrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ArcOrchestrationServiceImpl(
                novelBaseMapper, novelArcMapper, clueBookService,
                characterStateService, orchestrationRepository,
                langChainConfig, llmConfigService);
    }

    @Test
    void orchestrate_returnsExisting_ifAlreadyPresent() {
        ArcOrchestration existing = new ArcOrchestration();
        existing.setArcId("arc1");
        when(orchestrationRepository.findByArcId("arc1")).thenReturn(existing);

        ArcOrchestration result = service.orchestrate("novel1", "arc1", null);

        assertSame(existing, result);
        verify(orchestrationRepository, never()).save(any());
    }

    @Test
    void orchestrate_throwsIfNovelNotFound() {
        when(orchestrationRepository.findByArcId("arc1")).thenReturn(null);
        when(novelBaseMapper.selectById("novel1")).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> service.orchestrate("novel1", "arc1", null));
    }

    @Test
    void orchestrate_throwsIfArcNotFound() {
        when(orchestrationRepository.findByArcId("arc1")).thenReturn(null);
        when(novelBaseMapper.selectById("novel1")).thenReturn(new NovelBase());
        when(novelArcMapper.selectById("arc1")).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> service.orchestrate("novel1", "arc1", null));
    }

    @Test
    void orchestrate_throwsIfNoLlmConfig() {
        when(orchestrationRepository.findByArcId("arc1")).thenReturn(null);
        NovelBase novel = new NovelBase();
        novel.setTotalOutline("test");
        when(novelBaseMapper.selectById("novel1")).thenReturn(novel);
        NovelArc arc = new NovelArc();
        arc.setArcId("arc1");
        when(novelArcMapper.selectById("arc1")).thenReturn(arc);
        when(llmConfigService.getAnyActiveConfig("all")).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> service.orchestrate("novel1", "arc1", null));
    }
}
