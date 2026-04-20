<template>
  <el-card class="characters-card">
    <template #header>
      <div class="card-header">
        <span>人物列表</span>
        <el-button type="primary" size="small" @click="$emit('add')">添加</el-button>
      </div>
    </template>
    <div class="character-list">
      <div v-for="char in characters" :key="char.characterId" class="character-item">
        <div class="char-name">{{ char.name }}</div>
        <div class="char-traits">{{ char.role || '未设置' }}</div>
      </div>
      <el-empty v-if="characters.length === 0" description="暂无人物" :image-size="60" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { CharacterVO } from '@/types'

defineProps<{
  characters: CharacterVO[]
}>()

defineEmits<{
  add: []
}>()
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.character-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.character-item:last-child {
  border-bottom: none;
}

.char-name {
  font-weight: 500;
  font-size: 14px;
}

.char-traits {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
