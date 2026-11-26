package com.novel.system.controller;

import com.novel.system.dto.request.CreateClueRequest;
import com.novel.system.dto.response.ApiResponse;
import com.novel.system.dto.response.ClueResponse;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.service.clue.ClueBookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 伏笔管理控制器单元测试
 *
 * 覆盖手动创建伏笔时 deadline 字段的接口映射（此前 DTO 与控制器都没有 deadline，
 * 导致人工也无法设置伏笔时限）。
 */
@ExtendWith(MockitoExtension.class)
class ClueControllerTest {

    @Mock
    private ClueBookService clueBookService;

    @InjectMocks
    private ClueController controller;

    @Test
    void createClue_passesDeadlineFromRequestToEntity() {
        CreateClueRequest request = new CreateClueRequest();
        request.setClueContent("古墓中的神秘玉佩");
        request.setBuryChapter(7);
        request.setDeadline(12);

        when(clueBookService.createClue(any(ClueBook.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<ClueResponse> response = controller.createClue("novel1", request);

        assertEquals(200, response.getCode());
        assertEquals("伏笔创建成功", response.getMessage());

        ArgumentCaptor<ClueBook> captor = ArgumentCaptor.forClass(ClueBook.class);
        verify(clueBookService).createClue(captor.capture());
        ClueBook saved = captor.getValue();
        assertEquals("novel1", saved.getNovelId());
        assertEquals(7, saved.getBuryChapter());
        assertEquals(12, saved.getDeadline(), "手动创建时必须把请求中的 deadline 传给实体");
        assertEquals(0, saved.getStatus());

        assertEquals(12, response.getData().getDeadline());
    }

    @Test
    void createClue_withoutDeadline_passesNull_soServiceCanApplyDefaultPolicy() {
        CreateClueRequest request = new CreateClueRequest();
        request.setClueContent("未指定回收期限的伏笔");
        request.setBuryChapter(7);

        when(clueBookService.createClue(any(ClueBook.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<ClueResponse> response = controller.createClue("novel1", request);

        assertEquals(200, response.getCode());
        ArgumentCaptor<ClueBook> captor = ArgumentCaptor.forClass(ClueBook.class);
        verify(clueBookService).createClue(captor.capture());
        assertNull(captor.getValue().getDeadline(),
                "未指定时交由 ClueBookServiceImpl 按默认期限策略推导");
    }
}
