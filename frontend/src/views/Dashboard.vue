<template>
  <div class="dashboard-container">
    <el-card class="dashboard-card">
      <template #header>
        <div class="card-header">
          <span>小说创作仪表盘</span>
          <el-select v-model="selectedNovelId" placeholder="选择小说" @change="loadNovelData">
            <el-option 
              v-for="novel in novels" 
              :key="novel.novelId" 
              :label="novel.novelName" 
              :value="novel.novelId"
            />
          </el-select>
          <el-button type="danger" text @click="toggleTrash" style="margin-left: 8px;">
            {{ showTrash ? '返回列表' : '回收站' }}
          </el-button>
        </div>
      </template>
      
      <!-- 回收站 -->
      <div v-if="showTrash">
        <el-table :data="deletedNovels" v-loading="loadingTrash" style="width: 100%">
          <el-table-column prop="novelName" label="小说名称" />
          <el-table-column prop="genre" label="体裁" width="120" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleRestore(row.novelId)">恢复</el-button>
              <el-button type="danger" size="small" @click="handleHardDelete(row.novelId)">永久删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="loading" class="loading">
        <el-loading :fullscreen="false" text="加载中..." />
      </div>
      
      <div v-else class="dashboard-content">
        <!-- 统计卡片 -->
        <div class="stats-container">
          <el-card class="stat-card">
            <div class="stat-item">
              <div class="stat-value">{{ novelData?.chapterCount || 0 }}</div>
              <div class="stat-label">总章节数</div>
            </div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-item">
              <div class="stat-value">{{ novelData?.wordCount || 0 }}</div>
              <div class="stat-label">总字数</div>
            </div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-item">
              <div class="stat-value">{{ readPullTrend?.averageScore?.toFixed(1) || 0 }}</div>
              <div class="stat-label">平均追读力</div>
            </div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-item">
              <div class="stat-value">{{ characters?.length || 0 }}</div>
              <div class="stat-label">人物数量</div>
            </div>
          </el-card>
        </div>
        
        <!-- 图表区域 -->
        <div class="charts-container">
          <!-- 追读力趋势图 -->
          <el-card class="chart-card">
            <template #header>
              <span>追读力趋势</span>
            </template>
            <div ref="readPullChartRef" class="chart"></div>
          </el-card>
          
          <!-- 人物关系图谱 -->
          <el-card class="chart-card">
            <template #header>
              <span>人物关系图谱</span>
            </template>
            <div ref="characterGraphRef" class="chart"></div>
          </el-card>
        </div>
        
        <!-- 章节列表 -->
        <el-card class="chapter-list-card">
          <template #header>
            <span>最近章节</span>
          </template>
          <el-table :data="recentChapters" style="width: 100%">
            <el-table-column prop="chapterNum" label="章节号" width="80" />
            <el-table-column prop="chapterTitle" label="章节标题" />
            <el-table-column prop="wordCount" label="字数" width="100" />
            <el-table-column prop="readPullLevel" label="追读力" width="100" />
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button size="small" @click="viewChapter(scope.row)">查看</el-button>
                <el-button size="small" type="primary" @click="analyzeChapter(scope.row)">分析</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { novelApi, chapterApi, characterApi, rhythmApi, hookApi } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { NovelVO, ReadPullTrendVO, CharacterVO, ChapterListItem } from '@/types'
import * as echarts from 'echarts'

const router = useRouter()

// 响应式数据
const novels = ref<NovelVO[]>([])
const selectedNovelId = ref('')
const loading = ref(true)
const isUnmounted = ref(false)
const novelData = ref<NovelVO | null>(null)
const readPullTrend = ref<ReadPullTrendVO | null>(null)
const characters = ref<CharacterVO[]>([])
const recentChapters = ref<ChapterListItem[]>([])

// 回收站状态
const showTrash = ref(false)
const deletedNovels = ref<NovelVO[]>([])
const loadingTrash = ref(false)

// 图表引用
const readPullChartRef = ref<HTMLElement>()
const characterGraphRef = ref<HTMLElement>()
const readPullChart = shallowRef<echarts.ECharts | null>(null)
const characterGraphChart = shallowRef<echarts.ECharts | null>(null)

// 加载小说列表
const loadNovels = async () => {
  try {
    const res = await novelApi.getNovelList()
    if (res.code === 200) {
      novels.value = res.data
      if (novels.value.length > 0) {
        selectedNovelId.value = novels.value[0].novelId
        await loadNovelData()
      } else {
        loading.value = false
      }
    }
  } catch (error) {
    console.error('加载小说列表失败', error)
    loading.value = false
  }
}

// 回收站方法
const loadTrash = async () => {
  loadingTrash.value = true
  try {
    const res = await novelApi.getDeletedNovelList()
    deletedNovels.value = res.data || []
  } catch (e) {
    console.error('加载回收站失败:', e)
  } finally {
    loadingTrash.value = false
  }
}

const toggleTrash = () => {
  showTrash.value = !showTrash.value
  if (showTrash.value) {
    loadTrash()
  }
}

const handleRestore = async (novelId: string) => {
  try {
    await novelApi.restoreNovel(novelId)
    ElMessage.success('恢复成功')
    loadTrash()
    loadNovels()
  } catch {
    ElMessage.error('恢复失败')
  }
}

const handleHardDelete = async (novelId: string) => {
  try {
    await ElMessageBox.confirm('此操作将永久删除该小说，是否继续？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await novelApi.hardDeleteNovel(novelId)
    ElMessage.success('已永久删除')
    loadTrash()
  } catch {
    // 用户取消或删除失败
  }
}

// 加载小说数据
const loadNovelData = async () => {
  if (!selectedNovelId.value || isUnmounted.value) return
  
  loading.value = true
  
  try {
    // 并行加载数据（容错：单个请求失败不影响其余数据）
    const results = await Promise.allSettled([
      novelApi.getNovel(selectedNovelId.value),
      rhythmApi.getReadPullTrend(selectedNovelId.value),
      chapterApi.getRecentChapters(selectedNovelId.value, 10),
      characterApi.getCharacterList(selectedNovelId.value),
      novelApi.getNovelGraph(selectedNovelId.value)
    ])

    if (isUnmounted.value) return

    const [novelRes, trendRes, chaptersRes, charactersRes, graphRes] = results

    if (novelRes.status === 'fulfilled' && novelRes.value.code === 200) {
      novelData.value = novelRes.value.data
    }

    if (trendRes.status === 'fulfilled' && trendRes.value.code === 200) {
      readPullTrend.value = trendRes.value.data
      nextTick(() => {
        if (!isUnmounted.value) {
          renderReadPullChart()
        }
      })
    }

    if (chaptersRes.status === 'fulfilled' && chaptersRes.value.code === 200) {
      recentChapters.value = chaptersRes.value.data
    }

    if (charactersRes.status === 'fulfilled' && charactersRes.value.code === 200) {
      characters.value = charactersRes.value.data
      const graphData = graphRes.status === 'fulfilled' && graphRes.value.code === 200
        ? graphRes.value.data : null
      nextTick(() => {
        if (!isUnmounted.value) {
          renderCharacterGraph(graphData)
        }
      })
    }
  } catch (error) {
    console.error('加载小说数据失败', error)
    ElMessage.error('加载仪表盘数据失败')
  } finally {
    loading.value = false
  }
}

// 渲染追读力趋势图
const renderReadPullChart = () => {
  if (!readPullChartRef.value || !readPullTrend.value) return
  
  if (readPullChart.value) {
    readPullChart.value.dispose()
  }

  readPullChart.value = echarts.init(readPullChartRef.value)
  
  const option = {
    title: {
      text: '追读力趋势',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: readPullTrend.value.chapterNumbers || readPullTrend.value.scores?.map((_, i) => i + 1) || []
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 40
    },
    series: [{
      data: readPullTrend.value.readPullScores || readPullTrend.value.scores || [],
      type: 'line',
      smooth: true,
      itemStyle: {
        color: '#409EFF'
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
          { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
        ])
      }
    }]
  }
  
  readPullChart.value?.setOption(option)
}

// 渲染人物关系图谱（使用真实数据）
const renderCharacterGraph = (graphData: any) => {
  if (!characterGraphRef.value || !characters.value || characters.value.length === 0) return
  
  if (characterGraphChart.value) {
    characterGraphChart.value.dispose()
  }

  characterGraphChart.value = echarts.init(characterGraphRef.value)
  
  // 构建图谱数据
  const nodes = characters.value.map((char, index) => ({
    id: char.characterId,
    name: char.name,
    symbolSize: 30,
    itemStyle: {
      color: `hsl(${index * 60 % 360}, 70%, 60%)`
    }
  }))
  
  // 使用API返回的真实关系数据，若无则为空
  const links = (graphData?.edges || []).map((edge: any) => ({
    source: edge.source,
    target: edge.target,
    lineStyle: {
      width: 1,
      color: '#999'
    }
  }))
  
  const option = {
    title: {
      text: '人物关系图谱',
      left: 'center'
    },
    tooltip: {
      trigger: 'item'
    },
    animationDurationUpdate: 1500,
    animationEasingUpdate: 'quinticInOut',
    series: [{
      type: 'graph',
      layout: 'force',
      data: nodes,
      links: links,
      roam: true,
      label: {
        show: true,
        position: 'right',
        formatter: '{b}'
      },
      force: {
        repulsion: 1000,
        edgeLength: [80, 150]
      }
    }]
  }
  
  characterGraphChart.value?.setOption(option)
}

// 查看章节
const viewChapter = (chapter: any) => {
  if (selectedNovelId.value && chapter.chapterNum) {
    router.push(`/novel/${selectedNovelId.value}/chapter/${chapter.chapterNum}`)
  }
}

// 分析章节
const analyzeChapter = async (chapter: any) => {
  if (!selectedNovelId.value || !chapter.chapterNum) return
  try {
    const res = await hookApi.analyzeHook(selectedNovelId.value, chapter.chapterNum)
    if (res.code === 200) {
      ElMessage.success('钩子分析完成，请查看章节详情')
    } else {
      ElMessage.error(res.message || '分析失败')
    }
  } catch (error) {
    ElMessage.error('钩子分析请求失败')
  }
}

// 监听窗口大小变化
const handleResize = () => {
  readPullChart.value?.resize()
  characterGraphChart.value?.resize()
}

// 生命周期
onMounted(() => {
  loadNovels()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  isUnmounted.value = true
  window.removeEventListener('resize', handleResize)
  readPullChart.value?.dispose()
  characterGraphChart.value?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}

.dashboard-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.loading {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stats-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-item {
  padding: 20px 0;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 10px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}

.charts-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.chart-card {
  min-height: 400px;
}

.chart {
  width: 100%;
  height: 350px;
}

.chapter-list-card {
  margin-top: 20px;
}

@media (max-width: 768px) {
  .charts-container {
    grid-template-columns: 1fr;
  }
  
  .stats-container {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>