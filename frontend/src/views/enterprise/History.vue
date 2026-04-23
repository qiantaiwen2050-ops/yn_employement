<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>历史数据查询</span>
        <span class="hint">按"提交尝试"展示，每次上报为独立一行</span>
      </div>
    </template>

    <el-form inline :model="filter" @submit.prevent="refresh">
      <el-form-item label="调查期">
        <el-select v-model="filter.periodId" placeholder="全部" clearable filterable style="width:200px">
          <el-option v-for="p in periods" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filter.status" placeholder="全部" clearable style="width:160px">
          <el-option v-for="(v,k) in REPORT_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="refresh">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" border stripe :row-class-name="rowClass">
      <el-table-column prop="periodName" label="调查期" min-width="150" />
      <el-table-column label="周期" width="80">
        <template #default="{row}">
          <el-tag size="small" :type="row.periodType === 'HALF_MONTH' ? 'warning' : ''">
            {{ row.periodType === 'HALF_MONTH' ? '半月' : '月' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="尝试 #" width="80" align="center">
        <template #default="{row}">
          <el-tag size="small" effect="plain">第 {{ row.attemptSeq }} 次</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="baseCount" label="建档期" width="80" align="right" />
      <el-table-column prop="currentCount" label="调查期" width="80" align="right" />
      <el-table-column label="本次结果" width="120">
        <template #default="{row}">
          <el-tag :type="REPORT_STATUS[row.status]?.tag || 'info'">{{ REPORT_STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="160" />
      <el-table-column label="审核处理" min-width="220">
        <template #default="{row}">
          <div v-if="row.cityReviewer">
            市级 {{ row.cityReviewer }} · {{ row.cityReviewAt }}
          </div>
          <div v-if="row.provReviewer">
            省级 {{ row.provReviewer }} · {{ row.provReviewAt }}
          </div>
          <div v-if="!row.cityReviewer && !row.provReviewer" style="color:#999">—</div>
        </template>
      </el-table-column>
      <el-table-column label="退回原因" min-width="220">
        <template #default="{row}">
          <span v-if="row.cityReturnReason" style="color:#f56c6c">市级：{{ row.cityReturnReason }}</span>
          <span v-else-if="row.provReturnReason" style="color:#f56c6c">省级：{{ row.provReturnReason }}</span>
          <span v-else style="color:#999">—</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listPeriods } from '@/api/period'
import { listMyAttempts, REPORT_STATUS } from '@/api/report'

const loading = ref(false)
const periods = ref([])
const rows = ref([])
const filter = reactive({ periodId: null, status: '' })

onMounted(async () => {
  const r = await listPeriods()
  periods.value = r.data || []
  await refresh()
})

async function refresh() {
  loading.value = true
  try {
    const r = await listMyAttempts({
      periodId: filter.periodId || undefined,
      status: filter.status || undefined,
    })
    rows.value = r.data || []
  } finally { loading.value = false }
}

function reset() {
  filter.periodId = null
  filter.status = ''
  refresh()
}

// Visually de-emphasize closed (returned) older attempts
function rowClass({ row }) {
  if (row.closedAt && (row.status === '03' || row.status === '06')) return 'returned-row'
  return ''
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: baseline; }
.hint { color: #888; font-size: 12px; }
:deep(.returned-row) { background: #fafafa; }
</style>
