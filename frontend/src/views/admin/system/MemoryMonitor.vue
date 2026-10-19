<template>
  <div class="page-card" v-loading="loading">
    <div class="toolbar">
      <el-button type="primary" :icon="Refresh" @click="loadData">刷新</el-button>
      <el-switch v-model="autoRefresh" active-text="每 5 秒自动刷新" @change="toggleAuto" />
      <span class="server-time">采集时间：{{ info.serverTime || '-' }}</span>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>堆内存（Heap）</template>
          <el-progress
            type="dashboard"
            :percentage="heapPercent"
            :color="progressColor"
            :width="160"
          >
            <template #default>
              <div class="gauge">
                <div class="gauge-num">{{ heapPercent }}%</div>
                <div class="gauge-label">已用</div>
              </div>
            </template>
          </el-progress>
          <div class="mem-detail">
            <div><span>已用</span><b>{{ fmt(info.heap?.used) }}</b></div>
            <div><span>已提交</span><b>{{ fmt(info.heap?.committed) }}</b></div>
            <div><span>上限</span><b>{{ fmt(info.heap?.max) }}</b></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>非堆内存（Non-Heap）</template>
          <el-progress
            type="dashboard"
            :percentage="nonHeapPercent"
            :color="progressColor"
            :width="160"
          >
            <template #default>
              <div class="gauge">
                <div class="gauge-num">{{ nonHeapPercent }}%</div>
                <div class="gauge-label">已用</div>
              </div>
            </template>
          </el-progress>
          <div class="mem-detail">
            <div><span>已用</span><b>{{ fmt(info.nonHeap?.used) }}</b></div>
            <div><span>已提交</span><b>{{ fmt(info.nonHeap?.committed) }}</b></div>
            <div><span>上限</span><b>{{ fmt(info.nonHeap?.max) }}</b></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="block">
      <template #header>运行时信息</template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="JVM">{{ info.runtime?.jvmName }} {{ info.runtime?.jvmVersion }}</el-descriptions-item>
        <el-descriptions-item label="运行时长">{{ fmtDuration(info.runtime?.uptimeMs) }}</el-descriptions-item>
        <el-descriptions-item label="CPU 核数">{{ info.runtime?.availableProcessors }}</el-descriptions-item>
        <el-descriptions-item label="当前线程数">{{ info.runtime?.threadCount }}</el-descriptions-item>
        <el-descriptions-item label="峰值线程数">{{ info.runtime?.peakThreadCount }}</el-descriptions-item>
        <el-descriptions-item label="守护线程数">{{ info.runtime?.daemonThreadCount }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="16" class="block">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>内存池</template>
          <el-table :data="info.pools" border stripe size="small" max-height="320">
            <el-table-column prop="name" label="内存池" min-width="200" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="90" align="center" />
            <el-table-column label="已用" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.used) }}</template>
            </el-table-column>
            <el-table-column label="上限" width="110" align="right">
              <template #default="{ row }">{{ fmt(row.max) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>垃圾回收（GC）</template>
          <el-table :data="info.gc" border stripe size="small">
            <el-table-column prop="name" label="收集器" min-width="160" show-overflow-tooltip />
            <el-table-column prop="count" label="次数" width="90" align="right" />
            <el-table-column label="耗时" width="110" align="right">
              <template #default="{ row }">{{ row.time }} ms</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getMemoryInfo } from '../../../api/system'

const loading = ref(false)
const info = ref({})
const autoRefresh = ref(false)
let timer = null

const heapPercent = computed(() => toPercent(info.value.heap?.used, info.value.heap?.max))
const nonHeapPercent = computed(() => toPercent(info.value.nonHeap?.used, info.value.nonHeap?.max))
const progressColor = [
  { color: '#67c23a', percentage: 60 },
  { color: '#e6a23c', percentage: 80 },
  { color: '#f56c6c', percentage: 100 }
]

function toPercent(used, max) {
  if (!used || !max || max <= 0) return 0
  return Math.round((used / max) * 1000) / 10
}

function fmt(bytes) {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes < 0) return '无上限'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function fmtDuration(ms) {
  if (!ms && ms !== 0) return '-'
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  return `${h} 时 ${m} 分 ${s} 秒`
}

async function loadData() {
  loading.value = true
  try {
    info.value = await getMemoryInfo()
  } finally {
    loading.value = false
  }
}

function toggleAuto(val) {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (val) {
    timer = setInterval(loadData, 5000)
  }
}

onMounted(loadData)
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped>
.page-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.server-time {
  color: #909399;
  font-size: 13px;
}
.gauge {
  text-align: center;
}
.gauge-num {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.gauge-label {
  font-size: 12px;
  color: #909399;
}
.mem-detail {
  display: flex;
  justify-content: space-around;
  margin-top: 8px;
  color: #606266;
  font-size: 13px;
}
.mem-detail b {
  color: #303133;
  margin-left: 6px;
}
.block {
  margin-top: 16px;
}
</style>
