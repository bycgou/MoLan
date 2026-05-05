package com.novel.system.service.chapter;

import com.novel.system.entity.mysql.ChapterVersion;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.ChapterVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节版本快照服务
 * 在重新生成章节前自动保存旧版本
 * 
 * @author AI Assistant
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterVersionService {
    
    private final ChapterVersionMapper versionMapper;
    private static final int MAX_VERSIONS = 10;
    
    /**
     * 保存版本快照（在重新生成前调用）
     * 
     * @param chapter 章节实体
     */
    public void saveSnapshot(NovelChapter chapter) {
        // 内容太少，不保存
        if (chapter.getContent() == null || chapter.getWordCount() < 100) {
            log.debug("章节内容太少，跳过版本保存: chapterId={}, wordCount={}", 
                chapter.getChapterId(), chapter.getWordCount());
            return;
        }
        
        try {
            // 获取当前最大版本号
            Integer maxVer = versionMapper.selectMaxVersion(chapter.getChapterId());
            int newVer = (maxVer != null ? maxVer : 0) + 1;
            
            // 创建版本快照
            ChapterVersion version = new ChapterVersion();
            version.setChapterId(chapter.getChapterId());
            version.setVersion(newVer);
            version.setContent(chapter.getContent());
            version.setOutline(chapter.getChapterOutline());
            version.setTitle(chapter.getChapterTitle());
            version.setWords(chapter.getWordCount());
            version.setCreateTime(LocalDateTime.now());
            
            // 保存版本
            versionMapper.insert(version);
            
            // 只保留最近10个版本
            versionMapper.deleteOldVersions(chapter.getChapterId(), MAX_VERSIONS);
            
            log.info("保存章节版本快照: chapterId={}, version={}, words={}", 
                chapter.getChapterId(), newVer, chapter.getWordCount());
            
        } catch (Exception e) {
            log.error("保存章节版本快照失败: chapterId={}, error={}", 
                chapter.getChapterId(), e.getMessage(), e);
        }
    }
    
    /**
     * 获取章节的所有版本
     * 
     * @param chapterId 章节ID
     * @return 版本列表
     */
    public List<ChapterVersion> getVersions(String chapterId) {
        return versionMapper.selectByChapterId(chapterId);
    }
    
    /**
     * 获取章节的指定版本
     * 
     * @param chapterId 章节ID
     * @param version 版本号
     * @return 版本快照
     */
    public ChapterVersion getVersion(String chapterId, int version) {
        List<ChapterVersion> versions = getVersions(chapterId);
        return versions.stream()
            .filter(v -> v.getVersion() == version)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取章节的最新版本
     * 
     * @param chapterId 章节ID
     * @return 最新版本快照
     */
    public ChapterVersion getLatestVersion(String chapterId) {
        List<ChapterVersion> versions = getVersions(chapterId);
        return versions.isEmpty() ? null : versions.get(0);
    }
}