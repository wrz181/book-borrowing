<template>
  <div class="page-card">
    <el-alert
      title="配置单点登录的第三方应用后，左侧菜单会自动出现对应栏目，点击即以单点登录方式跳转到该应用。"
      type="info"
      :closable="false"
      show-icon
      class="tip"
    />

    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增应用</el-button>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="appName" label="应用名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="appUrl" label="应用地址" min-width="220" show-overflow-tooltip />
      <el-table-column label="集成方式" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ row.appType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="单点登录密钥" width="180">
        <template #default="{ row }">
          <span class="secret">{{ showSecret[row.id] ? row.ssoSecret : '••••••••' }}</span>
          <el-button link type="primary" size="small" @click="toggleSecret(row.id)">
            {{ showSecret[row.id] ? '隐藏' : '显示' }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="success" text :icon="Link" :disabled="row.enabled !== 1" @click="gotoApp(row)">进入</el-button>
          <el-button type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该应用？" confirm-button-text="删除" cancel-button-text="取消" @confirm="onDelete(row)">
            <template #reference>
              <el-button type="danger" text :icon="Delete">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑应用' : '新增应用'" width="560px">
      <el-form ref="formRef" :model="editing" :rules="rules" label-width="120px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="editing.appName" placeholder="配置后作为左侧栏目名" maxlength="100" />
        </el-form-item>
        <el-form-item label="应用地址" prop="appUrl">
          <el-input v-model="editing.appUrl" placeholder="类ERP平台入口，如 http://localhost:5174/" maxlength="500" />
        </el-form-item>
        <el-form-item label="单点登录密钥">
          <el-input v-model="editing.ssoSecret" placeholder="留空自动生成" maxlength="100" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="editing.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editing.description" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Edit, Delete, Link } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listApps, createApp, updateApp, deleteApp } from '../../../api/integrationApp'
import { getSsoTicket } from '../../../api/sso'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
const showSecret = reactive({})

const editing = reactive({
  id: null, appName: '', appUrl: '', appType: 'SSO', ssoSecret: '',
  enabled: 1, description: ''
})
const rules = {
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  appUrl: [{ required: true, message: '请输入应用地址', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    tableData.value = await listApps()
  } finally {
    loading.value = false
  }
}

function toggleSecret(id) {
  showSecret[id] = !showSecret[id]
}

function openDialog(row) {
  if (row) {
    Object.assign(editing, {
      id: row.id, appName: row.appName, appUrl: row.appUrl, appType: row.appType || 'SSO',
      ssoSecret: row.ssoSecret,
      enabled: row.enabled, description: row.description
    })
  } else {
    Object.assign(editing, {
      id: null, appName: '', appUrl: 'http://localhost:5174/', appType: 'SSO', ssoSecret: '',
      enabled: 1, description: ''
    })
  }
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (editing.id) {
      await updateApp(editing.id, editing)
      ElMessage.success('修改成功')
    } else {
      await createApp(editing)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row) {
  await deleteApp(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

/** 以单点登录方式进入应用（与左侧栏目跳转一致） */
async function gotoApp(row) {
  const ticket = await getSsoTicket(row.id)
  const base = row.appUrl || 'http://localhost:5174/'
  const url = `${base}${base.includes('?') ? '&' : '?'}appId=${row.id}&ticket=${encodeURIComponent(ticket)}`
  window.open(url, '_blank')
}

onMounted(loadData)
</script>

<style scoped>
.page-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px;
}
.tip {
  margin-bottom: 12px;
}
.toolbar {
  margin-bottom: 16px;
}
.secret {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}
.ml4 {
  margin-left: 4px;
}
</style>
