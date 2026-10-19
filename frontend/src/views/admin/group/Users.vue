<template>
  <div class="page-card">
    <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="账号/姓名">
        <el-input v-model="query.keyword" placeholder="支持模糊查询" clearable style="width: 180px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="query.roleId" placeholder="全部" clearable style="width: 160px">
          <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增账号</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="username" label="登录账号" width="150" />
      <el-table-column prop="nickname" label="昵称/姓名" min-width="140" show-overflow-tooltip />
      <el-table-column prop="roleName" label="角色" width="140">
        <template #default="{ row }">
          <el-tag size="small">{{ row.roleName || '未分配' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="270" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-button type="warning" text :icon="Key" @click="onResetPassword(row)">重置密码</el-button>
          <el-popconfirm title="确认删除该账号？" confirm-button-text="删除" cancel-button-text="取消" @confirm="onDelete(row)">
            <template #reference>
              <el-button type="danger" text :icon="Delete">删除</el-button>
            </template>
          </el-popconfirm>
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

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑账号' : '新增账号'" width="480px">
      <el-form ref="formRef" :model="editing" :rules="rules" label-width="90px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="editing.username" :disabled="!!editing.id" maxlength="50" />
        </el-form-item>
        <el-form-item label="昵称/姓名" prop="nickname">
          <el-input v-model="editing.nickname" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="editing.roleId" placeholder="请选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editing.id" label="初始密码">
          <el-input v-model="editing.password" placeholder="留空默认 123456" maxlength="50" />
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
import { Search, Refresh, Plus, Edit, Delete, Key } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, createUser, updateUser, deleteUser, resetUserPassword, listRoles } from '../../../api/admin'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
const roles = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const query = reactive({ keyword: '', roleId: null })
const editing = reactive({ id: null, username: '', nickname: '', roleId: null, password: '' })
const rules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const data = await pageUsers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: query.keyword || undefined,
      roleId: query.roleId || undefined
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
  Object.assign(query, { keyword: '', roleId: null })
  pageNum.value = 1
  loadData()
}

function openDialog(row) {
  if (row) {
    Object.assign(editing, { id: row.id, username: row.username, nickname: row.nickname, roleId: row.roleId, password: '' })
  } else {
    Object.assign(editing, { id: null, username: '', nickname: '', roleId: null, password: '' })
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
      await updateUser(editing.id, { nickname: editing.nickname, roleId: editing.roleId })
      ElMessage.success('修改成功')
    } else {
      await createUser(editing)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function onResetPassword(row) {
  try {
    const { value } = await ElMessageBox.prompt(`为账号「${row.username}」设置新密码`, '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '留空则重置为 123456',
      inputValue: ''
    })
    await resetUserPassword(row.id, value || '')
    ElMessage.success('密码已重置')
  } catch {
    /* 取消 */
  }
}

async function onDelete(row) {
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

onMounted(async () => {
  const all = await listRoles()
  // 集团管理员只能创建/维护普通人员账号，角色下拉仅保留 NORMAL
  roles.value = (all || []).filter((r) => r.roleCode === 'NORMAL')
  await loadData()
})
</script>

<style scoped>
.page-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px;
}
.query-form {
  margin-bottom: 8px;
}
.toolbar {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
