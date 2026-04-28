import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: {
        title: '登录'
      }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue'),
      meta: {
        title: '注册'
      }
    },
    {
      path: '/share/:token?',
      name: 'share',
      component: () => import('@/views/Share.vue'),
      meta: {
        title: '分享访问'
      }
    },
    {
      path: '/',
      component: () => import('@/components/Layout.vue'),
      meta: {
        requiresAuth: true
      },
      children: [
        {
          path: '',
          redirect: '/dashboard'
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: {
            requiresAuth: true,
            title: '仪表盘'
          }
        },
        {
          path: 'folder',
          name: 'folder',
          component: () => import('@/views/Folder.vue'),
          meta: {
            requiresAuth: true,
            title: '文件与笔记'
          }
        },
        {
          path: 'templates',
          name: 'templates',
          component: () => import('@/views/Templates.vue'),
          meta: {
            requiresAuth: true,
            title: '模板中心'
          }
        },
        {
          path: 'attachments',
          name: 'attachments',
          component: () => import('@/views/Attachments.vue'),
          meta: {
            requiresAuth: true,
            title: '附件中心'
          }
        },
        {
          path: 'import',
          name: 'import',
          component: () => import('@/views/Import.vue'),
          meta: {
            requiresAuth: true,
            title: '导入中心'
          }
        },
        {
          path: 'note/new',
          name: 'note-new',
          component: () => import('@/views/NoteEdit.vue'),
          meta: {
            requiresAuth: true,
            title: '新建笔记'
          }
        },
        {
          path: 'note/:id/edit',
          name: 'note-edit',
          component: () => import('@/views/NoteEdit.vue'),
          meta: {
            requiresAuth: true,
            title: '编辑笔记'
          }
        },
        {
          path: 'graph',
          name: 'graph',
          component: () => import('@/views/Graph.vue'),
          meta: {
            requiresAuth: true,
            title: '知识图谱'
          }
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/Search.vue'),
          meta: {
            requiresAuth: true,
            title: '全文搜索'
          }
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/Profile.vue'),
          meta: {
            requiresAuth: true,
            title: '个人中心'
          }
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)

  if (!authStore.bootstrapped) {
    await authStore.bootstrap()
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if ((to.path === '/login' || to.path === '/register') && authStore.isAuthenticated) {
    return '/dashboard'
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '知识工作台'
  document.title = `知脉 | ${title}`
})

export default router
