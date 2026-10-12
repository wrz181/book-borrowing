<template>
  <div class="page-card">
    <!-- 动态条件查询区：图书名称、借阅人、状态、借阅日期范围可单独或组合使用 -->
    <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="图书名称">
        <el-input v-model="query.bookName" placeholder="支持模糊查询" clearable style="width: 170px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="借阅人">
        <el-input v-model="query.borrowerName" placeholder="姓名模糊查询" clearable style="width: 130px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="借阅证号">
        <el-input v-model="query.borrowerNo" placeholder="学号/证号模糊查询" clearable style="width: 150px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="借阅中" :value="1" />
          <el-option label="已归还" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="借阅日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增借阅记录</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="bookName" label="图书名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="author" label="作者" width="120" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" width="90" />
      <el-table-column prop="borrowerName" label="借阅人" width="100" />
      <el-table-column prop="borrowerNo" label="借阅证号/学号" width="140" />
      <el-table-column prop="borrowDate" label="借阅日期" width="120" align="center" />
      <el-table-column prop="dueDate" label="应还日期" width="120" align="center" />
      <el-table-column label="实际归还日期" width="120" align="center">
        <template #default="{ row }">{{ row.returnDate || '未归还' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'warning' : 'success'" size="small">
            {{ row.status === 1 ? '借阅中' : '已归还' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" type="success" text @click="onReturn(row)">归还</el-button>
          <el-button type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该借阅记录？" confirm-button-text="删除" cancel-button-text="取消" @confirm="onDelete(row)">
            <template #reference>
              <el-button type="danger" text :icon="Delete">删除</el-button>
            </template>
          </el-popconfirm>
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

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑借阅记录' : '新增借阅记录'" width="560px">
      <el-alert type="info" :closable="false" show-icon
        title="业务规则 R03：同一借阅人同一天不能重复借阅同一本图书；存在未归还记录时不能重复借阅。" />
      <el-form ref="formRef" :model="editing" :rules="rules" label-width="100px" class="dialog-form">
        <el-form-item label="借阅图书" prop="bookId">
          <el-select v-model="editing.bookId" placeholder="请选择图书" filterable style="width: 100%" :disabled="!!editing.id">
            <el-option v-for="b in bookOptions" :key="b.id" :label="`${b.bookName}（${b.author}）`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="借阅人姓名" prop="borrowerName">
          <el-input v-model="editing.borrowerName" maxlength="50" />
        </el-form-item>
        <el-form-item label="借阅证号/学号" prop="borrowerNo">
          <el-input v-model="editing.borrowerNo" maxlength="30" />
        </el-form-item>
        <el-form-item label="借阅日期" prop="borrowDate">
          <el-date-picker v-model="editing.borrowDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="应还日期" prop="dueDate">
          <el-date-picker v-model="editing.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editing.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageBorrowRecords, createBorrowRecord, updateBorrowRecord,
  deleteBorrowRecord, returnBorrowRecord
} from '../../api/borrowRecord'
import { pageBooks } from '../../api/book'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
const bookOptions = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dateRange = ref([])

const query = reactive({ bookName: '', borrowerName: '', borrowerNo: '', status: null })
const editing = reactive({
  id: null, bookId: null, borrowerName: '', borrowerNo: '',
  borrowDate: '', dueDate: '', remark: ''
})
const rules = {
  bookId: [{ required: true, message: '请选择借阅图书', trigger: 'change' }],
  borrowerName: [{ required: true, message: '请输入借阅人姓名', trigger: 'blur' }],
  borrowerNo: [{ required: true, message: '请输入借阅证号/学号', trigger: 'blur' }],
  borrowDate: [{ required: true, message: '请选择借阅日期', trigger: 'change' }],
  dueDate: [{ required: true, message: '请选择应还日期', trigger: 'change' }]
}

const beginDate = computed(() => (dateRange.value && dateRange.value.length === 2 ? dateRange.value[0] : undefined))
const endDate = computed(() => (dateRange.value && dateRange.value.length === 2 ? dateRange.value[1] : undefined))

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      bookName: query.bookName || undefined,
      borrowerName: query.borrowerName || undefined,
      borrowerNo: query.borrowerNo || undefined,
      status: query.status,
      beginDate: beginDate.value,
      endDate: endDate.value
    }
    const data = await pageBorrowRecords(params)
    tableData.value = data.list
    total.value = Number(data.total)
  } finally {
    loading.value = false
  }
}

async function loadBookOptions() {
  // 借阅对话框的图书下拉：取在架图书前 200 条
  const data = await pageBooks({ pageNum: 1, pageSize: 200, status: 1 })
  bookOptions.value = data.list
}

function onSearch() {
  pageNum.value = 1
  loadData()
}

function onReset() {
  Object.assign(query, { bookName: '', borrowerName: '', borrowerNo: '', status: null })
  dateRange.value = []
  pageNum.value = 1
  loadData()
}

function openDialog(row) {
  if (row) {
    Object.assign(editing, {
      id: row.id, bookId: row.bookId, borrowerName: row.borrowerName,
      borrowerNo: row.borrowerNo, borrowDate: row.borrowDate, dueDate: row.dueDate,
      remark: row.remark
    })
  } else {
    Object.assign(editing, {
      id: null, bookId: null, borrowerName: '', borrowerNo: '',
      borrowDate: today(), dueDate: defaultDueDate(), remark: ''
    })
  }
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function today() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function defaultDueDate() {
  // 默认 30 天后应还
  const d = new Date()
  d.setDate(d.getDate() + 30)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (editing.id) {
      await updateBorrowRecord(editing.id, editing)
      ElMessage.success('修改成功')
    } else {
      await createBorrowRecord(editing)
      ElMessage.success('借阅登记成功')
    }
    dialogVisible.value = false
    await loadData()
    await loadBookOptions()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row) {
  await deleteBorrowRecord(row.id)
  ElMessage.success('删除成功')
  await loadData()
  await loadBookOptions()
}

async function onReturn(row) {
  try {
    await ElMessageBox.confirm(`确认归还《${row.bookName}》（借阅人：${row.borrowerName}）？`, '归还确认', { type: 'warning' })
  } catch {
    return
  }
  await returnBorrowRecord(row.id)
  ElMessage.success('归还成功')
  await loadData()
  await loadBookOptions()
}

onMounted(async () => {
  await loadBookOptions()
  await loadData()
})
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
.toolbar {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.dialog-form {
  margin-top: 12px;
}
</style>
