import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/books',
    children: [
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    next('/login')
  } else if (to.path === '/login' && userStore.token) {
    next('/')
  } else {
    next()
  }
})

export default router
