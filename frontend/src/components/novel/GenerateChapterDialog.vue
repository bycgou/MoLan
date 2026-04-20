<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" title="生成新章节" width="700px" @close="$emit('close')">
    <el-form :model="form" label-width="100px" v-loading="generating && !taskId" element-loading-text="正在生成章纲...">
      <el-form-item label="章节号">
        <el-input-number v-model="form.chapterNum" :min="1" />
      </el-form-item>
      <el-form-item label="选择模型">
        <el-select v-model="form.configId" placeholder="选择要使用的模型" style="width: 100%">
          <el-option
            v-for="config in llmConfigs"
            :key="config.configId"
            :label="`${config.configName} (${config.modelName})`"
            :value="config.configId"
          />
          <el-option label="使用系统默认模型" value="" />
        </el-select>
      </el-form-item>
      <el-form-item label="Strand类型">
        <el-select v-model="form.strandType" placeholder="选择剧情线类型" style="width: 100%">
          <el-option label="Quest（主线）" value="quest" />
          <el-option label="Fire（感情线）" value="fire" />
          <el-option label="Constellation（世界观）" value="constellation" />
        </el-select>
        <div class="form-tip" v-if="recommendedStrand">
          系统推荐：<el-tag size="small">{{ formatStrand(recommendedStrand) }}</el-tag>
        </div>
      </el-form-item>
      <el-form-item label="本章意图">
        <el-input v-model="form.userIntent" type="textarea" :rows="3" placeholder="可选：描述你对本章的特殊要求" />
      </el-form-item>
    </el-form>

    <!-- 章纲预览 -->
    <div v-if="outlineData" class="outline-preview" v-loading="!!taskId" element-loading-text="后台正在生成正文，请耐心等待...">
      <h4>章纲预览</h4>
      <div class="outline-content">{{ outlineData.outline }}</div>
      <h4>实现思路</h4>
      <el-radio-group v-model="selectedIdeaIndex">
        <el-radio v-for="(idea, index) in outlineData.implementationIdeas" :key="index" :value="index">
          思路{{ index + 1 }}：{{ idea }}
        </el-radio>
      </el-radio-group>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button v-if="!outlineData" type="primary" @click="$emit('generate-outline', form)" :loading="generating">
        生成章纲
      </el-button>
      <el-button v-if="outlineData" type="primary" @click="$emit('generate-chapter', { ...form, ideaIndex: selectedIdeaIndex })" :loading="generating">
        生成正文
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import type { LlmConfigVO } from '@/types'
import { formatStrand } from '@/utils/format'

const props = defineProps<{
  visible: boolean
  llmConfigs: LlmConfigVO[]
  recommendedStrand: string
  generating: boolean
  taskId: string
  defaultChapterNum: number
  outlineData?: { outline: string; implementationIdeas: string[] } | null
}>()

defineEmits<{
  'update:visible': [value: boolean]
  'generate-outline': [form: GenerateForm]
  'generate-chapter': [form: GenerateForm & { ideaIndex: number }]
  close: []
}>()

export interface GenerateForm {
  chapterNum: number
  configId: string
  strandType: string
  userIntent: string
}

const selectedIdeaIndex = ref(0)
const form = reactive<GenerateForm>({
  chapterNum: 1,
  configId: '',
  strandType: 'quest',
  userIntent: '',
})

watch(() => props.defaultChapterNum, (val) => {
  form.chapterNum = val
}, { immediate: true })
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.outline-preview {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.outline-preview h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}

.outline-content {
  margin-bottom: 16px;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}
</style>
