<template>
  <div class="style-card-library">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>🎨 我的文风卡片库</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        创建新卡片
      </el-button>
    </div>

    <!-- 卡片列表 -->
    <div class="card-list" v-loading="loading">
      <el-empty v-if="cards.length === 0 && !loading" description="还没有文风卡片，快来创建一个吧~">
        <el-button type="primary" @click="showCreateDialog = true">创建第一个卡片</el-button>
      </el-empty>

      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="card in cards" :key="card.cardId">
          <el-card class="style-card" shadow="hover">
            <!-- 状态标签 -->
            <el-tag 
              :type="getStatusType(card.status)" 
              size="small" 
              class="status-tag"
            >
              {{ getStatusLabel(card.status) }}
            </el-tag>

            <!-- 卡片名称 -->
            <h3 class="card-name">{{ card.name }}</h3>

            <!-- 来源小说 -->
            <p class="source-novel" v-if="card.sourceNovel">
              📖 {{ card.sourceNovel }}
            </p>

            <!-- 风格描述 -->
            <p class="style-desc">
              {{ truncateText(card.styleDescription, 60) }}
            </p>

            <!-- 标签 -->
            <div class="tags" v-if="card.tags">
              <el-tag 
                v-for="tag in card.tags.split(',')" 
                :key="tag" 
                size="small" 
                type="info"
              >
                {{ tag.trim() }}
              </el-tag>
            </div>

            <!-- 统计信息 -->
            <div class="stats">
              <span>⭐ {{ card.avgRating?.toFixed(1) || '暂无' }}</span>
              <span>👤 {{ card.usageCount ?? 0 }}人使用</span>
            </div>

            <!-- 操作按钮 -->
            <div class="actions">
              <el-button 
                type="primary" 
                size="small" 
                @click="useCard(card)"
                :disabled="card.status !== 1"
              >
                使用
              </el-button>
              <el-button size="small" @click="previewCard(card)">预览</el-button>
              <el-dropdown trigger="click" @command="handleCommand($event, card)">
                <el-button size="small">
                  <el-icon><More /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="togglePublic">
                      {{ card.isPublic ? '设为私密' : '设为公开' }}
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 创建卡片对话框 -->
    <el-dialog 
      v-model="showCreateDialog" 
      title="创建文风卡片" 
      width="600px"
      :close-on-click-modal="false"
    >
      <el-tabs v-model="createTab">
        <!-- 方式1：粘贴文本 -->
        <el-tab-pane label="粘贴文本" name="text">
          <el-form :model="createForm" label-width="100px">
            <el-form-item label="卡片名称">
              <el-input 
                v-model="createForm.novelName" 
                placeholder="例如：诡秘之主"
              />
            </el-form-item>
            <el-form-item label="小说文本">
              <el-input
                v-model="createForm.text"
                type="textarea"
                :rows="10"
                placeholder="粘贴小说文本（建议至少5000字，包含前中后各部分）"
              />
              <div class="text-tip">
                当前字数：{{ createForm.text.length }} 字
                <span v-if="createForm.text.length < 5000" class="warning">
                  （建议至少5000字以获得更好的分析效果）
                </span>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 方式2：上传文件 -->
        <el-tab-pane label="上传文件" name="file">
          <el-form :model="fileForm" label-width="100px">
            <el-form-item label="卡片名称">
              <el-input 
                v-model="fileForm.novelName" 
                placeholder="例如：诡秘之主"
              />
            </el-form-item>
            <el-form-item label="上传文件">
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                accept=".txt"
                :on-change="handleFileChange"
                :on-exceed="handleExceed"
              >
                <template #trigger>
                  <el-button type="primary">选择文件</el-button>
                </template>
                <template #tip>
                  <div class="el-upload__tip">
                    仅支持 .txt 格式，最大50MB
                  </div>
                </template>
              </el-upload>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button 
          type="primary" 
          @click="handleCreate" 
          :loading="creating"
          :disabled="!canCreate"
        >
          开始分析
        </el-button>
      </template>
    </el-dialog>

    <!-- 卡片预览对话框 -->
    <el-dialog 
      v-model="showPreviewDialog" 
      title="文风卡片预览" 
      width="800px"
    >
      <div class="card-preview" v-if="previewingCard">
        <h3>{{ previewingCard.name }}</h3>
        
        <el-descriptions :column="2" border>
          <el-descriptions-item label="来源小说">
            {{ previewingCard.sourceNovel || '未知' }}
          </el-descriptions-item>
          <el-descriptions-item label="使用次数">
            {{ previewingCard.usageCount ?? 0 }}
          </el-descriptions-item>
        </el-descriptions>

        <h4>📝 风格描述</h4>
        <p>{{ previewingCard.styleDescription || '暂无描述' }}</p>

        <h4>📊 统计数据</h4>
        <div class="stats-grid" v-if="previewingCard.statsJson">
          <div class="stat-item">
            <span class="label">平均句长</span>
            <span class="value">{{ getStatValue(previewingCard.statsJson, 'avgSentenceLength') }} 字</span>
          </div>
          <div class="stat-item">
            <span class="label">短句占比</span>
            <span class="value">{{ getStatPercent(previewingCard.statsJson, 'shortSentenceRatio') }}%</span>
          </div>
          <div class="stat-item">
            <span class="label">对话比例</span>
            <span class="value">{{ getStatPercent(previewingCard.statsJson, 'dialogueRatio') }}%</span>
          </div>
          <div class="stat-item">
            <span class="label">段落长度</span>
            <span class="value">{{ getStatValue(previewingCard.statsJson, 'avgParagraphLength') }} 字</span>
          </div>
        </div>

        <h4>🎭 风格参考片段</h4>
        <div class="samples" v-if="getSamples(previewingCard).length">
          <div 
            class="sample-item" 
            v-for="(sample, index) in getSamples(previewingCard)" 
            :key="index"
          >
            <el-tag size="small">{{ sample.type }}</el-tag>
            <p class="sample-text">{{ sample.text }}</p>
            <p class="sample-annotation" v-if="sample.annotation">
              💡 {{ sample.annotation }}
            </p>
          </div>
        </div>
        <el-empty v-else description="暂无样本" :image-size="60" />

        <h4>🚫 禁用词汇</h4>
        <div class="forbidden-words" v-if="getForbiddenWords(previewingCard).length">
          <el-tag 
            v-for="word in getForbiddenWords(previewingCard)" 
            :key="word" 
            type="danger" 
            size="small"
          >
            {{ word }}
          </el-tag>
        </div>
        <p v-else>暂无</p>
      </div>

      <template #footer>
        <el-button @click="showPreviewDialog = false">关闭</el-button>
        <el-button 
          type="primary" 
          @click="useCard(previewingCard!)"
          :disabled="previewingCard?.status !== 1"
        >
          使用此风格
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, More } from '@element-plus/icons-vue'
import { styleApi, type StyleCard } from '@/api/style'

const router = useRouter()

const loading = ref(false)
const cards = ref<StyleCard[]>([])
const showCreateDialog = ref(false)
const showPreviewDialog = ref(false)
const previewingCard = ref<StyleCard | null>(null)
const creating = ref(false)
const createTab = ref('text')
const uploadRef = ref()
let pollingTimer: ReturnType<typeof setInterval> | null = null

const createForm = ref({
  novelName: '',
  text: ''
})

const fileForm = ref({
  novelName: ''
})
const selectedFile = ref<File | null>(null)

const canCreate = computed(() => {
  if (createTab.value === 'text') {
    return createForm.value.novelName && createForm.value.text.length >= 100
  } else {
    return fileForm.value.novelName && selectedFile.value
  }
})

const getStatusLabel = (status: number) => {
  const labels: Record<number, string> = { 0: '分析中', 1: '可用', 2: '分析失败' }
  return labels[status] || '未知'
}

const getStatusType = (status: number) => {
  const types: Record<number, string> = { 0: 'warning', 1: 'success', 2: 'danger' }
  return types[status] || 'info'
}

const hasAnalyzingCards = () => {
  return cards.value.some(card => card.status === 0)
}

const startPolling = () => {
  if (pollingTimer) return
  pollingTimer = setInterval(async () => {
    if (!hasAnalyzingCards()) {
      stopPolling()
      return
    }
    try {
      const res = await styleApi.getUserCards()
      if (res.code === 200) {
        cards.value = res.data
      }
    } catch {
      // ignore polling errors
    }
  }, 5000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const loadCards = async () => {
  loading.value = true
  try {
    const res = await styleApi.getUserCards()
    if (res.code === 200) {
      cards.value = res.data
    }
    if (hasAnalyzingCards()) {
      startPolling()
    }
  } catch (error) {
    console.error('加载卡片列表失败:', error)
  } finally {
    loading.value = false
  }
}

const truncateText = (text: string | undefined, maxLength: number) => {
  if (!text) return '暂无描述'
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text
}

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const handleCreate = async () => {
  creating.value = true
  try {
    let res
    if (createTab.value === 'text') {
      res = await styleApi.createFromText({
        novelName: createForm.value.novelName,
        text: createForm.value.text
      })
    } else {
      if (!selectedFile.value) {
        ElMessage.error('请选择文件')
        return
      }
      res = await styleApi.createFromFile(selectedFile.value, fileForm.value.novelName)
    }

    if (res.code === 200) {
      ElMessage.success('文风卡片创建成功，正在后台分析...')
      showCreateDialog.value = false
      resetForms()
      await loadCards()
      startPolling()
    } else {
      ElMessage.error(res.message || '创建失败')
    }
  } catch (error) {
    console.error('创建卡片失败:', error)
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

const resetForms = () => {
  createForm.value = { novelName: '', text: '' }
  fileForm.value = { novelName: '' }
  selectedFile.value = null
  uploadRef.value?.clearFiles()
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
  return (value * 100).toFixed(1)
}

const getSamples = (card: any) => {
  if (!card) return []
  if (card.samplesJson && Array.isArray(card.samplesJson)) {
    return card.samplesJson
  }
  return []
}

const getForbiddenWords = (card: any) => {
  if (!card) return []
  const rules = card.forbiddenRules
  if (!rules) return []
  if (Array.isArray(rules.words)) return rules.words
  return []
}

const previewCard = (card: StyleCard) => {
  previewingCard.value = card
  showPreviewDialog.value = true
}

const useCard = (card: StyleCard) => {
  router.push({ path: '/create', query: { styleCardId: card.cardId } })
}

const handleCommand = async (command: string, card: StyleCard) => {
  if (command === 'togglePublic') {
    try {
      await styleApi.updatePublic(card.cardId, !card.isPublic)
      ElMessage.success(card.isPublic ? '已设为私密' : '已设为公开')
      loadCards()
    } catch (error) {
      ElMessage.error('操作失败')
    }
  } else if (command === 'delete') {
    try {
      await ElMessageBox.confirm(
        `确定要删除文风卡片「${card.name}」吗？此操作不可恢复。`,
        '确认删除',
        { type: 'warning' }
      )
      await styleApi.delete(card.cardId)
      ElMessage.success('删除成功')
      loadCards()
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error('删除失败')
      }
    }
  }
}

onMounted(() => {
  loadCards()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.style-card-library {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
}

.card-list {
  min-height: 200px;
}

.style-card {
  margin-bottom: 20px;
  position: relative;
}

.status-tag {
  position: absolute;
  top: 10px;
  right: 10px;
}

.card-name {
  margin: 0 0 10px 0;
  font-size: 16px;
}

.source-novel {
  color: #666;
  font-size: 13px;
  margin: 5px 0;
}

.style-desc {
  color: #999;
  font-size: 13px;
  margin: 10px 0;
  min-height: 40px;
}

.tags {
  margin: 10px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.stats {
  display: flex;
  gap: 15px;
  color: #666;
  font-size: 12px;
  margin: 10px 0;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 15px;
}

.text-tip {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}

.text-tip .warning {
  color: #e6a23c;
}

.card-preview h3 {
  margin-top: 0;
}

.card-preview h4 {
  margin: 20px 0 10px 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 15px;
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
  font-size: 18px;
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

.sample-text {
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

.forbidden-words {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
</style>
