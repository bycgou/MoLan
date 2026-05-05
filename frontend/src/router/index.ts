import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { guest: true },
    },
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/Dashboard.vue'),
    },
    {
      path: '/novels',
      name: 'Novels',
      component: () => import('@/views/Novels.vue'),
    },
    {
      path: '/create',
      name: 'Create',
      component: () => import('@/views/CreateNovel.vue'),
    },
    {
      path: '/create-wizard',
      name: 'CreateWizard',
      component: () => import('@/views/CreateNovelWizard.vue'),
    },
    {
      path: '/novel/:novelId',
      name: 'NovelDetail',
      component: () => import('@/views/NovelDetail.vue'),
    },
    {
      path: '/novel/:novelId/edit',
      name: 'NovelEdit',
      component: () => import('@/views/NovelEdit.vue'),
    },
    {
      path: '/novel/:novelId/chapter/:chapterNum',
      name: 'ChapterDetail',
      component: () => import('@/views/ChapterDetail.vue'),
    },
    {
      path: '/novel/:novelId/chapter/:chapterNum/edit',
      name: 'ChapterEdit',
      component: () => import('@/views/ChapterEdit.vue'),
    },
    {
      path: '/novel/:novelId/characters',
      name: 'Characters',
      component: () => import('@/views/Characters.vue'),
    },
    {
      path: '/novel/:novelId/clues',
      name: 'Clues',
      component: () => import('@/views/Clues.vue'),
    },
    {
      path: '/llm-config',
      name: 'LlmConfig',
      component: () => import('@/views/LlmConfig.vue'),
    },
    {
      path: '/style-cards',
      name: 'StyleCards',
      component: () => import('@/views/style/StyleCardLibrary.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/NotFound.vue'),
    },
  ],
})

// 路由守卫
let isVerifying = false

router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('token')
  const isGuestRoute = to.meta.guest

  if (!token && !isGuestRoute) {
    // 未登录 → 跳转 /login
    next('/login')
  } else if (token && isGuestRoute) {
    // 已登录访问登录页 → 跳转 /novels
    next('/novels')
  } else if (token && !isGuestRoute && !isVerifying) {
    // 有Token但需验证有效性
    isVerifying = true
    try {
      const { useAuthStore } = await import('@/stores/authStore')
      const authStore = useAuthStore()
      await authStore.fetchUser()
      // fetchUser失败会logout并抛异常，走到catch
      isVerifying = false
      next()
    } catch {
      isVerifying = false
      localStorage.removeItem('token')
      next('/login')
    }
  } else {
    next()
  }
})

export default router
