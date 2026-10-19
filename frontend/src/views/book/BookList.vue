<template>
  <div class="page-card">
    <!-- 动态条件查询区：各条件可单独使用，也可组合使用 -->
    <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="图书名称">
        <el-input v-model="query.bookName" placeholder="支持模糊查询" clearable style="width: 180px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="作者">
        <el-input v-model="query.author" placeholder="支持模糊查询" clearable style="width: 160px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="图书分类">
        <el-select v-model="query.categoryId" placeholder="全部" clearable style="width: 160px">
          <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option label="在架可借" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button v-permission="'book:add'" type="primary" :icon="Plus" @click="openDialog()">新增图书</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="bookName" label="图书名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="author" label="作者" width="130" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="所属分类" width="110" />
      <el-table-column prop="publisher" label="出版社" width="150" show-overflow-tooltip />
      <el-table-column prop="price" label="单价" width="90" align="right">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="70" align="center" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '在架可借' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="warning" text :icon="View" @click="router.push(`/books/detail/${row.id}`)">详情/借阅记录</el-button>
          <el-button v-permission="'book:edit'" type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该图书？" confirm-button-text="删除" cancel-button-text="取消" @confirm="onDelete(row)">
            <template #reference>
              <el-button v-permission="'book:delete'" type="danger" text :icon="Delete">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑图书' : '新增图书'" width="560px">
      <el-form ref="formRef" :model="editing" :rules="rules" label-width="90px">
        <el-form-item label="图书名称" prop="bookName">
          <el-input v-model="editing.bookName" maxlength="100" />
        </el-form-item>
        <el-form-item label="作者" prop="author">
          <el-input v-model="editing.author" maxlength="50" />
        </el-form-item>
        <el-form-item label="所属分类" prop="categoryId">
          <el-select v-model="editing.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="ISBN">
          <el-input v-model="editing.isbn" maxlength="20" />
        </el-form-item>
        <el-form-item label="出版社">
          <el-input v-model="editing.publisher" maxlength="50" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="editing.price" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="editing.stock" :min="0" :max="99999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editing.status">
            <el-radio :value="1">在架可借</el-radio>
            <el-radio :value="0">已下架</el-radio>
          </el-radio-group>
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
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus, Edit, Delete, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pageBooks, createBook, updateBook, deleteBook } from '../../api/book'
import { listCategories } from '../../api/category'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
const categories = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const query = reactive({ bookName: '', author: '', categoryId: null, status: null })
const editing = reactive({
  id: null, bookName: '', author: '', isbn: '', publisher: '',
  categoryId: null, price: 0, stock: 0, status: 1
})
const rules = {
  bookName: [{ required: true, message: '请输入图书名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }]
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      bookName: query.bookName || undefined,
      author: query.author || undefined,
      categoryId: query.categoryId || undefined,
      status: query.status
    }
    const data = await pageBooks(params)
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

function onReset() {
  Object.assign(query, { bookName: '', author: '', categoryId: null, status: null })
  pageNum.value = 1
  loadData()
}

function openDialog(row) {
  if (row) {
    Object.assign(editing, {
      id: row.id, bookName: row.bookName, author: row.author, isbn: row.isbn,
      publisher: row.publisher, categoryId: row.categoryId, price: row.price,
      stock: row.stock, status: row.status
    })
  } else {
    Object.assign(editing, {
      id: null, bookName: '', author: '', isbn: '', publisher: '',
      categoryId: null, price: 0, stock: 0, status: 1
    })
  }
  dialogVisible.value = true
  formRef.value?.clearValidate()
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
      await updateBook(editing.id, editing)
      ElMessage.success('修改成功')
    } else {
      await createBook(editing)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row) {
  await deleteBook(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

onMounted(async () => {
  categories.value = await listCategories()
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
</style>
