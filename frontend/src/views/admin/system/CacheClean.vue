<template>
  <div class="page-card">
    <el-alert
      title="清理 JS 缓存会递增静态资源版本号，并清空浏览器 Cache Storage / 会话缓存后重新加载页面，确保前端资源最新。"
      type="warning"
      :closable="false"
      show-icon
      class="tip"
    />

    <el-card shadow="never" class="card">
      <template #header>JS 缓存清理</template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="当前资源版本号">{{ version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上次清理时间">{{ lastClearTime || '未清理' }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-popconfirm
          title="确认清理 JS 缓存？页面将重新加载。"
          confirm-button-text="立即清理"
          cancel-button-text="取消"
          width="260"
          @confirm="onClear"
        >
          <template #reference>
            <el-button type="danger" :icon="Delete" :loading="clearing">清理 JS 缓存</el-button>
          </template>
        </el-popconfirm>
        <el-button :icon="Refresh" @click="loadVersion">刷新版本号</el-button>
      </div>

      <el-steps v-if="steps.length" direction="vertical" :active="steps.length" class="steps">
        <el-step v-for="(s, i) in steps" :key="i" :title="s" status="finish" />
      </el-steps>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Delete, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCacheVersion, clearJsCache } from '../../../api/system'

const version = ref('')
const lastClearTime = ref('')
const clearing = ref(false)
const steps = ref([])

async function loadVersion() {
  version.value = await getCacheVersion()
}

async function onClear() {
  clearing.value = true
  steps.value = []
  try {
    // 1. 通知后端递增静态资源版本号
    version.value = await clearJsCache()
    steps.value.push(`已递增静态资源版本号：${version.value}`)

    // 2. 清空浏览器 Cache Storage
    if (window.caches) {
      const keys = await caches.keys()
      await Promise.all(keys.map((k) => caches.delete(k)))
      steps.value.push(`已清空浏览器 Cache Storage（${keys.length} 个缓存仓库）`)
    }

    // 3. 清空会话级缓存（保留登录 token）
    sessionStorage.clear()
    steps.value.push('已清空 sessionStorage 会话缓存')

    lastClearTime.value = new Date().toLocaleString()
    ElMessage.success('JS 缓存清理完成，页面即将重新加载')

    // 4. 重新加载页面，强制拉取最新资源
    setTimeout(() => window.location.reload(), 1200)
  } finally {
    clearing.value = false
  }
}

onMounted(loadVersion)
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
.card {
  max-width: 900px;
}
.actions {
  margin: 20px 0 8px;
  display: flex;
  gap: 12px;
}
.steps {
  margin-top: 12px;
}
</style>
