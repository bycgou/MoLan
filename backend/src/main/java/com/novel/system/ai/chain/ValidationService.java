package com.novel.system.ai.chain;

/**
 * 校验服务接口
 */
public interface ValidationService {
    
    /**
     * OOC检测
     */
    OOCResult validateOOC(ValidationRequest request);
    
    /**
     * 逻辑校验
     */
    LogicResult validateLogic(ValidationRequest request);
    
    /**
     * 伏笔校验
     */
    ClueResult validateClue(ValidationRequest request);
    
    /**
     * 设定校验
     */
    SettingResult validateSetting(ValidationRequest request);
    
    /**
     * 节奏校验
     */
    RhythmResult validateRhythm(ValidationRequest request);
    
    /**
     * 综合校验（并行执行所有校验）
     */
    ValidationResult validateAll(ValidationRequest request);
}
