<template>
  <div class="characters-page">
    <div class="page-header">
      <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
      <h2>人物管理</h2>
      <el-button type="primary" @click="showCreateDialog" :icon="Plus">添加人物</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="characters.length === 0 && !loading" description="暂无人物" />

      <el-row :gutter="16">
        <el-col :span="8" v-for="char in characters" :key="char.characterId">
          <el-card class="character-card" shadow="hover">
            <template #header>
              <div class="char-header">
                <span class="char-name">{{ char.name }}</span>
                <el-tag :type="getRoleType(char.role)" size="small">{{ char.role }}</el-tag>
              </div>
            </template>

            <div class="char-info">
              <p v-if="char.description"><strong>描述：</strong>{{ char.description }}</p>
              <p v-if="char.personality"><strong>性格：</strong>{{ char.personality }}</p>
              <p v-if="char.background"><strong>背景：</strong>{{ char.background }}</p>
            </div>

            <!-- 成长节点 -->
            <div class="growth-section" v-if="char.growthNodes?.length">
              <el-divider />
              <h4>成长轨迹</h4>
              <el-timeline>
                <el-timeline-item
                  v-for="node in char.growthNodes"
                  :key="node.nodeId"
                  :timestamp="`第${node.chapterNum}章`"
                  placement="top"
                >
                  <el-card class="growth-node-card" shadow="never">
                    <div class="growth-node-header">
                      <strong>{{ node.title }}</strong>
                      <el-tag size="small" type="info">{{ node.growthType }}</el-tag>
                    </div>
                    <p class="growth-desc">{{ node.description }}</p>
                  </el-card>
                </el-timeline-item>
              </el-timeline>
            </div>

            <!-- 关系管理 -->
            <div style="margin-top: 12px; padding-top: 12px; border-top: 1px solid #ebeef5;">
              <div style="font-size: 13px; color: #606266; margin-bottom: 8px;">关系管理</div>
              <div style="display: flex; gap: 8px; align-items: center;">
                <el-select v-model="relTargetId" placeholder="选择目标角色" size="small" style="width: 120px;">
                  <el-option
                    v-for="c in characters.filter(c => c.characterId !== char.characterId)"
                    :key="c.characterId"
                    :label="c.name"
                    :value="c.characterId"
                  />
                </el-select>
                <el-input-number v-model="relValue" :min="-100" :max="100" size="small" style="width: 120px;" />
                <el-button size="small" type="primary" @click="handleUpdateRelationship(char.characterId)">更新关系</el-button>
              </div>
            </div>

            <el-divider />
            <div class="char-actions">
              <el-button size="small" @click="editCharacter(char)">编辑</el-button>
              <el-button size="small" type="primary" @click="showGrowthDialog(char)">添加成长</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 创建/编辑人物对话框 -->
    <el-dialog
      v-model="charDialogVisible"
      :title="editingChar ? '编辑人物' : '添加人物'"
      width="500px"
      destroy-on-close
    >
      <el-form ref="charFormRef" :model="charForm" :rules="charRules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="charForm.name" placeholder="请输入人物姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="charForm.role" placeholder="请选择角色类型">
            <el-option label="主角" value="主角" />
            <el-option label="配角" value="配角" />
            <el-option label="反派" value="反派" />
            <el-option label="龙套" value="龙套" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="charForm.description" type="textarea" :rows="3" placeholder="外貌、身份等描述" />
        </el-form-item>
        <el-form-item label="性格">
          <el-input v-model="charForm.personality" type="textarea" :rows="2" placeholder="性格特点" />
        </el-form-item>
        <el-form-item label="背景">
          <el-input v-model="charForm.background" type="textarea" :rows="2" placeholder="出身背景" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="charDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveCharacter">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加成长节点对话框 -->
    <el-dialog v-model="growthDialogVisible" title="添加成长节点" width="450px" destroy-on-close>
      <el-form ref="growthFormRef" :model="growthForm" :rules="growthRules" label-width="80px">
        <el-form-item label="章节" prop="chapterNum">
          <el-input-number v-model="growthForm.chapterNum" :min="1" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="growthForm.title" placeholder="成长节点标题" />
        </el-form-item>
        <el-form-item label="类型" prop="growthType">
          <el-select v-model="growthForm.growthType" placeholder="请选择类型">
            <el-option label="能力提升" value="能力提升" />
            <el-option label="性格转变" value="性格转变" />
            <el-option label="关系变化" value="关系变化" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="growthForm.description" type="textarea" :rows="3" placeholder="详细描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="growthDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAddGrowth">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色关系图谱 -->
    <el-collapse style="margin-top: 20px;">
      <el-collapse-item title="角色关系图谱">
        <CharacterGraph :graph="relationshipGraph" />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { useNovelStore } from '@/stores'
import { getRoleType } from '@/utils/format'
import CharacterGraph from '@/components/CharacterGraph.vue'
import type { CharacterVO, CreateCharacterRequest, AddGrowthNodeRequest, CharacterGraph as CharacterGraphType } from '@/types'

const route = useRoute()
const router = useRouter()
const novelStore = useNovelStore()
const novelId = route.params.novelId as string

const loading = ref(true)
const saving = ref(false)
const characters = ref<CharacterVO[]>([])

// 人物对话框
const charDialogVisible = ref(false)
const editingChar = ref<CharacterVO | null>(null)
const charFormRef = ref<FormInstance>()
const charForm = reactive<CreateCharacterRequest>({
  name: '',
  role: '',
  description: '',
  personality: '',
  background: '',
})
const charRules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色类型', trigger: 'change' }],
}

// 成长节点对话框
const growthDialogVisible = ref(false)
const growthCharId = ref('')
const growthFormRef = ref<FormInstance>()
const growthForm = reactive<AddGrowthNodeRequest>({
  chapterNum: 1,
  title: '',
  description: '',
  growthType: '',
})
const growthRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  growthType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
}

// 关系管理
const relTargetId = ref('')
const relValue = ref(0)
const relationshipGraph = ref<CharacterGraphType | null>(null)

const handleUpdateRelationship = async (characterId: string) => {
  if (!relTargetId.value) {
    ElMessage.warning('请选择目标角色')
    return
  }
  try {
    await novelStore.updateRelationship(novelId, characterId, relTargetId.value, relValue.value)
    ElMessage.success('关系已更新')
    loadRelationshipGraph()
  } catch {
    ElMessage.error('更新关系失败')
  }
}

const loadRelationshipGraph = async () => {
  try {
    relationshipGraph.value = await novelStore.fetchRelationshipGraph(novelId)
  } catch {
    // 图谱加载失败不阻塞页面
  }
}

onMounted(async () => {
  await loadCharacters()
})

async function loadCharacters() {
  loading.value = true
  try {
    await novelStore.fetchCharacters(novelId)
    characters.value = novelStore.characters
    loadRelationshipGraph()
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  editingChar.value = null
  Object.assign(charForm, { name: '', role: '', description: '', personality: '', background: '' })
  charDialogVisible.value = true
}

function editCharacter(char: CharacterVO) {
  editingChar.value = char
  Object.assign(charForm, {
    name: char.name,
    role: char.role,
    description: char.description || '',
    personality: char.personality || '',
    background: char.background || '',
  })
  charDialogVisible.value = true
}

async function handleSaveCharacter() {
  const valid = await charFormRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    let success: boolean
    if (editingChar.value) {
      success = await novelStore.updateCharacter(novelId, editingChar.value.characterId, {
        currentStatus: {},
        relationshipMap: {},
      })
    } else {
      success = await novelStore.createCharacter(novelId, charForm)
    }
    if (success) {
      ElMessage.success(editingChar.value ? '更新成功' : '创建成功')
      charDialogVisible.value = false
      characters.value = novelStore.characters
    } else {
      ElMessage.error('操作失败')
    }
  } finally {
    saving.value = false
  }
}

function showGrowthDialog(char: CharacterVO) {
  growthCharId.value = char.characterId
  Object.assign(growthForm, { chapterNum: 1, title: '', description: '', growthType: '' })
  growthDialogVisible.value = true
}

async function handleAddGrowth() {
  const valid = await growthFormRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const success = await novelStore.addGrowthNode(novelId, growthCharId.value, growthForm)
    if (success) {
      ElMessage.success('添加成功')
      growthDialogVisible.value = false
      characters.value = novelStore.characters
    } else {
      ElMessage.error('添加失败')
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
.characters-page {
  max-width: 1200px;
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

.character-card {
  margin-bottom: 16px;
}

.char-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.char-name {
  font-size: 16px;
  font-weight: bold;
}

.char-info p {
  margin: 4px 0;
  font-size: 13px;
  color: #606266;
}

.growth-section h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #409EFF;
}

.growth-node-card {
  margin-bottom: 8px;
}

.growth-node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.growth-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}

.char-actions {
  display: flex;
  gap: 8px;
}
</style>
