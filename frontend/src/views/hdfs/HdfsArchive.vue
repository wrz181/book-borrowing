<template>
  <div class="hdfs-archive">
    <el-alert type="info" show-icon :closable="false" class="intro">
      <template #title>
        HDFS 分布式归档：一键把全部借阅记录导出为 CSV 写入 Hadoop HDFS 集群（/bookborrowing/archive/），
        支持文件列表与内容预览；HDFS 未启用或集群不可连接时会给出友好提示，不影响系统其他功能。
      </template>
    </el-alert>

    <el-card shadow="never" class="op-card">
      <template #header>
        <div class="card-header">
          <span>📦 归档操作</span>
          <el-button type="primary" :icon="Upload" :loading="archiving" @click="onArchive">
            立即归档
          </el-button>
        </div>
      </template>

      <el-descriptions v-if="lastResult" :column="3" border class="result-desc">
        <el-descriptions-item label="归档文件路径">{{ lastResult.path }}</el-descriptions-item>
        <el-descriptions-item label="归档记录数">{{ lastResult.records }} 条</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatSize(lastResult.sizeBytes) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="尚未归档：点击「立即归档」把全部借阅记录写入 HDFS" :image-size="72" />
    </el-card>

    <el-card shadow="never" class="files-card">
      <template #header>
        <div class="card-header">
          <span>📁 归档文件列表（HDFS /bookborrowing/archive/）</span>
          <el-button type="primary" text :icon="Refresh" :loading="loading" @click="loadFiles">刷新</el-button>
        </div>
      </template>

      <el-table :data="files" v-loading="loading" stripe>
        <el-table-column prop="name" label="文件名" min-width="260" show-overflow-tooltip />
        <el-table-column label="大小" width="140" align="center">
          <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="修改时间" width="200" align="center">
          <template #default="{ row }">{{ formatTime(row.modifyTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button type="primary" text @click="onPreview(row)">预览</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无归档文件：执行「立即归档」后即可在此查看" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="previewVisible" :title="`文件预览：${previewName}（前 ${previewLines} 行）`" width="860px" top="8vh">
      <pre class="preview-content">{{ previewContent }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Upload } from '@element-plus/icons-vue'
import { runArchive, listArchiveFiles, previewArchiveFile } from '../../api/hdfs'

const archiving = ref(false)
const loading = ref(false)
const lastResult = ref(null)
const files = ref([])

const previewVisible = ref(false)
const previewName = ref('')
const previewLines = ref(20)
const previewContent = ref('')

/**
 * 执行归档：成功后展示归档结果并刷新文件列表。
 */
async function onArchive() {
  archiving.value = true
  try {
    lastResult.value = await runArchive()
    ElMessage.success(`归档完成：${lastResult.value.records} 条记录已写入 HDFS`)
    await loadFiles()
  } finally {
    archiving.value = false
  }
}

/**
 * 加载 HDFS 归档目录下的文件列表。
 */
async function loadFiles() {
  loading.value = true
  try {
    files.value = await listArchiveFiles()
  } finally {
    loading.value = false
  }
}

/**
 * 预览归档 CSV 文件前 N 行内容。
 */
async function onPreview(row) {
  previewName.value = row.name
  previewContent.value = '加载中…'
  previewVisible.value = true
  try {
    previewContent.value = await previewArchiveFile(row.name, previewLines.value)
  } catch (e) {
    previewContent.value = '预览失败：' + (e?.message || '未知错误')
  }
}

/**
 * 字节数格式化为可读大小。
 */
function formatSize(bytes) {
  if (bytes == null) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

/**
 * 毫秒时间戳格式化为 yyyy-MM-dd HH:mm:ss。
 */
function formatTime(ts) {
  if (!ts) return '—'
  const d = new Date(Number(ts))
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(loadFiles)
</script>

<style scoped>
.intro {
  margin-bottom: 12px;
}
.op-card,
.files-card {
  border-radius: 8px;
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.result-desc {
  margin-top: 4px;
}
.preview-content {
  max-height: 55vh;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre;
}
</style>
