<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">图书借阅管理</div>
      <el-menu :default-active="activeMenu" class="menu" @select="onMenuSelect">
        <el-menu-item index="/books">
          <el-icon><Notebook /></el-icon><span>图书信息管理</span>
        </el-menu-item>
        <el-menu-item index="/categories">
          <el-icon><Folder /></el-icon><span>图书分类管理</span>
        </el-menu-item>
        <el-menu-item index="/borrow-records">
          <el-icon><List /></el-icon>
          <span>{{ userStore.isAdmin ? '借阅记录管理' : '我的借阅记录' }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ currentTitle }}</span>
        <div class="header-right">
          <el-tag size="small" :type="userStore.isAdmin ? 'danger' : 'info'" class="role-tag">
            {{ userStore.isAdmin ? '管理员' : '学生' }}
          </el-tag>
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Notebook, Folder, List, UserFilled, SwitchButton } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/books/detail')) return '/books'
  return path
})

const currentTitle = computed(() => route.meta.title || '图书借阅管理')

/**
 * 菜单选中处理：路由类菜单（index 以 / 开头）执行跳转。
 */
function onMenuSelect(index) {
  if (typeof index === 'string' && index.startsWith('/')) {
    router.push(index)
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
.main {
  background: #f0f2f5;
  padding: 16px;
}
</style>
