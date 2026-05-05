<template>
  <div class="style-selector">
    <el-select
      v-model="selectedCardId"
      placeholder="选择文风卡片（可选）"
      clearable
      filterable
      @change="handleChange"
    >
      <el-option
        v-for="card in cards"
        :key="card.cardId"
        :label="card.name"
        :value="card.cardId"
        :disabled="card.status !== 1"
      >
        <div class="card-option">
          <span class="card-name">{{ card.name }}</span>
          <span class="card-info">
            <el-tag 
              v-if="card.status !== 1" 
              size="small" 
              type="warning"
            >
              分析中
            </el-tag>
            <span v-else class="rating">
              ⭐ {{ card.avgRating?.toFixed(1) || '暂无' }}
            </span>
          </span>
        </div>
      </el-option>

      <template #footer>
        <div class="selector-footer">
          <el-button 
            type="primary" 
            link 
            @click="$emit('create')"
          >
            <el-icon><Plus /></el-icon>
            上传小说创建新风格
          </el-button>
        </div>
      </template>
    </el-select>

    <!-- 选中卡片的预览 -->
    <div class="selected-preview" v-if="selectedCard && showPreview">
      <div class="preview-header">
        <span>📝 风格预览</span>
        <el-button 
          type="primary" 
          link 
          @click="showDetail = true"
        >
          查看详情
        </el-button>
      </div>
      
      <div class="preview-content">
        <p class="style-desc">
          {{ truncateText(selectedCard.styleDescription, 100) }}
        </p>
        
        <div class="preview-stats" v-if="selectedCard.statsJson">
          <span>⚡ 短句占比 {{ getStatPercent(selectedCard.statsJson, 'shortSentenceRatio') }}%</span>
          <span>💬 对话占比 {{ getStatPercent(selectedCard.statsJson, 'dialogueRatio') }}%</span>
        </div>

        <!-- 参考片段预览 -->
        <div class="sample-preview" v-if="getSamples(selectedCard).length">
          <p class="sample-label">参考片段：</p>
          <p class="sample-text">
            "{{ truncateText(getSamples(selectedCard)[0].text, 80) }}"
          </p>
        </div>
      </div>
    </div>

    <!-- 卡片详情对话框 -->
    <el-dialog 
      v-model="showDetail" 
      title="文风卡片详情" 
      width="600px"
      append-to-body
    >
      <div class="card-detail" v-if="selectedCard">
        <h3>{{ selectedCard.name }}</h3>
        
        <h4>📝 风格描述</h4>
        <p>{{ selectedCard.styleDescription || '暂无描述' }}</p>

        <h4>📊 统计数据</h4>
        <div class="stats-grid" v-if="selectedCard.statsJson">
          <div class="stat-item">
            <span class="label">平均句长</span>
            <span class="value">{{ getStatValue(selectedCard.statsJson, 'avgSentenceLength') }} 字</span>
          </div>
          <div class="stat-item">
            <span class="label">短句占比</span>
            <span class="value">{{ getStatPercent(selectedCard.statsJson, 'shortSentenceRatio') }}%</span>
          </div>
          <div class="stat-item">
            <span class="label">对话比例</span>
            <span class="value">{{ getStatPercent(selectedCard.statsJson, 'dialogueRatio') }}%</span>
          </div>
          <div class="stat-item">
            <span class="label">段落长度</span>
            <span class="value">{{ getStatValue(selectedCard.statsJson, 'avgParagraphLength') }} 字</span>
          </div>
        </div>

        <h4>🎭 风格参考片段</h4>
        <div class="samples" v-if="getSamples(selectedCard).length">
          <div 
            class="sample-item" 
            v-for="(sample, index) in getSamples(selectedCard).slice(0, 2)" 
            :key="index"
          >
            <el-tag size="small">{{ sample.type }}</el-tag>
            <p class="sample-text">{{ sample.text }}</p>
            <p class="sample-annotation" v-if="sample.annotation">
              💡 {{ sample.annotation }}
            </p>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { styleApi, type StyleCard } from '@/api/style'

const props = withDefaults(defineProps<{
  modelValue?: string
  showPreview?: boolean
  includePublic?: boolean
}>(), {
  showPreview: true,
  includePublic: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', card: StyleCard | null): void
  (e: 'create'): void
}>()

const cards = ref<StyleCard[]>([])
const selectedCardId = ref<string>('')
const showDetail = ref(false)

const selectedCard = computed(() => {
  return cards.value.find(c => c.cardId === selectedCardId.value) || null
})

const loadCards = async () => {
  try {
    const userRes = await styleApi.getUserCards()
    if (userRes.code === 200) {
      cards.value = userRes.data
    }

    if (props.includePublic) {
      const publicRes = await styleApi.getPublicCards(1, 20)
      if (publicRes.code === 200) {
        const userCardIds = new Set(cards.value.map(c => c.cardId))
        const publicCards = publicRes.data.filter(c => !userCardIds.has(c.cardId))
        cards.value = [...cards.value, ...publicCards]
      }
    }
  } catch (error) {
    console.error('加载文风卡片失败:', error)
  }
}

const handleChange = (value: string) => {
  emit('update:modelValue', value)
  emit('change', selectedCard.value)
}

const truncateText = (text: string | undefined, maxLength: number) => {
  if (!text) return '暂无描述'
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text
}

const getStatValue = (stats: any, key: string, decimals: number = 1) => {
  if (!stats) return '--'
  const value = stats[key]
  if (value === null || value === undefined || isNaN(value)) return '--'
  return typeof value === 'number' ? value.toFixed(decimals) : value
}

const getStatPercent = (stats: any, key: string) => {
  if (!stats) return '--'
  const value = stats[key]
  if (value === null || value === undefined || isNaN(value)) return '--'
  return (value * 100).toFixed(0)
}

const getSamples = (card: StyleCard) => {
  if (!card) return []
  if (card.samplesJson && Array.isArray(card.samplesJson)) {
    return card.samplesJson
  }
  return []
}

watch(() => props.modelValue, (val) => {
  selectedCardId.value = val || ''
})

onMounted(() => {
  loadCards()
  selectedCardId.value = props.modelValue || ''
})
</script>

<style scoped>
.style-selector {
  width: 100%;
}

.card-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-name {
  font-weight: 500;
}

.card-info .rating {
  color: #e6a23c;
  font-size: 12px;
}

.selector-footer {
  padding: 5px 0;
  border-top: 1px solid #eee;
  margin-top: 5px;
}

.selected-preview {
  margin-top: 10px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-weight: 500;
}

.preview-content {
  font-size: 13px;
}

.style-desc {
  color: #666;
  margin: 0 0 10px 0;
}

.preview-stats {
  display: flex;
  gap: 15px;
  color: #909399;
  margin-bottom: 10px;
}

.sample-preview {
  background: white;
  padding: 10px;
  border-radius: 4px;
}

.sample-label {
  color: #909399;
  margin: 0 0 5px 0;
  font-size: 12px;
}

.sample-text {
  margin: 0;
  font-style: italic;
  color: #606266;
}

.card-detail h3 {
  margin-top: 0;
}

.card-detail h4 {
  margin: 20px 0 10px 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin: 10px 0;
}

.stat-item {
  text-align: center;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.stat-item .label {
  display: block;
  font-size: 12px;
  color: #999;
}

.stat-item .value {
  display: block;
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin-top: 5px;
}

.samples {
  margin: 10px 0;
}

.sample-item {
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 10px;
}

.sample-item .sample-text {
  margin: 10px 0;
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.6;
}

.sample-annotation {
  color: #67c23a;
  font-size: 13px;
  margin: 0;
}
</style>
