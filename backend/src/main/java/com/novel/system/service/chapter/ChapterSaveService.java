package com.novel.system.service.chapter;

import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.ai.rag.RagService;
import com.novel.system.repository.mongo.NovelGraphRepository;
import com.novel.system.entity.mongo.NovelGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 章节保存服务 - 独立的事务方法
 * 
 * 从 ChapterGenerationServiceImpl 抽取，消除自注入反模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterSaveService {
    
    private final NovelBaseMapper novelBaseMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final RagService ragService;
    private final NovelGraphRepository novelGraphRepository;
    
    @Transactional
    public void saveGenerationResult(String novelId, int chapterNum, String chapterTitle,
                                     String chapterOutline, String chapterContent,
                                     int wordCount, String userIntent, String plotFingerprint,
                                     ChapterSummary summary) {
        // 保存章节内容
        NovelChapter chapter = new NovelChapter();
        chapter.setNovelId(novelId);
        chapter.setChapterNum(chapterNum);
        chapter.setChapterTitle(chapterTitle);
        chapter.setChapterOutline(chapterOutline);
        chapter.setContent(chapterContent);
        chapter.setWordCount(wordCount);
        chapter.setGenerationStatus(2);
        chapter.setUserIntent(userIntent);
        chapter.setPlotFingerprint(plotFingerprint);
        chapter.setCreateTime(LocalDateTime.now());
        chapter.setUpdateTime(LocalDateTime.now());
        
        // 检查章节是否已存在
        NovelChapter existing = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (existing != null) {
            chapter.setChapterId(existing.getChapterId());
            novelChapterMapper.updateById(chapter);
        } else {
            chapter.setChapterId(UUID.randomUUID().toString().replace("-", ""));
            novelChapterMapper.insert(chapter);
        }
        
        // 保存摘要
        ragService.saveSummary(summary);
        
        // 更新小说字数统计
        NovelBase novel = novelBaseMapper.selectById(novelId);
        if (novel != null) {
            novel.setWordCount((novel.getWordCount() != null ? novel.getWordCount() : 0) + wordCount);
            novel.setChapterCount(Math.max(novel.getChapterCount() != null ? novel.getChapterCount() : 0, chapterNum));
            novel.setUpdateTime(LocalDateTime.now());
            novelBaseMapper.updateById(novel);
        }
        
        // 更新知识图谱
        if (summary != null && summary.getRelations() != null && !summary.getRelations().isEmpty()) {
            updateNovelGraph(novelId, chapterNum, summary.getRelations());
        }
    }
    
    private void updateNovelGraph(String novelId, int chapterNum, List<Map<String, String>> relations) {
        try {
            NovelGraph graph = novelGraphRepository.findById(novelId).orElseGet(() -> {
                NovelGraph g = new NovelGraph();
                g.setNovelId(novelId);
                g.setNodes(new ArrayList<>());
                g.setEdges(new ArrayList<>());
                return g;
            });
            
            Set<String> existingNodes = new HashSet<>();
            for (NovelGraph.Node n : graph.getNodes()) {
                existingNodes.add(n.getName());
            }
            
            for (Map<String, String> rel : relations) {
                String source = rel.get("source");
                String target = rel.get("target");
                String relation = rel.get("relation");
                
                if (source != null && !existingNodes.contains(source)) {
                    NovelGraph.Node n = new NovelGraph.Node();
                    n.setId(UUID.randomUUID().toString());
                    n.setName(source);
                    n.setType("unknown");
                    graph.getNodes().add(n);
                    existingNodes.add(source);
                }
                if (target != null && !existingNodes.contains(target)) {
                    NovelGraph.Node n = new NovelGraph.Node();
                    n.setId(UUID.randomUUID().toString());
                    n.setName(target);
                    n.setType("unknown");
                    graph.getNodes().add(n);
                    existingNodes.add(target);
                }
                
                boolean edgeExists = false;
                for (NovelGraph.Edge e : graph.getEdges()) {
                    if (e.getSource().equals(source) && e.getTarget().equals(target)) {
                        e.setRelation(relation);
                        e.setLastUpdateChapter(chapterNum);
                        edgeExists = true;
                        break;
                    }
                }
                if (!edgeExists && source != null && target != null) {
                    NovelGraph.Edge e = new NovelGraph.Edge();
                    e.setSource(source);
                    e.setTarget(target);
                    e.setRelation(relation);
                    e.setLastUpdateChapter(chapterNum);
                    graph.getEdges().add(e);
                }
            }
            novelGraphRepository.save(graph);
        } catch (Exception e) {
            log.warn("更新知识图谱失败: {}", e.getMessage());
        }
    }
}
