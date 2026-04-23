<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>报表管理</span>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
      </div>
    </template>

    <el-tabs v-model="filter.status" @tab-change="onTabChange">
      <el-tab-pane label="待省级审核" name="04" />
      <el-tab-pane label="省级已通过" name="05" />
      <el-tab-pane label="省级退回" name="06" />
      <el-tab-pane label="已上报国家" name="07" />
      <el-tab-pane label="全部" name="" />
    </el-tabs>

    <el-form inline>
      <el-form-item label="所属地区">
        <el-select v-model="filter.regionCode" clearable placeholder="全部" style="width:140px" @change="refresh">
          <el-option v-for="o in dictStore.get('REGION')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="调查期">
        <el-select v-model="filter.periodId" clearable filterable placeholder="全部" style="width:200px" @change="refresh">
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

    <div class="batch-bar" v-if="filter.status === '05'">
      <span>已选 <b>{{ selectedIds.length }}</b> 条</span>
      <el-button type="primary" :disabled="!selectedIds.length" @click="onSubmitNation">批量上报国家</el-button>
    </div>

    <el-table :data="rows" border stripe @selection-change="onSelectionChange">
      <el-table-column v-if="filter.status === '05'" type="selection" width="48" />
      <el-table-column prop="enterpriseName" label="企业" min-width="180" />
      <el-table-column prop="regionName" label="地区" width="100" />
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
          <span :style="{color: delta(row) > 0 ? '#67c23a' : delta(row) < 0 ? '#f56c6c' : '#999'}">
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
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openRevise(row)">修改</el-button>
          <template v-if="row.status === '04'">
            <el-button link type="success" @click="approve(row)">通过</el-button>
            <el-button link type="danger" @click="openReturn(row)">退回</el-button>
          </template>
          <template v-if="row.status === '05'">
            <el-button link type="primary" @click="submitOne(row)">上报国家</el-button>
          </template>
          <el-button link type="danger" :disabled="row.status === '07'" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- detail dialog with raw vs effective + revisions timeline -->
    <el-dialog v-model="detailVisible" title="报表详情" width="780px">
      <div v-if="detail">
        <el-tag v-if="detail.hasRevision" type="warning" effect="dark" style="margin-bottom:12px">
          已被修订 {{ detail.revisionCount }} 次（下方"原始 vs 当前有效"对照展示）
        </el-tag>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="企业">{{ enterpriseName }}</el-descriptions-item>
          <el-descriptions-item label="调查期">{{ periodName }}</el-descriptions-item>
          <el-descriptions-item label="原始 建档期">{{ detail.raw.baseCount }}</el-descriptions-item>
          <el-descriptions-item label="原始 调查期">{{ detail.raw.currentCount }}</el-descriptions-item>
          <el-descriptions-item label="有效 建档期">
            <b>{{ detail.effective.baseCount }}</b>
            <el-tag v-if="detail.effective.baseCount !== detail.raw.baseCount" size="small" type="warning" style="margin-left:6px">已改</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="有效 调查期">
            <b>{{ detail.effective.currentCount }}</b>
            <el-tag v-if="detail.effective.currentCount !== detail.raw.currentCount" size="small" type="warning" style="margin-left:6px">已改</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="减少类型">{{ dictStore.label('DECREASE_TYPE', detail.effective.decreaseType) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="主要原因">{{ dictStore.label('DECREASE_REASON', detail.effective.primaryReason) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="REPORT_STATUS[detail.raw.status]?.tag">{{ REPORT_STATUS[detail.raw.status]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="上报时间">{{ detail.raw.submittedAt }}</el-descriptions-item>
        </el-descriptions>

        <el-divider v-if="revisions.length">修改记录</el-divider>
        <el-timeline v-if="revisions.length">
          <el-timeline-item v-for="r in revisions" :key="r.id" :timestamp="r.createdAt" placement="top" type="warning">
            <div><b>第 {{ r.revisionSeq }} 次修改</b> · {{ r.revisedByName }}</div>
            <div style="margin-top:4px">建档期 → {{ r.baseCount }} ; 调查期 → {{ r.currentCount }}</div>
            <div style="margin-top:4px; color:#666">原因：{{ r.reason }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>

    <!-- return dialog -->
    <el-dialog v-model="returnVisible" title="退回报表" width="480px">
      <el-form :model="returnForm">
        <el-form-item label="退回原因" required>
          <el-input v-model="returnForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit
                    placeholder="退回后将进入「省级退回」状态，企业需重新编辑" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReturn">确认退回</el-button>
      </template>
    </el-dialog>

    <!-- revise dialog -->
    <el-dialog v-model="reviseVisible" title="修改报表（留痕，不覆盖原始数据）" width="640px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="修改后会生成一条修改记录；原始上报数据保留在数据库中，汇总分析使用最新有效值。" />
      <el-form :model="reviseForm" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="建档期就业人数">
              <el-input-number v-model="reviseForm.baseCount" :min="0" :max="999999" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调查期就业人数">
              <el-input-number v-model="reviseForm.currentCount" :min="0" :max="999999" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="减少类型">
              <el-select v-model="reviseForm.decreaseType" clearable placeholder="可选" style="width:100%">
                <el-option v-for="o in dictStore.get('DECREASE_TYPE')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主要原因">
              <el-select v-model="reviseForm.primaryReason" clearable placeholder="可选" style="width:100%">
                <el-option v-for="o in dictStore.get('DECREASE_REASON')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="修改原因" required>
              <el-input v-model="reviseForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit
                        placeholder="说明修改的原因（必填，写入修改记录）" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="reviseVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRevise">提交修改</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import { listPeriods } from '@/api/period'
import {
  listProvReports, getProvReport,
  provApproveReport, provReturnReport, provSubmitNation, provDeleteReport,
  provReviseReport, provGetRevisions,
} from '@/api/provinceReport'
import { REPORT_STATUS } from '@/api/report'
import { downloadFile } from '@/utils/download'

const route = useRoute()
const dictStore = useDictStore()
const loading = ref(false)
const periods = ref([])
const rows = ref([])
const selectedIds = ref([])
const filter = reactive({ status: route.query.tab || '04', regionCode: '', periodId: null, keyword: '' })

const detailVisible = ref(false)
const detail = ref(null)
const revisions = ref([])
const enterpriseName = ref('')
const periodName = ref('')

const returnVisible = ref(false)
const returnForm = reactive({ id: null, reason: '' })

const reviseVisible = ref(false)
const reviseForm = reactive({ id: null, baseCount: null, currentCount: null, decreaseType: '', primaryReason: '', reason: '' })

onMounted(async () => {
  await dictStore.load(['REGION', 'DECREASE_TYPE', 'DECREASE_REASON'])
  const r = await listPeriods()
  periods.value = r.data || []
  await refresh()
})

function onTabChange() { selectedIds.value = []; refresh() }
function reset() { filter.regionCode = ''; filter.periodId = null; filter.keyword = ''; refresh() }
function delta(r) { return (r.currentCount ?? 0) - (r.baseCount ?? 0) }
function onSelectionChange(rs) { selectedIds.value = rs.map(r => r.id) }

async function refresh() {
  loading.value = true
  try {
    const r = await listProvReports({
      status: filter.status || undefined,
      regionCode: filter.regionCode || undefined,
      periodId: filter.periodId || undefined,
      keyword: filter.keyword || undefined,
    })
    rows.value = r.data || []
  } finally { loading.value = false }
}

async function openDetail(row) {
  detailVisible.value = true
  enterpriseName.value = row.enterpriseName
  periodName.value = row.periodName
  const [d, rev] = await Promise.all([getProvReport(row.id), provGetRevisions(row.id)])
  detail.value = d.data
  revisions.value = rev.data || []
}

async function approve(row) {
  await ElMessageBox.confirm(`确认通过 [${row.enterpriseName}] 的 ${row.periodName}？通过后即可上报国家。`, '提示', { type: 'warning' })
  await provApproveReport(row.id)
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
  await provReturnReport(returnForm.id, returnForm.reason.trim())
  ElMessage.success('已退回，企业需重新上报')
  returnVisible.value = false
  refresh()
}

async function submitOne(row) {
  await ElMessageBox.confirm(`确认将 [${row.periodName}] 上报至国家失业监测系统？`, '提示', { type: 'warning' })
  const r = await provSubmitNation([row.id])
  showBatchResult('上报国家', r.data)
  refresh()
}

async function onSubmitNation() {
  await ElMessageBox.confirm(`将 ${selectedIds.value.length} 条数据上报至国家系统？`, '提示', { type: 'warning' })
  const r = await provSubmitNation(selectedIds.value)
  showBatchResult('上报国家', r.data)
  refresh()
}

function showBatchResult(action, data) {
  if (data.failed === 0) ElMessage.success(`${action}成功 ${data.success} 条`)
  else ElMessage.warning(`${action}：成功 ${data.success}/${data.total}，失败 ${data.failed}`)
}

async function onDelete(row) {
  await ElMessageBox.confirm(
      `确认删除 [${row.enterpriseName}] 的 ${row.periodName} 报表？\n\n（逻辑删除，记录保留以审计）`,
      '请二次确认', { type: 'error', confirmButtonText: '确认删除' })
  await provDeleteReport(row.id)
  ElMessage.success('已删除')
  refresh()
}

function openRevise(row) {
  Object.assign(reviseForm, {
    id: row.id,
    baseCount: row.baseCount,
    currentCount: row.currentCount,
    decreaseType: row.decreaseType || '',
    primaryReason: row.primaryReason || '',
    reason: '',
  })
  reviseVisible.value = true
}

async function confirmRevise() {
  if (!reviseForm.reason.trim()) { ElMessage.warning('请填写修改原因'); return }
  await provReviseReport(reviseForm.id, {
    baseCount: reviseForm.baseCount,
    currentCount: reviseForm.currentCount,
    decreaseType: reviseForm.decreaseType || null,
    primaryReason: reviseForm.primaryReason || null,
    reason: reviseForm.reason.trim(),
  })
  ElMessage.success('已写入一条修改记录')
  reviseVisible.value = false
  refresh()
}

function onExport() {
  const params = {
    status: filter.status, regionCode: filter.regionCode,
    periodId: filter.periodId, keyword: filter.keyword,
  }
  Object.keys(params).forEach(k => !params[k] && delete params[k])
  downloadFile('/province/report/export', params, `省级报表-${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.batch-bar { padding: 8px 12px; background: #f6f7fa; border-radius: 4px; margin-bottom: 12px; display: flex; align-items: center; gap: 12px; }
</style>
