package com.novel.system.mapper;

import com.novel.system.entity.mysql.StyleCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文风卡片Mapper
 */
@Mapper
public interface StyleCardMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<StyleCard> {

    /**
     * 根据创建者查询卡片列表
     */
    List<StyleCard> selectByCreatedBy(@Param("userId") String userId);

    /**
     * 查询公开的卡片列表
     */
    List<StyleCard> selectPublicCards(@Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 根据标签搜索
     */
    List<StyleCard> searchByTag(@Param("tag") String tag);

    /**
     * 获取所有卡片ID（绕过 JacksonTypeHandler，用于数据迁移）
     */
    @Select("SELECT card_id FROM style_card")
    List<String> selectAllCardIds();

    /**
     * 获取单个卡片的 JSON 字段原始值（String 类型，绕过 JacksonTypeHandler）
     */
    @Select("SELECT samples_json FROM style_card WHERE card_id = #{cardId}")
    String selectSamplesJsonRaw(@Param("cardId") String cardId);

    @Select("SELECT stats_json FROM style_card WHERE card_id = #{cardId}")
    String selectStatsJsonRaw(@Param("cardId") String cardId);

    /**
     * 修复单个卡片的 JSON 字段：去掉外层引号，保留内部转义由应用层处理。
     * 仅对以引号开头的值生效（即双重编码的值），已经是合法 JSON 的值不受影响。
     */
    @Update("UPDATE style_card SET " +
            "samples_json = CASE WHEN samples_json LIKE '\"%' THEN SUBSTRING(samples_json, 2, LENGTH(samples_json) - 2) ELSE samples_json END, " +
            "stats_json = CASE WHEN stats_json LIKE '\"%' THEN SUBSTRING(stats_json, 2, LENGTH(stats_json) - 2) ELSE stats_json END, " +
            "rhythm_rules = CASE WHEN rhythm_rules LIKE '\"%' THEN SUBSTRING(rhythm_rules, 2, LENGTH(rhythm_rules) - 2) ELSE rhythm_rules END, " +
            "dialogue_rules = CASE WHEN dialogue_rules LIKE '\"%' THEN SUBSTRING(dialogue_rules, 2, LENGTH(dialogue_rules) - 2) ELSE dialogue_rules END, " +
            "description_rules = CASE WHEN description_rules LIKE '\"%' THEN SUBSTRING(description_rules, 2, LENGTH(description_rules) - 2) ELSE description_rules END, " +
            "emotion_rules = CASE WHEN emotion_rules LIKE '\"%' THEN SUBSTRING(emotion_rules, 2, LENGTH(emotion_rules) - 2) ELSE emotion_rules END, " +
            "structure_rules = CASE WHEN structure_rules LIKE '\"%' THEN SUBSTRING(structure_rules, 2, LENGTH(structure_rules) - 2) ELSE structure_rules END, " +
            "forbidden_rules = CASE WHEN forbidden_rules LIKE '\"%' THEN SUBSTRING(forbidden_rules, 2, LENGTH(forbidden_rules) - 2) ELSE forbidden_rules END, " +
            "preferred_rules = CASE WHEN preferred_rules LIKE '\"%' THEN SUBSTRING(preferred_rules, 2, LENGTH(preferred_rules) - 2) ELSE preferred_rules END " +
            "WHERE card_id = #{cardId}")
    int fixDoubleEncodedJson(@Param("cardId") String cardId);
}
