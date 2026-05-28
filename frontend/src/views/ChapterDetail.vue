<template>
  <div class="chapter-detail" v-loading="loading">
    <el-card class="chapter-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
            <span class="chapter-title">第{{ chapterNum }}章 {{ chapter?.chapterTitle }}</span>
          </div>
          <div class="header-right">
            <el-button type="warning" @click="rewriteDialogVisible = true" plain>
              <el-icon><MagicStick /></el-icon> AI重写
            </el-button>
            <el-button type="info" @click="showVersions" plain>
              <el-icon><RefreshLeft /></el-icon>
              历史版本
            </el-button>
            <el-button @click="prevChapter" :disabled="chapterNum <= 1">上一章</el-button>
            <el-button @click="nextChapter">下一章</el-button>
          </div>
        </div>
      </template>
      
      <div class="chapter-info" v-if="chapter">
        <div class="info-row">
          <span class="info-label">字数：</span>
          <span>{{ chapter.wordCount || 0 }}字</span>
        </div>
        <div class="info-row" v-if="chapter.plotFingerprint">
          <span class="info-label">剧情摘要：</span>
          <span>{{ chapter.plotFingerprint }}</span>
        </div>
      </div>
      
      <el-divider />
      
      <div class="chapter-content" v-if="chapter?.content">
        <p v-for="(para, idx) in chapterParagraphs" :key="idx">{{ para }}</p>
      </div>
      
      <el-empty v-else description="暂无内容" />
    </el-card>

    <!-- 本章伏笔 -->
    <el-collapse style="margin-top: 20px;">
      <el-collapse-item name="bury">
        <template #title>
          <span>本章埋设的伏笔 ({{ buryClues.length }})</span>
        </template>
        <el-table :data="buryClues" v-loading="loadingBuryClues" size="small">
          <el-table-column prop="title" label="伏笔名称" />
          <el-table-column prop="clueType" label="类型" width="100" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="buryClues.length === 0 && !loadingBuryClues" description="本章未埋设伏笔" :image-size="60" />
      </el-collapse-item>
      <el-collapse-item name="recycle">
        <template #title>
          <span>本章回收的伏笔 ({{ recycleClues.length }})</span>
        </template>
        <el-table :data="recycleClues" v-loading="loadingRecycleClues" size="small">
          <el-table-column prop="title" label="伏笔名称" />
          <el-table-column prop="clueType" label="类型" width="100" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="recycleClues.length === 0 && !loadingRecycleClues" description="本章未回收伏笔" :image-size="60" />
      </el-collapse-item>
    </el-collapse>
    <!-- AI 重写对话框 -->
    <RewriteChapterDialog
      v-model:visible="rewriteDialogVisible"
      :novel-id="novelId"
      :chapter-num="chapterNum"
      :chapter-outline="chapter?.chapterOutline || ''"
      @rewritten="loadChapter"
    />
    <!-- 历史版本对话框 -->
    <el-dialog v-model="versionDialogVisible" title="历史版本记录" width="800px">
      <el-table :data="versionList" style="width: 100%" v-loading="loadingVersions">
        <el-table-column prop="createTime" label="保存时间" width="180">
          <template #default="scope">
            {{ new Date(scope.row.createTime).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="versionDesc" label="版本描述" min-width="150" />
        <el-table-column prop="wordCount" label="字数" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button type="primary" size="small" plain @click="previewVersion(scope.row)">预览</el-button>
            <el-button type="warning" size="small" @click="rollbackVersion(scope.row)">回滚到此版</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 版本预览对话框 -->
    <el-dialog v-model="previewDialogVisible" title="版本预览" width="800px">
      <div class="chapter-content" style="white-space: pre-wrap;">{{ previewContent }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RefreshLeft, MagicStick } from '@element-plus/icons-vue'
import RewriteChapterDialog from '@/components/novel/RewriteChapterDialog.vue'
import { chapterApi, novelApi, clueApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const novelId = route.params.novelId as string
const chapterNum = ref(parseInt(route.params.chapterNum as string))

const loading = ref(false)
const rewriteDialogVisible = ref(false)
const chapter = ref<any>(null)
const totalChapters = ref(0)

const chapterParagraphs = computed(() => {
  if (!chapter.value?.content) return []
  return chapter.value.content
    .split(/\n{2,}/)  // 按双换行分段
    .map((p: string) => p.trim())
    .filter((p: string) => p.length > 0)
})
const versionDialogVisible = ref(false)
const loadingVersions = ref(false)
const versionList = ref<any[]>([])
const previewDialogVisible = ref(false)
const previewContent = ref('')

// 伏笔查询
const buryClues = ref<import('@/types').ClueVO[]>([])
const recycleClues = ref<import('@/types').ClueVO[]>([])
const loadingBuryClues = ref(false)
const loadingRecycleClues = ref(false)

const loadChapterClues = async () => {
  if (!novelId || !chapterNum.value) return
  loadingBuryClues.value = true
  loadingRecycleClues.value = true
  try {
    const [buryRes, recycleRes] = await Promise.all([
      clueApi.getCluesByBuryChapter(novelId, chapterNum.value),
      clueApi.getCluesByRecycleChapter(novelId, chapterNum.value),
    ])
    buryClues.value = buryRes.data || []
    recycleClues.value = recycleRes.data || []
  } catch {
    // 伏笔查询失败不阻塞页面
  } finally {
    loadingBuryClues.value = false
    loadingRecycleClues.value = false
  }
}

const showVersions = async () => {
  versionDialogVisible.value = true
  loadingVersions.value = true
  try {
    const response = await chapterApi.getChapterVersions(novelId, chapterNum.value)
    if (response.code === 200) {
      versionList.value = response.data
    }
  } catch (error) {
    console.error('加载历史版本失败:', error)
  } finally {
    loadingVersions.value = false
  }
}

const previewVersion = (version: any) => {
  previewContent.value = version.content
  previewDialogVisible.value = true
}

const rollbackVersion = (version: any) => {
  ElMessageBox.confirm('回滚后，当前内容将作为历史版本保存，确定要回滚吗？', '回滚确认', {
    confirmButtonText: '确定回滚',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const response = await chapterApi.rollbackChapter(novelId, chapterNum.value, version.versionId)
      if (response.code === 200) {
        ElMessage.success('回滚成功')
        versionDialogVisible.value = false
        // 重新加载章节内容
        await loadChapter()
      } else {
        ElMessage.error(response.message || '回滚失败')
      }
    } catch (error) {
      ElMessage.error('回滚失败')
    }
  }).catch(() => {})
}

const loadChapter = async () => {
  loading.value = true
  try {
    const response = await chapterApi.getChapter(novelId, chapterNum.value)
    if (response.code === 200) {
      chapter.value = response.data
      loadChapterClues()
    } else {
      ElMessage.error('加载章节失败')
    }
  } catch (error) {
    console.error('加载章节失败:', error)
    ElMessage.error('加载章节失败')
  } finally {
    loading.value = false
  }
}

const loadNovelInfo = async () => {
  try {
    const response = await novelApi.getNovel(novelId)
    if (response.code === 200) {
      totalChapters.value = response.data.chapterCount || 0
    }
  } catch (error) {
    console.error('加载小说信息失败:', error)
  }
}

const goBack = () => {
  router.push(`/novel/${novelId}`)
}

const prevChapter = () => {
  if (chapterNum.value > 1) {
    router.push(`/novel/${novelId}/chapter/${chapterNum.value - 1}`)
  }
}

const nextChapter = () => {
  if (totalChapters.value > 0 && chapterNum.value >= totalChapters.value) {
    ElMessage.info('已经是最后一章了')
    return
  }
  router.push(`/novel/${novelId}/chapter/${chapterNum.value + 1}`)
}

watch(() => route.params.chapterNum, (newVal) => {
  if (newVal) {
    chapterNum.value = parseInt(newVal as string)
    loadChapter()
  }
})

onMounted(() => {
  loadChapter()
  loadNovelInfo()
})
</script>

<style scoped>
.chapter-detail {
  max-width: 900px;
  margin: 0 auto;
}

.chapter-card {
  min-height: 500px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.chapter-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.chapter-info {
  margin-bottom: 20px;
}

.info-row {
  margin: 10px 0;
  color: #606266;
}

.info-label {
  font-weight: bold;
  color: #303133;
}

.chapter-content {
  line-height: 2;
  font-size: 16px;
  color: #303133;
}

.chapter-content p {
  text-indent: 2em;
  margin: 0 0 1em;
}
</style>
