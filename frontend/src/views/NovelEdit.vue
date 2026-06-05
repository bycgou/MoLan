<template>
  <div class="novel-edit-page" v-loading="loading">
    <div class="page-header">
      <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
      <h2>编辑小说</h2>
    </div>

    <el-card v-if="novel">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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

        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option :value="0" label="草稿" />
            <el-option :value="1" label="连载中" />
            <el-option :value="2" label="已完结" />
          </el-select>
        </el-form-item>

        <el-form-item label="简介" prop="totalOutline">
          <el-input v-model="form.totalOutline" type="textarea" :rows="6" placeholder="请输入小说简介" maxlength="2000" show-word-limit />
        </el-form-item>

        <el-form-item label="世界观设定" prop="worldSetting">
          <el-input v-model="form.worldSetting" type="textarea" :rows="4" placeholder="描述小说的世界观设定，包括时代背景、地理环境、社会制度、力量体系等" maxlength="2000" show-word-limit />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { novelApi } from '@/api'
import type { NovelVO } from '@/types'
import { useAutoSave } from '@/utils/autoSave'

const route = useRoute()
const router = useRouter()
const novelId = route.params.novelId as string

const loading = ref(true)
const saving = ref(false)
const novel = ref<NovelVO | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  novelName: '',
  genre: '',
  status: 0,
  totalOutline: '',
  worldSetting: '',
})

const novelData = computed(() => ({
  novelName: form.novelName,
  genre: form.genre,
  status: form.status,
  totalOutline: form.totalOutline,
  worldSetting: form.worldSetting,
}))

const autoSave = useAutoSave({
  key: `novel_edit_${route.params.novelId}`,
  data: novelData,
  delay: 2000,
})

const rules: FormRules = {
  novelName: [
    { required: true, message: '请输入小说名称', trigger: 'blur' },
  ],
  genre: [
    { required: true, message: '请选择小说类型', trigger: 'change' },
  ],
}

onMounted(async () => {
  try {
    const res = await novelApi.getNovel(novelId)
    if (res.code === 200) {
      novel.value = res.data
      form.novelName = res.data.novelName
      form.genre = res.data.genre
      form.status = res.data.status
      form.totalOutline = res.data.totalOutline || ''
      form.worldSetting = res.data.worldSetting || ''

      if (await autoSave.promptRestore()) {
        const draft = autoSave.load()
        if (draft) {
          Object.assign(form, draft)
          ElMessage.success('已恢复未保存的编辑')
        }
      }
    }
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const res = await novelApi.updateNovel(novelId, form)
    if (res.code === 200) {
      ElMessage.success('保存成功')
      autoSave.clear()
      router.push(`/novel/${novelId}`)
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
  router.push(`/novel/${novelId}`)
}
</script>

<style scoped>
.novel-edit-page {
  max-width: 800px;
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
</style>
