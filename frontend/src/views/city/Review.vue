<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>数据审核</span>
      </div>
    </template>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="待审核" name="pending" />
      <el-tab-pane label="已通过待上报省级" name="approved" />
      <el-tab-pane label="已退回" name="returned" />
      <el-tab-pane label="已上报省级" name="submitted" />
    </el-tabs>

    <el-form inline>
      <el-form-item label="调查期">
        <el-select v-model="filter.periodId" placeholder="全部" clearable filterable style="width:200px" @change="refresh">
          <el-option v-for="p in periods" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input v-model="filter.keyword" placeholder="企业名 / 组织机构代码" clearable style="width:240px" @keyup.enter="refresh" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="refresh">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="batch-bar" v-if="canBatch">
      <span>已选 <b>{{ selectedIds.length }}</b> 条</span>
      <el-button v-if="activeTab === 'pending'" type="success" :disabled="!selectedIds.length" @click="onBatchApprove">批量审核通过</el-button>
      <el-button v-if="activeTab === 'approved'" type="primary" :disabled="!selectedIds.length" @click="onSubmitProvince">批量上报省级</el-button>
    </div>

    <el-table :data="rows" border stripe @selection-change="onSelectionChange" ref="tableRef">
      <el-table-column v-if="canBatch" type="selection" width="48" />
      <el-table-column prop="enterpriseName" label="企业名称" min-width="180" />
      <el-table-column prop="orgCode" label="组织机构代码" width="130" />
      <el-table-column prop="periodName" label="调查期" width="150" />
      <el-table-column label="周期" width="80">
        <template #default="{row}">
          <el-tag size="small" :type="row.periodType === 'HALF_MONTH' ? 'warning' : ''">
            {{ row.periodType === 'HALF_MONTH' ? '半月' : '月' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="baseCount" label="建档期" width="80" align="right" />
      <el-table-column prop="currentCount" label="调查期" width="80" align="right" />
      <el-table-column label="变化" width="80" align="right">
        <template #default="{row}">
          <span v-if="row.baseCount != null && row.currentCount != null"
                :style="{color: delta(row) > 0 ? '#67c23a' : delta(row) < 0 ? '#f56c6c' : '#999'}">
            {{ delta(row) > 0 ? '+' : '' }}{{ delta(row) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{row}">
          <el-tag :type="REPORT_STATUS[row.status]?.tag || 'info'">{{ REPORT_STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="上报时间" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <template v-if="row.status === '01'">
            <el-button link type="success" @click="approve(row)">通过</el-button>
            <el-button link type="danger" @click="openReturn(row)">退回</el-button>
          </template>
          <template v-if="row.status === '02'">
            <el-button link type="primary" @click="submitOne(row)">上报省级</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- detail dialog -->
    <el-dialog v-model="detailVisible" title="报表详情" width="640px">
      <div v-if="detail" class="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="企业">{{ detail.enterpriseName }}</el-descriptions-item>
          <el-descriptions-item label="组织机构代码">{{ detail.orgCode }}</el-descriptions-item>
          <el-descriptions-item label="所属地区">{{ detail.regionName }}</el-descriptions-item>
          <el-descriptions-item label="调查期">{{ detail.periodName }}</el-descriptions-item>
          <el-descriptions-item label="建档期就业人数">{{ detail.baseCount }}</el-descriptions-item>
          <el-descriptions-item label="调查期就业人数">{{ detail.currentCount }}</el-descriptions-item>
          <el-descriptions-item label="减少类型">{{ dictStore.label('DECREASE_TYPE', detail.decreaseType) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="主要原因">{{ dictStore.label('DECREASE_REASON', detail.primaryReason) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="REPORT_STATUS[detail.status]?.tag">{{ REPORT_STATUS[detail.status]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="上报时间">{{ detail.submittedAt }}</el-descriptions-item>
          <el-descriptions-item label="审核人" :span="2">{{ detail.cityReviewer || '—' }} {{ detail.cityReviewAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.cityReturnReason" label="退回原因" :span="2">
            <span style="color:#f56c6c">{{ detail.cityReturnReason }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- return dialog -->
    <el-dialog v-model="returnVisible" title="退回修改" width="480px">
      <el-form :model="returnForm">
        <el-form-item label="退回原因" required>
          <el-input v-model="returnForm.reason" type="textarea" :rows="3" placeholder="请填写退回原因（必填）" maxlength="200" show-word-limit />
        </el-form-item>
        <div class="quick-reasons">
          <span style="color:#999; font-size:12px">常用原因：</span>
          <el-tag v-for="t in quickReasons" :key="t" size="small" style="cursor:pointer; margin:2px"
                  @click="returnForm.reason = t">{{ t }}</el-tag>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReturn">确认退回</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPeriods } from '@/api/period'
import { listCityReviews, getCityReview, cityApprove, cityReturn, cityBatchApprove, citySubmitToProvince } from '@/api/cityReview'
import { REPORT_STATUS } from '@/api/report'
import { useDictStore } from '@/stores/dict'

const route = useRoute()
const dictStore = useDictStore()
const loading = ref(false)
const activeTab = ref(route.query.tab || 'pending')
const periods = ref([])
const rows = ref([])
const selectedIds = ref([])
const filter = reactive({ periodId: null, keyword: '' })

const detailVisible = ref(false)
const detail = ref(null)
const returnVisible = ref(false)
const returnForm = reactive({ id: null, reason: '' })
const quickReasons = [
  '调查期就业人数填写有误，请核实后重新上报',
  '建档期就业人数与备案信息不一致，请核对',
  '就业人数减少原因未填写完整，请补充',
  '数据逻辑异常，请检查',
  '必填项缺失，请补充完整',
]

const canBatch = computed(() => activeTab.value === 'pending' || activeTab.value === 'approved')

const TAB_STATUS = { pending: '01', approved: '02', returned: '03', submitted: '04' }

onMounted(async () => {
  await dictStore.load(['DECREASE_TYPE', 'DECREASE_REASON'])
  const r = await listPeriods()
  periods.value = r.data || []
  await refresh()
})

function onTabChange() {
  selectedIds.value = []
  refresh()
}

async function refresh() {
  loading.value = true
  try {
    const params = {
      status: TAB_STATUS[activeTab.value],
      periodId: filter.periodId || undefined,
      keyword: filter.keyword || undefined,
    }
    // For "submitted" tab show 04+ — backend already supports specific status only;
    // we issue separate calls for 04/05/06/07 and merge if needed.
    if (activeTab.value === 'submitted') {
      const all = []
      for (const s of ['04', '05', '06', '07']) {
        const r = await listCityReviews({ ...params, status: s })
        all.push(...(r.data || []))
      }
      rows.value = all.sort((a, b) => (b.submittedAt || '').localeCompare(a.submittedAt || ''))
    } else {
      const r = await listCityReviews(params)
      rows.value = r.data || []
    }
  } finally { loading.value = false }
}

function reset() {
  filter.periodId = null
  filter.keyword = ''
  refresh()
}

function delta(row) { return (row.currentCount ?? 0) - (row.baseCount ?? 0) }

function onSelectionChange(rs) { selectedIds.value = rs.map(r => r.id) }

async function openDetail(row) {
  detailVisible.value = true
  const r = await getCityReview(row.id)
  detail.value = r.data
}

async function approve(row) {
  await ElMessageBox.confirm(`确认通过 [${row.enterpriseName}] 的 ${row.periodName}？`, '提示', { type: 'warning' })
  await cityApprove(row.id)
  ElMessage.success('已通过')
  refresh()
}

function openReturn(row) {
  returnForm.id = row.id
  returnForm.reason = ''
  returnVisible.value = true
}

async function confirmReturn() {
  if (!returnForm.reason.trim()) { ElMessage.warning('请填写退回原因'); return }
  await cityReturn(returnForm.id, returnForm.reason.trim())
  ElMessage.success('已退回，企业将看到退回原因')
  returnVisible.value = false
  refresh()
}

async function onBatchApprove() {
  await ElMessageBox.confirm(`将批量通过 ${selectedIds.value.length} 条记录？`, '提示', { type: 'warning' })
  const r = await cityBatchApprove(selectedIds.value)
  showBatchResult('审核', r.data)
  refresh()
}

async function submitOne(row) {
  await ElMessageBox.confirm(`确认将 [${row.periodName}] 上报至省级？`, '提示', { type: 'warning' })
  const r = await citySubmitToProvince([row.id])
  showBatchResult('上报省级', r.data)
  refresh()
}

async function onSubmitProvince() {
  await ElMessageBox.confirm(`将 ${selectedIds.value.length} 条数据上报至省级？`, '提示', { type: 'warning' })
  const r = await citySubmitToProvince(selectedIds.value)
  showBatchResult('上报省级', r.data)
  refresh()
}

function showBatchResult(action, data) {
  if (data.failed === 0) {
    ElMessage.success(`${action}成功 ${data.success} 条`)
  } else {
    ElMessage.warning(`${action}：成功 ${data.success}/${data.total}，失败 ${data.failed}`)
  }
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.batch-bar { padding: 8px 12px; background: #f6f7fa; border-radius: 4px; margin-bottom: 12px; display: flex; align-items: center; gap: 12px; }
.quick-reasons { margin-top: 8px; }
.detail :deep(.el-descriptions__label) { width: 130px; }
</style>
