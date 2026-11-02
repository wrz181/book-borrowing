<template>
  <div class="dashboard">
    <!-- 热门排行说明 -->
    <el-alert type="info" show-icon :closable="false" class="intro">
      <template #title>
        热门图书排行基于 Redis ZSet 实时统计：浏览详情 +1 分，提交借阅申请 +10 分；
        Redis 不可用时自动降级为数据库按借阅记录数统计（来源列会标注）。
      </template>
    </el-alert>

    <el-card shadow="never" class="rank-card">
      <template #header>
        <div class="card-header">
          <span>🔥 热门借阅 Top {{ top }}</span>
          <el-button type="primary" text :icon="Refresh" :loading="loading" @click="loadRank">刷新</el-button>
        </div>
      </template>

      <el-table :data="rankList" v-loading="loading" stripe class="rank-table">
        <el-table-column label="排名" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.rank === 1" type="danger" effect="dark" round>🥇 1</el-tag>
            <el-tag v-else-if="row.rank === 2" type="warning" effect="dark" round>🥈 2</el-tag>
            <el-tag v-else-if="row.rank === 3" type="success" effect="dark" round>🥉 3</el-tag>
            <span v-else class="rank-num">{{ row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bookName" label="图书名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="author" label="作者" min-width="120" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" show-overflow-tooltip />
        <el-table-column label="热度分" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.hotScore != null" class="score">{{ row.hotScore }}</span>
            <span v-else class="score-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="统计来源" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.source === 'redis' ? 'success' : 'info'">
              {{ row.source === 'redis' ? 'Redis 实时' : '数据库降级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button type="primary" text @click="goDetail(row.bookId)">查看详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无热度数据：浏览或申请借阅图书后即可生成排行" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getHotBooks } from '../../api/book'

const router = useRouter()

const top = ref(10)
const loading = ref(false)
const rankList = ref([])

/**
 * 加载热门排行：来源（redis/db）与热度分由后端返回，前端不做二次加工。
 */
async function loadRank() {
  loading.value = true
  try {
    rankList.value = await getHotBooks(top.value)
  } finally {
    loading.value = false
  }
}

/**
 * 跳转图书详情。
 */
function goDetail(bookId) {
  router.push(`/books/detail/${bookId}`)
}

onMounted(loadRank)
</script>

<style scoped>
.intro {
  margin-bottom: 12px;
}
.rank-card {
  border-radius: 8px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}
.rank-num {
  color: #909399;
  font-size: 14px;
}
.score {
  font-weight: 600;
  color: #e6a23c;
}
.score-muted {
  color: #c0c4cc;
}
</style>
