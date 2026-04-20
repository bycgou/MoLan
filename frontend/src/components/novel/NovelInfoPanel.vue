<template>
  <el-card class="info-card">
    <template #header>
      <div class="card-header">
        <span>小说信息</span>
        <div>
          <el-button type="primary" size="small" @click="$emit('edit')">编辑</el-button>
          <el-button size="small" @click="$emit('manage-clues')">伏笔</el-button>
          <el-button size="small" @click="$emit('manage-chars')">人物</el-button>
        </div>
      </div>
    </template>
    <div class="novel-info" v-if="novel">
      <h2>{{ novel.novelName }}</h2>
      <el-tag :type="getNovelStatusType(novel.status)">{{ getNovelStatusText(novel.status) }}</el-tag>
      <div class="info-item"><strong>类型：</strong>{{ novel.genre || '未设置' }}</div>
      <div class="info-item"><strong>章节：</strong>{{ novel.chapterCount || 0 }} 章</div>
      <div class="info-item"><strong>字数：</strong>{{ formatWordCount(novel.wordCount || 0) }}</div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { NovelVO } from '@/types'
import { getNovelStatusType, getNovelStatusText, formatWordCount } from '@/utils/format'

defineProps<{
  novel: NovelVO | null
}>()

defineEmits<{
  edit: []
  'manage-clues': []
  'manage-chars': []
}>()
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-item {
  margin-top: 8px;
  font-size: 14px;
  color: #606266;
}

.novel-info h2 {
  margin: 0 0 8px;
}
</style>
