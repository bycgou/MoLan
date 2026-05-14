package com.novel.system.mcp.server.tool;

import com.novel.system.dto.style.StyleCheckResult;
import com.novel.system.entity.mysql.StyleCard;
import com.novel.system.mcp.server.McpServerConfig;
import com.novel.system.service.style.StyleCardService;
import com.novel.system.service.style.StyleCheckerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class StyleMcpTools extends BaseMcpTools {

    private final StyleCardService styleCardService;
    private final StyleCheckerService styleCheckerService;

    @Autowired
    public StyleMcpTools(McpServerConfig mcpConfig,
                         StyleCardService styleCardService,
                         StyleCheckerService styleCheckerService) {
        super(mcpConfig);
        this.styleCardService = styleCardService;
        this.styleCheckerService = styleCheckerService;
    }

    @Override
    public void registerAll() {
        addTool("moyun_list_style_cards",
            "获取用户的文风卡片列表",
            Map.of(), List.of(),
            args -> listStyleCards());

        addTool("moyun_quick_style_check",
            "快速文风校验，检查文本是否符合指定文风卡片的风格",
            Map.of(
                "text", Map.of("type", "string", "description", "待校验的文本"),
                "cardId", Map.of("type", "string", "description", "文风卡片ID")
            ),
            List.of("text", "cardId"),
            args -> quickStyleCheck(
                (String) args.get("text"),
                (String) args.get("cardId")));

        addTool("moyun_deep_style_check",
            "深度文风校验，详细分析文本与目标文风的差异",
            Map.of(
                "text", Map.of("type", "string", "description", "待校验的文本"),
                "cardId", Map.of("type", "string", "description", "文风卡片ID")
            ),
            List.of("text", "cardId"),
            args -> deepStyleCheck(
                (String) args.get("text"),
                (String) args.get("cardId")));
    }

    private String listStyleCards() {
        List<StyleCard> cards = styleCardService.getUserCards(resolveUserId());
        if (cards.isEmpty()) return "暂无文风卡片";
        StringBuilder sb = new StringBuilder();
        for (StyleCard card : cards) {
            sb.append("- [").append(card.getCardId()).append("] ")
              .append(card.getName()).append("\n");
        }
        return sb.toString();
    }

    private String quickStyleCheck(String text, String cardId) {
        StyleCheckResult result = styleCheckerService.quickCheck(text, cardId);
        return "文风匹配度: " + result.getScore() + "%\n" +
               "建议: " + String.join("; ", result.getSuggestions());
    }

    private String deepStyleCheck(String text, String cardId) {
        StyleCheckResult result = styleCheckerService.deepCheck(text, cardId);
        StringBuilder sb = new StringBuilder();
        sb.append("文风匹配度: ").append(result.getScore()).append("%\n");
        sb.append("是否通过: ").append(result.isPass() ? "是" : "否").append("\n");
        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            sb.append("问题: ").append(String.join("; ", result.getIssues())).append("\n");
        }
        if (result.getSuggestions() != null && !result.getSuggestions().isEmpty()) {
            sb.append("建议: ").append(String.join("; ", result.getSuggestions())).append("\n");
        }
        return sb.toString();
    }
}
