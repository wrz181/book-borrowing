<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">系统后台管理</div>
      <el-menu :default-active="activeMenu" class="menu" router>
        <el-menu-item v-if="userStore.hasPerm('group:user')" index="/backend/users">
          <el-icon><User /></el-icon><span>账号管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('group:role')" index="/backend/roles">
          <el-icon><Key /></el-icon><span>角色权限配置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('group:app')" index="/backend/apps">
          <el-icon><Connection /></el-icon><span>应用集成配置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('system:log')" index="/backend/system/logs">
          <el-icon><Document /></el-icon><span>日志类型设置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('system:monitor')" index="/backend/system/memory">
          <el-icon><Odometer /></el-icon><span>内存监控</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('system:cache')" index="/backend/system/cache">
          <el-icon><Delete /></el-icon><span>JS缓存清理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ route.meta.title || '系统后台' }}</span>
        <div class="header-right">
          <el-dropdown v-if="userStore.roles.length > 1" @command="onSwitchRole">
            <span class="role-switch">
              <el-icon><Switch /></el-icon>
              {{ userStore.activeRole?.roleName || '切换身份' }}
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
          <el-tag v-else-if="userStore.activeRole?.roleName" size="small" type="info" class="role-tag">
            {{ userStore.activeRole.roleName }}
          </el-tag>
          <el-button text type="primary" :icon="Back" @click="router.push('/')">返回门户</el-button>
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
import {
  User, Key, Connection, Document, Odometer, Delete,
  Switch, ArrowDown, Back, SwitchButton
} from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'
import { getUserInfo } from '../../api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

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
    if (route.meta.perm && !userStore.hasPerm(route.meta.perm)) {
      router.push('/')
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
.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.75);
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
.role-switch {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #409eff;
  cursor: pointer;
}
.role-tag {
  margin-right: 4px;
}
.main {
  background: #f0f2f5;
  padding: 16px;
}
</style>
