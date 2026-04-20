<template>
  <div class="clues-page">
    <div class="page-header">
      <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
      <h2>伏笔管理</h2>
      <el-button type="primary" @click="showCreateDialog" :icon="Plus">添加伏笔</el-button>
    </div>

    <!-- 筛选 -->
    <el-card class="filter-card" shadow="never">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-select v-model="filterType" placeholder="类型筛选" clearable @change="filterClues">
            <el-option label="主线伏笔" value="主线伏笔" />
            <el-option label="支线伏笔" value="支线伏笔" />
            <el-option label="暗线" value="暗线" />
          </el-select>
        </el-col>
        <el-col :span="8">
          <el-select v-model="filterStatus" placeholder="状态筛选" clearable @change="filterClues">
            <el-option label="未回收" :value="0" />
            <el-option label="已回收" :value="1" />
          </el-select>
        </el-col>
      </el-row>
    </el-card>

    <div v-loading="loading">
      <el-empty v-if="filteredClues.length === 0 && !loading" description="暂无伏笔" />

      <el-card v-for="clue in filteredClues" :key="clue.clueId" class="clue-card" shadow="hover">
        <div class="clue-header">
          <div class="clue-title-row">
            <h3>{{ clue.title }}</h3>
            <div class="clue-tags">
              <el-tag size="small" :type="getClueTypeColor(clue.clueType)">{{ clue.clueType }}</el-tag>
              <el-tag size="small" :type="clue.status === 1 ? 'success' : 'warning'">
                {{ clue.status === 1 ? '已回收' : '未回收' }}
              </el-tag>
            </div>
          </div>
          <p class="clue-desc">{{ clue.description }}</p>
          <div class="clue-meta">
            <span>埋设章节：第{{ clue.plantedChapter }}章</span>
            <span v-if="clue.recycleChapter">| 回收章节：第{{ clue.recycleChapter }}章</span>
          </div>
        </div>

        <div class="clue-actions" v-if="clue.status === 0">
          <el-button size="small" type="primary" @click="showRecycleDialog(clue)">回收伏笔</el-button>
        </div>
      </el-card>
    </div>

    <!-- 创建伏笔对话框 -->
    <el-dialog v-model="createDialogVisible" title="添加伏笔" width="500px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" placeholder="伏笔标题" />
        </el-form-item>
        <el-form-item label="类型" prop="clueType">
          <el-select v-model="createForm.clueType" placeholder="请选择类型">
            <el-option label="主线伏笔" value="主线伏笔" />
            <el-option label="支线伏笔" value="支线伏笔" />
            <el-option label="暗线" value="暗线" />
          </el-select>
        </el-form-item>
        <el-form-item label="埋设章节" prop="plantedChapter">
          <el-input-number v-model="createForm.plantedChapter" :min="1" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="4" placeholder="伏笔详细描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 回收伏笔对话框 -->
    <el-dialog v-model="recycleDialogVisible" title="回收伏笔" width="400px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="伏笔">
          <span>{{ recyclingClue?.title }}</span>
        </el-form-item>
        <el-form-item label="回收章节">
          <el-input-number v-model="recycleChapter" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recycleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleRecycle">确定回收</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { useNovelStore } from '@/stores'
import type { ClueVO, CreateClueRequest } from '@/types'

const route = useRoute()
const router = useRouter()
const novelStore = useNovelStore()
const novelId = route.params.novelId as string

const loading = ref(true)
const saving = ref(false)
const clues = ref<ClueVO[]>([])
const filteredClues = ref<ClueVO[]>([])

const filterType = ref('')
const filterStatus = ref<number | ''>('')

// 创建对话框
const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<CreateClueRequest>({
  title: '',
  description: '',
  clueType: '',
  plantedChapter: 1,
})
const createRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  clueType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
  plantedChapter: [{ required: true, message: '请输入埋设章节', trigger: 'blur' }],
}

// 回收对话框
const recycleDialogVisible = ref(false)
const recyclingClue = ref<ClueVO | null>(null)
const recycleChapter = ref(1)

onMounted(async () => {
  await loadClues()
})

async function loadClues() {
  loading.value = true
  try {
    await novelStore.fetchClues(novelId)
    clues.value = novelStore.clues
    filterClues()
  } finally {
    loading.value = false
  }
}

function filterClues() {
  filteredClues.value = clues.value.filter((c) => {
    if (filterType.value && c.clueType !== filterType.value) return false
    if (filterStatus.value !== '' && c.status !== filterStatus.value) return false
    return true
  })
}

function getClueTypeColor(type: string): string {
  const map: Record<string, string> = {
    '主线伏笔': 'danger',
    '支线伏笔': 'warning',
    '暗线': 'info',
  }
  return map[type] || 'info'
}

function showCreateDialog() {
  Object.assign(createForm, { title: '', description: '', clueType: '', plantedChapter: 1 })
  createDialogVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const success = await novelStore.createClue(novelId, createForm)
    if (success) {
      ElMessage.success('创建成功')
      createDialogVisible.value = false
      clues.value = novelStore.clues
      filterClues()
    } else {
      ElMessage.error('创建失败')
    }
  } finally {
    saving.value = false
  }
}

function showRecycleDialog(clue: ClueVO) {
  recyclingClue.value = clue
  recycleChapter.value = 1
  recycleDialogVisible.value = true
}

async function handleRecycle() {
  if (!recyclingClue.value) return

  saving.value = true
  try {
    const success = await novelStore.recycleClue(novelId, recyclingClue.value.clueId, recycleChapter.value)
    if (success) {
      ElMessage.success('回收成功')
      recycleDialogVisible.value = false
      clues.value = novelStore.clues
      filterClues()
    } else {
      ElMessage.error('回收失败')
    }
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push(`/novel/${novelId}`)
}
</script>

<style scoped>
.clues-page {
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
  flex: 1;
}

.filter-card {
  margin-bottom: 16px;
}

.clue-card {
  margin-bottom: 12px;
}

.clue-header {
  margin-bottom: 12px;
}

.clue-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.clue-title-row h3 {
  margin: 0;
  font-size: 16px;
}

.clue-tags {
  display: flex;
  gap: 8px;
}

.clue-desc {
  margin: 8px 0;
  color: #606266;
  font-size: 14px;
}

.clue-meta {
  font-size: 13px;
  color: #909399;
}

.clue-actions {
  display: flex;
  gap: 8px;
}
</style>
