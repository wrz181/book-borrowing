<template>
  <div class="page-card">
    <el-form :inline="true" class="query-form">
      <el-form-item label="状态">
        <el-select v-model="readFlag" placeholder="全部" clearable style="width: 140px" @change="onSearch">
          <el-option label="未读" :value="0" />
          <el-option label="已读" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button :icon="Refresh" @click="onSearch">刷新</el-button>
        <el-button type="primary" :icon="Check" @click="onReadAll">全部标为已读</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="标题" min-width="200">
        <template #default="{ row }">
          <span :class="{ unread: row.readFlag === 0 }">{{ row.title }}</span>
          <el-tag v-if="row.readFlag === 0" type="danger" size="small" class="ml4">未读</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="appName" label="来源应用" width="140" />
      <el-table-column prop="createTime" label="时间" width="180" />
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.readFlag === 0" type="primary" text :icon="View" @click="onRead(row)">标为已读</el-button>
          <span v-else class="done-text">已读</span>
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
import { Refresh, Check, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pageMessages, readMessage, readAllMessages } from '../../../api/message'

const loading = ref(false)
const tableData = ref([])
const readFlag = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const data = await pageMessages({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      readFlag: readFlag.value
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

async function onRead(row) {
  await readMessage(row.id)
  ElMessage.success('已标记为已读')
  await loadData()
}

async function onReadAll() {
  await readAllMessages()
  ElMessage.success('已全部标为已读')
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
.query-form {
  margin-bottom: 8px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.unread {
  font-weight: 600;
}
.ml4 {
  margin-left: 4px;
}
.done-text {
  color: #909399;
  font-size: 12px;
}
</style>
