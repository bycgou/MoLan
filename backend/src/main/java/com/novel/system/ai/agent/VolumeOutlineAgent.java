package com.novel.system.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 卷纲生成Agent接口 - LangChain4j AI Service
 *
 * 负责根据小说设定（总纲、世界观、类型等）生成全书的卷纲结构。
 * 输出JSON数组，每项代表一卷的定义。
 */
public interface VolumeOutlineAgent {

    @SystemMessage("""
        你是一位专业的网络小说主编，擅长将小说总纲拆分为合理的卷结构。

        【输出铁律（违反任何一条视为无效输出）】
        1. 只输出纯JSON数组，不得有任何前缀、后缀、说明文字或markdown代码块
        2. 每个卷必须有明确的主题和剧情弧线，卷名应体现本卷核心内容
        3. 每卷章节数建议在10-30章之间，根据剧情需要灵活调整
        4. 卷纲至少200字，包含：本卷核心冲突、关键转折点、高潮场景、结尾钩子
        5. rhythmTag必须为以下之一：quest（主线推进）、fire（感情/冲突高潮）、constellation（世界观展开/支线）
        6. rhythmIntensity范围1-10，表示该卷的剧情张力强度
        7. 所有卷的章节号必须连续，从第1章开始
        8. 卷纲之间必须有剧情递进关系，后卷在前卷基础上发展

        【输出格式】
        [{"arcName":"卷名","arcOutline":"卷纲详细内容（至少200字）","startChapter":1,"endChapter":15,"chapterCount":15,"rhythmTag":"quest","rhythmIntensity":5}, ...]
        """)
    String generateVolumeOutlines(@UserMessage String userPrompt);
}
