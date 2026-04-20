<template>
  <!-- 登录页不显示 Layout -->
  <router-view v-if="isGuestRoute" />

  <div v-else class="layout">
    <!-- 左侧导航 -->
    <div class="sidebar">
      <div class="logo">
        <h2>AI小说系统</h2>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        router
        background-color="#001529"
        text-color="#fff"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/novels">
          <el-icon><Document /></el-icon>
          <span>我的小说</span>
        </el-menu-item>
        <el-menu-item index="/create">
          <el-icon><Edit /></el-icon>
          <span>创建小说</span>
        </el-menu-item>
        <el-menu-item index="/llm-config">
          <el-icon><Setting /></el-icon>
          <span>模型配置</span>
        </el-menu-item>
      </el-menu>

      <!-- 底部用户信息 -->
      <div class="sidebar-footer">
        <div class="user-info" v-if="authStore.isLoggedIn">
          <el-icon><User /></el-icon>
          <span>{{ authStore.username }}</span>
        </div>
        <el-button text style="color: #fff; width: 100%;" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </el-button>
      </div>
    </div>

    <!-- 右侧内容 -->
    <div class="main-content">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled, DataAnalysis, Document, Edit, Setting, User, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)
const isGuestRoute = computed(() => route.meta.guest || route.path === '/login')

onMounted(() => {
  if (authStore.isLoggedIn) {
    authStore.fetchUser()
  }
})

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 200px;
  background-color: #001529;
  color: white;
  display: flex;
  flex-direction: column;
}

.logo {
  padding: 20px;
  border-bottom: 1px solid #002140;
}

.logo h2 {
  margin: 0;
  font-size: 18px;
  color: white;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #002140;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #ccc;
}

.main-content {
  flex: 1;
  padding: 20px;
  background-color: #f5f7fa;
}
</style>
