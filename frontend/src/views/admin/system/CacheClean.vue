<template>
  <div class="page-card">
    <el-alert
      title="按致远 OA 的 resSuffix 语义：递增资源版本号，并用新版本号作为 URL 参数重新加载页面。URL 变化后浏览器不会命中旧缓存，会重新拉取 index.html 与其引用的最新静态资源（不影响服务端缓存，也不影响其他人的浏览器）。"
      type="info"
      :closable="false"
      show-icon
      class="tip"
    />

    <el-card shadow="never" class="card">
      <template #header>前端资源版本刷新</template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="当前资源版本号">{{ version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上次刷新时间">{{ lastClearTime || '未刷新' }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-popconfirm
          title="确认刷新资源版本？页面将以带版本参数的 URL 重新加载。"
          confirm-button-text="立即刷新"
          cancel-button-text="取消"
          width="280"
          @confirm="onClear"
        >
          <template #reference>
            <el-button type="primary" :icon="Refresh" :loading="clearing">刷新资源版本并重载</el-button>
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
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCacheVersion, clearJsCache } from '../../../api/system'

const version = ref('')
const lastClearTime = ref('')
const clearing = ref(false)
const steps = ref([])

async function loadVersion() {
  version.value = await getCacheVersion()
}

/**
 * 按 OA resSuffix 语义刷新前端资源：
 * 1. 后端递增资源版本号；2. 用 `?v=<版本号>` 换一个 URL 重新加载。
 * URL 变了，浏览器不会命中旧缓存，从而拿到最新的 index.html 与其中的静态资源。
 * 不清理服务端缓存，也不影响其他客户端（与 OA 的版本参数机制一致）。
 */
async function onClear() {
  clearing.value = true
  steps.value = []
  try {
    version.value = await clearJsCache()
    steps.value.push(`已递增资源版本号：${version.value}`)

    const target = `${window.location.pathname}?v=${version.value}`
    steps.value.push(`准备以新 URL 重新加载：${target}`)

    lastClearTime.value = new Date().toLocaleString()
    ElMessage.success('资源版本已刷新，页面即将以新 URL 重新加载')

    setTimeout(() => window.location.replace(target), 800)
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
