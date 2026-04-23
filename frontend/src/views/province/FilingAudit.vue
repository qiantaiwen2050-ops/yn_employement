<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>企业备案审核</span>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
      </div>
    </template>

    <el-tabs v-model="filter.status" @tab-change="refresh">
      <el-tab-pane label="待审核" name="PENDING" />
      <el-tab-pane label="已通过" name="APPROVED" />
      <el-tab-pane label="已退回" name="REJECTED" />
      <el-tab-pane label="全部" name="" />
    </el-tabs>

    <el-form inline>
      <el-form-item label="所属地区">
        <el-select v-model="filter.regionCode" clearable placeholder="全部" style="width:160px" @change="refresh">
          <el-option v-for="o in dictStore.get('REGION')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
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

    <el-table :data="rows" border stripe>
      <el-table-column prop="name" label="企业名称" min-width="220" />
      <el-table-column prop="orgCode" label="组织机构代码" width="130" />
      <el-table-column prop="regionName" label="所属地区" width="100" />
      <el-table-column label="性质" width="120">
        <template #default="{row}">{{ dictStore.label('ENT_NATURE', row.nature) }}</template>
      </el-table-column>
      <el-table-column label="行业" width="200" show-overflow-tooltip>
        <template #default="{row}">{{ dictStore.label('INDUSTRY', row.industry) }}</template>
      </el-table-column>
      <el-table-column prop="contact" label="联系人" width="100" />
      <el-table-column prop="phone" label="联系电话" width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="STATUS_TAG[row.filingStatus]">{{ STATUS_LABEL[row.filingStatus] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <template v-if="row.filingStatus === 'PENDING'">
            <el-button link type="success" @click="approve(row)">通过</el-button>
            <el-button link type="danger" @click="openReject(row)">退回</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="企业备案详情" width="720px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="企业名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="组织机构代码">{{ detail.orgCode }}</el-descriptions-item>
        <el-descriptions-item label="所属地区">{{ detail.regionName }} ({{ detail.regionCode }})</el-descriptions-item>
        <el-descriptions-item label="企业性质">{{ dictStore.label('ENT_NATURE', detail.nature) }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{ dictStore.label('INDUSTRY', detail.industry) }}</el-descriptions-item>
        <el-descriptions-item label="主要经营业务" :span="2">{{ detail.mainBusiness }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contact }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="联系地址" :span="2">{{ detail.address }}</el-descriptions-item>
        <el-descriptions-item label="邮政编码">{{ detail.postcode }}</el-descriptions-item>
        <el-descriptions-item label="传真">{{ detail.fax }}</el-descriptions-item>
        <el-descriptions-item label="电子邮箱" :span="2">{{ detail.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="STATUS_TAG[detail.filingStatus]">{{ STATUS_LABEL[detail.filingStatus] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.submittedAt }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detail.reviewedAt || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.rejectReason" label="退回原因" :span="2">
          <span style="color:#f56c6c">{{ detail.rejectReason }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="退回备案" width="480px">
      <el-form :model="rejectForm">
        <el-form-item label="退回原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit
                    placeholder="请填写退回原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认退回</el-button>
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
import { listProvFilings, getProvFiling, provApproveFiling, provRejectFiling } from '@/api/provinceFiling'
import { downloadFile } from '@/utils/download'

const route = useRoute()
const dictStore = useDictStore()
const loading = ref(false)
const rows = ref([])
const filter = reactive({ status: route.query.tab || 'PENDING', regionCode: '', keyword: '' })

const STATUS_LABEL = { DRAFT: '未提交', PENDING: '待审核', APPROVED: '已通过', REJECTED: '已退回' }
const STATUS_TAG   = { DRAFT: 'info',  PENDING: 'warning',  APPROVED: 'success', REJECTED: 'danger' }

const detailVisible = ref(false)
const detail = ref(null)
const rejectVisible = ref(false)
const rejectForm = reactive({ id: null, reason: '' })

onMounted(async () => {
  await dictStore.load(['REGION', 'ENT_NATURE', 'INDUSTRY'])
  await refresh()
})

async function refresh() {
  loading.value = true
  try {
    const r = await listProvFilings({
      status: filter.status || undefined,
      regionCode: filter.regionCode || undefined,
      keyword: filter.keyword || undefined,
    })
    rows.value = r.data || []
  } finally { loading.value = false }
}

function reset() {
  filter.regionCode = ''
  filter.keyword = ''
  refresh()
}

async function openDetail(row) {
  detailVisible.value = true
  const r = await getProvFiling(row.id)
  detail.value = r.data
}

async function approve(row) {
  await ElMessageBox.confirm(`确认通过 [${row.name}] 的备案？`, '提示', { type: 'warning' })
  await provApproveFiling(row.id)
  ElMessage.success('已通过')
  refresh()
}

function openReject(row) {
  rejectForm.id = row.id
  rejectForm.reason = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectForm.reason.trim()) { ElMessage.warning('请填写退回原因'); return }
  await provRejectFiling(rejectForm.id, rejectForm.reason.trim())
  ElMessage.success('已退回')
  rejectVisible.value = false
  refresh()
}

function onExport() {
  const params = { status: filter.status, regionCode: filter.regionCode, keyword: filter.keyword }
  Object.keys(params).forEach(k => !params[k] && delete params[k])
  downloadFile('/province/filing/export', params, `企业备案-${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
