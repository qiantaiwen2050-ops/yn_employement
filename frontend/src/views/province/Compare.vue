<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>对比分析 — 两期对比双折线</span>
        <el-button :icon="Download" :disabled="!result" @click="onExport">导出 Excel</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="周期类型">
        <el-radio-group v-model="filter.periodType" @change="onTypeChange">
          <el-radio-button label="HALF_MONTH">半月报</el-radio-button>
          <el-radio-button label="MONTH">月报</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="调查期 A">
        <el-select v-model="filter.periodIdA" filterable style="width:200px" @change="refresh">
          <el-option v-for="p in availablePeriods" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="调查期 B">
        <el-select v-model="filter.periodIdB" filterable style="width:200px" @change="refresh">
          <el-option v-for="p in availablePeriods" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="维度">
        <el-radio-group v-model="filter.dimension" @change="refresh">
          <el-radio-button label="region">地区</el-radio-button>
          <el-radio-button label="nature">性质</el-radio-button>
          <el-radio-button label="industry">行业</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <div v-if="result">
      <EChart v-if="result.rows.length" :option="lineOption" height="380px" />
      <el-empty v-else description="所选条件下暂无数据" style="margin: 32px 0" />

      <el-table :data="result.rows" border stripe size="small" style="margin-top:16px">
        <el-table-column prop="name" :label="result.dimensionName" min-width="120" fixed="left" />
        <el-table-column :label="result.periodAName" align="center">
          <el-table-column label="企业数" width="80" align="right">
            <template #default="{row}">{{ row.entCountA }}</template>
          </el-table-column>
          <el-table-column label="调查期人数" width="100" align="right">
            <template #default="{row}">{{ row.currentCountA }}</template>
          </el-table-column>
          <el-table-column label="减少数" width="80" align="right">
            <template #default="{row}">
              <span :style="{color: row.decreaseCountA > 0 ? '#f56c6c' : '#999'}">{{ row.decreaseCountA }}</span>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column :label="result.periodBName" align="center">
          <el-table-column label="企业数" width="80" align="right">
            <template #default="{row}">{{ row.entCountB }}</template>
          </el-table-column>
          <el-table-column label="调查期人数" width="100" align="right">
            <template #default="{row}">{{ row.currentCountB }}</template>
          </el-table-column>
          <el-table-column label="减少数" width="80" align="right">
            <template #default="{row}">
              <span :style="{color: row.decreaseCountB > 0 ? '#f56c6c' : '#999'}">{{ row.decreaseCountB }}</span>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="调查期差值" width="110" align="right">
          <template #default="{row}">
            <span :style="{color: row.delta > 0 ? '#67c23a' : row.delta < 0 ? '#f56c6c' : '#999'}">
              {{ row.delta > 0 ? '+' : '' }}{{ row.delta }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变化率" width="100" align="right">
          <template #default="{row}">{{ row.changeRatePct }}</template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listPeriods } from '@/api/period'
import { compare } from '@/api/analysis'
import { downloadFile } from '@/utils/download'
import EChart from '@/components/EChart.vue'

const loading = ref(false)
const periods = ref([])
const result = ref(null)
const filter = reactive({ periodType: 'HALF_MONTH', periodIdA: null, periodIdB: null, dimension: 'region' })

const availablePeriods = computed(() => periods.value.filter(p => p.periodType === filter.periodType))

onMounted(async () => {
  const r = await listPeriods()
  periods.value = r.data || []
})

function onTypeChange() {
  filter.periodIdA = null
  filter.periodIdB = null
  result.value = null
}

async function refresh() {
  if (!filter.periodIdA || !filter.periodIdB) { result.value = null; return }
  if (filter.periodIdA === filter.periodIdB) { ElMessage.warning('请选择两个不同的调查期'); return }
  loading.value = true
  try {
    const r = await compare(filter.periodIdA, filter.periodIdB, filter.dimension)
    result.value = r.data
  } catch { result.value = null } finally { loading.value = false }
}

const lineOption = computed(() => {
  if (!result.value) return {}
  const names = result.value.rows.map(r => r.name)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: [result.value.periodAName, result.value.periodBName] },
    grid: { left: 60, right: 30, bottom: 60, top: 50 },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: names.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value', name: '调查期人数' },
    series: [
      {
        name: result.value.periodAName,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { width: 3 },
        itemStyle: { color: '#5470c6' },
        label: { show: true, position: 'top', fontSize: 11 },
        data: result.value.rows.map(r => r.currentCountA),
      },
      {
        name: result.value.periodBName,
        type: 'line',
        smooth: true,
        symbol: 'diamond',
        symbolSize: 8,
        lineStyle: { width: 3, type: 'dashed' },
        itemStyle: { color: '#ee6666' },
        label: { show: true, position: 'bottom', fontSize: 11 },
        data: result.value.rows.map(r => r.currentCountB),
      },
    ],
  }
})

function onExport() {
  if (!filter.periodIdA || !filter.periodIdB) return
  downloadFile('/province/analysis/compare/export',
    { periodIdA: filter.periodIdA, periodIdB: filter.periodIdB, dimension: filter.dimension },
    `对比分析-${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
