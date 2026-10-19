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
      <el-form-item label="采购状态">
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
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      <el-tooltip
        content="采购单按流程节点链流转：草稿 → 提交申请 → 审批通过（下发 ERP 超级节点）→ 等待 ERP 回调 → 完成入库。超级节点绑定关系来自后台「集成业务配置」。"
        placement="right"
      >
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
      <el-table-column label="流程节点" width="170" align="center">
        <template #default="{ row }">
          <el-tag :type="nodeTag(row)" size="small">{{ nodeLabel(row) }}</el-tag>
          <div class="sub-text">{{ subStatusText(row) }}</div>
          <el-tooltip v-if="rejectReason(row)" :content="rejectReason(row)" placement="top">
            <div class="sub-text reject-text">驳回：{{ rejectReason(row) }}</div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="ERP 表单" width="130" align="center">
        <template #default="{ row }">
          <template v-if="erpFormId(row)">
            <el-tag type="success" size="small">#{{ erpFormId(row) }}</el-tag>
            <div class="sub-text">{{ erpFormTip(row) }}</div>
          </template>
          <span v-else class="sub-text">尚未发送</span>
        </template>
      </el-table-column>
      <el-table-column prop="stockInRemark" label="入库结果" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="320" align="center" fixed="right">
        <template #default="{ row }">
          <!-- 草稿：提交申请（超级节点由集成业务配置决定，不再选应用） -->
          <el-button v-if="flowActions(row).submitApply" v-permission="'purchase:manage'" type="success" text :icon="Upload" @click="openSubmit(row)">提交申请</el-button>
          <!-- 提交申请节点（进行中）：审批通过并下发 ERP / 驳回 -->
          <el-button v-if="flowActions(row).approve" v-permission="'purchase:manage'" type="primary" text :icon="CircleCheck" @click="openFlowDialog(row, 'approve')">审批通过（下发ERP）</el-button>
          <el-button v-if="flowActions(row).reject" v-permission="'purchase:manage'" type="danger" text :icon="CloseBold" @click="openFlowDialog(row, 'reject')">驳回</el-button>
          <!-- 已打回：重新提交 -->
          <el-popconfirm v-if="flowActions(row).resubmit" title="确认重新提交该采购单？" confirm-button-text="重新提交" cancel-button-text="返回" @confirm="onResubmit(row)">
            <template #reference>
              <el-button v-permission="'purchase:manage'" type="success" text :icon="RefreshRight">重新提交</el-button>
            </template>
          </el-popconfirm>
          <!-- 超级节点：只读等待，不提供审批按钮 -->
          <span v-if="flowActions(row).waiting" class="wait-text">等待 ERP 处理</span>
          <!-- 入库：仅「流程已完成但采购单未入库」的补偿场景，见 canStockIn -->
          <el-button v-if="canStockIn(row)" v-permission="'purchase:manage'" type="warning" text :icon="Box" @click="onStockIn(row)">入库</el-button>
          <el-button v-if="row.status === 0" v-permission="'purchase:manage'" type="primary" text :icon="Edit" @click="openDialog(row)">编辑</el-button>
          <!-- 无流程实例的旧数据没有轨迹可看，隐藏入口 -->
          <el-button v-if="hasFlow(row)" text :icon="List" @click="openFlowDrawer(row)">查看流程</el-button>
          <el-popconfirm v-if="canCancel(row)" title="确认取消该采购单？" confirm-button-text="取消单据" cancel-button-text="返回" @confirm="onCancel(row)">
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

    <!-- 提交申请：超级节点来自集成业务配置，用户不再选择应用 -->
    <el-dialog v-model="submitVisible" title="提交采购申请" width="560px">
      <el-form label-width="110px">
        <el-form-item label="采购单号">
          <span>{{ submittingRow?.purchaseNo }}</span>
        </el-form-item>
        <el-form-item label="超级节点">
          <div v-loading="submitBizLoading" class="biz-box">
            <template v-if="submitBizReady">
              <div>
                将按集成业务配置（{{ BIZ_TYPE }}）下发到超级节点：<strong>{{ submitBiz.superNodeName }}</strong>
              </div>
              <div class="sub-text">业务名称：{{ submitBiz.bizName || '-' }}</div>
            </template>
            <template v-else-if="submitBizDenied">
              <div>当前身份无权查看集成业务配置，将按 <strong>{{ BIZ_TYPE }}</strong> 提交。</div>
              <div class="sub-text">超级节点由后端按配置解析并校验，未配置时会在提交时报错。</div>
            </template>
            <span v-else class="warn-text">请先在后台配置「集成业务」</span>
          </div>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="提交后进入「提交申请」节点；审批通过时按该配置下发 ERP 并挂起等待回调。"
      />
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!submitReady" @click="onSubmitApply">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 审批通过 / 驳回：收集流程意见 -->
    <el-dialog v-model="flowDialog.visible" :title="flowDialog.mode === 'approve' ? '审批通过并下发 ERP' : '驳回采购申请'" width="540px">
      <el-form label-width="90px">
        <el-form-item label="采购单号">
          <span>{{ flowDialog.row?.purchaseNo }}</span>
        </el-form-item>
        <el-form-item label="当前节点">
          <el-tag size="small" :type="nodeTag(flowDialog.row)">{{ nodeLabel(flowDialog.row) }}</el-tag>
        </el-form-item>
        <el-form-item :label="flowDialog.mode === 'approve' ? '审批意见' : '驳回原因'">
          <el-input
            v-model="flowDialog.comment"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            :placeholder="flowDialog.mode === 'approve' ? '可留空' : '必填，会记入流程轨迹并展示给申请人'"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="flowDialog.mode === 'approve'"
        type="warning"
        :closable="false"
        show-icon
        title="通过后将按集成业务配置下发到超级节点（ERP），并挂起等待对方回调。"
      />
      <template #footer>
        <el-button @click="flowDialog.visible = false">取消</el-button>
        <el-button :type="flowDialog.mode === 'approve' ? 'primary' : 'danger'" :loading="submitting" @click="onFlowConfirm">
          {{ flowDialog.mode === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 流程轨迹 -->
    <el-drawer v-model="drawerVisible" title="流程轨迹" size="600px">
      <div v-loading="drawerLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="采购单号">{{ drawerRow?.purchaseNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">
            <el-tag size="small" :type="nodeTag(drawerRow)">{{ nodeLabel(drawerRow) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ drawerData.flow?.bizType || drawerData.biz?.bizType || BIZ_TYPE }}</el-descriptions-item>
          <el-descriptions-item label="超级节点">{{ drawerData.biz?.superNodeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="ERP 表单号">{{ drawerData.flow?.erpFormId ? '#' + drawerData.flow.erpFormId : '尚未发送' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ drawerData.flow?.applicantName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ fmtTime(drawerData.flow?.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ fmtTime(drawerData.flow?.approveTime) }}</el-descriptions-item>
          <el-descriptions-item label="下发 ERP 时间">{{ fmtTime(drawerData.flow?.erpSubmitTime) }}</el-descriptions-item>
          <el-descriptions-item label="ERP 回调时间">{{ fmtTime(drawerData.flow?.erpCallbackTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ fmtTime(drawerData.flow?.finishTime) }}</el-descriptions-item>
          <el-descriptions-item label="驳回原因">{{ drawerData.flow?.rejectReason || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">流转日志</el-divider>
        <el-table :data="drawerData.logs" border size="small">
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="动作" width="140">
            <template #default="{ row }">{{ ACTION_TEXT[row.action] || row.action || '-' }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="100" show-overflow-tooltip />
          <el-table-column prop="comment" label="意见" min-width="140" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!drawerLoading && !drawerData.logs.length" description="暂无流转日志" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Search, Refresh, Plus, Edit, Delete, Upload, Box, CircleClose, CircleCheck,
  CloseBold, RefreshRight, List, QuestionFilled
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pagePurchases, createPurchase, updatePurchase, deletePurchase,
  stockInPurchase, cancelPurchase, submitApply, approveFlow, rejectFlow,
  resubmitFlow, getPurchaseFlow
} from '../../api/purchase'
import { pageBizs } from '../../api/biz'
import { listCategories } from '../../api/category'

/** 采购单绑定的业务类型（与后端集成业务配置一致） */
const BIZ_TYPE = 'BOOK_PURCHASE'

// 采购单自身状态（次要信息；老模型 status=3 按新模型改称「ERP已确认」）
const STATUS_TEXT = { 0: '草稿', 1: '已提交', 2: '已入库', 3: 'ERP已确认', 9: '已取消' }
// 流程节点中文名（节点与状态由分页接口按行返回：currentNode / nodeStatus）
const NODE_TEXT = { DRAFT: '草稿', SUBMIT: '提交申请', APPROVE: '审批中', SUPER: '等待ERP', DONE: '已完成', REJECTED: '已打回', CANCELED: '已取消' }
const NODE_TAG = { DRAFT: 'info', SUBMIT: 'primary', APPROVE: 'primary', SUPER: 'warning', DONE: 'success', REJECTED: 'danger', CANCELED: 'info' }
/** 流程节点状态：0 进行中 / 1 已同意 / 2 已打回 / 3 已取消 */
const NODE_STATUS_CANCELED = 3
const NODE_STATUS_REJECTED = 2
const NODE_STATUS_AGREED = 1
// 流程日志动作中文名
const ACTION_TEXT = {
  SUBMIT_APPLY: '提交申请',
  APPROVE_SEND_ERP: '审批通过并下发ERP',
  ERP_AGREE: 'ERP同意',
  ERP_REJECT: 'ERP驳回',
  RESUBMIT: '重新提交',
  CANCEL: '取消'
}

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const tableData = ref([])
const categories = ref([])
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

// 提交申请弹窗
const submittingRow = ref(null)
const submitVisible = ref(false)
const submitBiz = ref(null)
const submitBizLoading = ref(false)
/** 集成业务配置接口 403：无权读配置，但允许提交，由后端按配置校验兜底 */
const submitBizDenied = ref(false)
const submitBizReady = computed(() => !!(submitBiz.value && submitBiz.value.superNodeName))
const submitReady = computed(() => submitBizReady.value || submitBizDenied.value)

// 审批/驳回弹窗
const flowDialog = reactive({ visible: false, row: null, mode: 'approve', comment: '' })

// 流程轨迹抽屉
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerRow = ref(null)
const drawerData = reactive({ flow: null, logs: [], biz: null })

const totalPrice = computed(() => {
  const q = Number(editing.quantity) || 0
  const p = Number(editing.unitPrice) || 0
  return (q * p).toFixed(2)
})

/** 行内是否带流程实例：分页接口直接按行返回 currentNode / nodeStatus，无实例为 null */
function hasFlow(row) {
  return !!row?.currentNode
}

function nodeLabel(row) {
  if (hasFlow(row)) {
    if (row.nodeStatus === NODE_STATUS_CANCELED) return NODE_TEXT.CANCELED
    return NODE_TEXT[row.currentNode] || row.currentNode || '-'
  }
  if (row?.status === 0) return '草稿'
  if (row?.status === 9) return '已取消'
  return '无流程数据'
}

function nodeTag(row) {
  if (!hasFlow(row)) return 'info'
  if (row.nodeStatus === NODE_STATUS_CANCELED) return NODE_TAG.CANCELED
  return NODE_TAG[row.currentNode] || 'info'
}

/** 次要信息：采购单自身状态 */
function subStatusText(row) {
  return `采购状态：${STATUS_TEXT[row?.status] || '-'}`
}

/** 驳回原因：分页行带 rejectReason 时直接展示，不再按行补拉流程详情 */
function rejectReason(row) {
  return row?.rejectReason || ''
}

/** ERP 表单号：优先取流程回填的 erpFormId，老数据回退旧字段 integrationFormId */
function erpFormId(row) {
  return row?.erpFormId || row?.integrationFormId || null
}

function erpFormTip(row) {
  return row?.erpCallbackTime ? `回调 ${fmtTime(row.erpCallbackTime)}` : '已发送待回调'
}

/**
 * 单行可用流程按钮（互斥，同一行只会命中一类），节点取自行数据 currentNode/nodeStatus：
 * - 无流程实例：仅草稿（status=0）给「提交申请」，其余旧数据不给流程按钮
 * - 节点状态 3 已取消 / 1 已同意 / 节点 DONE → 无流程按钮
 * - 节点状态 2 或节点 REJECTED → 重新提交
 * - SUBMIT / APPROVE 且进行中 → 审批通过（下发ERP）+ 驳回
 * - SUPER → 只读「等待 ERP 处理」
 */
function flowActions(row) {
  if (!hasFlow(row)) return { submitApply: row?.status === 0 }
  if (row.nodeStatus === NODE_STATUS_CANCELED || row.currentNode === 'CANCELED') return {}
  if (row.nodeStatus === NODE_STATUS_AGREED || row.currentNode === 'DONE') return {}
  if (row.nodeStatus === NODE_STATUS_REJECTED || row.currentNode === 'REJECTED') return { resubmit: true }
  switch (row.currentNode) {
    case 'DRAFT':
      return { submitApply: true }
    case 'SUBMIT':
    case 'APPROVE':
      return { approve: true, reject: true }
    case 'SUPER':
      return { waiting: true }
    default:
      return {}
  }
}

/**
 * 入库按钮（补偿场景）：后端 stock-in 要求存在流程实例且流程已 DONE，
 * 因此只在「有流程实例 + currentNode=DONE + 采购单 status=1（已提交未入库）」时显示，
 * 无流程实例的行一律不显示。
 */
function canStockIn(row) {
  return hasFlow(row) && row.currentNode === 'DONE' && row.status === 1
}

/** 取消入口：已进入超级节点（等待 ERP）或已完成的单据不再提供取消，避免与流程冲突 */
function canCancel(row) {
  if (row?.currentNode === 'SUPER' || row?.currentNode === 'DONE') return false
  return row?.status === 0 || row?.status === 1
}

function fmtTime(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

/**
 * 分页接口已按行返回 currentNode / nodeStatus，页面不再逐行补拉 /flow（避免 N+1）。
 */
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
      ElMessage.success('新增成功，可在列表中提交申请')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

/**
 * 打开提交申请弹窗：超级节点名直接查后台「集成业务」配置（权限 group:app），
 * 不再先拉流程接口。查不到配置则提示并要求先配置；
 * 若该接口对当前身份 403，兜底按 BIZ_TYPE 原样提交并允许确认，由后端校验。
 */
async function openSubmit(row) {
  submittingRow.value = row
  submitBiz.value = null
  submitBizDenied.value = false
  submitVisible.value = true
  submitBizLoading.value = true
  try {
    const page = await pageBizs({ bizType: BIZ_TYPE, enabled: 1, pageNum: 1, pageSize: 1 })
    submitBiz.value = (page?.list && page.list[0]) || null
  } catch (error) {
    submitBiz.value = null
    submitBizDenied.value = error?.response?.status === 403 || /403|权限/.test(error?.message || '')
  } finally {
    submitBizLoading.value = false
  }
}

async function onSubmitApply() {
  submitting.value = true
  try {
    await submitApply(submittingRow.value.id)
    ElMessage.success('已提交申请，等待审批')
    submitVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

function openFlowDialog(row, mode) {
  flowDialog.row = row
  flowDialog.mode = mode
  flowDialog.comment = ''
  flowDialog.visible = true
}

async function onFlowConfirm() {
  const comment = (flowDialog.comment || '').trim()
  if (flowDialog.mode === 'reject' && !comment) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  submitting.value = true
  try {
    if (flowDialog.mode === 'approve') {
      await approveFlow(flowDialog.row.id, comment)
      ElMessage.success('已审批通过，正在下发 ERP')
    } else {
      await rejectFlow(flowDialog.row.id, comment)
      ElMessage.success('已驳回')
    }
    flowDialog.visible = false
    await loadData()
  } finally {
    submitting.value = false
  }
}

async function onResubmit(row) {
  await resubmitFlow(row.id)
  ElMessage.success('已重新提交')
  await loadData()
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

/** 查看流程：用户主动点击才请求 /flow（拿 logs 与时间点，非 N+1） */
async function openFlowDrawer(row) {
  drawerRow.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  try {
    const data = await getPurchaseFlow(row.id)
    drawerData.flow = data?.flow || null
    drawerData.logs = data?.logs || []
    drawerData.biz = data?.biz || null
  } catch {
    drawerData.flow = null
    drawerData.logs = []
    drawerData.biz = null
  } finally {
    drawerLoading.value = false
  }
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
  display: flex;
  align-items: center;
  gap: 6px;
}
.tip-icon {
  color: #909399;
  cursor: help;
}
.sub-text {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.reject-text {
  color: #f56c6c;
}
.warn-text {
  color: #e6a23c;
  font-size: 13px;
}
.wait-text {
  color: #e6a23c;
  font-size: 13px;
}
.biz-box {
  min-height: 24px;
  line-height: 1.6;
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
