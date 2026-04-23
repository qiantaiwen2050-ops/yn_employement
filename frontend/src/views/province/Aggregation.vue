<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>数据汇总</span>
        <el-button :icon="Download" :disabled="!result" @click="onExport">导出 Excel</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="调查期" required>
        <el-select v-model="filter.periodId" filterable placeholder="请选择调查期" style="width:240px" @change="refresh">
          <el-option v-for="p in periods" :key="p.id" :label="p.name" :value="p.id">
            <span style="float:left">{{ p.name }}</span>
            <span style="float:right; color:#999; font-size:12px">{{ p.periodType === 'HALF_MONTH' ? '半月' : '月' }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="维度">
        <el-radio-group v-model="filter.dimension" @change="refresh">
          <el-radio-button label="region">按地区</el-radio-button>
          <el-radio-button label="nature">按企业性质</el-radio-button>
          <el-radio-button label="industry">按行业</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <div v-if="result">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">参与企业</div>
            <div class="m-value">{{ result.totalEnterprises }} <span class="m-unit">家</span></div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">建档期总人数</div>
            <div class="m-value">{{ result.totalBase }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">调查期总人数</div>
            <div class="m-value">{{ result.totalCurrent }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">岗位减少总数</div>
            <div class="m-value" :style="{color: result.totalDecrease ? '#f56c6c' : undefined}">{{ result.totalDecrease }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-table :data="result.groups" border stripe>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="enterpriseCount" label="企业数" width="100" align="right" />
        <el-table-column prop="baseCount" label="建档期总人数" width="130" align="right" />
        <el-table-column prop="currentCount" label="调查期总人数" width="130" align="right" />
        <el-table-column label="变化" width="100" align="right">
          <template #default="{row}">
            <span :style="{color: row.change > 0 ? '#67c23a' : row.change < 0 ? '#f56c6c' : '#999'}">
              {{ row.change > 0 ? '+' : '' }}{{ row.change }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="decreaseCount" label="减少总数" width="100" align="right" />
        <el-table-column label="变化率(%)" width="100" align="right">
          <template #default="{row}">
            <span :style="{color: row.changePct > 0 ? '#67c23a' : row.changePct < 0 ? '#f56c6c' : '#999'}">
              {{ row.changePct > 0 ? '+' : '' }}{{ row.changePct }}%
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!result.groups.length" description="所选调查期下暂无省级终审通过数据" style="margin-top:32px" />
    </div>
    <el-empty v-else description="请选择调查期开始汇总" />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { listPeriods } from '@/api/period'
import { aggregate } from '@/api/aggregation'
import { downloadFile } from '@/utils/download'

const loading = ref(false)
const periods = ref([])
const result = ref(null)
const filter = reactive({ periodId: null, dimension: 'region' })

onMounted(async () => {
  const r = await listPeriods()
  periods.value = r.data || []
})

async function refresh() {
  if (!filter.periodId) return
  loading.value = true
  try {
    const r = await aggregate(filter.periodId, filter.dimension)
    result.value = r.data
  } finally { loading.value = false }
}

function onExport() {
  if (!filter.periodId) return
  downloadFile('/province/aggregation/export',
    { periodId: filter.periodId, dimension: filter.dimension },
    `汇总-${result.value?.periodName || ''}-${filter.dimension}-${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.metric { text-align: center; }
.metric .m-label { color: #888; font-size: 13px; margin-bottom: 6px; }
.metric .m-value { font-size: 22px; font-weight: 600; color: #1e3c72; }
.metric .m-unit { font-size: 12px; color: #999; font-weight: normal; margin-left: 4px; }
</style>
