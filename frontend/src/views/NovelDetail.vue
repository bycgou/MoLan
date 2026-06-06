<template>
  <div class="novel-detail" v-loading="loading">

    <!-- 向导模式：卷纲结构 + 批量生成 -->
    <el-card v-if="isWizardMode && novel" class="wizard-panel" shadow="hover">
      <template #header>
        <div class="wizard-header">
          <span>📚 卷纲结构</span>
          <div>
            <el-button
              type="primary"
              :loading="batchPolling"
              :disabled="batchPolling"
              @click="startBatchGeneration"
            >
              {{ batchPolling ? '生成中...' : '继续批量生成' }}
            </el-button>
            <el-button
              v-if="batchPolling"
              type="danger"
              plain
              @click="cancelBatchGeneration"
            >取消生成</el-button>
          </div>
        </div>
      </template>

      <!-- 批量生成进度 -->
      <div v-if="batchProgress" class="batch-progress">
        <el-progress
          :percentage="Math.round((batchProgress.completedChapters / Math.max(batchProgress.totalChapters, 1)) * 100)"
          :status="batchProgress.status === 'COMPLETED' ? 'success' : batchProgress.status === 'FAILED' ? 'exception' : batchProgress.status === 'CANCELLED' ? 'warning' : ''"
          :stroke-width="20"
          striped
          striped-flow
        />
        <div class="batch-progress-info">
          <span>已完成: {{ batchProgress.completedChapters }}/{{ batchProgress.totalChapters }}</span>
          <span v-if="batchProgress.failedChapters > 0" style="color: #f56c6c;">失败: {{ batchProgress.failedChapters }}</span>
          <span v-if="batchProgress.currentPhase">{{ batchProgress.currentPhase }}</span>
        </div>
        <el-alert
          v-if="batchProgress.status === 'COMPLETED'"
          title="批量生成已完成"
          type="success"
          :closable="false"
          show-icon
          style="margin-top: 8px;"
        />
        <el-alert
          v-if="batchProgress.status === 'FAILED'"
          :title="'生成失败: ' + (batchProgress.errorMessage || '未知错误')"
          type="error"
          :closable="false"
          show-icon
          style="margin-top: 8px;"
        />
        <el-alert
          v-if="batchProgress.status === 'CANCELLED'"
          title="生成已取消"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top: 8px;"
        />
      </div>

      <!-- 卷结构列表 -->
      <div v-if="novel.volumes?.length" class="arc-list">
        <div v-for="vol in novel.volumes" :key="vol.arcId" class="arc-item" @click="toggleArc(vol.arcId)">
          <div class="arc-item-header">
            <el-tag type="primary" size="small">{{ vol.arcName }}</el-tag>
            <span class="arc-chapters">第{{ vol.startChapter }}-{{ vol.endChapter }}章（{{ vol.chapterCount }}章）</span>
            <span class="arc-expand-icon">{{ expandedArcs.has(vol.arcId) ? '▾' : '▸' }}</span>
          </div>
          <div v-if="vol.arcOutline" class="arc-outline">{{ vol.arcOutline }}</div>
          <el-progress
            :percentage="getArcProgress(vol)"
            :stroke-width="8"
            :status="getArcProgress(vol) === 100 ? 'success' : ''"
            style="margin-top: 4px;"
          />
          <!-- 展开的章纲列表 -->
          <div v-if="expandedArcs.has(vol.arcId)" class="arc-chapter-list">
            <div v-for="ch in getArcChapters(vol)" :key="ch.chapterId" class="arc-chapter-item">
              <div class="arc-chapter-main">
                <span class="arc-chapter-num">第{{ ch.chapterNum }}章</span>
                <span class="arc-chapter-title">{{ ch.chapterTitle || '未生成标题' }}</span>
                <el-tag size="small" :type="ch.status === 2 ? 'success' : ch.status === 3 ? 'danger' : 'info'">
                  {{ ch.status === 2 ? '已生成' : ch.status === 1 ? '生成中' : ch.status === 3 ? '失败' : '待生成' }}
                </el-tag>
              </div>
              <div v-if="ch.chapterOutline" class="arc-chapter-outline">{{ ch.chapterOutline }}</div>
            </div>
            <el-empty v-if="getArcChapters(vol).length === 0" description="暂无章节" :image-size="40" />
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无卷纲数据" :image-size="60" />
    </el-card>

    <el-row :gutter="20">
      <!-- 左侧：面板 -->
      <el-col :span="8">
        <NovelInfoPanel
          :novel="novel"
          @edit="router.push(`/novel/${novelId}/edit`)"
          @manage-clues="router.push(`/novel/${novelId}/clues`)"
          @manage-chars="router.push(`/novel/${novelId}/characters`)"
        />
        <RhythmPanel
          :rhythm-status="rhythmStatus"
          :recommended-strand="recommendedStrand"
          @refresh="loadRhythmStatus"
        />
        <CharacterPanel
          :characters="characters"
          @add="router.push(`/novel/${novelId}/characters`)"
        />
      </el-col>

      <!-- 右侧：章节列表 -->
      <el-col :span="16">
        <ChapterList
          :chapters="chapters"
          :loading="loadingChapters"
          @recycle-bin="showRecycleBinDialog"
          @export="exportNovel"
          @geek-mode="geekDialogVisible = true"
          @manual-write="showManualDialog"
          @ai-generate="showGenerateDialog"
          @show-graph="graphDialogVisible = true"
          @show-timeline="showTimelineDialog"
          @view-chapter="(num) => router.push(`/novel/${novelId}/chapter/${num}`)"
          @edit-chapter="(num) => router.push(`/novel/${novelId}/chapter/${num}/edit`)"
          @rewrite-chapter="showRewriteDialog"
          @analyze-hook="analyzeHook"
          @delete-chapter="deleteChapter"
        />
      </el-col>
    </el-row>

    <!-- AI 重写对话框 -->
    <RewriteChapterDialog
      v-model:visible="rewriteDialogVisible"
      :novel-id="novelId"
      :chapter-num="rewriteChapterNum"
      :chapter-outline="rewriteChapterOutline"
      @rewritten="onRewriteComplete"
    />

    <!-- 生成章节对话框 -->
    <GenerateChapterDialog
      v-model:visible="generateDialogVisible"
      :llm-configs="llmConfigs"
      :recommended-strand="recommendedStrand"
      :generating="generating"
      :task-id="generationTaskId"
      :default-chapter-num="(novel?.chapterCount || 0) + 1"
      :outline-data="outlineData"
      @generate-outline="handleGenerateOutline"
      @generate-chapter="handleGenerateChapter"
      @generate-chapter-stream="handleGenerateChapterStream"
      @close="stopPollingStatus"
    />

    <!-- 手动撰写章节对话框 -->
    <el-dialog v-model="manualDialogVisible" title="手动撰写章节" width="800px" top="5vh" @closed="onManualDialogClosed">
      <el-form :model="manualForm" label-width="80px">
        <el-form-item label="章节号">
          <el-input-number v-model="manualForm.chapterNum" :min="1" />
        </el-form-item>
        <el-form-item label="章节标题">
          <el-input v-model="manualForm.chapterTitle" placeholder="请输入章节标题" />
        </el-form-item>
        <el-form-item label="章节内容">
          <div style="border: 1px solid #ccc; width: 100%;">
            <Toolbar
              v-if="manualEditorVisible"
              :editor="manualEditorRef"
              :defaultConfig="toolbarConfig"
              mode="simple"
              style="border-bottom: 1px solid #ccc"
            />
            <Editor
              v-if="manualEditorVisible"
              v-model="manualForm.content"
              :defaultConfig="editorConfig"
              mode="simple"
              style="height: 400px; overflow-y: hidden;"
              @onCreated="onManualEditorCreated"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveManual" :loading="savingManual">保存</el-button>
      </template>
    </el-dialog>

    <!-- 极客模式对话框 -->
    <el-dialog v-model="geekDialogVisible" title="✍️ 极客模式 — 外部 AI 创作" width="850px" top="3vh" @closed="resetGeekForm">
      <el-steps :active="geekStep" finish-status="success" align-center style="margin-bottom: 20px;">
        <el-step title="① 复制提示词" />
        <el-step title="② 粘贴正文" />
        <el-step title="③ 粘贴摘要" />
        <el-step title="④ 保存" />
      </el-steps>

      <!-- 步骤1: 提示词模板 -->
      <el-card shadow="never" class="geek-step-card" :class="{ 'step-done': geekStep > 0 }">
        <template #header>
          <div class="step-header">
            <span>📋 步骤1：复制下方提示词，发给 ChatGPT / Claude 等外部 AI</span>
            <el-button size="small" type="primary" plain @click="copyGeekPrompt">复制提示词</el-button>
          </div>
        </template>
        <el-input
          v-model="geekPromptText"
          type="textarea"
          :rows="4"
          readonly
          style="font-family: monospace; font-size: 12px;"
        />
        <div class="step-tip">💡 把你的章节构思要求填入提示词中的 [在此描述] 位置，然后发给外部 AI</div>
      </el-card>

      <!-- 步骤2: 粘贴正文 -->
      <el-card shadow="never" class="geek-step-card" :class="{ 'step-done': geekStep > 1 }">
        <template #header>
          <div class="step-header">
            <span>📝 步骤2：将 AI 生成的章节正文粘贴到下方</span>
          </div>
        </template>
        <el-form :model="geekForm" label-width="80px">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="章节号">
                <el-input-number v-model="geekForm.chapterNum" :min="1" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="章节标题">
                <el-input v-model="geekForm.chapterTitle" placeholder="如：第一章 觉醒" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="章节正文">
            <el-input v-model="geekForm.content" type="textarea" :rows="8" placeholder="在此粘贴外部 AI 生成的章节正文..." @input="geekStep = Math.max(geekStep, 1)" />
            <div class="step-tip">字数：{{ geekWordCount }} 字</div>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 步骤3: 粘贴摘要JSON -->
      <el-card shadow="never" class="geek-step-card">
        <template #header>
          <div class="step-header">
            <span>🔍 步骤3（可选）：粘贴 AI 返回的剧情摘要 JSON</span>
            <el-button size="small" plain @click="geekJsonVisible = !geekJsonVisible">
              {{ geekJsonVisible ? '收起' : '展开' }}
            </el-button>
          </div>
        </template>
        <div v-if="geekJsonVisible">
          <el-input v-model="geekForm.jsonString" type="textarea" :rows="5" placeholder='粘贴 AI 返回的 JSON，格式如：
{
  "fingerprint": "本章剧情摘要...",
  "keywords": ["关键词1", "关键词2"],
  "characters": ["人物A", "人物B"],
  "locations": ["地点1"],
  "relations": [{"source": "A", "target": "B", "relation": "师徒"}]
}' style="font-family: monospace; font-size: 12px;" />
          <div class="step-tip">💡 不填此项，系统会在保存时自动生成摘要（但准确度不如外部 AI）</div>
        </div>
        <div v-else class="step-tip" style="text-align: center; color: #909399;">
          跳过此步骤，系统将自动提取摘要
        </div>
      </el-card>
      <template #footer>
        <el-button @click="geekDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveGeekChapter" :loading="savingGeek" :disabled="!geekForm.content.trim()">
          💾 保存章节
        </el-button>
      </template>
    </el-dialog>

    <!-- 钩子分析对话框 -->
    <el-dialog v-model="hookDialogVisible" title="钩子强度分析" width="500px">
      <div v-if="hookResult" class="hook-analysis">
        <div v-for="key in ['openingAttraction', 'suspenseSetup', 'emotionTrigger', 'endingHook', 'coolPointDensity']" :key="key" class="hook-item">
          <span class="hook-label">{{ hookLabels[key] }}</span>
          <el-progress :percentage="(hookResult[key] || 0) * 10" :stroke-width="15" />
        </div>
        <div class="hook-total">
          <el-tag :type="hookResult.pullLevel === '高' ? 'success' : hookResult.pullLevel === '中' ? 'warning' : 'danger'" size="large">
            追读力等级：{{ hookResult.pullLevel }}（{{ hookResult.totalScore }}分）
          </el-tag>
        </div>
        <div v-if="hookResult.suggestion" class="hook-suggestion">
          <el-alert :title="hookResult.suggestion" type="info" :closable="false" />
        </div>
      </div>
    </el-dialog>

    <!-- 回收站对话框 -->
    <el-dialog v-model="recycleBinVisible" title="章节回收站" width="700px">
      <el-table :data="recycleBinList" v-loading="loadingRecycleBin">
        <el-table-column prop="chapterNum" label="章节号" width="80">
          <template #default="scope">第{{ scope.row.chapterNum }}章</template>
        </el-table-column>
        <el-table-column prop="chapterTitle" label="标题" min-width="150" />
        <el-table-column prop="wordCount" label="字数" width="100" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button type="success" size="small" @click="restoreChapter(scope.row.chapterId)">恢复</el-button>
            <el-button type="danger" size="small" @click="hardDeleteChapter(scope.row.chapterId)">彻底删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 关系图谱对话框 -->
    <el-dialog v-model="graphDialogVisible" title="人物关系图谱" width="900px" @opened="initGraph" @closed="destroyGraph">
      <div v-loading="loadingGraph" style="min-height: 500px;">
        <div id="graph-container" style="width: 100%; height: 500px;"></div>
      </div>
    </el-dialog>

    <!-- 时间线对话框 -->
    <el-dialog v-model="timelineDialogVisible" title="小说世界时间线" width="600px">
      <el-form :model="timelineForm" label-width="100px" v-loading="loadingTimeline">
        <el-form-item label="当前时间">
          <el-input v-model="timelineForm.currentTime" placeholder="如：午夜 / 清晨" />
        </el-form-item>
        <el-form-item label="当前季节">
          <el-input v-model="timelineForm.season" placeholder="如：春 / 夏" />
        </el-form-item>
        <el-form-item label="当前天气">
          <el-input v-model="timelineForm.weather" placeholder="如：晴 / 大雨" />
        </el-form-item>
        <el-form-item label="本章天数">
          <el-input-number v-model="timelineForm.chapterDay" :min="0" />
        </el-form-item>
        <el-form-item label="累计天数">
          <el-input-number v-model="timelineForm.totalDays" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timelineDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTimeline" :loading="savingTimeline">保存</el-button>
      </template>
    </el-dialog>

    <!-- 流式生成对话框 -->
    <el-dialog v-model="streamDialogVisible" :title="streamingStatus === 'generating' ? 'AI 正在流式生成...' : streamingStatus === 'completed' ? '生成完成' : '已取消'" width="800px" :close-on-click-modal="streamingStatus !== 'generating'" :close-on-press-escape="streamingStatus !== 'generating'" :show-close="streamingStatus !== 'generating'">
      <!-- AuthorAgent 检查点时间线 -->
      <div v-if="streamCheckpoints.length > 0" style="display: flex; flex-wrap: wrap; gap: 8px; padding: 8px 0; margin-bottom: 8px; border-bottom: 1px solid #e4e7ed;">
        <div v-for="(cp, i) in streamCheckpoints" :key="i" style="display: flex; align-items: center; gap: 6px; font-size: 12px; padding: 4px 10px; border-radius: 6px; background: #f5f7fa; border: 1px solid #e4e7ed;" :title="cp.rationale">
          <span style="color: #909399;">{{ { outline: '章纲审查', writing: '正文审查', audit_router: '终审决策' }[cp.node] || cp.node }}</span>
          <span :style="{ color: { APPROVE: '#67c23a', REVISE: '#e6a23c', REJECT: '#f56c6c', REDIRECT: '#409eff' }[cp.action] || '#909399', fontWeight: 600 }">
            {{ { APPROVE: '通过', REVISE: '修订', REJECT: '驳回', REDIRECT: '重定向' }[cp.action] || cp.action }}
          </span>
        </div>
      </div>
      <div style="white-space: pre-wrap; line-height: 2; font-size: 16px; max-height: 500px; overflow-y: auto; padding: 16px; background: #f5f7fa; border-radius: 8px;">
        {{ streamingContent }}<span v-if="streamingStatus === 'generating'" class="cursor-blink">▌</span>
      </div>
      <div v-if="streamValidationSummary" style="margin-top: 12px; padding: 8px 12px; background: #fdf6ec; border-radius: 4px; font-size: 13px; color: #e6a23c;">
        校验提示：{{ streamValidationSummary }}
      </div>
      <template #footer>
        <template v-if="streamingStatus === 'generating'">
          <el-button type="danger" @click="cancelStreamGeneration">取消生成</el-button>
        </template>
        <template v-else-if="streamingStatus === 'completed'">
          <el-button type="primary" @click="handleStreamSave">保存</el-button>
          <el-button @click="handleStreamEdit">编辑</el-button>
          <el-button type="danger" plain @click="handleStreamDelete">删除</el-button>
          <el-button @click="handleStreamRegenerate">重新生成</el-button>
          <el-button @click="handleCloseStreamDialog">关闭</el-button>
        </template>
        <template v-else>
          <el-button v-if="streamingContent" type="primary" @click="handleStreamSaveContent">保存已生成内容</el-button>
          <el-button @click="handleStreamRegenerate">重新生成</el-button>
          <el-button @click="handleCloseStreamDialog">关闭</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IToolbarConfig, IEditorConfig } from '@wangeditor/editor'
import { novelApi, chapterApi, characterApi, llmConfigApi, rhythmApi, hookApi, wizardApi } from '@/api'
import type { NovelVO, ChapterListItem, CharacterVO, LlmConfigVO, RhythmStatusVO } from '@/types'

// Sub-components
import NovelInfoPanel from '@/components/novel/NovelInfoPanel.vue'
import RhythmPanel from '@/components/novel/RhythmPanel.vue'
import CharacterPanel from '@/components/novel/CharacterPanel.vue'
import ChapterList from '@/components/novel/ChapterList.vue'
import GenerateChapterDialog, { type GenerateForm } from '@/components/novel/GenerateChapterDialog.vue'
import RewriteChapterDialog from '@/components/novel/RewriteChapterDialog.vue'

const route = useRoute()
const router = useRouter()
const novelId = route.params.novelId as string

// ---- 状态 ----
const loading = ref(false)
const loadingChapters = ref(false)
const novel = ref<NovelVO | null>(null)
const chapters = ref<ChapterListItem[]>([])
const characters = ref<CharacterVO[]>([])
const llmConfigs = ref<LlmConfigVO[]>([])
const rhythmStatus = ref<RhythmStatusVO | null>(null)
const recommendedStrand = ref('')
const expandedArcs = ref<Set<string>>(new Set())

// ---- 向导模式批量生成 ----
const isWizardMode = computed(() => novel.value?.creationMode === 'wizard')
const batchTaskId = ref('')
const batchProgress = ref<import('@/types').BatchGenerationProgress | null>(null)
const batchPolling = ref(false)
let batchTimer: number | null = null

// ---- 对话框 ----
const generateDialogVisible = ref(false)
const manualDialogVisible = ref(false)
const geekDialogVisible = ref(false)
const hookDialogVisible = ref(false)
const recycleBinVisible = ref(false)
const graphDialogVisible = ref(false)
const timelineDialogVisible = ref(false)
const rewriteDialogVisible = ref(false)
const rewriteChapterNum = ref(0)
const rewriteChapterOutline = ref('')

// ---- 加载状态 ----
const generating = ref(false)
const savingManual = ref(false)
const savingGeek = ref(false)
const loadingGraph = ref(false)
const loadingTimeline = ref(false)
const savingTimeline = ref(false)
const loadingRecycleBin = ref(false)

// ---- 数据 ----
const outlineData = ref<{ outline: string; implementationIdeas: string[] } | null>(null)
const hookResult = ref<Record<string, number> & { pullLevel: string; totalScore: number; suggestion: string } | null>(null)
const recycleBinList = ref<unknown[]>([])
const generationTaskId = ref('')
const generationTimer = ref<number | null>(null)
const pollCount = ref(0)
const MAX_POLL_COUNT = 60 // 最多轮询60次（5分钟）
let graphChartInstance: echarts.ECharts | null = null

const hookLabels: Record<string, string> = {
  openingAttraction: '开头吸引力',
  suspenseSetup: '悬念设置',
  emotionTrigger: '情绪调动',
  endingHook: '结尾钩子',
  coolPointDensity: '爽点密度',
}

// ---- 表单 ----
const manualForm = reactive({ chapterNum: 1, chapterTitle: '', content: '' })
const geekForm = reactive({ chapterNum: 1, chapterTitle: '', content: '', jsonString: '' })
const geekStep = ref(0)
const geekJsonVisible = ref(false)
const geekWordCount = computed(() => geekForm.content.replaceAll(/\s+/g, '').length)
const geekPromptText = computed(() => {
  return `你是一位专业网文作家。请根据以下要求，撰写一章小说正文（2000-3000字）。

要求：[在此描述你的本章写作要求，比如：主角发现了一个神秘遗迹，里面有上古传承]

写完后，请额外输出一个JSON摘要，格式如下（不要省略）：
{
  "fingerprint": "300字以内的剧情摘要",
  "keywords": ["关键词1", "关键词2"],
  "characters": ["出场人物1", "出场人物2"],
  "locations": ["地点1"],
  "relations": [{"source": "A", "target": "B", "relation": "关系描述"}]
}

请先输出正文，然后输出JSON摘要（用---分隔）。`
})
async function copyGeekPrompt() {
  try {
    await navigator.clipboard.writeText(geekPromptText.value)
    ElMessage.success('提示词已复制到剪贴板')
    geekStep.value = Math.max(geekStep.value, 1)
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}
function resetGeekForm() {
  geekForm.chapterNum = (novel.value?.chapterCount || 0) + 1
  geekForm.chapterTitle = ''
  geekForm.content = ''
  geekForm.jsonString = ''
  geekStep.value = 0
  geekJsonVisible.value = false
}
// ---- 流式生成状态 ----
const streamingContent = ref('')
const streamDialogVisible = ref(false)
const streamingStatus = ref<'generating' | 'completed' | 'error'>('generating')
let streamAbortController: AbortController | null = null
const streamChapterNum = ref(0)
const streamValidationSummary = ref('')
const streamCheckpoints = ref<Array<{ node: string; action: string; rationale: string }>>([])
const timelineForm = reactive({ currentTime: '', season: '', weather: '', chapterDay: 0, totalDays: 0 })

// ---- 富文本编辑器 ----
const manualEditorRef = shallowRef<IDomEditor | null>(null)
const manualEditorVisible = ref(false)
const toolbarConfig: Partial<IToolbarConfig> = {
  excludeKeys: ['fullScreen', 'group-video', 'group-image']
}
const editorConfig: Partial<IEditorConfig> = {
  placeholder: '请输入章节内容...',
  MENU_CONF: {}
}

function onManualEditorCreated(editor: IDomEditor) {
  manualEditorRef.value = editor
}

function onManualDialogClosed() {
  manualEditorVisible.value = false
  if (manualEditorRef.value) {
    manualEditorRef.value.destroy()
    manualEditorRef.value = null
  }
}

// ---- 加载数据 ----
async function loadNovel() {
  loading.value = true
  try {
    const res = await novelApi.getNovel(novelId)
    if (res.code === 200) novel.value = res.data
  } finally { loading.value = false }
}

async function loadChapters() {
  loadingChapters.value = true
  try {
    const res = await chapterApi.getChapterList(novelId)
    if (res.code === 200) chapters.value = res.data
  } finally {
    loadingChapters.value = false
  }
}

async function loadCharacters() {
  try {
    const res = await characterApi.getCharacterList(novelId)
    if (res.code === 200) {
      characters.value = res.data
    }
  } catch (e) {
    console.warn('加载人物列表失败:', e)
  }
}

async function loadLlmConfigs() {
  const res = await llmConfigApi.getConfigList()
  if (res.code === 200) llmConfigs.value = res.data.filter((c) => c.isActive === 1)
}

async function loadRhythmStatus() {
  try {
    const res = await rhythmApi.getRhythmStatus(novelId)
    if (res.code === 200) rhythmStatus.value = res.data
    const recRes = await rhythmApi.getRecommendedStrand(novelId)
    if (recRes.code === 200) recommendedStrand.value = recRes.data
  } catch (e) { console.warn('节奏系统加载失败', e) }
}

// ---- 向导模式批量生成 ----
function getArcProgress(vol: import('@/types').VolumeSummary): number {
  if (!chapters.value.length || vol.chapterCount === 0) return 0
  const completed = chapters.value.filter(
    c => c.chapterNum >= vol.startChapter && c.chapterNum <= vol.endChapter && c.status === 2
  ).length
  return Math.round((completed / vol.chapterCount) * 100)
}

function toggleArc(arcId: string) {
  if (expandedArcs.value.has(arcId)) {
    expandedArcs.value.delete(arcId)
  } else {
    expandedArcs.value.add(arcId)
  }
}

function getArcChapters(vol: import('@/types').VolumeSummary) {
  return chapters.value.filter(
    c => c.chapterNum >= vol.startChapter && c.chapterNum <= vol.endChapter
  ).sort((a, b) => a.chapterNum - b.chapterNum)
}

async function startBatchGeneration() {
  try {
    const res = await wizardApi.batchGenerate(novelId)
    if (res.code === 200) {
      batchTaskId.value = res.data as string
      batchPolling.value = true
      ElMessage.info('批量生成已开始，请耐心等待...')
      startBatchPolling()
    } else {
      ElMessage.error(res.message || '启动批量生成失败')
    }
  } catch (e: any) {
    ElMessage.error('启动批量生成失败: ' + (e.message || '未知错误'))
  }
}

function startBatchPolling() {
  stopBatchPolling()
  batchTimer = window.setInterval(async () => {
    if (!batchTaskId.value) return
    try {
      const res = await wizardApi.getBatchStatus(novelId, batchTaskId.value)
      if (res.code === 200) {
        batchProgress.value = res.data as import('@/types').BatchGenerationProgress
        if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(batchProgress.value.status)) {
          stopBatchPolling()
          if (batchProgress.value.status === 'COMPLETED') {
            ElMessage.success('批量生成完成！')
            loadChapters()
            loadNovel()
            loadCharacters()
          }
        }
      }
    } catch { /* ignore */ }
  }, 3000)
}

function stopBatchPolling() {
  batchPolling.value = false
  if (batchTimer) {
    clearInterval(batchTimer)
    batchTimer = null
  }
}

async function cancelBatchGeneration() {
  if (!batchTaskId.value) return
  try {
    await wizardApi.batchCancel(novelId, batchTaskId.value)
    ElMessage.info('已请求取消生成')
    stopBatchPolling()
    batchProgress.value = null
  } catch { ElMessage.error('取消失败') }
}

// ---- 操作 ----
function showGenerateDialog() {
  outlineData.value = null
  generateDialogVisible.value = true
  loadLlmConfigs()
  loadRhythmStatus()
}

function showManualDialog() {
  manualForm.chapterNum = (novel.value?.chapterCount || 0) + 1
  manualForm.chapterTitle = `第${manualForm.chapterNum}章 `
  manualForm.content = ''
  manualEditorVisible.value = true
  manualDialogVisible.value = true
}

function showRewriteDialog(chapterNum: number) {
  const ch = chapters.value.find(c => c.chapterNum === chapterNum)
  rewriteChapterNum.value = chapterNum
  rewriteChapterOutline.value = ch?.chapterOutline || ''
  rewriteDialogVisible.value = true
}

function onRewriteComplete() {
  loadChapters()
  loadNovel()
  loadCharacters()
  loadRhythmStatus()
}

async function handleGenerateOutline(form: GenerateForm) {
  generating.value = true
  try {
    const res = await novelApi.generateOutline(novelId, {
      chapterNum: form.chapterNum,
      userIntent: form.userIntent,
      llmConfigId: form.configId,
    })
    if (res.code === 200) {
      outlineData.value = res.data as typeof outlineData.value
      ElMessage.success('章纲生成成功！')
    } else {
      ElMessage.error(res.message || '生成章纲失败')
    }
  } catch (error: any) {
    console.error('生成章纲失败:', error)
    if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
      ElMessage.error('生成章纲超时，LLM模型响应较慢，请稍后重试')
    } else if (error.response?.status === 500) {
      ElMessage.error(error.response?.data?.message || '服务器内部错误')
    } else if (error.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error('生成章纲失败，请检查网络连接或稍后重试')
    }
  } finally { generating.value = false }
}

async function handleGenerateChapter(form: GenerateForm & { ideaIndex: number }) {
  generating.value = true
  try {
    const res = await chapterApi.generateChapterAsync(novelId, {
      chapterNum: form.chapterNum,
      chapterTitle: '',
      configId: form.configId,
      strandType: form.strandType,
    })
    if (res.code === 200) {
      generationTaskId.value = res.data as string
      ElMessage.info('章节正在后台生成，请稍候...')
      startPollingStatus()
    }
  } catch { ElMessage.error('生成正文失败') }
  finally { generating.value = false }
}

async function handleGenerateChapterStream(form: GenerateForm & { ideaIndex: number }) {
  generating.value = true
  streamingContent.value = ''
  streamingStatus.value = 'generating'
  streamChapterNum.value = form.chapterNum
  streamValidationSummary.value = ''
  streamCheckpoints.value = []

  // 创建 AbortController，支持客户端取消和超时
  streamAbortController = new AbortController()
  const timeoutId = setTimeout(() => streamAbortController?.abort(), 10 * 60 * 1000) // 10min 超时

  try {
    const token = localStorage.getItem('token') || ''
    const body = JSON.stringify({
      novelId,
      chapterNum: form.chapterNum,
      outline: outlineData.value?.outline || '',
      llmConfigId: form.configId,
    })

    const response = await fetch('/api/chapter/stream-write', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body,
      signal: streamAbortController.signal,
    })

    if (!response.ok) {
      const errText = await response.text()
      ElMessage.error(`流式生成失败: ${errText}`)
      streamingStatus.value = 'error'
      return
    }

    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    if (!reader) { streamingStatus.value = 'error'; return }

    generateDialogVisible.value = false
    streamDialogVisible.value = true

    let receivedComplete = false
    let currentEventName = ''  // 当前解析到的事件名
    let reachedCharactersMarker = false  // 是否到达人物JSON分隔符

    function processSSELine(line: string) {
      // 解析命名事件: event: xxx
      if (line.startsWith('event:')) {
        currentEventName = line.slice(6).trim()
        return
      }
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()

        // complete 事件的 data 可以为空，必须在空数据检查前处理
        if (currentEventName === 'complete') {
          if (data) streamValidationSummary.value = data
          receivedComplete = true
          currentEventName = ''
          return
        }

        if (!data) return

        if (currentEventName === 'checkpoint') {
          try {
            const cp = JSON.parse(data)
            streamCheckpoints.value.push(cp)
          } catch { /* ignore */ }
          currentEventName = ''
        } else if (currentEventName === 'validation_result') {
          // validation_result 事件：提取校验摘要（兼容 JSON 格式）
          try {
            const parsed = JSON.parse(data)
            if (parsed.summary) streamValidationSummary.value = parsed.summary
          } catch {
            streamValidationSummary.value = data
          }
          currentEventName = ''
        } else {
          // 普通内容 token — 检测人物JSON分隔符
          if (!reachedCharactersMarker) {
            const markerIdx = data.indexOf('---CHARACTERS---')
            if (markerIdx >= 0) {
              // 分隔符前可能有部分正文内容
              const before = data.substring(0, markerIdx)
              if (before) streamingContent.value += before
              reachedCharactersMarker = true
            } else {
              streamingContent.value += data
            }
          }
          // 分隔符后的 token 是人物JSON，不显示
          currentEventName = ''
        }
      }
    }

    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (buffer.trim()) processSSELine(buffer.trim())
        break
      }
      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        processSSELine(line)
      }
    }

    clearTimeout(timeoutId)

    // 流结束：如果有内容就视为成功（后端可能因异常未发 complete 事件）
    if (receivedComplete || streamingContent.value) {
      streamingStatus.value = 'completed'
      generating.value = false
      loadChapters()
      loadNovel()
      loadCharacters()
      loadRhythmStatus()
    } else {
      streamingStatus.value = 'error'
      generating.value = false
      ElMessage.error('流式生成失败: 未收到任何内容')
    }
  } catch (error: any) {
    clearTimeout(timeoutId)
    // AbortError 是用户主动取消，不算失败
    if (error.name === 'AbortError') {
      streamingStatus.value = 'error'
      generating.value = false
      ElMessage.info('已取消生成')
    } else {
      // 连接异常断开：如果有已接收的内容，视为成功（后端已完成保存）
      if (streamingContent.value) {
        streamingStatus.value = 'completed'
        generating.value = false
        loadChapters()
        loadNovel()
        loadCharacters()
        loadRhythmStatus()
      } else {
        streamingStatus.value = 'error'
        generating.value = false
        ElMessage.error('流式生成失败: ' + (error.message || '未知错误'))
      }
    }
  } finally {
    streamAbortController = null
  }
}

/** 取消流式生成 */
async function cancelStreamGeneration() {
  try {
    // 1. 通知后端设置取消标记（后端会在下一个 token 时检查）
    await fetch(`/api/chapter/cancel?novelId=${novelId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token') || ''}` },
    })
    // 2. abort 前端 fetch 请求，立即中断读取
    streamAbortController?.abort()
    ElMessage.info('已发送取消请求')
  } catch { ElMessage.error('取消失败') }
}

/** 流式完成 -> 保存（内容已在后端自动保存，此处刷新即可） */
async function handleStreamSave() {
  ElMessage.success('章节已自动保存')
  streamDialogVisible.value = false
  loadChapters()
  loadNovel()
}

/** 取消/校验失败后手动保存已生成的内容 */
async function handleStreamSaveContent() {
  if (!streamingContent.value) {
    ElMessage.warning('没有可保存的内容')
    return
  }
  try {
    const token = localStorage.getItem('token') || ''
    const res = await fetch('/api/chapter/stream-save', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({
        novelId,
        chapterNum: String(streamChapterNum.value),
        content: streamingContent.value,
        outline: outlineData.value?.outline || '',
      }),
    })
    const data = await res.json()
    if (data.success) {
      ElMessage.success(`已保存（${data.wordCount}字）`)
      streamDialogVisible.value = false
      loadChapters()
      loadNovel()
    } else {
      ElMessage.error('保存失败: ' + (data.error || '未知错误'))
    }
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

/** 流式完成 -> 跳转编辑页 */
function handleStreamEdit() {
  streamDialogVisible.value = false
  router.push(`/novel/${novelId}/chapter/${streamChapterNum.value}/edit`)
}

/** 流式完成 -> 删除章节 */
async function handleStreamDelete() {
  try {
    await ElMessageBox.confirm('确定删除刚生成的章节？', '提示', { type: 'warning' })
    const res = await chapterApi.deleteChapter(novelId, streamChapterNum.value)
    if (res.code === 200) {
      ElMessage.success('已移入回收站')
      streamDialogVisible.value = false
      loadChapters()
      loadNovel()
    }
  } catch {}
}

/** 流式完成/出错 -> 重新生成 */
function handleStreamRegenerate() {
  streamDialogVisible.value = false
  showGenerateDialog()
}

/** 关闭流式对话框（有内容时需确认） */
async function handleCloseStreamDialog() {
  if (streamingContent.value && streamingStatus.value === 'completed') {
    try {
      await ElMessageBox.confirm(
        '当前生成的内容尚未保存，确定关闭吗？',
        '确认关闭',
        { confirmButtonText: '确定关闭', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return // 用户取消
    }
  }
  streamDialogVisible.value = false
}

function startPollingStatus() {
  stopPollingStatus()
  pollCount.value = 0
  generationTimer.value = window.setInterval(async () => {
    if (!generationTaskId.value) return
    pollCount.value++
    if (pollCount.value >= MAX_POLL_COUNT) {
      stopPollingStatus()
      ElMessage.warning('任务超时，请稍后刷新查看')
      return
    }
    try {
      const res = await chapterApi.getGenerationStatus(novelId, generationTaskId.value)
      if (res.code === 200) {
        const data = res.data as Record<string, unknown>
        if (data?.status === 'completed') {
          stopPollingStatus()
          generateDialogVisible.value = false
          if (data.validationSummary && data.validationSummary !== '所有校验通过') {
            ElMessage.warning(`章节生成完成（有校验提示）: ${data.validationSummary}`)
          } else {
            ElMessage.success('章节生成完成！')
          }
          loadChapters()
          loadNovel()
        } else if (data?.status === 'failed' || data?.status === 'error') {
          stopPollingStatus()
          const errorMsg = (data.errorMessage as string) || '章节生成失败'
          ElMessage.error(`生成失败: ${errorMsg}`)
        }
      }
    } catch { /* ignore */ }
  }, 5000)
}

function stopPollingStatus() {
  if (generationTimer.value) {
    clearInterval(generationTimer.value)
    generationTimer.value = null
  }
}

async function handleSaveManual() {
  if (!manualForm.content.trim()) { ElMessage.error('请输入内容'); return }
  savingManual.value = true
  try {
    const res = await chapterApi.saveManualChapter(novelId, {
      chapterNum: manualForm.chapterNum,
      chapterTitle: manualForm.chapterTitle || '',
      content: manualForm.content,
      summaryData: null,
    })
    if (res.code === 200) {
      ElMessage.success('保存成功')
      manualDialogVisible.value = false
      loadChapters()
      loadNovel()
    }
  } catch { ElMessage.error('保存失败') }
  finally { savingManual.value = false }
}

async function handleSaveGeekChapter() {
  if (!geekForm.content.trim()) { ElMessage.error('请输入正文'); return }
  savingGeek.value = true
  try {
    let summaryData = null
    if (geekForm.jsonString.trim()) {
      try { summaryData = JSON.parse(geekForm.jsonString) }
      catch { ElMessage.error('JSON格式不正确'); savingGeek.value = false; return }
    }
    const res = await chapterApi.saveManualChapter(novelId, {
      chapterNum: geekForm.chapterNum,
      chapterTitle: geekForm.chapterTitle || '',
      content: geekForm.content,
      summaryData,
    })
    if (res.code === 200) {
      ElMessage.success('保存成功')
      geekDialogVisible.value = false
      loadChapters()
      loadNovel()
    }
  } catch { ElMessage.error('保存失败') }
  finally { savingGeek.value = false }
}

async function analyzeHook(chapterNum: number) {
  try {
    const res = await hookApi.analyzeHook(novelId, chapterNum)
    if (res.code === 200) {
      hookResult.value = res.data as typeof hookResult.value
      hookDialogVisible.value = true
    }
  } catch { ElMessage.error('分析失败') }
}

function deleteChapter(chapterNum: number) {
  ElMessageBox.confirm('确定删除该章节？', '提示', { type: 'warning' }).then(async () => {
    try {
      const res = await chapterApi.deleteChapter(novelId, chapterNum)
      if (res.code === 200) { ElMessage.success('已移入回收站'); loadChapters(); loadNovel() }
    } catch { ElMessage.error('删除失败') }
  }).catch(() => {})
}

async function exportNovel() {
  try {
    const response = await novelApi.exportNovel(novelId)
    const blob = new Blob([response as BlobPart], { type: 'text/plain;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    const safeName = (novel.value?.novelName || 'novel').replace(/[\\/:*?"<>|]/g, '_')
    a.href = url; a.download = `${safeName}.txt`
    document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(url); document.body.removeChild(a)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

async function showRecycleBinDialog() {
  recycleBinVisible.value = true
  loadingRecycleBin.value = true
  try {
    const res = await chapterApi.getRecycleBin(novelId)
    if (res.code === 200) recycleBinList.value = res.data
  } finally { loadingRecycleBin.value = false }
}

async function restoreChapter(chapterId: string) {
  try {
    const res = await chapterApi.restoreChapter(novelId, chapterId)
    if (res.code === 200) { ElMessage.success('恢复成功'); showRecycleBinDialog(); loadChapters(); loadNovel() }
  } catch { ElMessage.error('恢复失败') }
}

async function hardDeleteChapter(chapterId: string) {
  ElMessageBox.confirm('彻底删除后无法恢复', '警告', { type: 'error' }).then(async () => {
    try {
      const res = await chapterApi.hardDeleteChapter(novelId, chapterId)
      if (res.code === 200) { ElMessage.success('已彻底删除'); showRecycleBinDialog() }
    } catch { ElMessage.error('删除失败') }
  }).catch(() => {})
}

async function showTimelineDialog() {
  timelineDialogVisible.value = true
  loadingTimeline.value = true
  try {
    const res = await novelApi.getTimeline(novelId)
    if (res.code === 200 && res.data) {
      const data = res.data as typeof timelineForm
      Object.assign(timelineForm, data)
    }
  } finally { loadingTimeline.value = false }
}

async function saveTimeline() {
  savingTimeline.value = true
  try {
    const res = await novelApi.updateTimeline(novelId, timelineForm as unknown as import('@/types').TimelineVO)
    if (res.code === 200) { ElMessage.success('保存成功'); timelineDialogVisible.value = false }
  } catch { ElMessage.error('保存失败') }
  finally { savingTimeline.value = false }
}

async function initGraph() {
  loadingGraph.value = true
  try {
    const res = await novelApi.getNovelGraph(novelId)
    const graphData = res.data
    if (!graphData?.nodes?.length) {
      ElMessage.info('暂无人物图谱数据')
      loadingGraph.value = false
      return
    }
    await nextTick()
    const container = document.getElementById('graph-container')
    if (!container) return
    // 先销毁旧实例
    if (graphChartInstance) {
      graphChartInstance.dispose()
    }
    graphChartInstance = echarts.init(container)
    graphChartInstance.setOption({
      tooltip: {},
      series: [{
        type: 'graph',
        layout: 'force',
        data: graphData.nodes.map((n) => ({ ...n, symbolSize: 40 })),
        links: graphData.edges.map((e) => ({ source: e.source, target: e.target, label: { show: true, formatter: e.relation || e.label || '' } })),
        roam: true,
        label: { show: true, position: 'right' },
        force: { repulsion: 200 },
      }],
    })
  } catch { ElMessage.error('加载图谱失败') }
  finally { loadingGraph.value = false }
}

function destroyGraph() {
  if (graphChartInstance) {
    graphChartInstance.dispose()
    graphChartInstance = null
  }
}

// ---- 生命周期 ----
onMounted(() => {
  loadNovel()
  loadChapters()
  loadCharacters()
  loadRhythmStatus()
})

onBeforeUnmount(() => {
  stopPollingStatus()
  stopBatchPolling()
  if (graphChartInstance) {
    graphChartInstance.dispose()
    graphChartInstance = null
  }
  if (manualEditorRef.value) {
    manualEditorRef.value.destroy()
    manualEditorRef.value = null
  }
})
</script>

<style scoped>
.novel-detail {
  max-width: 1400px;
  margin: 0 auto;
}

.hook-analysis {
  padding: 12px 0;
}

.hook-item {
  margin-bottom: 16px;
}

.hook-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  display: block;
}

.hook-total {
  text-align: center;
  margin: 20px 0 12px;
}

.hook-suggestion {
  margin-top: 12px;
}

/* 极客模式步骤卡片 */
.geek-step-card {
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
  transition: opacity 0.3s;
}

.geek-step-card.step-done {
  opacity: 0.7;
}

.geek-step-card :deep(.el-card__header) {
  padding: 10px 16px;
  background: #f5f7fa;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
}

.step-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  line-height: 1.5;
}

.cursor-blink {
  animation: blink 1s step-end infinite;
  color: #409eff;
  font-weight: bold;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.wizard-panel {
  margin-bottom: 20px;
}

.wizard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.batch-progress {
  margin-bottom: 16px;
}

.batch-progress-info {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #606266;
  margin-top: 6px;
}

.arc-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.arc-item {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  border-left: 3px solid #409eff;
  cursor: pointer;
  transition: background 0.2s;
}

.arc-item:hover {
  background: #ecf5ff;
}

.arc-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.arc-chapters {
  font-size: 13px;
  color: #909399;
}

.arc-outline {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.arc-expand-icon {
  margin-left: auto;
  font-size: 14px;
  color: #909399;
}

.arc-chapter-list {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e4e7ed;
}

.arc-chapter-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 6px 0;
  font-size: 13px;
}

.arc-chapter-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.arc-chapter-outline {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-top: 4px;
  padding-left: 48px;
}

.arc-chapter-num {
  font-weight: 500;
  color: #409eff;
  white-space: nowrap;
}

.arc-chapter-title {
  flex: 1;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
