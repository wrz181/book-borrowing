<template>
  <div class="page-card">
    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增分类</el-button>
      <el-radio-group v-model="sortBy" class="sort-group" @change="loadData">
        <el-radio-button value="default">默认顺序</el-radio-button>
        <el-radio-button value="name">按名称</el-radio-button>
        <el-radio-button value="updateTime">按修改时间</el-radio-button>
      </el-radio-group>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="categoryCode" label="分类编码" width="140" />
      <el-table-column prop="categoryName" label="分类名称" width="160" />
      <el-table-column prop="description" label="分类描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="修改时间" width="180" align="center">
        <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center">
        <template #default="{ row }">
          <el-button type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该分类？" confirm-button-text="删除" cancel-button-text="取消" @confirm="onDelete(row)">
            <template #reference>
              <el-button type="danger" text :icon="Delete">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑分类' : '新增分类'" width="460px">
      <el-form ref="formRef" :model="editing" :rules="rules" label-width="90px">
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="editing.categoryCode" maxlength="20" placeholder="如 T1005" />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="editing.categoryName" maxlength="50" placeholder="如 艺术" />
        </el-form-item>
        <el-form-item label="分类描述" prop="description">
          <el-input v-model="editing.description" type="textarea" :rows="3" maxlength="200" />
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
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listCategories, createCategory, updateCategory, deleteCategory } from '../../api/category'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
// default=创建顺序（新增排在最后） name=按名称 updateTime=按修改时间
const sortBy = ref('default')

const editing = reactive({ id: null, categoryCode: '', categoryName: '', description: '' })
const rules = {
  categoryCode: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 19)
}

async function loadData() {
  loading.value = true
  try {
    tableData.value = await listCategories(sortBy.value)
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(editing, {
      id: row.id,
      categoryCode: row.categoryCode,
      categoryName: row.categoryName,
      description: row.description
    })
  } else {
    Object.assign(editing, { id: null, categoryCode: '', categoryName: '', description: '' })
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
      await updateCategory(editing.id, editing)
      ElMessage.success('修改成功')
    } else {
      await createCategory(editing)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row) {
  await deleteCategory(row.id)
  ElMessage.success('删除成功')
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
.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sort-group {
  margin-left: auto;
}
</style>
