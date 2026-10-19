import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import Layout from '../views/Layout.vue'
import BackendLayout from '../views/backend/BackendLayout.vue'

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
    redirect: '/books',
    children: [
      {
        path: 'books',
        name: 'BookList',
        component: () => import('../views/book/BookList.vue'),
        meta: { title: '图书信息管理', perm: 'book:view' }
      },
      {
        path: 'books/detail/:id',
        name: 'BookDetail',
        component: () => import('../views/book/BookDetail.vue'),
        meta: { title: '图书详情与借阅记录', perm: 'book:view' }
      },
      {
        path: 'categories',
        name: 'CategoryList',
        component: () => import('../views/category/CategoryList.vue'),
        meta: { title: '图书分类管理', perm: 'category:view' }
      },
      {
        path: 'borrow-records',
        name: 'BorrowRecordList',
        component: () => import('../views/borrow/BorrowRecordList.vue'),
        meta: { title: '借阅记录管理', perm: 'borrow:view' }
      },
      {
        path: 'purchases',
        name: 'PurchaseList',
        component: () => import('../views/purchase/PurchaseList.vue'),
        meta: { title: '图书采购管理', perm: 'purchase:view' }
      },
      {
        path: 'admin/todos',
        name: 'GroupTodos',
        component: () => import('../views/admin/group/Todos.vue'),
        // perms：命中其一即可进入（group:todo 管理视图 / todo:own 我的待办）；perm 保留原有管理视图标注
        meta: { title: '待办中心', perm: 'group:todo', perms: ['group:todo', 'todo:own'] }
      },
      {
        path: 'admin/messages',
        name: 'GroupMessages',
        component: () => import('../views/admin/group/Messages.vue'),
        meta: { title: '消息中心', perm: 'group:message', perms: ['group:message', 'message:own'] }
      }
    ]
  },
  {
    // 独立后台路由组（三员/管理员维护功能，独立布局），业务功能留在门户
    path: '/backend',
    component: BackendLayout,
    redirect: '/backend/users',
    meta: { title: '系统后台' },
    children: [
      {
        path: 'users',
        name: 'BackendUsers',
        component: () => import('../views/admin/group/Users.vue'),
        meta: { title: '后台 - 账号管理', perm: 'group:user' }
      },
      {
        path: 'roles',
        name: 'BackendRoles',
        component: () => import('../views/admin/group/Roles.vue'),
        meta: { title: '后台 - 角色权限配置', perm: 'group:role' }
      },
      {
        path: 'apps',
        name: 'BackendApps',
        component: () => import('../views/admin/group/Apps.vue'),
        meta: { title: '后台 - 应用集成配置', perm: 'group:app' }
      },
      {
        path: 'biz',
        name: 'BackendBiz',
        component: () => import('../views/backend/IntegrationBiz.vue'),
        meta: { title: '集成业务配置', perm: 'group:app' }
      },
      {
        path: 'system/logs',
        name: 'BackendSystemLogs',
        component: () => import('../views/admin/system/LogSettings.vue'),
        meta: { title: '后台 - 日志类型设置', perm: 'system:log' }
      },
      {
        path: 'system/memory',
        name: 'BackendSystemMemory',
        component: () => import('../views/admin/system/MemoryMonitor.vue'),
        meta: { title: '后台 - 内存监控', perm: 'system:monitor' }
      },
      {
        path: 'system/cache',
        name: 'BackendSystemCache',
        component: () => import('../views/admin/system/CacheClean.vue'),
        meta: { title: '后台 - JS缓存清理', perm: 'system:cache' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫：未登录跳转登录页；无权限提示并回到首页
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  // 公开页面（仅登录页）直接放行
  if (to.meta.public) {
    if (to.path === '/login' && userStore.token) {
      next('/')
      return
    }
    next()
    return
  }
  if (!userStore.token) {
    next('/login')
    return
  }
  // meta.perms：命中其一即可（如 group:todo 或 todo:own，与后端 @RequirePermission(anyOf) 语义一致）；
  // 未声明 perms 的路由回退到原有的单值 meta.perm 判断，其余路由行为不变
  const perms = to.meta.perms
  const allowed = Array.isArray(perms) && perms.length
    ? userStore.hasAny(perms)
    : !to.meta.perm || userStore.permissions.includes(to.meta.perm)
  if (!allowed) {
    ElMessage.error('无访问权限')
    next('/books')
    return
  }
  next()
})

export default router
