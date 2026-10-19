<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">图书借阅管理</div>
      <el-menu :default-active="activeMenu" class="menu" @select="onMenuSelect">
        <!-- 图书借阅（按权限显示） -->
        <el-menu-item v-if="userStore.hasPerm('book:view')" index="/books">
          <el-icon><Notebook /></el-icon><span>图书信息管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('category:view')" index="/categories">
          <el-icon><Folder /></el-icon><span>图书分类管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('borrow:view')" index="/borrow-records">
          <el-icon><List /></el-icon><span>借阅记录管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('purchase:view')" index="/purchases">
          <el-icon><ShoppingCart /></el-icon><span>图书采购管理</span>
        </el-menu-item>

        <!-- 待办与消息：业务功能，留在门户（维护类功能已迁 /backend 后台） -->
        <el-menu-item v-if="userStore.hasAny(['group:todo', 'todo:own'])" index="/admin/todos">
          <el-icon><Bell /></el-icon>
          <span>待办中心</span>
          <el-badge v-if="todoCount > 0" :value="todoCount" class="menu-badge" />
        </el-menu-item>
        <el-menu-item v-if="userStore.hasAny(['group:message', 'message:own'])" index="/admin/messages">
          <el-icon><ChatDotRound /></el-icon>
          <span>消息中心</span>
          <el-badge v-if="msgCount > 0" :value="msgCount" class="menu-badge" />
        </el-menu-item>

        <!-- 集成应用动态栏目：集团管理员配置后自动出现；需「应用集成配置」权限方可领票进入 -->
        <el-sub-menu v-if="showApps && ssoApps.length > 0" index="apps">
          <template #title>
            <el-icon><Link /></el-icon><span>集成应用</span>
          </template>
          <el-menu-item
            v-for="app in ssoApps"
            :key="app.id"
            :index="'app-' + app.id"
            @click="openApp(app)"
          >
            <el-icon><Grid /></el-icon><span>{{ app.appName }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ currentTitle }}</span>
        <div class="header-right">
          <el-dropdown v-if="userStore.roles.length > 1" @command="onSwitchRole">
            <span class="role-switch">
              <el-icon><Switch /></el-icon>
              {{ userStore.activeRole?.roleName || userStore.userInfo?.roleName || '切换身份' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="r in userStore.roles"
                  :key="r.roleId"
                  :command="r.roleId"
                  :disabled="r.roleId === userStore.activeRoleId"
                >
                  {{ r.roleName }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-tag v-else-if="userStore.activeRole?.roleName || userStore.userInfo?.roleName" size="small" type="info" class="role-tag">
            {{ userStore.activeRole?.roleName || userStore.userInfo?.roleName }}
          </el-tag>
          <el-button v-if="showBackend" text type="primary" :icon="Setting" @click="router.push('/backend')">
            后台管理
          </el-button>
          <span class="header-user">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.userInfo?.nickname || userStore.userInfo?.username || '用户' }}
          </span>
          <el-button type="danger" text :icon="SwitchButton" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Notebook, Folder, List, UserFilled, SwitchButton, User,
  Bell, ChatDotRound, Link, Grid, Setting, ShoppingCart, Switch, ArrowDown
} from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import { getUserInfo } from '../api/auth'
import { listSsoApps, getSsoTicket } from '../api/sso'
import { countPendingTodos } from '../api/todo'
import { countUnreadMessages } from '../api/message'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const ssoApps = ref([])
const todoCount = ref(0)
const msgCount = ref(0)

const showBackend = computed(() =>
  userStore.hasAny(['group:user', 'group:role', 'group:app', 'system:log', 'system:monitor', 'system:cache'])
)

const showApps = computed(() => userStore.hasAny(['group:app']))

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/books/detail')) return '/books'
  return path
})

const currentTitle = computed(() => route.meta.title || '图书借阅管理')

/**
 * 菜单选中处理：路由类菜单（index 以 / 开头）执行跳转；
 * 集成应用菜单（index 形如 app-1）改用新窗口打开，不做本页跳转。
 */
function onMenuSelect(index) {
  if (typeof index === 'string' && index.startsWith('/')) {
    router.push(index)
  }
}

/** 单点登录跳转到类ERP平台（独立端口运行的第三方系统） */
async function openApp(app) {
  try {
    const ticket = await getSsoTicket(app.id)
    const base = app.appUrl || 'http://localhost:5174/'
    const url = `${base}${base.includes('?') ? '&' : '?'}appId=${app.id}&ticket=${encodeURIComponent(ticket)}`
    window.open(url, '_blank')
  } catch {
    /* 错误提示已由请求拦截器统一处理 */
  }
}

async function loadBadges() {
  // 待办/消息共用同一批接口，数据范围恒为本人（徽标即本人的未办/未读数）
  if (userStore.hasAny(['group:todo', 'todo:own'])) {
    todoCount.value = Number(await countPendingTodos()) || 0
  }
  if (userStore.hasAny(['group:message', 'message:own'])) {
    msgCount.value = Number(await countUnreadMessages()) || 0
  }
}

/**
 * 顶栏身份切换：换 X-Active-Role 后重取权限，当前页无权限则回门户首页。
 *
 * @param {number} roleId 目标身份角色ID
 */
async function onSwitchRole(roleId) {
  if (roleId === userStore.activeRoleId) return
  userStore.setActiveRoleId(roleId)
  try {
    const data = await getUserInfo()
    userStore.setPermissions(data.permissions)
    if (data.activeRoleId) userStore.setActiveRoleId(data.activeRoleId)
    ElMessage.success('已切换身份：' + (userStore.activeRole?.roleName || ''))
    // 与路由守卫保持一致：优先按 meta.perms（命中其一）判断，未声明时回退单值 meta.perm
    const perms = route.meta.perms
    const denied = Array.isArray(perms) && perms.length
      ? !userStore.hasAny(perms)
      : route.meta.perm && !userStore.hasPerm(route.meta.perm)
    if (denied) {
      router.push('/books')
    }
  } catch {
    /* 错误提示已由请求拦截器统一处理 */
  }
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  } catch {
    return
  }
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(async () => {
  try {
    ssoApps.value = await listSsoApps()
  } catch {
    ssoApps.value = []
  }
  await loadBadges()
})
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #001529;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
}
.menu {
  border-right: none;
  background: #001529;
}
.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  color: rgba(255, 255, 255, 0.75);
}
.menu :deep(.el-sub-menu .el-menu) {
  background: #000c17;
}
.menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #1677ff;
}
.menu-badge {
  margin-left: 8px;
}
.header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-user {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #606266;
}
.role-tag {
  margin-right: 4px;
}
.role-switch {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #409eff;
  cursor: pointer;
}
.main {
  background: #f0f2f5;
  padding: 16px;
}
</style>
