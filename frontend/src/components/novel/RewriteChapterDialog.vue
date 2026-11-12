<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="streamingStatus === 'connecting' ? '正在连接...' : streamingStatus === 'generating' ? 'AI 正在重写...' : streamingStatus === 'completed' ? '重写完成' : 'AI 重写章节'"
    width="800px"
    :close-on-click-modal="streamingStatus !== 'generating' && streamingStatus !== 'connecting'"
    :close-on-press-escape="streamingStatus !== 'generating' && streamingStatus !== 'connecting'"
    :show-close="streamingStatus !== 'generating' && streamingStatus !== 'connecting'"
    @closed="onClosed"
  >
    <!-- 输入区域：仅在 idle 状态显示 -->
    <div v-if="streamingStatus === 'idle'" class="rewrite-input">
      <p class="rewrite-hint">基于当前章纲重新生成正文。旧内容会自动保存为历史版本，可随时回滚。</p>
      <el-input
        v-model="userInstruction"
        type="textarea"
        :rows="3"
        placeholder="可选：输入你希望改进的方向，如'让对话更紧凑'、'增加环境描写'、'节奏加快一些'"
      />
    </div>

    <!-- 流式展示区域 -->
    <div v-if="streamingStatus !== 'idle'" class="rewrite-content">
      <!-- AuthorAgent 检查点时间线 -->
      <div v-if="streamCheckpoints.length > 0" class="rewrite-checkpoints">
        <div v-for="(cp, i) in streamCheckpoints" :key="i" class="rewrite-checkpoint-item">
          <span class="rewrite-cp-node">{{ { outline: '章纲审查', writing: '正文审查', audit_router: '终审决策' }[cp.node] || cp.node }}</span>
          <span class="rewrite-cp-action" :class="'cp-' + cp.action.toLowerCase()">
            {{ { APPROVE: '通过', REVISE: '修订', REJECT: '驳回', REDIRECT: '重定向' }[cp.action] || cp.action }}
          </span>
          <el-popover v-if="cp.rationale" placement="top" :width="300" trigger="click">
            <template #reference>
              <el-button text size="small" type="info">详情</el-button>
            </template>
            <p style="margin: 0; font-size: 13px; line-height: 1.6;">{{ cp.rationale }}</p>
          </el-popover>
        </div>
      </div>
      <div class="rewrite-stream" ref="contentRef">
        {{ streamingContent }}<span v-if="streamingStatus === 'generating'" class="cursor-blink">▌</span>
      </div>
      <div v-if="streamingContent" class="rewrite-actions-bar">
        <el-button size="small" @click="copyContent" :icon="DocumentCopy">复制全部</el-button>
        <span class="rewrite-word-count">{{ streamingContent.length }} 字</span>
      </div>
      <div v-if="streamValidationSummary" class="rewrite-validation">
        校验提示：{{ streamValidationSummary }}
      </div>
    </div>

    <template #footer>
      <template v-if="streamingStatus === 'idle'">
        <el-button @click="$emit('update:visible', false)">取消</el-button>
        <el-button type="warning" @click="startRewrite" :loading="starting">开始重写</el-button>
      </template>
      <template v-else-if="streamingStatus === 'connecting' || streamingStatus === 'generating'">
        <el-button type="danger" @click="cancelRewrite">取消重写</el-button>
      </template>
      <template v-else-if="streamingStatus === 'completed'">
        <el-button type="primary" @click="onClose">完成</el-button>
      </template>
      <template v-else>
        <el-button v-if="streamingContent" type="primary" @click="savePartial">保存已生成内容</el-button>
        <el-button @click="startRewrite">重新重写</el-button>
        <el-button @click="onClose">关闭</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createSSEStream, type SSEError } from '@/utils/sse'
import { apiUrl } from '@/utils/base'
import { DocumentCopy } from '@element-plus/icons-vue'

const props = defineProps<{
  visible: boolean
  novelId: string
  chapterNum: number
  chapterOutline: string
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  rewritten: []
}>()

const userInstruction = ref('')
const streamingContent = ref('')
const streamingStatus = ref<'idle' | 'connecting' | 'generating' | 'completed' | 'error'>('idle')
const streamValidationSummary = ref('')
const streamCheckpoints = ref<Array<{ node: string; action: string; rationale: string }>>([])
const starting = ref(false)
let streamAbortController: AbortController | null = null

const contentRef = ref<HTMLDivElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (contentRef.value) {
      contentRef.value.scrollTop = contentRef.value.scrollHeight
    }
  })
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(streamingContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    userInstruction.value = ''
    streamingContent.value = ''
    streamingStatus.value = 'idle'
    streamValidationSummary.value = ''
    streamCheckpoints.value = []
    starting.value = false
  }
})

let sseHandle: { cancel: () => void } | null = null

async function startRewrite() {
  starting.value = true
  streamingContent.value = ''
  streamingStatus.value = 'connecting'
  streamValidationSummary.value = ''
  streamCheckpoints.value = []

  sseHandle = createSSEStream({
    url: '/api/chapter/stream-write',
    body: {
      novelId: props.novelId,
      chapterNum: props.chapterNum,
      outline: props.chapterOutline,
      userInstruction: userInstruction.value.trim() || undefined,
    },
    onConnecting: () => {
      streamingStatus.value = 'connecting'
    },
    onOpen: () => {
      streamingStatus.value = 'generating'
      starting.value = false
    },
    onMessage: (data) => {
      if (data.includes('---CHARACTERS---')) {
        const before = data.substring(0, data.indexOf('---CHARACTERS---'))
        if (before) streamingContent.value += before
      } else {
        streamingContent.value += data
      }
      scrollToBottom()
    },
    onEvent: (event, data) => {
      if (event === 'checkpoint') {
        try {
          streamCheckpoints.value.push(JSON.parse(data))
        } catch { /* ignore */ }
      } else if (event === 'complete') {
        streamValidationSummary.value = data
      } else if (event === 'validation_result') {
        try {
          const parsed = JSON.parse(data)
          if (parsed.summary) streamValidationSummary.value = parsed.summary
        } catch {
          streamValidationSummary.value = data
        }
      }
    },
    onError: (err: SSEError) => {
      if (streamingContent.value) {
        streamingStatus.value = 'completed'
        emit('rewritten')
      } else {
        streamingStatus.value = 'error'
        ElMessage.error(err.message)
      }
      starting.value = false
    },
    onDone: () => {
      if (streamingContent.value) {
        streamingStatus.value = 'completed'
        emit('rewritten')
      } else {
        streamingStatus.value = 'error'
        ElMessage.error('重写失败: 未收到任何内容')
      }
      starting.value = false
    },
    signal: streamAbortController?.signal,
    maxRetries: 2,
  })
}

async function cancelRewrite() {
  sseHandle?.cancel()
  try {
    await fetch(`/api/chapter/cancel?novelId=${props.novelId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token') || ''}` },
    })
  } catch { /* ignore */ }
  streamingStatus.value = 'error'
  ElMessage.info('已取消重写')
}

async function savePartial() {
  if (!streamingContent.value) return
  try {
    await ElMessageBox.confirm(
      '改写后的内容将覆盖原章节，原内容会保留在版本历史中。确定保存？',
      '确认保存改写',
      { confirmButtonText: '保存', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const token = localStorage.getItem('token') || ''
    const res = await fetch(apiUrl('/api/chapter/stream-save'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify({
        novelId: props.novelId,
        chapterNum: String(props.chapterNum),
        content: streamingContent.value,
        outline: props.chapterOutline,
      }),
    })
    const data = await res.json()
    if (data.success) {
      ElMessage.success(`已保存（${data.wordCount}字）`)
      emit('rewritten')
      onClose()
    } else {
      ElMessage.error('保存失败: ' + (data.error || '未知错误'))
    }
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

function onClose() {
  emit('update:visible', false)
}

function onClosed() {
  sseHandle?.cancel()
  sseHandle = null
  if (streamAbortController) {
    streamAbortController.abort()
    streamAbortController = null
  }
}
</script>

<style scoped>
.rewrite-hint {
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
}

.rewrite-stream {
  white-space: pre-wrap;
  line-height: 2;
  font-size: 16px;
  max-height: 500px;
  overflow-y: auto;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.rewrite-validation {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 13px;
  color: #e6a23c;
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

.rewrite-checkpoints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
  margin-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.rewrite-checkpoint-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
}

.rewrite-cp-node {
  color: #909399;
}

.cp-approve { color: #67c23a; font-weight: 600; }
.cp-revise { color: #e6a23c; font-weight: 600; }
.cp-reject { color: #f56c6c; font-weight: 600; }
.cp-redirect { color: #409eff; font-weight: 600; }

.rewrite-actions-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding: 8px 0;
}

.rewrite-word-count {
  font-size: 12px;
  color: #909399;
}
</style>
