<template>
  <div class="create-page">
    <el-card class="create-card">
      <template #header>
        <div class="card-header">
          <span>创建新小说</span>
          <el-button text @click="router.push('/create-wizard')">切换到向导模式 →</el-button>
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
          </el-select>
          <div class="form-tip">AI生成时会严格避免这些内容</div>
        </el-form-item>
        
        <!-- 文风选择器 -->
        <el-form-item label="写作风格">
          <StyleSelector 
            v-model="form.styleCardId" 
            :show-preview="true"
            @create="router.push('/style-cards')"
          />
          <div class="form-tip">
            可选：选择一个文风卡片，AI会模仿该风格写作
            <el-button type="primary" link @click="router.push('/style-cards')">
              上传小说创建新风格 →
            </el-button>
          </div>
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
          <!-- 写作指南 -->
          <div v-if="guideFiles.length > 0" style="margin-top: 10px;">
            <div style="font-weight: 500; margin-bottom: 8px;">写作指南</div>
            <div style="display: flex; flex-wrap: wrap; gap: 8px;">
              <el-tag
                v-for="file in guideFiles"
                :key="file"
                type="info"
                style="cursor: pointer;"
                @click="openGuideFile(file)"
              >
                {{ file.replace('.md', '') }}
              </el-tag>
            </div>
          </div>
          <!-- 指南内容对话框 -->
          <el-dialog v-model="guideDialogVisible" :title="guideDialogTitle" width="60%">
            <div style="white-space: pre-wrap; max-height: 60vh; overflow-y: auto;">{{ guideDialogContent }}</div>
          </el-dialog>
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { novelApi, genreApi } from '@/api'
import { styleApi } from '@/api/style'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import StyleSelector from '@/components/StyleSelector.vue'
import { useAutoSave } from '@/utils/autoSave'
import { computed as vueComputed } from 'vue'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const generating = ref(false)
const genreTemplate = ref<import('@/types').GenreTemplate | null>(null)
const activeCollapse = ref(['tropes'])

const form = reactive({
  novelName: '',
  genre: '',
  novelTypes: [] as string[],
  tones: [] as string[],
  taboos: [] as string[],
  prompt: '',
  totalOutline: '',
  worldSetting: '',
  styleCardId: ''  // 文风卡片ID
})

const formData = vueComputed(() => ({ ...form }))
const autoSave = useAutoSave({
  key: 'create_novel',
  data: formData,
  delay: 2000,
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
  ],
  worldSetting: [
    { required: true, message: '请输入世界观设定', trigger: 'blur' },
    { min: 10, message: '世界观设定至少10个字符', trigger: 'blur' }
  ]
}

const guideFiles = ref<string[]>([])
const guideDialogContent = ref('')
const guideDialogVisible = ref(false)
const guideDialogTitle = ref('')

const onGenreChange = async (genreCode: string) => {
  if (!genreCode) {
    genreTemplate.value = null
    guideFiles.value = []
    return
  }
  try {
    const response = await genreApi.getGenreTemplate(genreCode)
    if (response.code === 200 && response.data) {
      genreTemplate.value = response.data
    } else {
      genreTemplate.value = null
    }
  } catch (error) {
    console.error('加载题材模板失败:', error)
    genreTemplate.value = null
  }
  // 加载体裁指南文件列表
  try {
    const guideRes = await genreApi.getGuideList(genreCode)
    guideFiles.value = guideRes.code === 200 ? (guideRes.data || []) : []
  } catch {
    guideFiles.value = []
  }
}

const openGuideFile = async (filename: string) => {
  if (!form.genre) return
  try {
    const res = await genreApi.getGuideFile(form.genre, filename)
    if (res.code === 200) {
      guideDialogContent.value = res.data || ''
      guideDialogTitle.value = filename.replace('.md', '')
      guideDialogVisible.value = true
    }
  } catch (error) {
    console.error('加载指南文件失败:', error)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    submitting.value = true
    
    const response = await novelApi.createNovel({
      novelName: form.novelName,
      genre: form.genre,
      novelType: form.novelTypes.length > 0 ? JSON.stringify(form.novelTypes) : undefined,
      tone: form.tones.length > 0 ? JSON.stringify(form.tones) : undefined,
      tabooList: form.taboos.length > 0 ? JSON.stringify(form.taboos) : undefined,
      totalOutline: form.totalOutline,
      worldSetting: form.worldSetting
    })
    if (response.code === 200) {
      // 如果选择了文风卡片，绑定到小说
      if (form.styleCardId) {
        try {
          await styleApi.bindNovel(response.data.novelId, form.styleCardId)
        } catch (e) {
          console.warn('绑定文风卡片失败:', e)
        }
      }
      ElMessage.success('小说创建成功！')
      autoSave.clear()
      router.push(`/novel/${response.data.novelId}`)
    } else {
      ElMessage.error(response.message || '创建失败')
    }
  } catch (error) {
    // Axios 拦截器已显示友好错误消息，此处不再重复提示
    console.error('创建小说失败:', error)
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  formRef.value?.resetFields()
  genreTemplate.value = null
  form.prompt = ''
  form.styleCardId = ''
  form.novelTypes = []
  form.tones = []
  form.taboos = []
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
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  const styleCardId = route.query.styleCardId as string
  if (styleCardId) {
    form.styleCardId = styleCardId
  }

  // 检查并恢复草稿
  if (await autoSave.promptRestore()) {
    const draft = autoSave.load()
    if (draft) {
      Object.assign(form, draft)
      ElMessage.success('已恢复草稿')
    }
  }
})
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
  display: flex;
  justify-content: space-between;
  align-items: center;
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
