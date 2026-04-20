<template>
  <div class="loading-wrapper" :style="{ minHeight: minHeight }">
    <el-icon class="loading-icon" :size="size" :color="color">
      <Loading />
    </el-icon>
    <span v-if="text" class="loading-text">{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'

withDefaults(defineProps<{
  text?: string
  size?: number
  color?: string
  minHeight?: string
}>(), {
  text: '加载中...',
  size: 32,
  color: '#409EFF',
  minHeight: '200px',
})
</script>

<style scoped>
.loading-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

.loading-text {
  color: #909399;
  font-size: 14px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
