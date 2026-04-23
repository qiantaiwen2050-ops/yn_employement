<template>
  <div v-loading="loading">
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover" @click="$router.push('/province/filing-audit?tab=PENDING')">
          <div class="m-label">待审备案</div>
          <div class="m-value" :style="{color: pendingFilings ? '#e6a23c' : undefined}">{{ pendingFilings }} <span class="m-unit">家</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover" @click="$router.push('/province/report?tab=04')">
          <div class="m-label">待审报表</div>
          <div class="m-value" :style="{color: provPending ? '#e6a23c' : undefined}">{{ provPending }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover" @click="$router.push('/province/report?tab=05')">
          <div class="m-label">待上报国家</div>
          <div class="m-value" :style="{color: provApproved ? '#409eff' : undefined}">{{ provApproved }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="m-label">已上报国家累计</div>
          <div class="m-value">{{ submitted }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>报表流转概览</span>
              <el-button type="primary" link @click="$router.push('/province/report')">进入报表管理 →</el-button>
            </div>
          </template>
          <el-table :data="recentReports.slice(0, 8)" stripe>
            <el-table-column prop="enterpriseName" label="企业" min-width="180" />
            <el-table-column prop="periodName" label="调查期" width="150" />
            <el-table-column label="人数" width="120">
              <template #default="{row}">{{ row.baseCount }} → {{ row.currentCount }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{row}">
                <el-tag :type="REPORT_STATUS[row.status]?.tag || 'info'">{{ REPORT_STATUS[row.status]?.label || row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>快速入口</span>
            </div>
          </template>
          <div class="quick-grid">
            <div class="qi" @click="$router.push('/province/filing-audit')"><el-icon><DocumentChecked /></el-icon><span>备案审核</span></div>
            <div class="qi" @click="$router.push('/province/report')"><el-icon><Files /></el-icon><span>报表管理</span></div>
            <div class="qi" @click="$router.push('/province/aggregation')"><el-icon><DataAnalysis /></el-icon><span>数据汇总</span></div>
            <div class="qi" @click="$router.push('/province/period')"><el-icon><Calendar /></el-icon><span>调查期管理</span></div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listProvFilings } from '@/api/provinceFiling'
import { listProvReports } from '@/api/provinceReport'
import { REPORT_STATUS } from '@/api/report'

const loading = ref(false)
const filings = ref([])
const reports = ref([])

const pendingFilings = computed(() => filings.value.filter(f => f.filingStatus === 'PENDING').length)
const provPending  = computed(() => reports.value.filter(r => r.status === '04').length)
const provApproved = computed(() => reports.value.filter(r => r.status === '05').length)
const submitted    = computed(() => reports.value.filter(r => r.status === '07').length)
const recentReports = computed(() => [...reports.value].sort((a,b) => (b.submittedAt||'').localeCompare(a.submittedAt||'')))

onMounted(async () => {
  loading.value = true
  try {
    const [f, r] = await Promise.all([
      listProvFilings({}).catch(() => ({ data: [] })),
      listProvReports({}).catch(() => ({ data: [] })),
    ])
    filings.value = f.data || []
    reports.value = r.data || []
  } finally { loading.value = false }
})
</script>

<style scoped>
.metric-card { text-align: center; cursor: pointer; transition: transform .15s; }
.metric-card:hover { transform: translateY(-2px); }
.metric-card .m-label { color: #888; font-size: 13px; margin-bottom: 8px; }
.metric-card .m-value { font-size: 26px; font-weight: 600; color: #1e3c72; }
.metric-card .m-unit { font-size: 13px; color: #999; font-weight: normal; margin-left: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.qi { display: flex; flex-direction: column; align-items: center; padding: 24px; border-radius: 8px; background: #f6f7fa; cursor: pointer; transition: background .15s; }
.qi:hover { background: #ecf0f5; }
.qi .el-icon { font-size: 28px; color: #1e3c72; margin-bottom: 8px; }
.qi span { font-size: 13px; color: #555; }
</style>
