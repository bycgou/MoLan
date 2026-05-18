<template>
  <div class="novels-page">
    <div class="page-header">
      <h2>我的小说</h2>
      <el-button type="primary" @click="$router.push('/create')">
        <el-icon><Plus /></el-icon>
        创建小说
      </el-button>
    </div>
    
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="8" v-for="novel in novels" :key="novel.novelId">
        <el-card class="novel-card" shadow="hover">
          <div class="novel-header">
            <h3 class="novel-title">{{ novel.novelName }}</h3>
            <div class="novel-tags">
              <el-tag v-if="novel.creationMode === 'wizard'" type="warning" size="small" effect="dark">
                向导模式
              </el-tag>
              <el-tag :type="getStatusType(novel.status)">
                {{ getStatusText(novel.status) }}
              </el-tag>
            </div>
          </div>
          <div class="novel-info">
            <p><strong>类型：</strong>{{ novel.genre || '未设置' }}</p>
            <template v-if="novel.creationMode === 'wizard'">
              <p><strong>章节：</strong>{{ novel.chapterCount || 0 }} 章</p>
              <div v-if="novel.volumes && novel.volumes.length > 0" class="volume-list">
                <p class="volume-title"><strong>卷结构：</strong></p>
                <div v-for="vol in novel.volumes" :key="vol.arcId" class="volume-item">
                  <span class="volume-name">{{ vol.arcName }}</span>
                  <span class="volume-range">（第{{ vol.startChapter }}-{{ vol.endChapter }}章）</span>
                  <p v-if="vol.arcOutline" class="volume-outline">{{ vol.arcOutline }}</p>
                </div>
              </div>
            </template>
            <template v-else>
              <p><strong>章节：</strong>{{ novel.chapterCount || 0 }} 章</p>
            </template>
            <p><strong>字数：</strong>{{ formatWordCount(novel.wordCount || 0) }}</p>
            <p><strong>更新：</strong>{{ formatDate(novel.updateTime) }}</p>
          </div>
          <div class="novel-actions">
            <el-button type="primary" size="small" @click="viewNovel(novel.novelId)">
              查看详情
            </el-button>
            <el-button size="small" @click="editNovel(novel.novelId)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="deleteNovel(novel.novelId)">
              删除
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-empty v-if="!loading && novels.length === 0" description="暂无小说，点击创建开始创作吧！">
      <el-button type="primary" @click="$router.push('/create')">创建小说</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { novelApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { NovelVO } from '@/types'

const router = useRouter()
const loading = ref(false)
const novels = ref<NovelVO[]>([])

const getStatusType = (status: number) => {
  const typeMap: Record<number, string> = {
    0: 'info',    // 草稿
    1: 'success', // 连载中
    2: 'warning'  // 已完结
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status: number) => {
  const textMap: Record<number, string> = {
    0: '草稿',
    1: '连载中',
    2: '已完结'
  }
  return textMap[status] || '未知'
}

const formatWordCount = (count: number) => {
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + '万字'
  }
  return count + '字'
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

const loadNovels = async () => {
  loading.value = true
  try {
    const response = await novelApi.getNovelList()
    if (response.code === 200) {
      novels.value = response.data
    }
  } catch (error) {
    console.error('加载小说列表失败:', error)
    ElMessage.error('加载小说列表失败')
  } finally {
    loading.value = false
  }
}

const viewNovel = (novelId: string) => {
  router.push(`/novel/${novelId}`)
}

const editNovel = (novelId: string) => {
  router.push(`/novel/${novelId}/edit`)
}

const deleteNovel = async (novelId: string) => {
  try {
    await ElMessageBox.confirm('确定要删除这部小说吗？删除后可在回收站恢复。', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await novelApi.deleteNovel(novelId)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      loadNovels()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除小说失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadNovels()
})
</script>

<style scoped>
.novels-page {
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

.novel-card {
  margin-bottom: 20px;
}

.novel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.novel-tags {
  display: flex;
  gap: 6px;
  align-items: center;
}

.novel-title {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.novel-info {
  margin-bottom: 15px;
}

.novel-info p {
  margin: 8px 0;
  color: #606266;
  font-size: 14px;
}

.novel-actions {
  display: flex;
  gap: 10px;
}

.volume-list {
  margin-top: 4px;
}

.volume-title {
  margin: 8px 0 4px;
}

.volume-item {
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
}

.volume-item:last-child {
  border-bottom: none;
}

.volume-name {
  font-weight: 500;
  color: #303133;
  font-size: 13px;
}

.volume-range {
  font-size: 12px;
  color: #909399;
}

.volume-outline {
  margin: 2px 0 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
