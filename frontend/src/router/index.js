import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/Dashboard.vue'),
        meta: { title: '首页看板' }
      },
      {
        path: 'books',
        name: 'BookList',
        component: () => import('../views/book/BookList.vue'),
        meta: { title: '图书信息管理' }
      },
      {
        path: 'books/detail/:id',
        name: 'BookDetail',
        component: () => import('../views/book/BookDetail.vue'),
        meta: { title: '图书详情与借阅记录' }
      },
      {
        path: 'categories',
        name: 'CategoryList',
        component: () => import('../views/category/CategoryList.vue'),
        meta: { title: '图书分类管理' }
      },
      {
        path: 'borrow-records',
        name: 'BorrowRecordList',
        component: () => import('../views/borrow/BorrowRecordList.vue'),
        meta: { title: '借阅记录管理' }
      },
      {
        path: 'hdfs-archive',
        name: 'HdfsArchive',
        component: () => import('../views/hdfs/HdfsArchive.vue'),
        meta: { title: 'HDFS 数据归档', adminOnly: true }
      }
    ]
  },
  // 手输错误地址：统一回首页，避免白屏
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫：未登录跳转登录页；adminOnly 页面学生重定向回首页
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!userStore.token) {
    next('/login')
    return
  }
  if (to.meta.adminOnly && !userStore.isAdmin) {
    next('/dashboard')
    return
  }
  next()
})

export default router
