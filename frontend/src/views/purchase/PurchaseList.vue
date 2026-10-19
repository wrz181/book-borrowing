<template>
  <div class="page-card">
    <!-- 动态条件查询区：各条件可单独使用，也可组合使用 -->
    <el-form :inline="true" :model="query" class="query-form">
      <el-form-item label="采购单号">
        <el-input v-model="query.purchaseNo" placeholder="支持模糊查询" clearable style="width: 170px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="图书名称">
        <el-input v-model="query.bookName" placeholder="支持模糊查询" clearable style="width: 170px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="所属分类">
        <el-select v-model="query.categoryId" placeholder="全部" clearable style="width: 150px">
          <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="(text, value) in STATUS_TEXT" :key="value" :label="text" :value="Number(value)" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button v-permission="'purchase:manage'" type="primary" :icon="Plus" @click="openDialog()">新增采购单</el-button>
      <el-tooltip content="采购单提交后可下发到外部集成应用，对方回执后即可入库；入库会按 ISBN/名称匹配已有图书增加库存，匹配不到则新增图书。" placement="right">
        <el-icon class="tip-icon"><QuestionFilled /></el-icon>
      </el-tooltip>
    </div>

    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="purchaseNo" label="采购单号" width="185" show-overflow-tooltip />
      <el-table-column prop="bookName" label="图书名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="所属分类" width="110" />
      <el-table-column prop="quantity" label="数量" width="70" align="center" />
      <el-table-column prop="unitPrice" label="单价" width="90" align="right">
        <template #default="{ row }">¥{{ row.unitPrice }}</template>
      </el-table-column>
      <el-table-column prop="totalPrice" label="总价" width="100" align="right">
        <template #default="{ row }">¥{{ row.totalPrice }}</template>
      </el-table-column>
      <el-table-column prop="applicantName" label="申请人" width="100" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.status] || 'info'" size="small">{{ STATUS_TEXT[row.status] || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="外平台回执" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="!row.integrationFormId" type="info" size="small">未下发</el-tag>
          <el-tag v-else :type="row.formStatus === 1 ? 'success' : 'warning'" size="small">
            {{ row.formStatus === 1 ? '已回执' : '待回执' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stockInRemark" label="入库结果" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="250" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" v-permission="'purchase:manage'" type="success" text :icon="Upload" @click="openSubmit(row)">提交</el-button>
          <el-button v-if="row.status === 1" v-permission="'purchase:manage'" type="warning" text :icon="Box" :disabled="row.integrationFormId && row.formStatus !== 1" @click="onStockIn(row)">入库</el-button>
          <el-button v-if="row.status === 0" v-permission="'purchase:manage'" type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="row.status === 0 || row.status === 1" title="确认取消该采购单？" confirm-button-text="取消单据" cancel-button-text="返回" @confirm="onCancel(row)">
            <template #reference>
              <el-button v-permission="'purchase:manage'" type="info" text :icon="CircleClose">取消</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-if="row.status === 0 || row.status === 9" title="确认删除该采购单？" confirm-button-text="删除" cancel-button-text="取消" @confirm="onDelete(row)">
            <template #reference>
              <el-button v-permission="'purchase:manage'" type="danger" text :icon="Delete">删除</el-button>
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

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing.id ? '编辑采购单' : '新增采购单'" width="620px">
      <el-form ref="formRef" :model="editing" :rules="rules" label-width="100px">
        <el-form-item label="图书名称" prop="bookName">
          <el-input v-model="editing.bookName" maxlength="100" placeholder="如：数据结构与算法分析" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="editing.author" maxlength="50" />
        </el-form-item>
        <el-form-item label="ISBN">
          <el-input v-model="editing.isbn" maxlength="20" placeholder="入库时优先按 ISBN 匹配已有图书" />
        </el-form-item>
        <el-form-item label="出版社">
          <el-input v-model="editing.publisher" maxlength="50" />
        </el-form-item>
        <el-form-item label="所属分类" prop="categoryId">
          <el-select v-model="editing.categoryId" placeholder="入库时归入该分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="采购数量" prop="quantity">
          <el-input-number v-model="editing.quantity" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="editing.unitPrice" :min="0" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="总价">
          <span class="total">¥{{ totalPrice }}</span>
        </el-form-item>
        <el-form-item label="期望到货">
          <el-date-picker v-model="editing.expectedDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
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

    <!-- 提交（下发外部集成应用） -->
    <el-dialog v-model="submitVisible" title="提交采购单" width="520px">
      <el-form label-width="110px">
        <el-form-item label="采购单号">
          <span>{{ submittingRow?.purchaseNo }}</span>
        </el-form-item>
        <el-form-item label="下发外部平台">
          <el-radio-group v-model="submitAppId">
            <el-radio :value="0">不下发</el-radio>
            <el-radio v-for="app in apps" :key="app.id" :value="app.id">{{ app.appName }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="submitAppId"
        type="info"
        :closable="false"
        show-icon
        title="提交后将向该应用下发集成表单，需对方处理回执后方可入库。"
      />
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmitSubmit">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus, Edit, Delete, Upload, Box, CircleClose, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pagePurchases, createPurchase, updatePurchase, deletePurchase,
  submitPurchase, stockInPurchase, cancelPurchase
} from '../../api/purchase'
import { listCategories } from '../../api/category'
import { listSsoApps } from '../../api/sso'

const STATUS_TEXT = { 0: '草稿', 1: '已提交', 2: '已入库', 9: '已取消' }
const STATUS_TAG = { 0: 'info', 1: 'warning', 2: 'success', 9: 'danger' }

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const submitVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
const categories = ref([])
const apps = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const query = reactive({ purchaseNo: '', bookName: '', categoryId: null, status: null })
const editing = reactive({
  id: null, bookName: '', author: '', isbn: '', publisher: '',
  categoryId: null, quantity: 1, unitPrice: 0, expectedDate: null, remark: ''
})
const rules = {
  bookName: [{ required: true, message: '请输入图书名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入采购数量', trigger: 'blur' }]
}

// 提交弹窗
const submittingRow = ref(null)
const submitAppId = ref(null)

const totalPrice = computed(() => {
  const q = Number(editing.quantity) || 0
  const p = Number(editing.unitPrice) || 0
  return (q * p).toFixed(2)
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      purchaseNo: query.purchaseNo || undefined,
      bookName: query.bookName || undefined,
      categoryId: query.categoryId || undefined,
      status: query.status
    }
    const data = await pagePurchases(params)
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
  Object.assign(query, { purchaseNo: '', bookName: '', categoryId: null, status: null })
  pageNum.value = 1
  loadData()
}

function openDialog(row) {
  if (row) {
    Object.assign(editing, {
      id: row.id, bookName: row.bookName, author: row.author, isbn: row.isbn,
      publisher: row.publisher, categoryId: row.categoryId, quantity: row.quantity,
      unitPrice: Number(row.unitPrice), expectedDate: row.expectedDate, remark: row.remark
    })
  } else {
    Object.assign(editing, {
      id: null, bookName: '', author: '', isbn: '', publisher: '',
      categoryId: null, quantity: 1, unitPrice: 0, expectedDate: null, remark: ''
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
      await updatePurchase(editing.id, editing)
      ElMessage.success('修改成功')
    } else {
      await createPurchase(editing)
      ElMessage.success('新增成功，可在列表中提交')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

function openSubmit(row) {
  submittingRow.value = row
  submitAppId.value = apps.value.length ? apps.value[0].id : 0
  submitVisible.value = true
}

async function onSubmitSubmit() {
  submitting.value = true
  try {
    const appId = submitAppId.value || null
    await submitPurchase(submittingRow.value.id, appId)
    ElMessage.success(appId ? '已提交并下发外部平台' : '已提交')
    submitVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function onStockIn(row) {
  let remark = ''
  try {
    const res = await ElMessageBox.prompt('入库说明（可留空）：入库将按 ISBN 优先匹配已有图书增加库存，未匹配则新增图书。', '采购入库', {
      confirmButtonText: '确认入库',
      cancelButtonText: '取消',
      inputPlaceholder: '如：验收合格入库',
      inputValue: ''
    })
    remark = res.value || ''
  } catch {
    return
  }
  await stockInPurchase(row.id, remark || undefined)
  ElMessage.success('入库成功，已更新图书库存')
  await loadData()
}

async function onCancel(row) {
  await cancelPurchase(row.id)
  ElMessage.success('已取消')
  await loadData()
}

async function onDelete(row) {
  await deletePurchase(row.id)
  ElMessage.success('删除成功')
  await loadData()
}

onMounted(async () => {
  categories.value = await listCategories()
  try {
    apps.value = await listSsoApps()
  } catch {
    apps.value = []
  }
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
  display: flex;
  align-items: center;
  gap: 6px;
}
.tip-icon {
  color: #909399;
  cursor: help;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.total {
  color: #f56c6c;
  font-weight: 600;
}
</style>
