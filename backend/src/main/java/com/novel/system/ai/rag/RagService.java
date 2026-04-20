package com.novel.system.ai.rag;

import com.novel.system.entity.mongo.ChapterSummary;
import java.util.List;

/**
 * RAG检索服务接口 - 增强版（支持Rerank和混合检索）
 */
public interface RagService {
    
    /**
     * 混合检索上下文
     * @param novelId 小说ID
     * @param currentChapterNum 当前章节号
     * @param characters 相关人物
     * @param keywords 相关关键词
     * @return 检索到的摘要列表
     */
    List<ChapterSummary> retrieveContext(String novelId, int currentChapterNum, 
                                         List<String> characters, List<String> keywords);
    
    /**
     * 获取最近N章的摘要
     * @param novelId 小说ID
     * @param currentChapterNum 当前章节号
     * @param count 章节数
     * @return 摘要列表
     */
    List<ChapterSummary> getRecentSummaries(String novelId, int currentChapterNum, int count);
    
    /**
     * 向量相似度检索
     * @param novelId 小说ID
     * @param queryText 查询文本
     * @param topK 返回数量
     * @return 摘要列表
     */
    List<ChapterSummary> vectorSearch(String novelId, String queryText, int topK);
    
    /**
     * BM25关键词检索
     * @param novelId 小说ID
     * @param queryText 查询文本
     * @param topK 返回数量
     * @return 摘要列表
     */
    List<ChapterSummary> bm25Search(String novelId, String queryText, int topK);
    
    /**
     * 混合检索（向量 + BM25 + RRF融合 + Rerank）
     * @param novelId 小说ID
     * @param queryText 查询文本
     * @param topK 返回数量
     * @return 摘要列表
     */
    List<ChapterSummary> hybridSearch(String novelId, String queryText, int topK);
    
    void compressMemory(String novelId, int endChapter);
    String getGlobalMemory(String novelId);
    
    /**
     * 保存摘要
     * @param summary 摘要对象
     * @return 保存后的摘要
     */
    ChapterSummary saveSummary(ChapterSummary summary);
    
    /**
     * 组装完整上下文
     * @param novelId 小说ID
     * @param chapterNum 章节号
     * @param totalOutline 总纲
     * @param arcOutline 卷纲
     * @param characterStates 人物状态
     * @param unrecycledClues 未回收伏笔
     * @return 组装后的上下文字符串
     */
    String assembleContext(String novelId, int chapterNum, String totalOutline, 
                          String arcOutline, String characterStates, String unrecycledClues);
    
    /**
     * Rerank重排序
     * @param queryText 查询文本
     * @param summaries 待排序的摘要列表
     * @param topK 返回数量
     * @return 重排序后的摘要列表
     */
    List<ChapterSummary> rerank(String queryText, List<ChapterSummary> summaries, int topK);
}
