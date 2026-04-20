<template>
  <div class="create-page">
    <el-card class="create-card">
      <template #header>
        <div class="card-header">
          <span>创建新小说</span>
        </div>
      </template>
      
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="小说名称" prop="novelName">
          <el-input v-model="form.novelName" placeholder="请输入小说名称" maxlength="100" show-word-limit />
        </el-form-item>
        
        <el-form-item label="小说类型" prop="genre">
          <el-select v-model="form.genre" placeholder="请选择小说类型" @change="onGenreChange">
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
        
        <!-- 题材模板预览 -->
        <el-form-item label="题材指导" v-if="genreTemplate">
          <div class="genre-template">
            <el-collapse v-model="activeCollapse">
              <el-collapse-item title="常见套路" name="tropes">
                <ul class="template-list">
                  <li v-for="(trope, index) in genreTemplate.tropes" :key="index">{{ trope }}</li>
                </ul>
              </el-collapse-item>
              <el-collapse-item title="避坑指南" name="pitfalls">
                <ul class="template-list">
                  <li v-for="(pitfall, index) in genreTemplate.pitfalls" :key="index">{{ pitfall }}</li>
                </ul>
              </el-collapse-item>
              <el-collapse-item title="人物原型" name="archetypes">
                <div class="archetype-list">
                  <div v-for="(desc, role) in genreTemplate.characterArchetypes" :key="role" class="archetype-item">
                    <span class="archetype-role">{{ role }}：</span>
                    <span class="archetype-desc">{{ desc }}</span>
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-form-item>
        
        <el-form-item label="核心创意" prop="prompt">
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="3"
            placeholder="可选：一句话描述你的小说创意（比如：男主是个被退婚的赘婿，后来发现自己是神帝转世）"
          />
          <div style="margin-top: 10px;">
            <el-button type="success" plain @click="handleAutoGenerate" :loading="generating" :disabled="!form.novelName || !form.genre">
              ✨ AI一键生成【总纲】与【世界观】
            </el-button>
          </div>
        </el-form-item>
        
        <el-form-item label="总纲" prop="totalOutline">
          <el-input
            v-model="form.totalOutline"
            type="textarea"
            :rows="8"
            placeholder="请输入小说总纲，描述主要剧情走向、核心冲突、结局等&#10;&#10;建议包含：&#10;- 卷划分和每卷主题&#10;- 主要剧情转折点&#10;- 人物成长弧线&#10;- 核心冲突和结局"
          />
          <div class="form-tip">总纲越详细，AI生成的内容越符合预期</div>
        </el-form-item>
        
        <el-form-item label="世界观" prop="worldSetting">
          <el-input
            v-model="form.worldSetting"
            type="textarea"
            :rows="4"
            placeholder="请输入世界观设定，包括时间背景、地理环境、力量体系等"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            创建小说
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { novelApi, genreTemplateApi } from '@/api'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const generating = ref(false)
const genreTemplate = ref<any>(null)
const activeCollapse = ref(['tropes'])

const form = reactive({
  novelName: '',
  genre: '',
  prompt: '',
  totalOutline: '',
  worldSetting: ''
})

const rules: FormRules = {
  novelName: [
    { required: true, message: '请输入小说名称', trigger: 'blur' },
    { min: 2, max: 100, message: '小说名称长度在2-100个字符之间', trigger: 'blur' }
  ],
  genre: [
    { required: true, message: '请选择小说类型', trigger: 'change' }
  ],
  totalOutline: [
    { required: true, message: '请输入小说总纲', trigger: 'blur' },
    { min: 50, message: '总纲至少50个字符', trigger: 'blur' }
  ]
}

const onGenreChange = async (genreCode: string) => {
  if (!genreCode) {
    genreTemplate.value = null
    return
  }
  try {
    const response = await genreTemplateApi.getTemplatesByGenre(genreCode)
    if (response.code === 200 && response.data) {
      genreTemplate.value = response.data
    } else {
      genreTemplate.value = null
    }
  } catch (error) {
    console.error('加载题材模板失败:', error)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    submitting.value = true
    
    const response = await novelApi.createNovel(form)
    if (response.code === 200) {
      ElMessage.success('小说创建成功！')
      router.push(`/novel/${response.data.novelId}`)
    } else {
      ElMessage.error(response.message || '创建失败')
    }
  } catch (error) {
    console.error('创建小说失败:', error)
    ElMessage.error('创建失败，请检查输入')
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  formRef.value?.resetFields()
  genreTemplate.value = null
  form.prompt = ''
}

const handleAutoGenerate = async () => {
  if (!form.novelName || !form.genre) {
    ElMessage.warning('请先填写小说名称并选择类型')
    return
  }
  
  generating.value = true
  try {
    const response = await novelApi.autoGenerateOutline({
      novelName: form.novelName,
      genre: form.genre,
      prompt: form.prompt
    })
    
    if (response.code === 200 && response.data) {
      const data = response.data as { totalOutline?: string; worldSetting?: string }
      form.totalOutline = data.totalOutline || ''
      form.worldSetting = data.worldSetting || ''
      ElMessage.success('AI 生成大纲成功！您可以继续修改完善')
    } else {
      ElMessage.error(response.message || '生成失败')
    }
  } catch (error) {
    console.error('自动生成大纲失败:', error)
    ElMessage.error('网络请求失败，请稍后重试')
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.create-page {
  max-width: 900px;
  margin: 0 auto;
}

.create-card {
  margin-bottom: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.el-form-item {
  margin-bottom: 22px;
}

.el-select {
  width: 100%;
}

.genre-template {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
}

.template-list {
  margin: 0;
  padding-left: 20px;
}

.template-list li {
  margin: 5px 0;
  color: #606266;
}

.archetype-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.archetype-item {
  background: white;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
}

.archetype-role {
  font-weight: bold;
  color: #409EFF;
}

.archetype-desc {
  color: #606266;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}
</style>
