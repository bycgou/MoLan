# 文风卡片采样策略优化方案

## 当前问题

| 问题 | 影响 |
|------|------|
| 每章500字 | 样本不足，无法完整体现风格 |
| 只采样9章 | 覆盖率低（0.9% for 1000章小说） |
| 固定采样数 | 无论小说多长都只采9章 |

## 优化方案

### 方案1：按小说长度自适应采样

```java
private List<String> sampleChapters(String text, int targetSampleWords) {
    // 计算章节数
    String[] chapters = text.split("(?=第[一二三四五六七八九十百千\\d]+章)");
    int totalChapters = chapters.length;
    
    // 自适应采样数量
    int sampleCount;
    int wordsPerSample;
    
    if (totalChapters <= 10) {
        // 短篇小说：全采
        sampleCount = totalChapters;
        wordsPerSample = 1000;
    } else if (totalChapters <= 50) {
        // 中篇：采20%
        sampleCount = totalChapters / 5;
        wordsPerSample = 1500;
    } else if (totalChapters <= 200) {
        // 长篇：采15-20章
        sampleCount = Math.min(20, totalChapters / 10);
        wordsPerSample = 2000;
    } else {
        // 超长篇：采30章
        sampleCount = 30;
        wordsPerSample = 2000;
    }
    
    // 均匀分布采样（不只是前中后）
    List<Integer> sampleIndices = distributeSamples(totalChapters, sampleCount);
    
    List<String> samples = new ArrayList<>();
    for (int idx : sampleIndices) {
        samples.add(truncate(chapters[idx], wordsPerSample));
    }
    
    return samples;
}
```

### 方案2：分层采样策略

```
小说结构：
├── 开篇（前10%）：建立基调
├── 发展（10%-40%）：风格稳定
├── 高潮（40%-70%）：情绪密集
├── 结局（70%-100%）：收束风格

采样策略：
├── 开篇：采3章 × 2000字
├── 发展：采5章 × 2000字
├── 高潮：采5章 × 2000字
├── 结局：采3章 × 2000字
└── 总计：16章 × 2000字 = 32000字
```

### 方案3：智能采样（推荐）

```java
/**
 * 智能采样：根据章节类型选择代表性章节
 */
private List<String> smartSample(String text) {
    List<String> samples = new ArrayList<>();
    
    // 1. 按章节分割
    String[] chapters = text.split("(?=第[一二三四五六七八九十百千\\d]+章)");
    
    // 2. 分类章节类型
    List<Integer> dialogueChapters = new ArrayList<>();  // 对话密集
    List<Integer> actionChapters = new ArrayList<>();    // 动作密集
    List<Integer> descriptionChapters = new ArrayList<>(); // 描写密集
    
    for (int i = 0; i < chapters.length; i++) {
        String chapter = chapters[i];
        double dialogueRatio = calcDialogueRatio(chapter);
        
        if (dialogueRatio > 0.4) {
            dialogueChapters.add(i);
        } else if (containsActionWords(chapter)) {
            actionChapters.add(i);
        } else {
            descriptionChapters.add(i);
        }
    }
    
    // 3. 每种类型采样代表章节
    samples.addAll(sampleFromList(chapters, dialogueChapters, 5, 2000));
    samples.addAll(sampleFromList(chapters, actionChapters, 5, 2000));
    samples.addAll(sampleFromList(chapters, descriptionChapters, 5, 2000));
    
    // 4. 确保包含首尾章节
    samples.add(0, truncate(chapters[0], 2000));  // 开头
    samples.add(truncate(chapters[chapters.length - 1], 2000));  // 结尾
    
    return samples;  // 约17章 × 2000字 = 34000字
}
```

## Token消耗对比

| 方案 | 采样字数 | 约Token数 | LLM成本 |
|------|---------|----------|---------|
| 当前方案 | 4500字 | ~6000 | 低 |
| 方案1（自适应） | 40000字 | ~50000 | 中 |
| 方案2（分层） | 32000字 | ~40000 | 中 |
| 方案3（智能） | 34000字 | ~42000 | 中 |

## 推荐实施方案

**短期优化（立即可做）：**
1. 每章采样从500字增加到2000字
2. 采样章节数从9章增加到20章

**中期优化：**
1. 实现自适应采样（根据总章节数调整）
2. 添加章节类型分类

**长期优化：**
1. 实现智能采样（按对话/动作/描写分类）
2. 多次LLM调用（开篇、发展、高潮分别分析）
