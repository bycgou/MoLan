package com.novel.system.interceptor;

import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.StyleCard;
import com.novel.system.mapper.LlmConfigMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.StyleCardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnerAccessInterceptorCacheTest {

    @Mock
    private NovelBaseMapper novelBaseMapper;
    @Mock
    private LlmConfigMapper llmConfigMapper;
    @Mock
    private StyleCardMapper styleCardMapper;

    private OwnerAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new OwnerAccessInterceptor(novelBaseMapper, llmConfigMapper, styleCardMapper);
    }

    @Test
    void checkNovelOwnership_cacheHit_onlyCallsDbOnce() {
        NovelBase novel = new NovelBase();
        novel.setNovelId("n1");
        novel.setUserId("u1");
        when(novelBaseMapper.selectById("n1")).thenReturn(novel);

        // First call — cache miss, hits DB
        assertTrue(interceptor.checkNovelOwnership("n1", "u1"));
        // Second call — cache hit, no DB call
        assertTrue(interceptor.checkNovelOwnership("n1", "u1"));

        verify(novelBaseMapper, times(1)).selectById("n1");
    }

    @Test
    void checkConfigOwnership_cacheHit_onlyCallsDbOnce() {
        LlmConfig config = new LlmConfig();
        config.setConfigId("c1");
        config.setUserId("u1");
        when(llmConfigMapper.selectById("c1")).thenReturn(config);

        assertTrue(interceptor.checkConfigOwnership("c1", "u1"));
        assertTrue(interceptor.checkConfigOwnership("c1", "u1"));

        verify(llmConfigMapper, times(1)).selectById("c1");
    }

    @Test
    void checkStyleCardOwnership_cacheHit_onlyCallsDbOnce() {
        StyleCard card = new StyleCard();
        card.setCardId("sc1");
        card.setCreatedBy("u1");
        when(styleCardMapper.selectById("sc1")).thenReturn(card);

        assertTrue(interceptor.checkStyleCardOwnership("sc1", "u1"));
        assertTrue(interceptor.checkStyleCardOwnership("sc1", "u1"));

        verify(styleCardMapper, times(1)).selectById("sc1");
    }

    @Test
    void checkNovelOwnership_wrongUser_returnsFalse() {
        NovelBase novel = new NovelBase();
        novel.setNovelId("n1");
        novel.setUserId("u1");
        when(novelBaseMapper.selectById("n1")).thenReturn(novel);

        assertFalse(interceptor.checkNovelOwnership("n1", "u2"));
        assertFalse(interceptor.checkNovelOwnership("n1", "u2"));

        verify(novelBaseMapper, times(1)).selectById("n1");
    }

    @Test
    void checkNovelOwnership_notFound_returnsFalse() {
        when(novelBaseMapper.selectById("n99")).thenReturn(null);

        assertFalse(interceptor.checkNovelOwnership("n99", "u1"));
        assertFalse(interceptor.checkNovelOwnership("n99", "u1"));

        verify(novelBaseMapper, times(1)).selectById("n99");
    }

    @Test
    void differentKeys_callDbSeparately() {
        NovelBase novel1 = new NovelBase();
        novel1.setNovelId("n1");
        novel1.setUserId("u1");
        NovelBase novel2 = new NovelBase();
        novel2.setNovelId("n2");
        novel2.setUserId("u1");
        when(novelBaseMapper.selectById("n1")).thenReturn(novel1);
        when(novelBaseMapper.selectById("n2")).thenReturn(novel2);

        assertTrue(interceptor.checkNovelOwnership("n1", "u1"));
        assertTrue(interceptor.checkNovelOwnership("n2", "u1"));

        verify(novelBaseMapper, times(1)).selectById("n1");
        verify(novelBaseMapper, times(1)).selectById("n2");
    }
}
