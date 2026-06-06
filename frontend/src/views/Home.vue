<template>
  <div class="home">
    <el-card class="welcome-card">
      <template #header>
        <div class="card-header">
          <span>欢迎使用AI小说创作系统</span>
        </div>
      </template>
      <div class="welcome-content">
        <h2>百万字AI长篇小说创作系统</h2>
        <p>基于多Agent协作的智能小说创作平台，支持大纲生成、正文创作、多重校验等功能。</p>
        <div class="features">
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="feature-item">
                <el-icon :size="40" color="#409EFF"><Edit /></el-icon>
                <h3>大纲生成</h3>
                <p>AI自动生成章纲和实现思路</p>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="feature-item">
                <el-icon :size="40" color="#67C23A"><Document /></el-icon>
                <h3>正文创作</h3>
                <p>基于上下文的连贯正文生成</p>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="feature-item">
                <el-icon :size="40" color="#E6A23C"><Check /></el-icon>
                <h3>多重校验</h3>
                <p>OOC检测、逻辑校验、伏笔追踪</p>
              </div>
            </el-col>
          </el-row>
        </div>
        <div class="actions">
          <el-button type="primary" size="large" @click="$router.push('/create')">
            开始创作
          </el-button>
          <el-button size="large" @click="$router.push('/novels')">
            我的小说
          </el-button>
        </div>
      </div>
    </el-card>
    
    <el-card class="stats-card" v-if="novelCount > 0">
      <template #header>
        <div class="card-header">
          <span>创作统计</span>
        </div>
      </template>
      <el-row :gutter="20" v-loading="loadingStats">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ novelCount }}</div>
            <div class="stat-label">小说数量</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ totalChapters }}</div>
            <div class="stat-label">总章节</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ formatWordCount(totalWords) }}</div>
            <div class="stat-label">总字数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ activeNovels }}</div>
            <div class="stat-label">连载中</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Edit, Document, Check } from '@element-plus/icons-vue'
import { novelApi } from '@/api'
import { ElMessage } from 'element-plus'

const novelCount = ref(0)
const totalChapters = ref(0)
const totalWords = ref(0)
const activeNovels = ref(0)
const loadingStats = ref(false)

const formatWordCount = (count: number) => {
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + '万'
  }
  return count.toString()
}

const loadStats = async () => {
  loadingStats.value = true
  try {
    const response = await novelApi.getNovelList()
    if (response.code === 200) {
      const novels = response.data
      novelCount.value = novels.length
      totalChapters.value = novels.reduce((sum: number, n: any) => sum + (n.chapterCount || 0), 0)
      totalWords.value = novels.reduce((sum: number, n: any) => sum + (n.wordCount || 0), 0)
      activeNovels.value = novels.filter((n: any) => n.status === 1).length
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
    ElMessage.error('加载统计数据失败')
  } finally {
    loadingStats.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
}

.welcome-card {
  margin-bottom: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: bold;
}

.welcome-content {
  text-align: center;
  padding: 20px;
}

.welcome-content h2 {
  margin-bottom: 10px;
  color: #303133;
}

.welcome-content p {
  color: #606266;
  margin-bottom: 30px;
}

.features {
  margin: 40px 0;
}

.feature-item {
  text-align: center;
  padding: 20px;
}

.feature-item h3 {
  margin: 15px 0 10px;
  color: #303133;
}

.feature-item p {
  color: #909399;
  font-size: 14px;
}

.actions {
  margin-top: 30px;
}

.actions .el-button {
  margin: 0 10px;
}

.stats-card {
  margin-top: 20px;
}

.stat-item {
  text-align: center;
  padding: 20px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}
</style>
