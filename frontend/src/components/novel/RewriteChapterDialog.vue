<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="streamingStatus === 'generating' ? 'AI 正在重写...' : streamingStatus === 'completed' ? '重写完成' : 'AI 重写章节'"
    width="800px"
    :close-on-click-modal="streamingStatus !== 'generating'"
    :close-on-press-escape="streamingStatus !== 'generating'"
    :show-close="streamingStatus !== 'generating'"
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
      <div class="rewrite-stream">
        {{ streamingContent }}<span v-if="streamingStatus === 'generating'" class="cursor-blink">▌</span>
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
      <template v-else-if="streamingStatus === 'generating'">
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
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

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
const streamingStatus = ref<'idle' | 'generating' | 'completed' | 'error'>('idle')
const streamValidationSummary = ref('')
const starting = ref(false)
let streamAbortController: AbortController | null = null

watch(() => props.visible, (val) => {
  if (val) {
    userInstruction.value = ''
    streamingContent.value = ''
    streamingStatus.value = 'idle'
    streamValidationSummary.value = ''
    starting.value = false
  }
})

async function startRewrite() {
  starting.value = true
  streamingContent.value = ''
  streamingStatus.value = 'generating'
  streamValidationSummary.value = ''

  streamAbortController = new AbortController()
  const timeoutId = setTimeout(() => streamAbortController?.abort(), 10 * 60 * 1000)

  try {
    const token = localStorage.getItem('token') || ''
    const body = JSON.stringify({
      novelId: props.novelId,
      chapterNum: props.chapterNum,
      outline: props.chapterOutline,
      userInstruction: userInstruction.value.trim() || undefined,
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
      ElMessage.error(`重写失败: ${errText}`)
      streamingStatus.value = 'error'
      return
    }

    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    if (!reader) { streamingStatus.value = 'error'; return }

    let receivedComplete = false
    let currentEventName = ''
    let reachedCharactersMarker = false

    function processSSELine(line: string) {
      if (line.startsWith('event:')) {
        currentEventName = line.slice(6).trim()
        return
      }
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data) return

        if (currentEventName === 'complete') {
          streamValidationSummary.value = data
          receivedComplete = true
          currentEventName = ''
        } else if (currentEventName === 'validation_result') {
          try {
            const parsed = JSON.parse(data)
            if (parsed.summary) streamValidationSummary.value = parsed.summary
          } catch {
            streamValidationSummary.value = data
          }
          currentEventName = ''
        } else {
          if (!reachedCharactersMarker) {
            const markerIdx = data.indexOf('---CHARACTERS---')
            if (markerIdx >= 0) {
              const before = data.substring(0, markerIdx)
              if (before) streamingContent.value += before
              reachedCharactersMarker = true
            } else {
              streamingContent.value += data
            }
          }
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

    if (receivedComplete || streamingContent.value) {
      streamingStatus.value = 'completed'
      starting.value = false
      emit('rewritten')
    } else {
      streamingStatus.value = 'error'
      starting.value = false
      ElMessage.error('重写失败: 未收到任何内容')
    }
  } catch (error: any) {
    clearTimeout(timeoutId)
    if (error.name === 'AbortError') {
      streamingStatus.value = 'error'
      starting.value = false
      ElMessage.info('已取消重写')
    } else {
      if (streamingContent.value) {
        streamingStatus.value = 'completed'
        starting.value = false
        emit('rewritten')
      } else {
        streamingStatus.value = 'error'
        starting.value = false
        ElMessage.error('重写失败: ' + (error.message || '未知错误'))
      }
    }
  } finally {
    streamAbortController = null
  }
}

async function cancelRewrite() {
  try {
    await fetch(`/api/chapter/cancel?novelId=${props.novelId}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token') || ''}` },
    })
    streamAbortController?.abort()
    ElMessage.info('已发送取消请求')
  } catch { ElMessage.error('取消失败') }
}

async function savePartial() {
  if (!streamingContent.value) return
  try {
    const token = localStorage.getItem('token') || ''
    const res = await fetch('/api/chapter/stream-save', {
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
</style>
