<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>系统监控</span>
        <div>
          <el-tag v-if="autoRefresh" type="success" effect="plain" style="margin-right:8px">每 5 秒刷新</el-tag>
          <el-button :icon="Refresh" @click="refresh">手动刷新</el-button>
          <el-button @click="autoRefresh = !autoRefresh">{{ autoRefresh ? '停止自动刷新' : '开始自动刷新' }}</el-button>
        </div>
      </div>
    </template>

    <div v-if="data">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">CPU 使用率</div>
            <EChart :option="gauge('CPU', data.cpu.systemCpuPct ?? data.cpu.processCpuPct ?? 0)" height="180px" />
            <div class="m-foot">{{ data.cpu.availableCores }} 核 · 系统平均负载 {{ (data.cpu.systemLoadAverage ?? 0).toFixed(2) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">物理内存</div>
            <EChart :option="gauge('内存', data.memory.usedPct || 0)" height="180px" />
            <div class="m-foot">{{ fmtBytes(data.memory.used) }} / {{ fmtBytes(data.memory.total) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">JVM 堆内存</div>
            <EChart :option="gauge('JVM', data.jvm.heapUsedPct || 0)" height="180px" />
            <div class="m-foot">{{ fmtBytes(data.jvm.heapUsed) }} / {{ fmtBytes(data.jvm.heapMax) }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="metric" shadow="never">
            <div class="m-label">硬盘 ({{ data.disk.partition }})</div>
            <EChart :option="gauge('硬盘', data.disk.usedPct || 0)" height="180px" />
            <div class="m-foot">{{ fmtBytes(data.disk.used) }} / {{ fmtBytes(data.disk.total) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card>
        <template #header><span>应用状态</span></template>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="系统版本">{{ data.app.version }}</el-descriptions-item>
          <el-descriptions-item label="运行时长">{{ data.app.uptimeHuman }}</el-descriptions-item>
          <el-descriptions-item label="启动时间">{{ new Date(data.app.startedAt).toLocaleString('zh-CN') }}</el-descriptions-item>
          <el-descriptions-item label="当前在线用户">
            <el-tag :type="data.app.onlineUsers > 0 ? 'success' : 'info'" effect="dark">{{ data.app.onlineUsers }} 人</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最后采集时间" :span="2">{{ new Date().toLocaleString('zh-CN') }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { monitorSnapshot } from '@/api/system'
import EChart from '@/components/EChart.vue'

const loading = ref(false)
const data = ref(null)
const autoRefresh = ref(true)
let timer = null

onMounted(async () => { await refresh(); startAuto() })
onBeforeUnmount(() => stopAuto())

watch(autoRefresh, v => v ? startAuto() : stopAuto())

function startAuto() {
  stopAuto()
  timer = setInterval(refresh, 5000)
}
function stopAuto() { if (timer) clearInterval(timer); timer = null }

async function refresh() {
  loading.value = !data.value
  try {
    const r = await monitorSnapshot()
    data.value = r.data
  } finally { loading.value = false }
}

function gauge(title, value) {
  const v = Math.max(0, Math.min(100, Number(value) || 0))
  return {
    series: [{
      type: 'gauge',
      progress: { show: true, width: 12 },
      axisLine: { lineStyle: { width: 12, color: [[0.6, '#67c23a'], [0.85, '#e6a23c'], [1, '#f56c6c']] } },
      axisTick: { show: false },
      splitLine: { distance: -16, length: 6 },
      axisLabel: { show: false },
      pointer: { length: '60%', width: 4 },
      detail: { valueAnimation: true, fontSize: 18, color: '#1e3c72', formatter: '{value}%', offsetCenter: [0, '60%'] },
      data: [{ value: v, name: title }],
      title: { show: false },
      radius: '90%',
      center: ['50%', '55%'],
    }],
  }
}

function fmtBytes(n) {
  if (!n && n !== 0) return '—'
  const u = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0; let v = n
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++ }
  return v.toFixed(v < 10 ? 2 : 1) + ' ' + u[i]
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.metric { text-align: center; }
.metric .m-label { color: #888; font-size: 13px; margin-bottom: 4px; font-weight: 500; }
.metric .m-foot { color: #666; font-size: 12px; margin-top: -8px; }
</style>
