import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  // 用 hash 路由：网关把服务挂在子路径下时，location.pathname 始终是挂载目录，
  // 路由跳转不会把子路径丢掉（history 模式会把 /<用户名>/novel/list 写成 /novel/list）
  history: createWebHashHistory(),
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
const VERIFY_CACHE_TTL = 60_000 // 60秒内不重复验证

router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('token')
  const isGuestRoute = to.meta.guest

  if (!token && !isGuestRoute) {
    next('/login')
    return
  }

  if (token && isGuestRoute) {
    next('/novels')
    return
  }

  if (token && !isGuestRoute) {
    try {
      const { useAuthStore } = await import('@/stores/authStore')
      const authStore = useAuthStore()

      // 60秒缓存内跳过验证
      const now = Date.now()
      if (now - authStore.lastVerifiedAt < VERIFY_CACHE_TTL) {
        next()
        return
      }

      await authStore.fetchUser()

      if (!authStore.isLoggedIn) {
        // fetchUser 中 401 已触发 logout，跳转登录页并携带 redirect
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }

      next()
    } catch (err: any) {
      if (err?.response?.status === 401 || err?.message?.includes('401')) {
        next({ path: '/login', query: { redirect: to.fullPath } })
      } else {
        const { ElMessage } = await import('element-plus')
        ElMessage.error('网络异常，请检查网络后重试')
        next(false) // abort navigation
      }
    }
    return
  }

  next()
})

export default router
