<template>
  <div v-loading="loading">
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="m-label">备案状态</div>
          <div class="m-value">
            <el-tag :type="filingTag" effect="dark" size="large">{{ filingLabel }}</el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="m-label">开放中调查期</div>
          <div class="m-value">{{ openCount }} <span class="m-unit">期</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="m-label">已上报报表</div>
          <div class="m-value">{{ submittedCount }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="m-label">退回待处理</div>
          <div class="m-value" :style="{color: returnedCount ? '#f56c6c' : undefined}">{{ returnedCount }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>近期开放调查期</span>
          <el-button type="primary" link @click="$router.push('/enterprise/report')">去填报 →</el-button>
        </div>
      </template>
      <el-table :data="periods.slice(0, 8)" stripe>
        <el-table-column prop="name" label="调查期" min-width="200" />
        <el-table-column label="类型" width="100">
          <template #default="{row}">
            <el-tag size="small" :type="row.periodType === 'HALF_MONTH' ? 'warning' : ''">
              {{ row.periodType === 'HALF_MONTH' ? '半月报' : '月报' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="起始日期" width="130" />
        <el-table-column prop="endDate"   label="截止日期" width="130" />
        <el-table-column label="状态" width="100">
          <template #default><el-tag type="success">开放</el-tag></template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMyFiling } from '@/api/filing'
import { listOpenPeriods } from '@/api/period'
import { listReports, REPORT_STATUS, FILING_STATUS } from '@/api/report'

const loading = ref(false)
const filing = ref(null)
const periods = ref([])
const reports = ref([])

const filingLabel = computed(() => FILING_STATUS[filing.value?.filingStatus]?.label || '—')
const filingTag   = computed(() => FILING_STATUS[filing.value?.filingStatus]?.tag || 'info')
const openCount   = computed(() => periods.value.length)
const submittedCount = computed(() => reports.value.filter(r => r.status !== 'DRAFT').length)
const returnedCount  = computed(() => reports.value.filter(r => r.status === '03' || r.status === '06').length)

onMounted(async () => {
  loading.value = true
  try {
    const [f, p, r] = await Promise.all([
      getMyFiling().catch(() => null),
      listOpenPeriods(),
      listReports().catch(() => ({ data: [] })),
    ])
    filing.value = f?.data
    periods.value = p.data || []
    reports.value = r.data || []
  } finally { loading.value = false }
})
</script>

<style scoped>
.metric-card { text-align: center; }
.metric-card .m-label { color: #888; font-size: 13px; margin-bottom: 8px; }
.metric-card .m-value { font-size: 26px; font-weight: 600; color: #1e3c72; }
.metric-card .m-unit { font-size: 13px; color: #999; font-weight: normal; margin-left: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
