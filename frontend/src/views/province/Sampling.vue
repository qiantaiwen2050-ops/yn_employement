<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>取样分析 — 各市企业数及占比</span>
        <el-button :icon="Download" :disabled="!result" @click="onExport">导出 Excel</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="所属地区">
        <el-select v-model="filter.regionCode" clearable placeholder="全省" style="width:200px" @change="refresh">
          <el-option v-for="o in dictStore.get('REGION')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="refresh">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <div v-if="result">
      <div class="summary">
        <span>{{ result.scopeName }}</span>
        共 <b style="color:#1e3c72">{{ result.totalEnterprises }}</b> 家备案企业
      </div>
      <el-row :gutter="16">
        <el-col :span="14">
          <EChart v-if="result.rows.length" :option="pieOption" height="420px" />
          <el-empty v-else description="暂无数据" />
        </el-col>
        <el-col :span="10">
          <el-table :data="result.rows" border stripe size="small">
            <el-table-column prop="rank" label="排名" width="60" align="center" />
            <el-table-column prop="regionName" label="地区" min-width="100" />
            <el-table-column prop="count" label="企业数" width="80" align="right" />
            <el-table-column label="占比" width="100" align="right">
              <template #default="{row}">{{ row.percent }}%</template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { useDictStore } from '@/stores/dict'
import { sampling } from '@/api/analysis'
import { downloadFile } from '@/utils/download'
import EChart from '@/components/EChart.vue'

const dictStore = useDictStore()
const loading = ref(false)
const result = ref(null)
const filter = reactive({ regionCode: '' })

onMounted(async () => {
  await dictStore.load(['REGION'])
  await refresh()
})

async function refresh() {
  loading.value = true
  try {
    const r = await sampling(filter.regionCode || undefined)
    result.value = r.data
  } finally { loading.value = false }
}

function reset() { filter.regionCode = ''; refresh() }

const pieOption = computed(() => {
  if (!result.value) return {}
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 家 ({d}%)' },
    legend: { orient: 'vertical', left: 'left', top: 'middle' },
    series: [{
      name: '企业分布',
      type: 'pie',
      radius: ['35%', '70%'],
      avoidLabelOverlap: true,
      label: {
        formatter: '{b}\n{c} 家 ({d}%)',
        fontSize: 12,
      },
      data: result.value.rows.map(r => ({ name: r.regionName, value: r.count })),
    }],
    color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'],
  }
})

function onExport() {
  downloadFile('/province/analysis/sampling/export',
    filter.regionCode ? { regionCode: filter.regionCode } : {},
    `取样分析-${result.value?.scopeName || '全省'}-${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.summary { padding: 8px 12px; background: #f6f7fa; border-radius: 4px; margin-bottom: 16px; }
.summary span { color: #666; margin-right: 8px; }
</style>
