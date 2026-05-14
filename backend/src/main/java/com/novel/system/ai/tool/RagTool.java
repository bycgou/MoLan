package com.novel.system.ai.tool;

import com.novel.system.entity.mongo.ChapterSummary;
import com.novel.system.ai.rag.RagService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG检索工具 - Agent可按需调用历史章节检索
 *
 * 【核心作用】
 * 提供历史章节的语义检索能力，让Agent在需要回忆早期情节时主动调用。
 *
 * 【使用场景】
 * - 回忆早期章节的具体细节
 * - 回收很久以前的伏笔
 * - 角色重逢场景需要了解之前的互动
 * - 确保新内容与历史情节保持一致
 *
 * 【与自动注入的区别】
 * 之前：每次生成都自动检索，浪费Token
 * 现在：Agent按需调用，只在需要时检索，节省约800 Token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagTool {

    private final RagService ragService;

    /**
     * 检索历史章节相关内容
     *
     * 【使用场景】
     * 当Agent需要回忆早期情节、回收伏笔、或确保与历史内容一致时调用。
     *
     * 【检索方式】
     * 使用混合检索（向量相似度 + BM25关键词 + RRF融合），返回最相关的3个章节摘要。
     *
     * @param queryText 查询文本，描述需要回忆的内容（如"林辰第一次见到师父的场景"）
     * @param novelId 小说ID
     * @return 格式化的历史章节摘要，包含章节号和内容摘要
     */
    @Tool("检索历史章节内容，用于回忆早期情节、回收伏笔、确保与前文一致。当你需要回忆早期章节的具体细节、回收很久以前的伏笔、角色重逢场景时，调用此工具。")
    public String recallHistory(String queryText, String novelId) {
        log.info("[Tool] 检索历史章节: novelId={}, query={}", novelId, queryText);

        try {
            List<ChapterSummary> results = ragService.hybridSearch(novelId, queryText, 3);

            if (results.isEmpty()) {
                return "未找到相关历史章节内容";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【相关历史章节】\n");

            for (ChapterSummary summary : results) {
                sb.append("第").append(summary.getChapterNum()).append("章: ");
                String content = summary.getContent();
                if (content != null && content.length() > 300) {
                    content = content.substring(0, 300) + "...";
                }
                sb.append(content != null ? content : "无摘要");
                sb.append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("检索历史章节失败: {}", e.getMessage());
            return "检索失败，请根据上下文信息继续创作";
        }
    }
}
