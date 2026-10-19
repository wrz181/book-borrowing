<template>
  <div class="role-page">
    <el-card class="role-list" shadow="never">
      <template #header>
        <span>角色列表</span>
      </template>
      <el-menu :default-active="String(selectedRoleId)" class="role-menu">
        <el-menu-item
          v-for="role in roles"
          :key="role.id"
          :index="String(role.id)"
          @click="selectRole(role)"
        >
          <span>{{ role.roleName }}</span>
          <el-tag v-if="role.isBuiltin === 1" size="small" type="info" class="builtin-tag">内置</el-tag>
        </el-menu-item>
      </el-menu>
    </el-card>

    <el-card class="perm-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <span>
            权限配置
            <el-tag v-if="currentRole" size="small" class="role-tag">{{ currentRole.roleName }}</el-tag>
          </span>
          <el-button type="primary" :icon="Check" :disabled="!canEdit" :loading="saving" @click="onSave">保存</el-button>
        </div>
      </template>

      <el-alert
        v-if="currentRole && !canEdit"
        title="内置管理员角色（系统管理员 / 集团管理员）的权限已锁定，不可修改。"
        type="warning"
        :closable="false"
        show-icon
        class="tip"
      />

      <el-alert
        v-if="currentRole && currentRole.roleCode === 'NORMAL'"
        title="普通人员默认仅可查看数据与发起借阅表单；可按需勾选权限点。"
        type="info"
        :closable="false"
        show-icon
        class="tip"
      />

      <div v-if="currentRole" class="perm-groups">
        <div v-for="(perms, module) in groupedPermissions" :key="module" class="perm-group">
          <div class="group-title">{{ moduleLabel(module) }}</div>
          <el-checkbox-group v-model="checked" :disabled="!canEdit">
            <el-checkbox v-for="p in perms" :key="p.permCode" :value="p.permCode">
              {{ p.permName }}
              <span class="perm-code">{{ p.permCode }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
      <el-empty v-else description="请选择左侧角色" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listRoles, listPermissions, updateRolePermissions } from '../../../api/admin'

const roles = ref([])
const permissions = ref([])
const selectedRoleId = ref(null)
const currentRole = ref(null)
const checked = ref([])
const saving = ref(false)

const MODULE_LABELS = {
  book: '图书管理',
  category: '分类管理',
  borrow: '借阅管理',
  purchase: '采购管理',
  group: '集团管理',
  system: '系统管理'
}

const groupedPermissions = computed(() => {
  const groups = {}
  permissions.value.forEach((p) => {
    const key = p.module || '其他'
    if (!groups[key]) groups[key] = []
    groups[key].push(p)
  })
  return groups
})

const canEdit = computed(() => currentRole.value && currentRole.value.roleCode === 'NORMAL')

function moduleLabel(module) {
  return MODULE_LABELS[module] || module
}

function selectRole(role) {
  currentRole.value = role
  selectedRoleId.value = role.id
  checked.value = [...(role.permissions || [])]
}

async function loadRoles() {
  roles.value = await listRoles()
  const normal = roles.value.find((r) => r.roleCode === 'NORMAL') || roles.value[0]
  if (normal) selectRole(normal)
}

async function onSave() {
  if (!currentRole.value) return
  saving.value = true
  try {
    await updateRolePermissions(currentRole.value.id, checked.value)
    ElMessage.success('权限保存成功')
    await loadRoles()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  permissions.value = await listPermissions()
  await loadRoles()
})
</script>

<style scoped>
.role-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.role-list {
  width: 260px;
  flex-shrink: 0;
}
.role-menu {
  border-right: none;
}
.builtin-tag {
  margin-left: 8px;
}
.perm-panel {
  flex: 1;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.role-tag {
  margin-left: 8px;
}
.tip {
  margin-bottom: 12px;
}
.perm-group {
  margin-bottom: 18px;
}
.group-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-left: 4px;
  border-left: 3px solid #1677ff;
}
.perm-code {
  color: #c0c4cc;
  font-size: 12px;
  margin-left: 6px;
}
</style>
