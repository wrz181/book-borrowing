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
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" type="primary" text @click="onReturn(row)">发起归还</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="book && (book.borrowRecords?.length ?? 0) === 0" description="该图书暂无借阅记录" />
    </div>

    <!-- 图书评论区（book_comment 表，一对多） -->
    <div class="records-block">
      <div class="records-title">
        <el-icon><ChatDotRound /></el-icon>
        图书评论区
      </div>
      <div v-if="userStore.token" class="comment-form">
        <el-rate v-model="commentForm.rating" />
        <el-input
          v-model="commentForm.content"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="写下你对这本书的评价…"
          style="margin-top: 8px"
        />
        <div style="margin-top: 8px; text-align: right">
          <el-button type="primary" size="small" :loading="commentSubmitting" @click="onSubmitComment">发表评论</el-button>
        </div>
      </div>
      <el-table :data="comments" border stripe>
        <el-table-column prop="userName" label="评论人" width="110" />
        <el-table-column label="评分" width="150" align="center">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评论内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="createTime" label="评论时间" width="170" align="center" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button
              v-if="userStore.isAdmin || row.userId === userStore.userInfo?.id"
              type="danger"
              text
              @click="onDeleteComment(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="comments.length === 0" description="暂无评论，来发表第一条吧" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Notebook, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBookDetail } from '../../api/book'
import { returnBorrowRecord } from '../../api/borrowRecord'
import { listComments, createComment, deleteComment } from '../../api/comment'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const book = ref(null)
const comments = ref([])
const commentSubmitting = ref(false)
const commentForm = ref({ rating: 5, content: '' })

/** 状态展示：0-借阅待确认 1-借阅中 2-已归还 3-已驳回 4-归还待确认 */
function statusMeta(status) {
  switch (status) {
    case 0: return { label: '借阅待确认', type: 'warning' }
    case 1: return { label: '借阅中', type: 'primary' }
    case 2: return { label: '已归还', type: 'success' }
    case 3: return { label: '已驳回', type: 'danger' }
    case 4: return { label: '归还待确认', type: 'warning' }
    default: return { label: '未知', type: 'info' }
  }
}

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
    await ElMessageBox.confirm(`确认申请归还《${book.value.bookName}》（借阅人：${row.borrowerName}）？提交后需管理员确认。`, '归还申请', { type: 'warning' })
  } catch {
    return
  }
  await returnBorrowRecord(row.id)
  ElMessage.success('归还申请已提交，等待管理员确认')
  await loadData()
}

async function loadComments() {
  try {
    comments.value = await listComments(route.params.id)
  } catch {
    comments.value = []
  }
}

async function onSubmitComment() {
  if (!commentForm.value.content || !commentForm.value.content.trim()) {
    ElMessage.warning('请填写评论内容')
    return
  }
  commentSubmitting.value = true
  try {
    await createComment({
      bookId: route.params.id,
      rating: commentForm.value.rating || 5,
      content: commentForm.value.content.trim()
    })
    ElMessage.success('评论发表成功')
    commentForm.value = { rating: 5, content: '' }
    await loadComments()
  } finally {
    commentSubmitting.value = false
  }
}

async function onDeleteComment(row) {
  try {
    await ElMessageBox.confirm('确认删除该评论？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteComment(row.id)
  ElMessage.success('删除成功')
  await loadComments()
}

onMounted(() => {
  loadData()
  loadComments()
})
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
