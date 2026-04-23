<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>趋势分析 — 连续多期变化趋势</span>
        <el-button :icon="Download" :disabled="!result" @click="onExport">导出 Excel</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="周期类型">
        <el-radio-group v-model="filter.periodType" @change="onTypeChange">
          <el-radio-button label="HALF_MONTH">半月报视图</el-radio-button>
          <el-radio-button label="MONTH">月报视图</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="连续调查期（≥3）">
        <el-select v-model="filter.periodIds" multiple filterable collapse-tags collapse-tags-tooltip
                   placeholder="按时间顺序选择 3 期及以上" style="width:380px" @change="refresh">
          <el-option v-for="p in availablePeriods" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button link type="primary" @click="selectAllAvailable">全选当前类型</el-button>
      </el-form-item>
      <el-form-item label="维度">
        <el-radio-group v-model="filter.dimension" @change="refresh">
          <el-radio-button label="">全省汇总</el-radio-button>
          <el-radio-button label="region">按地区</el-radio-button>
          <el-radio-button label="nature">按性质</el-radio-button>
          <el-radio-button label="industry">按行业</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <div v-if="result">
      <EChart v-if="result.series.length" :option="lineOption" height="420px" />
      <el-empty v-else description="所选连续期暂无数据" />

      <el-table :data="tableRows" border stripe size="small" style="margin-top:16px">
        <el-table-column prop="name" :label="result.dimensionName || '分组'" min-width="120" fixed="left" />
        <el-table-column v-for="(pn, i) in result.periodNames" :key="i" :label="pn" align="right" min-width="120">
          <template #default="{row}">
            <span :style="{color: row.pcts[i] > 0 ? '#67c23a' : row.pcts[i] < 0 ? '#f56c6c' : '#999'}">
              {{ row.pcts[i] > 0 ? '+' : '' }}{{ row.pcts[i] }}%
            </span>
            <span style="color:#999; font-size:11px; margin-left:4px">({{ row.currents[i] }})</span>
          </template>
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
import { trend } from '@/api/analysis'
import { downloadFile } from '@/utils/download'
import EChart from '@/components/EChart.vue'

const loading = ref(false)
const periods = ref([])
const result = ref(null)
const filter = reactive({ periodType: 'HALF_MONTH', periodIds: [], dimension: '' })

const availablePeriods = computed(() => periods.value
        .filter(p => p.periodType === filter.periodType)
        .sort((a, b) => a.year - b.year || a.seqInYear - b.seqInYear))

onMounted(async () => {
  const r = await listPeriods()
  periods.value = r.data || []
})

function onTypeChange() {
  filter.periodIds = []
  result.value = null
}

function selectAllAvailable() {
  filter.periodIds = availablePeriods.value.map(p => p.id)
  refresh()
}

async function refresh() {
  if (filter.periodIds.length < 3) {
    result.value = null
    if (filter.periodIds.length > 0) ElMessage.warning('至少选择 3 个连续调查期')
    return
  }
  loading.value = true
  try {
    const r = await trend(filter.periodIds, filter.dimension || undefined)
    result.value = r.data
  } catch { result.value = null } finally { loading.value = false }
}

const lineOption = computed(() => {
  if (!result.value) return {}
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: result.value.series.map(s => s.name), top: 4 },
    grid: { left: 60, right: 30, bottom: 60, top: 50 },
    xAxis: {
      type: 'category',
      data: result.value.periodNames,
      axisLabel: { rotate: result.value.periodNames.length > 5 ? 25 : 0 },
    },
    yAxis: { type: 'value', name: '岗位变化数量占比 (%)', axisLine: { show: true } },
    series: result.value.series.map((s, i) => ({
      name: s.name,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 7,
      lineStyle: { width: 2 },
      data: s.changePcts,
    })),
    color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'],
    markLine: { silent: true, symbol: 'none', data: [{ yAxis: 0, lineStyle: { color: '#999' } }] },
  }
})

const tableRows = computed(() => {
  if (!result.value) return []
  return result.value.series.map(s => ({
    name: s.name, code: s.code,
    pcts: s.changePcts, currents: s.currentCounts, bases: s.baseCounts,
  }))
})

function onExport() {
  if (filter.periodIds.length < 3) return
  const params = { periodIds: filter.periodIds.join(',') }
  if (filter.dimension) params.dimension = filter.dimension
  downloadFile('/province/analysis/trend/export', params,
    `趋势分析-${result.value?.dimensionName || '全省'}-${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
