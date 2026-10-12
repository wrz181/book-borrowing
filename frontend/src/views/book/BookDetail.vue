<template>
  <div class="page-card" v-loading="loading">
    <div class="detail-header">
      <el-button :icon="Back" @click="router.push('/books')">返回图书列表</el-button>
      <span class="detail-title">图书详情与借阅记录</span>
    </div>

    <!-- 图书主对象信息 -->
    <el-descriptions v-if="book" :column="3" border class="book-info">
      <el-descriptions-item label="图书名称">{{ book.bookName }}</el-descriptions-item>
      <el-descriptions-item label="作者">{{ book.author }}</el-descriptions-item>
      <el-descriptions-item label="所属分类">{{ book.categoryName }}</el-descriptions-item>
      <el-descriptions-item label="ISBN">{{ book.isbn }}</el-descriptions-item>
      <el-descriptions-item label="出版社">{{ book.publisher }}</el-descriptions-item>
      <el-descriptions-item label="单价">¥{{ book.price }}</el-descriptions-item>
      <el-descriptions-item label="库存">{{ book.stock }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="book.status === 1 ? 'success' : 'info'" size="small">
          {{ book.status === 1 ? '在架可借' : '已下架' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="借阅记录数">{{ book.borrowRecords?.length ?? 0 }} 条</el-descriptions-item>
    </el-descriptions>

    <!-- 一对多关联查询结果：该图书的全部借阅记录 -->
    <div class="records-block">
      <div class="records-title">
        <el-icon><Notebook /></el-icon>
        该图书全部借阅记录（一对多关联查询）
      </div>
      <el-table :data="book?.borrowRecords ?? []" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="borrowerName" label="借阅人" width="110" />
        <el-table-column prop="borrowerNo" label="借阅证号/学号" width="140" />
        <el-table-column prop="borrowDate" label="借阅日期" width="130" align="center" />
        <el-table-column prop="dueDate" label="应还日期" width="130" align="center" />
        <el-table-column label="实际归还日期" width="130" align="center">
          <template #default="{ row }">{{ row.returnDate || '未归还' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'warning' : 'success'" size="small">
              {{ row.status === 1 ? '借阅中' : '已归还' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" type="primary" text @click="onReturn(row)">归还</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="book && (book.borrowRecords?.length ?? 0) === 0" description="该图书暂无借阅记录" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Notebook } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBookDetail } from '../../api/book'
import { returnBorrowRecord } from '../../api/borrowRecord'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const book = ref(null)

async function loadData() {
  loading.value = true
  try {
    book.value = await getBookDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

async function onReturn(row) {
  try {
    await ElMessageBox.confirm(`确认归还《${book.value.bookName}》（借阅人：${row.borrowerName}）？`, '归还确认', { type: 'warning' })
  } catch {
    return
  }
  await returnBorrowRecord(row.id)
  ElMessage.success('归还成功')
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
.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.book-info {
  margin-bottom: 24px;
}
.records-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.records-block {
  margin-top: 8px;
}
</style>
