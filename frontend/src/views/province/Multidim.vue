<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>多维分析 — 自由组合 地区/性质/行业</span>
        <el-button :icon="Download" :disabled="!result" @click="onExport">导出 Excel</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="调查期" required>
        <el-select v-model="filter.periodId" filterable style="width:240px" @change="refresh">
          <el-option v-for="p in periods" :key="p.id" :label="p.name" :value="p.id">
            <span style="float:left">{{ p.name }}</span>
            <span style="float:right; color:#999; font-size:12px">{{ p.periodType === 'HALF_MONTH' ? '半月' : '月' }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="维度（至少 1，最多 3）">
        <el-checkbox-group v-model="filter.dims" @change="refresh">
          <el-checkbox label="region">地区</el-checkbox>
          <el-checkbox label="nature">企业性质</el-checkbox>
          <el-checkbox label="industry">行业</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>

    <div v-if="result">
      <el-table :data="result.rows" border stripe>
        <el-table-column v-for="(name, i) in result.dimensionNames" :key="i" :label="name" min-width="140">
          <template #default="{row}">{{ row.dimensionValueLabels[i] }}</template>
        </el-table-column>
        <el-table-column prop="enterpriseCount" label="企业数" width="80" align="right" />
        <el-table-column prop="baseCount" label="建档期总人数" width="120" align="right" />
        <el-table-column prop="currentCount" label="调查期总人数" width="120" align="right" />
        <el-table-column label="变化" width="80" align="right">
          <template #default="{row}">
            <span :style="{color: row.change > 0 ? '#67c23a' : row.change < 0 ? '#f56c6c' : '#999'}">
              {{ row.change > 0 ? '+' : '' }}{{ row.change }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="decreaseCount" label="减少数" width="80" align="right" />
        <el-table-column label="变化率(%)" width="100" align="right">
          <template #default="{row}">
            <el-tag size="small" :type="row.changePct > 0 ? 'success' : row.changePct < 0 ? 'danger' : 'info'" effect="plain">
              {{ row.changePct > 0 ? '+' : '' }}{{ row.changePct }}%
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!result.rows.length" description="所选条件下暂无省级终审通过数据" style="margin-top:32px" />
    </div>
    <el-empty v-else description="请选择调查期和维度" />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listPeriods } from '@/api/period'
import { multidim } from '@/api/analysis'
import { downloadFile } from '@/utils/download'

const loading = ref(false)
const periods = ref([])
const result = ref(null)
const filter = reactive({ periodId: null, dims: ['region'] })

onMounted(async () => {
  const r = await listPeriods()
  periods.value = r.data || []
})

async function refresh() {
  if (!filter.periodId) { result.value = null; return }
  if (!filter.dims.length) { ElMessage.warning('至少选择 1 个维度'); return }
  if (filter.dims.length > 3) { ElMessage.warning('最多 3 个维度'); return }
  loading.value = true
  try {
    const r = await multidim(filter.periodId, filter.dims)
    result.value = r.data
  } finally { loading.value = false }
}

function onExport() {
  if (!filter.periodId) return
  downloadFile('/province/analysis/multidim/export',
    { periodId: filter.periodId, dimensions: filter.dims.join(',') },
    `多维分析-${result.value?.periodName || ''}-${filter.dims.join('+')}-${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
