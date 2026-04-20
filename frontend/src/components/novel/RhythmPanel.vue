<template>
  <el-card class="rhythm-card">
    <template #header>
      <div class="card-header">
        <span>Strand节奏状态</span>
        <el-button size="small" @click="$emit('refresh')">刷新</el-button>
      </div>
    </template>
    <div class="rhythm-info" v-if="rhythmStatus">
      <div class="rhythm-item">
        <span class="label">上一章类型：</span>
        <el-tag :type="getStrandType(rhythmStatus.lastStrand)">
          {{ formatStrand(rhythmStatus.lastStrand) }}
        </el-tag>
      </div>
      <div class="rhythm-item">
        <span class="label">Quest连续：</span>
        <el-progress
          :percentage="rhythmStatus.questConsecutiveCount / 5 * 100"
          :format="() => rhythmStatus?.questConsecutiveCount + '/5章'"
          :stroke-width="10"
          :status="(rhythmStatus?.questConsecutiveCount ?? 0) >= 4 ? 'exception' : 'success'"
        />
      </div>
      <div class="rhythm-item">
        <span class="label">Fire断档：</span>
        <el-progress
          :percentage="(rhythmStatus?.fireGapCount ?? 0) / 10 * 100"
          :format="() => rhythmStatus?.fireGapCount + '/10章'"
          :stroke-width="10"
          :status="(rhythmStatus?.fireGapCount ?? 0) >= 8 ? 'exception' : 'success'"
        />
      </div>
      <div class="rhythm-item">
        <span class="label">Constellation断档：</span>
        <el-progress
          :percentage="(rhythmStatus?.constellationGapCount ?? 0) / 15 * 100"
          :format="() => rhythmStatus?.constellationGapCount + '/15章'"
          :stroke-width="10"
          :status="(rhythmStatus?.constellationGapCount ?? 0) >= 12 ? 'exception' : 'success'"
        />
      </div>
      <div class="rhythm-ratio">
        <div class="ratio-item">
          <span class="ratio-label">Quest</span>
          <span class="ratio-value">{{ ((rhythmStatus.questRatio ?? 0) * 100).toFixed(0) }}%</span>
        </div>
        <div class="ratio-item">
          <span class="ratio-label">Fire</span>
          <span class="ratio-value">{{ ((rhythmStatus.fireRatio ?? 0) * 100).toFixed(0) }}%</span>
        </div>
        <div class="ratio-item">
          <span class="ratio-label">Constellation</span>
          <span class="ratio-value">{{ ((rhythmStatus.constellationRatio ?? 0) * 100).toFixed(0) }}%</span>
        </div>
      </div>
      <div class="recommend" v-if="recommendedStrand">
        <el-alert
          :title="'推荐下章类型：' + formatStrand(recommendedStrand)"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
    </div>
    <el-empty v-else description="暂无节奏数据" :image-size="60" />
  </el-card>
</template>

<script setup lang="ts">
import type { RhythmStatusVO } from '@/types'
import { getStrandType, formatStrand } from '@/utils/format'

defineProps<{
  rhythmStatus: RhythmStatusVO | null
  recommendedStrand: string
}>()

defineEmits<{
  refresh: []
}>()
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rhythm-item {
  margin-bottom: 12px;
}

.rhythm-item .label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  display: block;
}

.rhythm-ratio {
  display: flex;
  justify-content: space-around;
  margin: 16px 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.ratio-item {
  text-align: center;
}

.ratio-label {
  display: block;
  font-size: 12px;
  color: #909399;
}

.ratio-value {
  display: block;
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.recommend {
  margin-top: 12px;
}
</style>
