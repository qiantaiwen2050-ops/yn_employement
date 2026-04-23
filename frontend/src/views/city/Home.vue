<template>
  <div v-loading="loading">
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover" @click="$router.push('/city/review?tab=pending')">
          <div class="m-label">待我审核</div>
          <div class="m-value" :style="{color: pendingCount ? '#e6a23c' : undefined}">{{ pendingCount }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover" @click="$router.push('/city/review?tab=audited')">
          <div class="m-label">已通过待上报省级</div>
          <div class="m-value" :style="{color: approvedCount ? '#409eff' : undefined}">{{ approvedCount }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="m-label">本辖区累计退回</div>
          <div class="m-value">{{ returnedCount }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card" shadow="hover">
          <div class="m-label">已上报省级</div>
          <div class="m-value">{{ submittedCount }} <span class="m-unit">条</span></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最新待审核数据</span>
              <el-button type="primary" link @click="$router.push('/city/review?tab=pending')">查看全部 →</el-button>
            </div>
          </template>
          <el-table :data="pendingList.slice(0, 5)" stripe>
            <el-table-column prop="enterpriseName" label="企业" min-width="180" />
            <el-table-column prop="periodName" label="调查期" width="150" />
            <el-table-column label="人数" width="120">
              <template #default="{row}">{{ row.baseCount }} → {{ row.currentCount }}</template>
            </el-table-column>
            <el-table-column prop="submittedAt" label="上报时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>我发布的通知</span>
              <el-button type="primary" link @click="$router.push('/city/notice')">管理 →</el-button>
            </div>
          </template>
          <div v-if="!myNotices.length" style="color:#999; padding: 20px 0; text-align:center">暂无</div>
          <div v-else>
            <div v-for="n in myNotices.slice(0, 5)" :key="n.id" class="notice-row">
              <span class="title">{{ n.title }}</span>
              <span class="date">{{ n.createdAt?.substring(0, 10) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listCityReviews } from '@/api/cityReview'
import { listMyNotices } from '@/api/notice'

const loading = ref(false)
const allReports = ref([])
const myNotices = ref([])

const pendingList = computed(() => allReports.value.filter(r => r.status === '01'))
const pendingCount   = computed(() => pendingList.value.length)
const approvedCount  = computed(() => allReports.value.filter(r => r.status === '02').length)
const returnedCount  = computed(() => allReports.value.filter(r => r.status === '03').length)
const submittedCount = computed(() => allReports.value.filter(r => ['04','05','06','07'].includes(r.status)).length)

onMounted(async () => {
  loading.value = true
  try {
    const [r, n] = await Promise.all([
      listCityReviews({}),
      listMyNotices().catch(() => ({ data: [] })),
    ])
    allReports.value = r.data || []
    myNotices.value = n.data || []
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
.notice-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed #eee; }
.notice-row:last-child { border-bottom: none; }
.notice-row .title { color: #333; font-size: 14px; max-width: 80%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-row .date { color: #999; font-size: 12px; }
</style>
