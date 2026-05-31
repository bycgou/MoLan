package com.novel.system.interceptor;

import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.StyleCard;
import com.novel.system.mapper.LlmConfigMapper;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.mapper.StyleCardMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwnerAccessInterceptor implements HandlerInterceptor {

    private final NovelBaseMapper novelBaseMapper;
    private final LlmConfigMapper llmConfigMapper;
    private final StyleCardMapper styleCardMapper;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            // 未认证用户不拦截（由 SecurityFilterChain 处理）
            return true;
        }

        Map<String, String> uriVars = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVars == null) {
            return true;
        }

        // Novel 归属校验
        String novelId = uriVars.get("novelId");
        if (novelId != null && !checkNovelOwnership(novelId, userId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权访问该小说\",\"data\":null}");
            return false;
        }

        // LLM Config 归属校验
        String configId = uriVars.get("configId");
        if (configId != null && !checkConfigOwnership(configId, userId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权访问该配置\",\"data\":null}");
            return false;
        }

        // Style Card 归属校验
        String cardId = uriVars.get("cardId");
        if (cardId != null && !checkStyleCardOwnership(cardId, userId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权访问该文风卡\",\"data\":null}");
            return false;
        }

        return true;
    }

    private boolean checkNovelOwnership(String novelId, String userId) {
        NovelBase novel = novelBaseMapper.selectById(novelId);
        return novel != null && userId.equals(novel.getUserId());
    }

    private boolean checkConfigOwnership(String configId, String userId) {
        LlmConfig config = llmConfigMapper.selectById(configId);
        return config != null && userId.equals(config.getUserId());
    }

    private boolean checkStyleCardOwnership(String cardId, String userId) {
        StyleCard card = styleCardMapper.selectById(cardId);
        return card != null && userId.equals(card.getCreatedBy());
    }
}
