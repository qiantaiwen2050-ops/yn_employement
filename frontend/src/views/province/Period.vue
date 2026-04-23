<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>调查期管理</span>
        <div>
          <el-input-number v-model="genYear" :min="2020" :max="2099" controls-position="right" style="width:130px" />
          <el-button type="primary" @click="onGenerate" :loading="generating" style="margin-left:8px">
            一键生成 {{ genYear }} 年 (15 期)
          </el-button>
        </div>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="筛选年份">
        <el-select v-model="filterYear" clearable style="width:140px" @change="refresh">
          <el-option v-for="y in availableYears" :key="y" :label="`${y} 年`" :value="y" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table :data="rows" border stripe>
      <el-table-column prop="seqInYear" label="期序" width="70" align="center" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column label="周期类型" width="100">
        <template #default="{row}">
          <el-tag size="small" :type="row.periodType === 'HALF_MONTH' ? 'warning' : ''">
            {{ row.periodType === 'HALF_MONTH' ? '半月报' : '月报' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startDate" label="起始日" width="120" />
      <el-table-column prop="endDate"   label="截止日" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'">
            {{ row.status === 'OPEN' ? '开放' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{row}">
          <el-button v-if="row.status === 'OPEN'" link type="warning" @click="toggle(row, 'CLOSED')">关闭</el-button>
          <el-button v-else link type="success" @click="toggle(row, 'OPEN')">开放</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPeriods, generatePeriods, updatePeriodStatus } from '@/api/period'

const loading = ref(false)
const generating = ref(false)
const rows = ref([])
const filterYear = ref(null)
const genYear = ref(new Date().getFullYear())

const availableYears = computed(() => [...new Set(rows.value.map(r => r.year))].sort((a, b) => b - a))

onMounted(refresh)

async function refresh() {
  loading.value = true
  try {
    const r = await listPeriods(filterYear.value || undefined)
    rows.value = r.data || []
  } finally { loading.value = false }
}

async function onGenerate() {
  await ElMessageBox.confirm(`确认为 ${genYear.value} 年生成 15 个调查期？（已存在则会失败）`, '提示', { type: 'warning' })
  generating.value = true
  try {
    const r = await generatePeriods(genYear.value)
    ElMessage.success(`已生成 ${r.data.generated} 期`)
    await refresh()
  } catch {} finally { generating.value = false }
}

async function toggle(row, status) {
  await updatePeriodStatus(row.id, status)
  ElMessage.success(status === 'OPEN' ? '已开放' : '已关闭')
  await refresh()
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
