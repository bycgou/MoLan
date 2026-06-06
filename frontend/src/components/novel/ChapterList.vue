<template>
  <el-card class="chapter-card">
    <template #header>
      <div class="card-header">
        <span>章节列表</span>
        <div class="header-actions">
          <el-button type="warning" @click="$emit('recycle-bin')" plain size="small" aria-label="打开章节回收站">
            <el-icon><Delete /></el-icon> 回收站
          </el-button>
          <el-button type="info" @click="$emit('export')" plain size="small" aria-label="导出小说为文本文件">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <el-button type="info" @click="$emit('geek-mode')" size="small" aria-label="打开极客模式，使用外部AI创作">
            <el-icon><Monitor /></el-icon> 极客模式
          </el-button>
          <el-button type="success" @click="$emit('manual-write')" size="small" aria-label="手动撰写新章节">
            <el-icon><Edit /></el-icon> 手动撰写
          </el-button>
          <el-button type="primary" @click="$emit('ai-generate')" size="small" aria-label="使用AI生成新章节">
            <el-icon><MagicStick /></el-icon> AI生成
          </el-button>
          <el-button type="info" @click="$emit('show-graph')" plain size="small" aria-label="查看人物关系图谱">
            <el-icon><Connection /></el-icon> 图谱
          </el-button>
          <el-button type="info" @click="$emit('show-timeline')" plain size="small" aria-label="查看小说世界时间线">
            <el-icon><Calendar /></el-icon> 时间线
          </el-button>
        </div>
      </div>
    </template>
    <div class="chapter-list" v-loading="loading">
      <div v-for="chapter in chapters" :key="chapter.chapterId" class="chapter-item">
        <div class="chapter-info">
          <span class="chapter-num">第{{ chapter.chapterNum }}章</span>
          <span class="chapter-title">{{ chapter.chapterTitle }}</span>
          <el-tag size="small" :type="getStrandType(chapter.strandType)">
            {{ formatStrand(chapter.strandType) }}
          </el-tag>
          <span class="chapter-words">{{ chapter.wordCount || 0 }}字</span>
        </div>
        <div class="chapter-actions">
          <el-button size="small" @click="$emit('view-chapter', chapter.chapterNum)">查看</el-button>
          <el-button size="small" @click="$emit('edit-chapter', chapter.chapterNum)">编辑</el-button>
          <el-button size="small" type="warning" plain @click="$emit('rewrite-chapter', chapter.chapterNum)">重写</el-button>
          <el-button size="small" @click="$emit('analyze-hook', chapter.chapterNum)">钩子</el-button>
          <el-button size="small" type="danger" plain @click="confirmDelete(chapter.chapterNum)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="chapters.length === 0" description="暂无章节，点击生成开始创作！" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { Edit, Monitor, MagicStick, Delete, Download, Connection, Calendar } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import type { ChapterListItem } from '@/types'
import { getStrandType, formatStrand } from '@/utils/format'

withDefaults(defineProps<{
  chapters: ChapterListItem[]
  loading?: boolean
}>(), {
  loading: false
})

const emit = defineEmits<{
  'recycle-bin': []
  export: []
  'geek-mode': []
  'manual-write': []
  'ai-generate': []
  'show-graph': []
  'show-timeline': []
  'view-chapter': [chapterNum: number]
  'edit-chapter': [chapterNum: number]
  'rewrite-chapter': [chapterNum: number]
  'analyze-hook': [chapterNum: number]
  'delete-chapter': [chapterNum: number]
}>()

async function confirmDelete(chapterNum: number) {
  try {
    await ElMessageBox.confirm(
      `确定要删除第 ${chapterNum} 章吗？删除后可在回收站中恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    emit('delete-chapter', chapterNum)
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chapter-list {
  max-height: 600px;
  overflow-y: auto;
}

.chapter-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.chapter-item:last-child {
  border-bottom: none;
}

.chapter-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.chapter-num {
  font-weight: bold;
  color: #409EFF;
  white-space: nowrap;
}

.chapter-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-words {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.chapter-actions {
  display: flex;
  gap: 6px;
  margin-left: 12px;
}
</style>
