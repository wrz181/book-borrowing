<template>
  <div class="page-card">
    <el-alert
      title="按业务模块设置日志级别（实时生效）；下方日志按「级别 ≥ 所选级别」过滤展示。"
      type="info"
      :closable="false"
      show-icon
      class="tip"
    />

    <el-card shadow="never" class="module-card">
      <template #header>日志类型设置</template>
      <el-table :data="modules" border size="small">
        <el-table-column prop="moduleName" label="业务模块" width="140" />
        <el-table-column prop="logger" label="Logger" min-width="320" show-overflow-tooltip />
        <el-table-column label="当前级别" width="200" align="center">
          <template #default="{ row }">
            <el-select v-model="row.level" size="small" style="width: 120px" @change="onLevelChange(row)">
              <el-option v-for="lv in levels" :key="lv" :label="lv" :value="lv" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="log-card">
      <template #header>
        <div class="log-header">
          <span>内存日志</span>
          <div class="log-actions">
            <el-select v-model="query.module" placeholder="全部模块" clearable size="small" style="width: 150px" @change="onSearch">
              <el-option v-for="m in modules" :key="m.module" :label="m.moduleName" :value="m.module" />
            </el-select>
            <el-select v-model="query.level" placeholder="默认级别" clearable size="small" style="width: 130px" @change="onSearch">
              <el-option v-for="lv in levels" :key="lv" :label="lv" :value="lv" />
            </el-select>
            <el-button size="small" :icon="Refresh" @click="loadLogs">刷新</el-button>
            <el-popconfirm title="确认清空内存日志？" confirm-button-text="清空" cancel-button-text="取消" @confirm="onClear">
              <template #reference>
                <el-button size="small" type="danger" :icon="Delete">清空</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </template>

      <el-table :data="logs" v-loading="loading" border stripe size="small" height="420">
        <el-table-column prop="time" label="时间" width="180" />
        <el-table-column label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="moduleName" label="模块" width="110" />
        <el-table-column prop="logger" label="来源" width="200" show-overflow-tooltip />
        <el-table-column prop="message" label="内容" min-width="320" show-overflow-tooltip />
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadLogs"
        @current-change="loadLogs"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listLogModules, setLogLevel, pageLogs, clearLogs } from '../../../api/system'

const levels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR']

const modules = ref([])
const logs = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const query = reactive({ module: '', level: '' })

function levelTag(level) {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  if (level === 'INFO') return 'primary'
  return 'info'
}

async function loadModules() {
  modules.value = await listLogModules()
}

async function onLevelChange(row) {
  const applied = await setLogLevel(row.module, row.level)
  row.level = applied
  ElMessage.success(`「${row.moduleName}」日志级别已设为 ${applied}`)
  await loadLogs()
}

async function loadLogs() {
  loading.value = true
  try {
    const data = await pageLogs({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      module: query.module || undefined,
      level: query.level || undefined
    })
    logs.value = data.list
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageNum.value = 1
  loadLogs()
}

async function onClear() {
  await clearLogs()
  ElMessage.success('内存日志已清空')
  await loadLogs()
}

onMounted(async () => {
  await loadModules()
  await loadLogs()
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
.module-card {
  margin-bottom: 16px;
}
.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.log-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
