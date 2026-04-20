package com.novel.system.service.novel;

import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelChapterMapper;
import com.novel.system.service.novel.StrandRhythmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Strand节奏系统服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrandRhythmServiceImpl implements StrandRhythmService {
    
    private final NovelChapterMapper novelChapterMapper;
    
    private static final int QUEST_MAX_CONSECUTIVE = 5;
    private static final int FIRE_MAX_GAP = 10;
    private static final int CONSTELLATION_MAX_GAP = 15;
    
    private static final double QUEST_IDEAL_RATIO = 0.6;
    private static final double FIRE_IDEAL_RATIO = 0.2;
    private static final double CONSTELLATION_IDEAL_RATIO = 0.2;
    
    @Override
    public RhythmStatus getRhythmStatus(String novelId) {
        RhythmStatus status = new RhythmStatus();
        
        List<NovelChapter> chapters = novelChapterMapper.selectByNovelIdDesc(novelId);
        
        if (chapters.isEmpty()) {
            status.setLastStrand(StrandType.QUEST);
            status.setTotalChapters(0);
            return status;
        }
        
        status.setTotalChapters(chapters.size());
        
        int questCount = 0;
        int fireCount = 0;
        int constellationCount = 0;
        
        int questConsecutive = 0;
        int fireGap = 0;
        int constellationGap = 0;
        
        boolean countingQuest = true;
        boolean foundFire = false;
        boolean foundConstellation = false;
        
        for (int i = 0; i < chapters.size(); i++) {
            NovelChapter chapter = chapters.get(i);
            StrandType strand = parseStrandType(chapter.getRhythmTag());
            
            switch (strand) {
                case QUEST -> questCount++;
                case FIRE -> fireCount++;
                case CONSTELLATION -> constellationCount++;
            }
            
            if (i == 0) {
                status.setLastStrand(strand);
            }
            
            if (strand == StrandType.QUEST && countingQuest) {
                questConsecutive++;
            } else {
                countingQuest = false;
            }
            
            if (strand == StrandType.FIRE) {
                foundFire = true;
                fireGap = 0;
            } else if (!foundFire) {
                fireGap++;
            }
            
            if (strand == StrandType.CONSTELLATION) {
                foundConstellation = true;
                constellationGap = 0;
            } else if (!foundConstellation) {
                constellationGap++;
            }
        }
        
        status.setQuestConsecutiveCount(questConsecutive);
        status.setFireGapCount(fireGap);
        status.setConstellationGapCount(constellationGap);
        
        int total = chapters.size();
        status.setQuestRatio((double) questCount / total);
        status.setFireRatio((double) fireCount / total);
        status.setConstellationRatio((double) constellationCount / total);
        
        return status;
    }
    
    @Override
    public RhythmViolation checkRhythmViolation(String novelId, StrandType newStrand) {
        RhythmViolation violation = new RhythmViolation();
        violation.setViolated(false);
        
        RhythmStatus status = getRhythmStatus(novelId);
        
        if (newStrand == StrandType.QUEST && status.getLastStrand() == StrandType.QUEST) {
            if (status.getQuestConsecutiveCount() >= QUEST_MAX_CONSECUTIVE) {
                violation.setViolated(true);
                violation.setViolationType("quest_overflow");
                violation.setMessage("Quest（主线）已连续" + status.getQuestConsecutiveCount() + 
                    "章，超过红线" + QUEST_MAX_CONSECUTIVE + "章，建议切换到Fire或Constellation线");
                return violation;
            }
        }
        
        if (newStrand != StrandType.FIRE) {
            int projectedFireGap = status.getFireGapCount() + 1;
            if (projectedFireGap > FIRE_MAX_GAP) {
                violation.setViolated(true);
                violation.setViolationType("fire_gap");
                violation.setMessage("Fire（感情线）已断档" + projectedFireGap + 
                    "章，超过红线" + FIRE_MAX_GAP + "章，建议加入感情线内容");
                return violation;
            }
        }
        
        if (newStrand != StrandType.CONSTELLATION) {
            int projectedConstellationGap = status.getConstellationGapCount() + 1;
            if (projectedConstellationGap > CONSTELLATION_MAX_GAP) {
                violation.setViolated(true);
                violation.setViolationType("constellation_gap");
                violation.setMessage("Constellation（世界观）已断档" + projectedConstellationGap + 
                    "章，超过红线" + CONSTELLATION_MAX_GAP + "章，建议加入世界观内容");
                return violation;
            }
        }
        
        return violation;
    }
    
    @Override
    public StrandType recommendNextStrand(String novelId) {
        RhythmStatus status = getRhythmStatus(novelId);
        
        if (status.getTotalChapters() == 0) {
            return StrandType.QUEST;
        }
        
        double questDeviation = status.getQuestRatio() - QUEST_IDEAL_RATIO;
        double fireDeviation = status.getFireRatio() - FIRE_IDEAL_RATIO;
        double constellationDeviation = status.getConstellationRatio() - CONSTELLATION_IDEAL_RATIO;
        
        if (status.getQuestConsecutiveCount() >= QUEST_MAX_CONSECUTIVE - 1) {
            return fireDeviation < constellationDeviation ? StrandType.FIRE : StrandType.CONSTELLATION;
        }
        
        if (status.getFireGapCount() >= FIRE_MAX_GAP - 2) {
            return StrandType.FIRE;
        }
        
        if (status.getConstellationGapCount() >= CONSTELLATION_MAX_GAP - 2) {
            return StrandType.CONSTELLATION;
        }
        
        if (questDeviation <= fireDeviation && questDeviation <= constellationDeviation) {
            return StrandType.QUEST;
        } else if (fireDeviation <= constellationDeviation) {
            return StrandType.FIRE;
        } else {
            return StrandType.CONSTELLATION;
        }
    }
    
    @Override
    public void updateChapterStrand(String novelId, Integer chapterNum, StrandType strandType) {
        NovelChapter chapter = novelChapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum);
        if (chapter != null) {
            chapter.setRhythmTag(strandType.name().toLowerCase());
            chapter.setUpdateTime(LocalDateTime.now());
            novelChapterMapper.updateById(chapter);
            log.info("更新章节{}的Strand类型为: {}", chapterNum, strandType);
        }
    }
    
    @Override
    public String getRhythmStatusString(String novelId) {
        RhythmStatus status = getRhythmStatus(novelId);
        
        StringBuilder sb = new StringBuilder();
        sb.append("【当前节奏状态】\n");
        sb.append("• 上一章类型：").append(formatStrandType(status.getLastStrand())).append("\n");
        sb.append("• Quest连续：").append(status.getQuestConsecutiveCount()).append("/").append(QUEST_MAX_CONSECUTIVE).append("章\n");
        sb.append("• Fire断档：").append(status.getFireGapCount()).append("/").append(FIRE_MAX_GAP).append("章\n");
        sb.append("• Constellation断档：").append(status.getConstellationGapCount()).append("/").append(CONSTELLATION_MAX_GAP).append("章\n");
        sb.append("• 当前占比：Quest ").append(String.format("%.0f", status.getQuestRatio() * 100)).append("%");
        sb.append(" | Fire ").append(String.format("%.0f", status.getFireRatio() * 100)).append("%");
        sb.append(" | Constellation ").append(String.format("%.0f", status.getConstellationRatio() * 100)).append("%\n");
        
        StrandType recommended = recommendNextStrand(novelId);
        sb.append("• 推荐下章类型：").append(formatStrandType(recommended)).append("\n");
        
        return sb.toString();
    }
    
    private StrandType parseStrandType(String rhythmTag) {
        if (rhythmTag == null) {
            return StrandType.QUEST;
        }
        try {
            return StrandType.valueOf(rhythmTag.toUpperCase());
        } catch (IllegalArgumentException e) {
            return StrandType.QUEST;
        }
    }
    
    private String formatStrandType(StrandType type) {
        return switch (type) {
            case QUEST -> "Quest（主线）";
            case FIRE -> "Fire（感情线）";
            case CONSTELLATION -> "Constellation（世界观）";
        };
    }
}
