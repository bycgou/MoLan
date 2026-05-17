<template>
  <div class="llm-config-page">
    <div class="page-header">
      <h2>模型配置</h2>
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>
        添加模型
      </el-button>
    </div>

    <el-alert
      title="配置说明"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    >
      <template #default>
        <p>支持所有 OpenAI 兼容 API（OpenAI / DeepSeek / 智谱 / 通义千问 / 月之暗面 / Ollama 等）。</p>
        <p>API 地址填写到版本路径为止（如 <code>/v1</code>），系统自动追加 <code>/chat/completions</code>。</p>
        <p>Ollama 本地模型地址示例：<code>http://localhost:11434/v1</code>，API Key 随意填写即可。</p>
      </template>
    </el-alert>

    <!-- 快速配置模板 -->
    <el-card class="template-card" shadow="never">
      <template #header>
        <span>快速配置模板（点击自动填入）</span>
      </template>
      <div class="template-list">
        <el-button 
          v-for="tpl in templates" 
          :key="tpl.name"
          @click="applyTemplate(tpl)"
          size="small"
        >
          {{ tpl.name }}
        </el-button>
      </div>
    </el-card>

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="8" v-for="config in configs" :key="config.configId">
        <el-card class="config-card" shadow="hover">
          <div class="config-header">
            <div class="config-name">
              <span>{{ config.configName }}</span>
              <el-tag :type="config.isActive === 1 ? 'success' : 'info'" size="small">
                {{ config.isActive === 1 ? '启用' : '禁用' }}
              </el-tag>
            </div>
            <el-dropdown @command="(cmd: string) => handleCommand(cmd, config)">
              <el-button :icon="MoreFilled" circle size="small" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item command="test">测试连接</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          
          <div class="config-info">
            <div class="info-item">
              <span class="label">模型：</span>
              <span class="value">{{ config.modelName }}</span>
            </div>
            <div class="info-item">
              <span class="label">API地址：</span>
              <el-tooltip :content="getFullUrl(config)" placement="top">
                <span class="value url">{{ formatUrl(getFullUrl(config)) }}</span>
              </el-tooltip>
            </div>
            <div class="info-item">
              <span class="label">认证：</span>
              <el-tag size="small" effect="plain">{{ config.authType }}</el-tag>
            </div>
            <div class="info-item">
              <span class="label">用途：</span>
              <el-tag size="small" :type="getUsageTypeTag(config.usageType)">
                {{ getUsageTypeText(config.usageType) }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && configs.length === 0" description="暂无模型配置">
      <el-button type="primary" @click="showAddDialog">添加模型</el-button>
    </el-empty>

    <!-- 添加/编辑对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑模型配置' : '添加模型配置'" 
      width="700px"
    >
      <el-form :model="form" :rules="getRules()" ref="formRef" label-width="100px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="例如：DeepSeek" />
        </el-form-item>
        
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="例如：deepseek-chat, gpt-4o, glm-4" />
          <div class="form-tip">填写API要求的模型标识符</div>
        </el-form-item>

        <el-form-item label="模型类型">
          <el-select v-model="form.modelType" placeholder="选择模型类型" style="width: 100%">
            <el-option label="Chat（对话模型）" value="chat" />
            <el-option label="Embedding（向量模型）" value="embedding" />
          </el-select>
          <div class="form-tip">Embedding 模型用于文本向量化（如 qwen3-embedding、nomic-embed-text）</div>
        </el-form-item>
        
        <el-form-item label="API地址">
          <el-input 
            v-model="form.baseUrl" 
            type="textarea"
            :rows="3"
            placeholder="API基础地址，例如：https://api.deepseek.com/v1 或 http://localhost:11434/v1" 
          />
          <div class="form-tip">
            <strong>填写API基础地址（到版本路径为止，不要包含 /chat/completions）</strong><br/>
            远程模型：https://api.deepseek.com/v1 或 https://api.openai.com/v1<br/>
            本地Ollama：http://localhost:11434/v1（API Key可随意填写如 "ollama"）
          </div>
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="认证类型">
              <el-select v-model="form.authType" placeholder="选择认证类型">
                <el-option label="Bearer Token" value="bearer" />
                <el-option label="X-Api-Key" value="api_key" />
                <el-option label="Claude专用" value="anthropic" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用途">
              <el-select v-model="form.usageType" placeholder="选择用途">
                <el-option label="全部" value="all" />
                <el-option label="大纲" value="outline" />
                <el-option label="正文" value="writing" />
                <el-option label="摘要" value="summary" />
                <el-option label="审核" value="audit" />
                <el-option label="向量化" value="embedding" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="isEdit ? '不修改请留空' : '请输入API Key'" />
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大Token">
              <el-input-number v-model="form.maxTokens" :min="100" :max="200000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="启用状态">
          <el-switch v-model="form.isActive" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="handleTest" :loading="testing">测试连接</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, MoreFilled } from '@element-plus/icons-vue'
import { llmConfigApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { LlmConfigVO } from '@/types'

// 快速配置模板
const templates = [
  { name: 'Ollama(本地)', baseUrl: 'http://localhost:11434/v1', modelName: 'qwen2.5:7b', apiKey: 'ollama' },
  { name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', modelName: 'deepseek-chat' },
  { name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', modelName: 'gpt-4o' },
  { name: '智谱GLM', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', modelName: 'glm-4' },
  { name: '通义千问', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'qwen-max' },
  { name: '月之暗面', baseUrl: 'https://api.moonshot.cn/v1', modelName: 'moonshot-v1-128k' },
  { name: '文心一言', baseUrl: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop', modelName: 'ernie-4.0' },
  // Embedding 模型模板
  { name: 'DashScope Embedding', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'text-embedding-v2', modelType: 'embedding', usageType: 'embedding' },
  { name: 'OpenAI Embedding', baseUrl: 'https://api.openai.com/v1', modelName: 'text-embedding-ada-002', modelType: 'embedding', usageType: 'embedding' },
  { name: 'Ollama Embedding', baseUrl: 'http://localhost:11434/v1', modelName: 'nomic-embed-text', apiKey: 'ollama', modelType: 'embedding', usageType: 'embedding' },
]

const loading = ref(false)
const submitting = ref(false)
const testing = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref<FormInstance>()

const configs = ref<LlmConfigVO[]>([])

const form = reactive({
  configName: '',
  provider: 'openai',
  modelName: '',
  modelType: 'chat',
  baseUrl: '',
  authType: 'bearer',
  apiKey: '',
  usageType: 'all',
  maxTokens: 16384,
  temperature: 0.7,
  topP: 1,
  isActive: 1
})

const rules: FormRules = {
  configName: [
    { required: true, message: '请输入配置名称', trigger: 'blur' }
  ],
  modelName: [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
  ],
  apiKey: [
    { required: true, message: '请输入API Key', trigger: 'blur' }
  ]
}

// 编辑时移除apiKey必填校验
const getRules = () => {
  if (isEdit.value) {
    return {
      ...rules,
      apiKey: []
    }
  }
  return rules
}

const getFullUrl = (config: any) => {
  if (config.apiEndpoint) {
    let baseUrl = config.baseUrl || ''
    if (baseUrl.endsWith('/')) baseUrl = baseUrl.slice(0, -1)
    let endpoint = config.apiEndpoint
    if (endpoint.startsWith('/')) endpoint = endpoint.slice(1)
    return baseUrl + '/' + endpoint
  }
  return config.baseUrl || ''
}

const formatUrl = (url: string) => {
  if (!url) return ''
  if (url.length > 40) return url.substring(0, 40) + '...'
  return url
}

const getUsageTypeText = (type: string) => {
  const map: Record<string, string> = {
    all: '全部',
    outline: '大纲',
    writing: '正文',
    summary: '摘要',
    audit: '审核',
    embedding: '向量化'
  }
  return map[type] || type
}

const getUsageTypeTag = (type: string) => {
  const map: Record<string, string> = {
    all: '',
    outline: 'warning',
    writing: 'success',
    summary: 'info',
    audit: 'danger',
    embedding: ''
  }
  return map[type] || ''
}

const loadConfigs = async () => {
  loading.value = true
  try {
    const response = await llmConfigApi.getConfigList()
    if (response.code === 200) {
      configs.value = response.data
    }
  } catch (error) {
    console.error('加载模型配置失败:', error)
  } finally {
    loading.value = false
  }
}

const applyTemplate = (template: any) => {
  resetForm()
  isEdit.value = false
  editingId.value = ''
  form.configName = template.name
  form.baseUrl = template.baseUrl
  form.modelName = template.modelName
  form.authType = template.authType || 'bearer'
  form.apiKey = template.apiKey || ''
  if (template.modelType) form.modelType = template.modelType
  if (template.usageType) form.usageType = template.usageType

  dialogVisible.value = true
}

const showAddDialog = () => {
  isEdit.value = false
  editingId.value = ''
  resetForm()
  dialogVisible.value = true
}

const showEditDialog = (config: any) => {
  isEdit.value = true
  editingId.value = config.configId
  Object.assign(form, {
    configName: config.configName,
    modelName: config.modelName,
    modelType: config.provider === 'embedding' ? 'embedding' : 'chat',
    baseUrl: config.apiBaseUrl || config.baseUrl || '',
    authType: config.authType || 'bearer',
    apiKey: '',  // 不填入脱敏值，留空表示不修改
    usageType: config.usageType,
    maxTokens: config.maxTokens,
    temperature: config.temperature,
    isActive: config.isActive
  })
  dialogVisible.value = true
}

const resetForm = () => {
  form.configName = ''
  form.modelName = ''
  form.modelType = 'chat'
  form.baseUrl = ''
  form.authType = 'bearer'
  form.apiKey = ''
  form.usageType = 'all'
  form.maxTokens = 4096
  form.temperature = 0.7
  form.isActive = 1
}

const handleTest = async () => {
  if (!form.apiKey || !form.modelName) {
    ElMessage.warning('请先填写API Key和模型名称')
    return
  }
  if (!form.baseUrl) {
    ElMessage.warning('请填写API地址')
    return
  }
  testing.value = true
  try {
    const testData = {
      provider: form.modelType === 'embedding' ? 'embedding' : form.provider,
      apiKey: form.apiKey,
      apiBaseUrl: form.baseUrl,  // 映射 baseUrl → apiBaseUrl
      modelName: form.modelName
    }
    const response = await llmConfigApi.testConnection(testData)
    if (response.code === 200 && response.data) {
      ElMessage.success('连接成功！')
    } else {
      ElMessage.error(response.message || '连接失败，请检查配置')
    }
  } catch (error) {
    ElMessage.error('连接测试失败')
  } finally {
    testing.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    
    submitting.value = true
    
    // 构建符合后端的数据结构
    const submitData: Record<string, unknown> = {
      configName: form.configName,
      provider: form.modelType === 'embedding' ? 'embedding' : form.provider,
      modelName: form.modelName,
      apiBaseUrl: form.baseUrl,
      authType: form.authType,
      usageType: form.usageType,
      maxTokens: form.maxTokens,
      temperature: form.temperature,
      topP: form.topP,
      isActive: form.isActive
    }
    // 编辑时若apiKey为空则不提交（保留原值）
    if (form.apiKey) {
      submitData.apiKey = form.apiKey
    }
    
    let response
    if (isEdit.value) {
      response = await llmConfigApi.updateConfig(editingId.value, submitData as any)
    } else {
      response = await llmConfigApi.createConfig(submitData as any)
    }
    
    if (response.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadConfigs()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitting.value = false
  }
}

const handleCommand = async (command: string, config: any) => {
  switch (command) {
    case 'edit':
      showEditDialog(config)
      break
    case 'test':
      testing.value = true
      try {
        const response = await llmConfigApi.testSavedConfig(config.configId)
        if (response.code === 200 && response.data) {
          ElMessage.success('连接成功！')
        } else {
          ElMessage.error(response.message || '连接失败')
        }
      } catch (error) {
        ElMessage.error('连接测试失败')
      } finally {
        testing.value = false
      }
      break
    case 'delete':
      try {
        await ElMessageBox.confirm('确定要删除这个模型配置吗？', '确认删除', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        const response = await llmConfigApi.deleteConfig(config.configId)
        if (response.code === 200) {
          ElMessage.success('删除成功')
          loadConfigs()
        }
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败')
        }
      }
      break
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.llm-config-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: #303133;
}

.template-card {
  margin-bottom: 20px;
}

.template-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.config-card {
  margin-bottom: 20px;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.config-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.config-info {
  font-size: 13px;
}

.info-item {
  display: flex;
  margin-bottom: 8px;
  align-items: center;
}

.info-item .label {
  color: #909399;
  width: 60px;
  flex-shrink: 0;
}

.info-item .value {
  color: #303133;
  word-break: break-all;
}

.info-item .value.url {
  cursor: pointer;
  color: #409EFF;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
  line-height: 1.5;
}

.el-select {
  width: 100%;
}
</style>
