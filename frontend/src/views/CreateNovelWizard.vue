<template>
  <div class="wizard-page">
    <el-card class="wizard-card">
      <template #header>
        <div class="card-header">
          <span>创建新小说（向导模式）</span>
          <el-button text @click="router.push('/create')">切换到经典模式 →</el-button>
        </div>
      </template>

      <el-steps :active="currentStep" finish-status="success" align-center class="wizard-steps">
        <el-step title="填写设定" />
        <el-step title="卷纲预览" />
        <el-step title="保存小说" />
        <el-step title="章纲生成" />
        <el-step title="正文生成" />
      </el-steps>

      <div class="step-content">
        <!-- Step 1: 填写设定 -->
        <div v-if="currentStep === 0">
          <el-form :model="form" :rules="rules" label-width="100px" class="step-form">
            <el-form-item label="小说名称" prop="novelName">
              <el-input v-model="form.novelName" placeholder="请输入小说名称" maxlength="100" show-word-limit />
            </el-form-item>

            <el-form-item label="小说类型" prop="genre">
              <el-select v-model="form.genre" placeholder="请选择小说类型">
                <el-option-group label="男频">
                  <el-option label="玄幻" value="玄幻" />
                  <el-option label="仙侠" value="仙侠" />
                  <el-option label="都市" value="都市" />
                  <el-option label="历史" value="历史" />
                  <el-option label="科幻" value="科幻" />
                  <el-option label="游戏" value="游戏" />
                  <el-option label="末世" value="末世" />
                </el-option-group>
                <el-option-group label="女频">
                  <el-option label="言情" value="言情" />
                </el-option-group>
                <el-option-group label="通用">
                  <el-option label="悬疑" value="悬疑" />
                </el-option-group>
              </el-select>
            </el-form-item>

            <el-form-item label="AI 模型">
              <el-select v-model="form.llmConfigId" placeholder="选择AI模型（默认使用系统模型）" clearable>
                <el-option v-for="c in llmConfigList" :key="c.configId" :label="c.configName" :value="c.configId" />
              </el-select>
            </el-form-item>

            <el-form-item label="文风卡片">
              <StyleSelector v-model="form.styleCardId" />
            </el-form-item>

            <el-form-item label="故事类型">
              <el-select v-model="form.novelTypes" multiple placeholder="选择故事类型标签（可多选）" style="width: 100%">
                <el-option label="爽文" value="爽文" />
                <el-option label="甜宠" value="甜宠" />
                <el-option label="成长" value="成长" />
                <el-option label="虐恋" value="虐恋" />
                <el-option label="种田" value="种田" />
                <el-option label="穿越" value="穿越" />
                <el-option label="重生" value="重生" />
                <el-option label="系统流" value="系统流" />
                <el-option label="无敌流" value="无敌流" />
                <el-option label="升级流" value="升级流" />
                <el-option label="后宫" value="后宫" />
                <el-option label="赘婿" value="赘婿" />
                <el-option label="回归" value="回归" />
                <el-option label="逆袭" value="逆袭" />
              </el-select>
              <div class="form-tip">影响AI的故事结构和节奏走向</div>
            </el-form-item>

            <el-form-item label="故事基调">
              <el-select v-model="form.tones" multiple placeholder="选择故事基调（可多选）" style="width: 100%">
                <el-option label="轻松" value="轻松" />
                <el-option label="温馨" value="温馨" />
                <el-option label="搞笑" value="搞笑" />
                <el-option label="治愈" value="治愈" />
                <el-option label="热血" value="热血" />
                <el-option label="紧张" value="紧张" />
                <el-option label="沉重" value="沉重" />
                <el-option label="甜蜜" value="甜蜜" />
                <el-option label="阴暗" value="阴暗" />
                <el-option label="荒诞" value="荒诞" />
              </el-select>
              <div class="form-tip">影响AI的叙事语气和情感氛围</div>
            </el-form-item>

            <el-form-item label="禁止雷点">
              <el-select v-model="form.taboos" multiple placeholder="选择要避免的内容（可多选）" style="width: 100%">
                <el-option label="NTR（绿帽）" value="NTR" />
                <el-option label="圣母" value="圣母" />
                <el-option label="虐主" value="虐主" />
                <el-option label="悲剧结局" value="悲剧" />
                <el-option label="狗血" value="狗血" />
                <el-option label="降智" value="降智" />
                <el-option label="后宫起火" value="后宫起火" />
                <el-option label="烂尾" value="烂尾" />
                <el-option label="太监（断更）" value="太监" />
              </el-select>
              <div class="form-tip">AI生成时会严格避免这些内容</div>
            </el-form-item>

            <el-form-item label="核心创意">
              <el-input v-model="form.prompt" type="textarea" :rows="3"
                placeholder="可选：一句话描述你的小说创意（比如：男主是个被退婚的赘婿，后来发现自己是神帝转世）" />
            </el-form-item>

            <el-form-item label="总纲" prop="totalOutline">
              <el-input v-model="form.totalOutline" type="textarea" :rows="6"
                placeholder="请输入小说总纲，描述主要剧情走向、核心冲突、结局等" />
            </el-form-item>

            <el-form-item label="世界观">
              <el-input v-model="form.worldSetting" type="textarea" :rows="4"
                placeholder="请输入世界观设定，包括时间背景、地理环境、力量体系等" />
            </el-form-item>

            <el-form-item>
              <el-button type="success" plain @click="handleGenerateVolumes" :loading="generatingVolumes"
                :disabled="!form.novelName || !form.genre">
                AI 一键生成卷纲
              </el-button>
              <span class="form-tip" v-if="arcs.length === 0">请先填写小说名称和类型，然后点击生成卷纲</span>
              <span class="form-tip success-tip" v-else>已生成 {{ arcs.length }} 卷卷纲</span>
            </el-form-item>

            <el-form-item v-if="arcs.length > 0">
              <el-button type="primary" @click="goToStep(1)">下一步：预览卷纲</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 2: 卷纲预览/编辑 -->
        <div v-if="currentStep === 1">
          <div class="arc-editor-header">
            <h3>卷纲列表（共 {{ arcs.length }} 卷）</h3>
            <div>
              <el-button size="small" @click="handleAddArc">添加一卷</el-button>
              <el-button size="small" @click="handleGenerateVolumes" :loading="generatingVolumes">重新生成</el-button>
            </div>
          </div>

          <div v-for="(arc, index) in arcs" :key="index" class="arc-card">
            <el-card shadow="hover">
              <template #header>
                <div class="arc-header">
                  <span>第 {{ index + 1 }} 卷</span>
                  <el-button type="danger" text size="small" @click="handleRemoveArc(index)"
                    v-if="arcs.length > 1">删除</el-button>
                </div>
              </template>

              <el-form label-width="90px" class="arc-form" @input="hasEditedArcs = true">
                <el-form-item label="卷名">
                  <el-input v-model="arc.arcName" placeholder="卷名称" />
                </el-form-item>
                <el-form-item label="卷纲">
                  <el-input v-model="arc.arcOutline" type="textarea" :rows="5" placeholder="卷纲内容" />
                </el-form-item>
                <el-row :gutter="12">
                  <el-col :span="8">
                    <el-form-item label="起始章">
                      <el-input-number v-model="arc.startChapter" :min="1" controls-position="right" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="结束章">
                      <el-input-number v-model="arc.endChapter" :min="arc.startChapter"
                        controls-position="right" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="章节数">
                      <el-input-number v-model="arc.chapterCount" :min="1" controls-position="right" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-form-item label="节奏标签">
                      <el-select v-model="arc.rhythmTag">
                        <el-option label="主线推进 (quest)" value="quest" />
                        <el-option label="冲突高潮 (fire)" value="fire" />
                        <el-option label="世界观展开 (constellation)" value="constellation" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="张力强度">
                      <el-slider v-model="arc.rhythmIntensity" :min="1" :max="10" show-stops />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </el-card>
          </div>

          <div class="step-actions">
            <el-button @click="goToStep(0)">上一步</el-button>
            <el-button type="primary" @click="handleSaveNovel" :loading="saving">确认并保存小说</el-button>
          </div>
        </div>

        <!-- Step 3: 保存中 -->
        <div v-if="currentStep === 2">
          <div class="saving-indicator">
            <el-icon class="is-loading" :size="40" v-if="saving">
              <Loading />
            </el-icon>
            <p v-if="saving">正在保存小说和卷纲...</p>
            <div v-else-if="novelId">
              <el-result icon="success" title="小说创建成功" sub-title="现在开始按卷生成章纲">
                <template #extra>
                  <el-button type="primary" @click="startChapterOutlineGeneration">开始生成章纲</el-button>
                </template>
              </el-result>
            </div>
          </div>
        </div>

        <!-- Step 4: 按卷生成章纲 -->
        <div v-if="currentStep === 3">
          <div class="arc-progress">
            <h3>章纲生成进度</h3>
            <el-progress :text-inside="true" :stroke-width="20"
              :percentage="outlineGenerationTotal > 0
                ? Math.round((outlineGenerationProgress / outlineGenerationTotal) * 100)
                : (arcs.length > 0 ? Math.round((currentArcIndex / arcs.length) * 100) : 0)"
              :status="generationFailed ? 'exception' : (generatingChapterOutlines ? '' : 'success')" />

            <p class="progress-text">
              <template v-if="generatingChapterOutlines && outlineGenerationTotal > 0">
                正在生成第 {{ outlineGenerationCurrentChapter }} 章...
                ({{ outlineGenerationProgress }}/{{ outlineGenerationTotal }})
              </template>
              <template v-else>
                当前进度：第 {{ currentArcIndex + 1 }}/{{ arcs.length }} 卷
                <template v-if="currentArcIndex < arcs.length">
                  — {{ arcs[currentArcIndex].arcName }}
                </template>
              </template>
            </p>
          </div>

          <!-- 生成中状态 -->
          <div v-if="generatingChapterOutlines" class="generating-indicator">
            <el-icon class="is-loading" :size="30">
              <Loading />
            </el-icon>
            <p>正在生成第 {{ currentArcIndex + 1 }} 卷「{{ arcs[currentArcIndex]?.arcName }}」的章纲...</p>
            <p class="form-tip" v-if="outlineGenerationTotal > 0">
              已生成 {{ currentChapterOutlines.length }}/{{ outlineGenerationTotal }} 章
            </p>
          </div>

          <!-- 生成失败状态 -->
          <div v-if="generationFailed && !generatingChapterOutlines" class="generating-indicator">
            <el-result icon="error" title="章纲生成失败" sub-title="请重试或检查后端日志">
              <template #extra>
                <el-button type="primary" @click="generateCurrentArcOutlines">重试</el-button>
                <el-button @click="goToStep(1)">返回卷纲编辑</el-button>
              </template>
            </el-result>
          </div>

          <!-- 章纲列表（可编辑）- 生成中也可查看已生成的章纲 -->
          <div v-if="currentChapterOutlines.length > 0">
            <h4>
              第 {{ currentArcIndex + 1 }} 卷「{{ arcs[currentArcIndex]?.arcName }}」章纲
              <el-tag v-if="generatingChapterOutlines" type="warning" size="small" style="margin-left: 8px;">
                生成中...
              </el-tag>
            </h4>

            <div v-for="(outline, index) in currentChapterOutlines" :key="index" class="chapter-outline-item">
              <el-card shadow="hover" size="small">
                <template #header>
                  <span>第 {{ outline.chapterNum }} 章</span>
                </template>
                <el-form-item label="标题">
                  <el-input v-model="outline.chapterTitle" size="small" />
                </el-form-item>
                <el-form-item label="章纲">
                  <el-input v-model="outline.outline" type="textarea" :rows="3" size="small" />
                </el-form-item>
              </el-card>
            </div>

            <div class="step-actions" v-if="!generatingChapterOutlines">
              <el-button type="primary" @click="handleConfirmChapterOutlines" :loading="confirmingOutlines">
                确认本卷章纲{{ currentArcIndex < arcs.length - 1 ? '，进入下一卷' : '' }}
              </el-button>
            </div>
          </div>
        </div>

        <!-- Step 5: 批量生成正文 -->
        <div v-if="currentStep === 4">
          <div v-if="!batchTaskId">
            <el-result icon="success" title="所有章纲已确认完成！" sub-title="现在可以开始批量生成正文内容">
              <template #extra>
                <el-button type="primary" @click="handleBatchGenerate" :loading="batchGenerating">
                  开始批量生成正文
                </el-button>
              </template>
            </el-result>
          </div>

          <div v-else class="batch-progress">
            <h3>正文生成进度</h3>
            <el-progress :text-inside="true" :stroke-width="24"
              :percentage="batchProgress.totalChapters > 0
                ? Math.round(((batchProgress.completedChapters + batchProgress.failedChapters) / batchProgress.totalChapters) * 100) : 0"
              :status="batchProgress.status === 'COMPLETED' ? 'success' : ''" />

            <div class="progress-details">
              <p>状态: {{ batchStatusText }}</p>
              <p v-if="batchProgress.totalChapters > 0">
                进度: {{ batchProgress.completedChapters }} / {{ batchProgress.totalChapters }} 章
              </p>
              <p v-if="batchProgress.failedChapters > 0" class="error-text">
                失败: {{ batchProgress.failedChapters }} 章
              </p>
              <p v-if="batchProgress.currentChapterNum">
                当前: 第 {{ batchProgress.currentChapterNum }} 章
              </p>
              <p v-if="batchProgress.errorMessage" class="error-text">{{ batchProgress.errorMessage }}</p>
            </div>

            <div v-if="batchProgress.status === 'GENERATING'" class="step-actions">
              <el-button type="danger" @click="handleCancelBatch" :loading="cancellingBatch">
                取消批量生成
              </el-button>
            </div>

            <div v-if="batchProgress.status === 'COMPLETED' || batchProgress.status === 'CANCELLED'" class="step-actions">
              <el-button type="primary" @click="router.push(`/novel/${novelId}`)">
                进入小说详情页
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onUnmounted, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { wizardApi, llmConfigApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import StyleSelector from '@/components/StyleSelector.vue'
import { createSSEStream, type SSEError } from '@/utils/sse'
import type { FormRules } from 'element-plus'
import type { ArcOutlineDTO, ArcChapterOutlineDTO, BatchGenerationProgress } from '@/types'

const router = useRouter()

const currentStep = ref(0)
const generatingVolumes = ref(false)
const saving = ref(false)
const generatingChapterOutlines = ref(false)
const generationFailed = ref(false)
const confirmingOutlines = ref(false)
const batchGenerating = ref(false)
const cancellingBatch = ref(false)
const llmConfigList = ref<{ configId: string; configName: string }[]>([])

const novelId = ref('')
const arcs = ref<ArcOutlineDTO[]>([])
const hasEditedArcs = ref(false)
const currentArcIndex = ref(0)
const currentChapterOutlines = ref<ArcChapterOutlineDTO[]>([])
const outlineGenerationProgress = ref(0)
const outlineGenerationTotal = ref(0)
const outlineGenerationCurrentChapter = ref(0)
const batchTaskId = ref('')
const batchProgress = reactive<BatchGenerationProgress>({
  taskId: '',
  status: '',
  totalChapters: 0,
  completedChapters: 0,
  failedChapters: 0,
  currentChapterNum: 0,
  currentPhase: '',
  errorMessage: ''
})

let pollingTimer: ReturnType<typeof setInterval> | null = null
const MAX_POLL_COUNT = 60
let pollCount = 0

const form = reactive({
  novelName: '',
  genre: '',
  novelTypes: [] as string[],
  tones: [] as string[],
  taboos: [] as string[],
  prompt: '',
  totalOutline: '',
  worldSetting: '',
  styleCardId: '',
  llmConfigId: ''
})

const rules: FormRules = {
  novelName: [
    { required: true, message: '请输入小说名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度2-100字符', trigger: 'blur' }
  ],
  genre: [{ required: true, message: '请选择小说类型', trigger: 'change' }],
  totalOutline: [
    { required: true, message: '请输入小说总纲', trigger: 'blur' },
    { min: 50, message: '总纲至少50个字符', trigger: 'blur' }
  ]
}

const batchStatusText = computed(() => {
  switch (batchProgress.status) {
    case 'GENERATING': return '生成中...'
    case 'COMPLETED': return '已完成'
    case 'FAILED': return '生成失败'
    case 'CANCELLED': return '已取消'
    default: return '未知'
  }
})

onMounted(async () => {
  try {
    const res = await llmConfigApi.getConfigList()
    if (res.code === 200 && res.data) {
      llmConfigList.value = res.data.map(c => ({ configId: c.configId, configName: c.configName }))
    }
  } catch (e) {
    console.warn('加载LLM配置列表失败:', e)
  }
})

// Step 1: 生成卷纲
const handleGenerateVolumes = async () => {
  if (!form.novelName || !form.genre) {
    ElMessage.warning('请先填写小说名称和类型')
    return
  }

  generatingVolumes.value = true
  try {
    const response = await wizardApi.generateVolumeOutlines({
      novelName: form.novelName,
      genre: form.genre,
      novelType: form.novelTypes.length > 0 ? JSON.stringify(form.novelTypes) : undefined,
      tone: form.tones.length > 0 ? JSON.stringify(form.tones) : undefined,
      tabooList: form.taboos.length > 0 ? JSON.stringify(form.taboos) : undefined,
      totalOutline: form.totalOutline,
      worldSetting: form.worldSetting,
      prompt: form.prompt,
      styleCardId: form.styleCardId || undefined,
      llmConfigId: form.llmConfigId || undefined
    })
    if (response.code === 200 && response.data) {
      arcs.value = response.data
      hasEditedArcs.value = false
      // 自动填充总纲和世界观（如果为空）
      if (!form.totalOutline) {
        form.totalOutline = `全书共${arcs.value.length}卷：` +
          arcs.value.map((a, i) => `第${i + 1}卷「${a.arcName}」(${a.startChapter}-${a.endChapter}章)`).join('；')
      }
      ElMessage.success(`成功生成 ${arcs.value.length} 卷卷纲！`)
    } else {
      ElMessage.error(response.message || '生成失败')
    }
  } catch (error) {
    console.error('生成卷纲失败:', error)
    ElMessage.error('生成卷纲失败，请稍后重试')
  } finally {
    generatingVolumes.value = false
  }
}

// Step 2: 添加/删除卷
const handleAddArc = () => {
  const lastArc = arcs.value[arcs.value.length - 1]
  const newStart = lastArc ? lastArc.endChapter + 1 : 1
  arcs.value.push({
    arcName: `第${arcs.value.length + 1}卷`,
    arcOutline: '',
    startChapter: newStart,
    endChapter: newStart + 14,
    chapterCount: 15,
    rhythmTag: 'quest',
    rhythmIntensity: 5
  })
}

const handleRemoveArc = (index: number) => {
  arcs.value.splice(index, 1)
}

// Step 3: 保存小说
const handleSaveNovel = async () => {
  if (arcs.value.length === 0) {
    ElMessage.warning('请先生成卷纲')
    return
  }

  saving.value = true
  goToStep(2)

  try {
    const response = await wizardApi.saveNovel({
      novelName: form.novelName,
      genre: form.genre,
      novelType: form.novelTypes.length > 0 ? JSON.stringify(form.novelTypes) : undefined,
      tone: form.tones.length > 0 ? JSON.stringify(form.tones) : undefined,
      tabooList: form.taboos.length > 0 ? JSON.stringify(form.taboos) : undefined,
      totalOutline: form.totalOutline,
      worldSetting: form.worldSetting,
      styleCardId: form.styleCardId || undefined,
      llmConfigId: form.llmConfigId || undefined,
      arcs: arcs.value
    })
    if (response.code === 200 && response.data) {
      novelId.value = response.data.novelId
      ElMessage.success('小说保存成功！')
    } else {
      ElMessage.error(response.message || '保存失败')
      goToStep(1)
    }
  } catch (error) {
    console.error('保存小说失败:', error)
    ElMessage.error('保存失败，请重试')
    goToStep(1)
  } finally {
    saving.value = false
  }
}

// Step 4: 开始生成章纲
const startChapterOutlineGeneration = () => {
  goToStep(3)
  currentArcIndex.value = 0
  generateCurrentArcOutlines()
}

let arcSseHandle: { cancel: () => void } | null = null

const generateCurrentArcOutlines = async () => {
  if (currentArcIndex.value >= arcs.value.length) {
    goToStep(4)
    return
  }

  generationFailed.value = false
  generatingChapterOutlines.value = true
  currentChapterOutlines.value = []
  outlineGenerationProgress.value = 0
  outlineGenerationTotal.value = 0
  outlineGenerationCurrentChapter.value = 0

  arcSseHandle = createSSEStream({
    url: `/api/wizard/novel/${novelId.value}/arc/${currentArcIndex.value}/chapter-outlines/stream`,
    body: {},
    onMessage: (data) => {
      try {
        const parsed = JSON.parse(data)
        handleSSEEvent('unknown', parsed)
      } catch {
        // 非 JSON data，忽略
      }
    },
    onEvent: (event, data) => {
      try {
        const parsed = JSON.parse(data)
        handleSSEEvent(event, parsed)
      } catch {
        console.warn('解析 SSE 数据失败:', data)
      }
    },
    onError: (err: SSEError) => {
      ElMessage.error(err.message || '章纲生成失败')
      generationFailed.value = true
      generatingChapterOutlines.value = false
    },
    onDone: () => {
      generatingChapterOutlines.value = false
      ElMessage.success(`第 ${currentArcIndex.value + 1} 卷章纲生成完成`)
    },
    maxRetries: 2,
  })
}

const handleSSEEvent = (type: string, data: any) => {
  switch (type) {
    case 'progress':
      outlineGenerationProgress.value = data.current
      outlineGenerationTotal.value = data.total
      outlineGenerationCurrentChapter.value = data.chapterNum
      break

    case 'outline':
      currentChapterOutlines.value.push({
        chapterNum: data.chapterNum,
        chapterTitle: data.chapterTitle,
        outline: data.outline,
        implementationIdeas: data.implementationIdeas || []
      })
      break

    case 'complete':
      console.log('章纲生成完成:', data)
      break

    case 'error':
      ElMessage.error(data.message || '章纲生成失败')
      generationFailed.value = true
      break
  }
}

const handleConfirmChapterOutlines = async () => {
  confirmingOutlines.value = true

  try {
    const response = await wizardApi.confirmChapterOutlines(
      novelId.value,
      currentArcIndex.value,
      currentChapterOutlines.value
    )
    if (response.code === 200) {
      ElMessage.success('章纲已保存')

      currentArcIndex.value++
      if (currentArcIndex.value < arcs.value.length) {
        // 进入下一卷
        generateCurrentArcOutlines()
      } else {
        // 全部完成
        goToStep(4)
      }
    } else {
      ElMessage.error(response.message || '保存失败')
    }
  } catch (error) {
    console.error('保存章纲失败:', error)
    ElMessage.error('保存章纲失败')
  } finally {
    confirmingOutlines.value = false
  }
}

// Step 5: 批量生成正文
const handleBatchGenerate = async () => {
  batchGenerating.value = true

  try {
    const response = await wizardApi.batchGenerate(novelId.value)
    if (response.code === 200 && response.data) {
      batchTaskId.value = response.data
      batchProgress.status = 'GENERATING'
      ElMessage.success('批量生成任务已启动')
      startBatchPolling()
    } else {
      ElMessage.error(response.message || '启动失败')
    }
  } catch (error) {
    console.error('批量生成启动失败:', error)
    ElMessage.error('批量生成启动失败')
  } finally {
    batchGenerating.value = false
  }
}

const startBatchPolling = () => {
  pollCount = 0
  pollingTimer = setInterval(async () => {
    if (!batchTaskId.value || !novelId.value) return

    pollCount++
    if (pollCount > MAX_POLL_COUNT) {
      stopPolling()
      ElMessage.warning('轮询超时，请刷新页面查看最终状态')
      return
    }

    try {
      const response = await wizardApi.getBatchStatus(novelId.value, batchTaskId.value)
      if (response.code === 200 && response.data) {
        Object.assign(batchProgress, response.data)

        if (batchProgress.status === 'COMPLETED' || batchProgress.status === 'FAILED' || batchProgress.status === 'CANCELLED') {
          stopPolling()
          if (batchProgress.status === 'COMPLETED') {
            ElMessage.success('全部章节生成完成！')
          } else if (batchProgress.status === 'CANCELLED') {
            ElMessage.info('批量生成已取消')
          }
        }
      }
    } catch (error) {
      console.error('查询进度失败:', error)
    }
  }, 5000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const handleCancelBatch = async () => {
  cancellingBatch.value = true
  try {
    const response = await wizardApi.batchCancel(novelId.value, batchTaskId.value)
    if (response.code === 200) {
      ElMessage.success('已请求取消，将在当前章节完成后停止')
    } else {
      ElMessage.error(response.message || '取消失败')
    }
  } catch (error) {
    console.error('取消批量生成失败:', error)
    ElMessage.error('取消失败')
  } finally {
    cancellingBatch.value = false
  }
}

const goToStep = async (step: number) => {
  // 从步骤2回退到步骤0时，如果编辑过卷纲，提示确认
  if (currentStep.value === 1 && step === 0 && hasEditedArcs.value) {
    try {
      await ElMessageBox.confirm(
        '返回修改设定后，如果重新生成卷纲，当前对卷纲的编辑将被覆盖。确定返回？',
        '确认返回',
        { confirmButtonText: '返回', cancelButtonText: '留在当前', type: 'warning' }
      )
    } catch {
      return // 用户取消
    }
  }
  currentStep.value = step
}

onUnmounted(() => {
  stopPolling()
  arcSseHandle?.cancel()
})
</script>

<style scoped>
.wizard-page {
  max-width: 960px;
  margin: 0 auto;
}

.wizard-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: bold;
}

.wizard-steps {
  margin-bottom: 30px;
}

.step-content {
  min-height: 400px;
}

.step-form {
  max-width: 720px;
  margin: 0 auto;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 10px;
}

.success-tip {
  color: #67c23a;
}

.arc-editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.arc-card {
  margin-bottom: 16px;
}

.arc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.arc-form {
  margin-top: 8px;
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.saving-indicator,
.generating-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 16px;
}

.arc-progress {
  text-align: center;
  margin-bottom: 24px;
}

.progress-text {
  margin-top: 12px;
  color: #606266;
  font-size: 14px;
}

.chapter-outline-item {
  margin-bottom: 12px;
}

.batch-progress {
  max-width: 600px;
  margin: 0 auto;
  text-align: center;
}

.progress-details {
  margin-top: 16px;
  color: #606266;
}

.progress-details p {
  margin: 4px 0;
}

.error-text {
  color: #f56c6c;
}

.el-select {
  width: 100%;
}
</style>
