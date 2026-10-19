<template>
  <div class="page-card">
    <el-alert
      title="在类ERP平台完成表单后，系统会在此生成待办与消息提醒。"
      type="info"
      :closable="false"
      show-icon
      class="tip"
    />

    <el-form :inline="true" class="query-form">
      <el-form-item label="状态">
        <el-select v-model="status" placeholder="全部" clearable style="width: 140px" @change="onSearch">
          <el-option label="待办" :value="0" />
          <el-option label="已办" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button :icon="Refresh" @click="onSearch">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="title" label="待办标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
      <el-table-column prop="appName" label="来源应用" width="140" />
      <el-table-column prop="createTime" label="生成时间" width="180" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
            {{ row.status === 1 ? '已办' : '待办' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" type="primary" text :icon="Check" @click="onDone(row)">办理</el-button>
          <span v-else class="done-text">{{ row.doneTime || '-' }}</span>
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pageTodos, doneTodo } from '../../../api/todo'

const loading = ref(false)
const tableData = ref([])
const status = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const data = await pageTodos({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: status.value
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

async function onDone(row) {
  await doneTodo(row.id)
  ElMessage.success('已办理')
  await loadData()
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
.query-form {
  margin-bottom: 8px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.done-text {
  color: #909399;
  font-size: 12px;
}
</style>
