<template>
  <div class="chapter-edit-page" v-loading="loading">
    <div class="page-header">
      <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
      <h2>编辑第{{ chapterNum }}章</h2>
    </div>

    <el-card v-if="chapter">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="章节标题" prop="chapterTitle">
          <el-input v-model="form.chapterTitle" placeholder="请输入章节标题" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="剧情摘要">
          <el-input v-model="form.plotFingerprint" placeholder="剧情摘要（可选）" maxlength="500" />
        </el-form-item>

        <el-form-item label="章节内容" prop="content">
          <div class="editor-toolbar">
            <span class="word-count">字数：{{ currentWordCount }}</span>
          </div>
          <div style="border: 1px solid #ccc; width: 100%;">
            <Toolbar
              v-if="editorVisible"
              :editor="editorRef"
              :defaultConfig="toolbarConfig"
              mode="simple"
              style="border-bottom: 1px solid #ccc"
            />
            <Editor
              v-if="editorVisible"
              v-model="form.content"
              :defaultConfig="editorConfig"
              mode="simple"
              style="height: 500px; overflow-y: hidden;"
              @onCreated="onEditorCreated"
            />
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { chapterApi } from '@/api'
import type { ChapterVO } from '@/types'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IToolbarConfig, IEditorConfig } from '@wangeditor/editor'

const route = useRoute()
const router = useRouter()
const novelId = route.params.novelId as string
const chapterNum = Number(route.params.chapterNum)

const loading = ref(true)
const saving = ref(false)
const chapter = ref<ChapterVO | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  chapterTitle: '',
  content: '',
  plotFingerprint: '',
})

const currentWordCount = computed(() => form.content ? form.content.replaceAll(/\s+/g, '').length : 0)

// ---- 富文本编辑器 ----
const editorRef = shallowRef<IDomEditor | null>(null)
const editorVisible = ref(false)
const toolbarConfig: Partial<IToolbarConfig> = {
  excludeKeys: ['fullScreen', 'group-video', 'group-image']
}
const editorConfig: Partial<IEditorConfig> = {
  placeholder: '请输入章节内容...',
  MENU_CONF: {}
}

function onEditorCreated(editor: IDomEditor) {
  editorRef.value = editor
}

const rules: FormRules = {
  chapterTitle: [
    { required: true, message: '请输入章节标题', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入章节内容', trigger: 'blur' },
  ],
}

onMounted(async () => {
  try {
    const res = await chapterApi.getChapter(novelId, chapterNum)
    if (res.code === 200) {
      chapter.value = res.data
      form.chapterTitle = res.data.chapterTitle || ''
      form.content = res.data.content || ''
      form.plotFingerprint = res.data.plotFingerprint || ''
    }
  } finally {
    loading.value = false
    editorVisible.value = true
  }
})

onBeforeUnmount(() => {
  editorVisible.value = false
  if (editorRef.value) {
    editorRef.value.destroy()
    editorRef.value = null
  }
})

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const res = await chapterApi.updateChapter(novelId, chapterNum, {
      chapterTitle: form.chapterTitle,
      content: form.content,
      plotFingerprint: form.plotFingerprint,
    })
    if (res.code === 200) {
      ElMessage.success('保存成功')
      router.push(`/novel/${novelId}/chapter/${chapterNum}`)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push(`/novel/${novelId}/chapter/${chapterNum}`)
}
</script>

<style scoped>
.chapter-edit-page {
  max-width: 900px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
}

.editor-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.word-count {
  font-size: 13px;
  color: #909399;
}

.chapter-textarea :deep(textarea) {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', serif;
  font-size: 16px;
  line-height: 1.8;
  letter-spacing: 0.5px;
}
</style>
