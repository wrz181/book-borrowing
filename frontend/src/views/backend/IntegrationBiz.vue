<template>
  <div class="page-card">
    <el-alert
      title="集成业务配置：一种业务类型只能配一个超级节点。采购单提交申请后，审批通过时按此配置把单据下发到绑定的集成应用（ERP）并挂起等待回调。"
      type="info"
      :closable="false"
      show-icon
      class="tip"
    />

    <!-- 动态条件查询区 -->
    <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="业务类型">
        <el-input v-model="query.bizType" placeholder="如 BOOK_PURCHASE" clearable style="width: 170px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="业务名称">
        <el-input v-model="query.bizName" placeholder="支持模糊查询" clearable style="width: 170px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="启用状态">
        <el-select v-model="query.enabled" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增业务配置</el-button>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="bizType" label="业务类型" width="160" show-overflow-tooltip />
      <el-table-column prop="bizName" label="业务名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="绑定应用" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ appName(row.appId) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="superNodeName" label="超级节点名" width="150" show-overflow-tooltip />
      <el-table-column label="出站地址" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.superNodeUrl">{{ row.superNodeUrl }}</span>
          <span v-else class="sub-text">默认：应用地址 + /erp/forms</span>
        </template>
      </el-table-column>
      <el-table-column prop="callbackTimeoutMinutes" label="回调超时(分钟)" width="130" align="center" />
      <el-table-column label="启用状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-button type="danger" text :icon="Delete" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :page-sizes="[5, 10, 20]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="loadData"
      @current-change="loadData"
    />

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑业务配置' : '新增业务配置'" width="620px">
      <el-form ref="formRef" :model="editing" :rules="rules" label-width="130px">
        <el-form-item label="业务类型" prop="bizType">
          <el-input v-model="editing.bizType" maxlength="50" placeholder="如 BOOK_PURCHASE" />
          <div class="form-tip">一个业务类型只能配一个超级节点，重复配置会被后端拒绝。</div>
        </el-form-item>
        <el-form-item label="业务名称" prop="bizName">
          <el-input v-model="editing.bizName" maxlength="100" placeholder="如 图书采购申请" />
        </el-form-item>
        <el-form-item label="绑定应用" prop="appId">
          <el-select v-model="editing.appId" placeholder="选择启用的集成应用" style="width: 100%">
            <el-option
              v-for="app in appOptions"
              :key="app.id"
              :label="app.enabled === 1 ? app.appName : app.appName + '（已停用）'"
              :value="app.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="超级节点名" prop="superNodeName">
          <el-input v-model="editing.superNodeName" maxlength="100" placeholder="如 ERP采购超级节点，采购页会展示该名称" />
        </el-form-item>
        <el-form-item label="出站地址">
          <el-input v-model="editing.superNodeUrl" maxlength="500" placeholder="可留空，默认取应用地址 + /erp/forms" />
        </el-form-item>
        <el-form-item label="回调超时(分钟)" prop="callbackTimeoutMinutes">
          <el-input-number v-model="editing.callbackTimeoutMinutes" :min="1" :max="43200" :step="30" />
          <div class="form-tip">超过该时长仍未收到 ERP 回调，流程按超时处理（默认 1440 分钟 = 1 天）。</div>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageBizs, createBiz, updateBiz, deleteBiz } from '../../api/biz'
import { listApps } from '../../api/integrationApp'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
const apps = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const query = reactive({ bizType: '', bizName: '', enabled: null })

const editing = reactive({
  id: null, bizType: 'BOOK_PURCHASE', bizName: '', appId: null,
  superNodeName: '', superNodeUrl: '', callbackTimeoutMinutes: 1440,
  enabled: 1, description: ''
})

const rules = {
  bizType: [{ required: true, message: '请输入业务类型', trigger: 'blur' }],
  bizName: [{ required: true, message: '请输入业务名称', trigger: 'blur' }],
  appId: [{ required: true, message: '请选择绑定应用', trigger: 'change' }],
  superNodeName: [{ required: true, message: '请输入超级节点名', trigger: 'blur' }],
  callbackTimeoutMinutes: [{ required: true, message: '请输入回调超时时间（分钟）', trigger: 'blur' }]
}

/** 下拉只给启用的应用；编辑历史数据时若原应用已停用，仍保留在选项里避免显示为空 */
const appOptions = computed(() => {
  const enabledApps = apps.value.filter((a) => a.enabled === 1)
  if (editing.appId && !enabledApps.some((a) => a.id === editing.appId)) {
    const current = apps.value.find((a) => a.id === editing.appId)
    if (current) return [current, ...enabledApps]
  }
  return enabledApps
})

function appName(appId) {
  if (!appId) return '未绑定'
  const app = apps.value.find((a) => a.id === appId)
  return app ? app.appName : `#${appId}`
}

async function loadData() {
  loading.value = true
  try {
    const data = await pageBizs({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      bizType: query.bizType || undefined,
      bizName: query.bizName || undefined,
      enabled: query.enabled === null || query.enabled === '' ? undefined : query.enabled
    })
    tableData.value = data.list
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadData()
}

function onReset() {
  Object.assign(query, { bizType: '', bizName: '', enabled: null })
  pageNum.value = 1
  loadData()
}

function openDialog(row) {
  if (row) {
    Object.assign(editing, {
      id: row.id, bizType: row.bizType, bizName: row.bizName, appId: row.appId,
      superNodeName: row.superNodeName, superNodeUrl: row.superNodeUrl || '',
      callbackTimeoutMinutes: row.callbackTimeoutMinutes || 1440,
      enabled: row.enabled === 1 ? 1 : 0, description: row.description || ''
    })
  } else {
    Object.assign(editing, {
      id: null, bizType: 'BOOK_PURCHASE', bizName: '', appId: null,
      superNodeName: '', superNodeUrl: '', callbackTimeoutMinutes: 1440,
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
      await updateBiz(editing.id, editing)
      ElMessage.success('修改成功')
    } else {
      await createBiz(editing)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除业务配置「${row.bizName}」？删除后该业务类型将无法下发超级节点。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await deleteBiz(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

async function loadApps() {
  try {
    apps.value = await listApps()
  } catch {
    apps.value = []
  }
}

onMounted(async () => {
  await loadApps()
  await loadData()
})
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
.query-form {
  margin-bottom: 8px;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.sub-text {
  color: #909399;
  font-size: 12px;
}
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
