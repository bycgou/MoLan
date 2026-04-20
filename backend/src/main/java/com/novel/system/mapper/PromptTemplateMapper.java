package com.novel.system.mapper;

import com.novel.system.entity.mysql.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromptTemplateMapper {
    PromptTemplate selectById(@Param("templateId") String templateId);
    PromptTemplate selectActiveByAgentType(@Param("agentType") String agentType);
    int insert(PromptTemplate template);
    int updateById(PromptTemplate template);
}
