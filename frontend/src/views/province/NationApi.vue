<template>
  <el-card v-loading="loading">
    <template #header><div class="card-header"><span>国家失业监测系统接口（Mock）</span></div></template>

    <el-tabs v-model="tab">
      <!-- 1) 手动上报 -->
      <el-tab-pane label="手动上报" name="upload">
        <el-alert type="info" show-icon :closable="false" style="margin-bottom:16px"
          title="选择调查期，将该期所有「省级已通过」(状态 05) 的报表打包上报国家系统。Mock 服务约 10% 概率会模拟失败，可在「上报日志」标签页中重试。" />
        <el-form inline>
          <el-form-item label="调查期">
            <el-select v-model="uploadPeriodId" filterable placeholder="请选择调查期" style="width:280px">
              <el-option v-for="p in periods" :key="p.id" :label="p.name" :value="p.id">
                <span style="float:left">{{ p.name }}</span>
                <span style="float:right; color:#999; font-size:12px">{{ p.periodType === 'HALF_MONTH' ? '半月' : '月' }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Promotion" :loading="uploading" :disabled="!uploadPeriodId" @click="onUpload">立即上报</el-button>
          </el-form-item>
        </el-form>

        <el-card v-if="lastResult" shadow="never" :class="['result', lastResult.status === 'SUCCESS' ? 'ok' : 'fail']">
          <div class="result-head">
            <el-tag :type="lastResult.status === 'SUCCESS' ? 'success' : 'danger'" size="large" effect="dark">
              {{ lastResult.status === 'SUCCESS' ? '上报成功' : '上报失败' }}
            </el-tag>
            <span class="meta">{{ lastResult.startedAt }} → {{ lastResult.finishedAt }}</span>
          </div>
          <el-descriptions :column="2" border size="small" style="margin-top:12px">
            <el-descriptions-item label="调查期">{{ lastResult.periodName }}</el-descriptions-item>
            <el-descriptions-item label="数据条数">{{ lastResult.dataCount }} 条</el-descriptions-item>
            <el-descriptions-item label="请求摘要" :span="2">{{ lastResult.requestSummary }}</el-descriptions-item>
            <el-descriptions-item label="国家系统响应" :span="2">
              <span :style="{color: lastResult.status === 'SUCCESS' ? '#67c23a' : '#f56c6c'}">{{ lastResult.responseSummary }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="lastResult.receiptNo" label="国家系统回执编号" :span="2">
              <code>{{ lastResult.receiptNo }}</code>
            </el-descriptions-item>
            <el-descriptions-item v-if="lastResult.errorCode" label="错误码">{{ lastResult.errorCode }}</el-descriptions-item>
            <el-descriptions-item v-if="lastResult.errorMessage" label="错误信息">{{ lastResult.errorMessage }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- 2) 上报日志 -->
      <el-tab-pane label="上报日志" name="log">
        <el-form inline>
          <el-form-item label="状态">
            <el-select v-model="filter.status" clearable placeholder="全部" style="width:140px" @change="loadLogs">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
              <el-option label="进行中" value="SENDING" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="filter.reportType" clearable placeholder="全部" style="width:120px" @change="loadLogs">
              <el-option label="手动" value="MANUAL" />
              <el-option label="自动" value="AUTO" />
            </el-select>
          </el-form-item>
          <el-form-item><el-button type="primary" @click="loadLogs">查询</el-button></el-form-item>
        </el-form>

        <el-table :data="logs" border stripe>
          <el-table-column prop="id" label="日志ID" width="80" />
          <el-table-column prop="periodName" label="调查期" min-width="150" />
          <el-table-column label="类型" width="80">
            <template #default="{row}">
              <el-tag size="small" :type="row.reportType === 'AUTO' ? 'info' : ''">{{ row.reportType === 'AUTO' ? '自动' : '手动' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="dataCount" label="条数" width="70" align="right" />
          <el-table-column label="状态" width="100">
            <template #default="{row}">
              <el-tag :type="STATUS_TAG[row.status]">{{ STATUS_LABEL[row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重试" width="60" align="center">
            <template #default="{row}">{{ row.retryCount }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="120" />
          <el-table-column prop="startedAt" label="开始时间" width="160" />
          <el-table-column label="结果" min-width="240">
            <template #default="{row}">
              <span v-if="row.status === 'SUCCESS'" style="color:#67c23a">回执 {{ row.receiptNo }}</span>
              <span v-else-if="row.status === 'FAILED'" style="color:#f56c6c">{{ row.errorCode }} · {{ row.errorMessage }}</span>
              <span v-else style="color:#999">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{row}">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 'FAILED'" link type="primary" @click="onRetry(row)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 3) 自动上报设置 (placeholder for演示) -->
      <el-tab-pane label="自动上报设置" name="auto">
        <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px"
          title="演示项目暂未启用定时调度。下方为配置界面占位 — 实际 @Scheduled Job 在生产环境启用即可。" />
        <el-form label-width="120px" style="max-width:520px">
          <el-form-item label="启用自动上报">
            <el-switch v-model="autoCfg.enabled" />
          </el-form-item>
          <el-form-item label="上报周期">
            <el-select v-model="autoCfg.frequency" style="width:200px" :disabled="!autoCfg.enabled">
              <el-option label="每月" value="MONTHLY" />
              <el-option label="每周" value="WEEKLY" />
            </el-select>
          </el-form-item>
          <el-form-item label="执行时间">
            <el-time-picker v-model="autoCfg.time" placeholder="HH:mm" format="HH:mm" :disabled="!autoCfg.enabled" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" disabled>保存（演示禁用）</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" title="上报详情" width="640px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="日志ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="STATUS_TAG[detail.status]">{{ STATUS_LABEL[detail.status] }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="调查期">{{ detail.periodName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.reportType === 'AUTO' ? '自动' : '手动' }}</el-descriptions-item>
        <el-descriptions-item label="数据条数">{{ detail.dataCount }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ detail.retryCount }} / 5</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="父记录">{{ detail.parentLogId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.startedAt }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detail.finishedAt }}</el-descriptions-item>
        <el-descriptions-item label="本地校验" :span="2">
          <span style="color:#67c23a">通过（必填项 / 格式 / 逻辑一致性）</span>
        </el-descriptions-item>
        <el-descriptions-item label="国家系统响应" :span="2">
          <span :style="{color: detail.status === 'SUCCESS' ? '#67c23a' : '#f56c6c'}">{{ detail.responseSummary }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.receiptNo" label="国家回执编号" :span="2">
          <code>{{ detail.receiptNo }}</code>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorCode" label="错误码">{{ detail.errorCode }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMessage" label="错误信息">{{ detail.errorMessage }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPeriods } from '@/api/period'
import { nationUpload, nationLogs, nationLogDetail, nationRetry } from '@/api/system'

const loading = ref(false)
const uploading = ref(false)
const tab = ref('upload')
const periods = ref([])
const uploadPeriodId = ref(null)
const lastResult = ref(null)

const logs = ref([])
const filter = reactive({ status: '', reportType: '' })
const detailVisible = ref(false)
const detail = ref(null)

const autoCfg = reactive({ enabled: false, frequency: 'MONTHLY', time: null })

const STATUS_LABEL = { SENDING: '进行中', SUCCESS: '成功', FAILED: '失败' }
const STATUS_TAG = { SENDING: 'warning', SUCCESS: 'success', FAILED: 'danger' }

onMounted(async () => {
  loading.value = true
  try {
    const [p, l] = await Promise.all([listPeriods(), nationLogs({})])
    periods.value = p.data || []
    logs.value = l.data || []
  } finally { loading.value = false }
})

async function onUpload() {
  uploading.value = true
  try {
    const r = await nationUpload(uploadPeriodId.value)
    lastResult.value = r.data
    if (r.data.status === 'SUCCESS') ElMessage.success('上报成功，回执 ' + r.data.receiptNo)
    else ElMessage.warning('上报失败：' + r.data.errorMessage + '（可在「上报日志」中重试）')
    loadLogs()
  } catch {} finally { uploading.value = false }
}

async function loadLogs() {
  const r = await nationLogs({
    status: filter.status || undefined,
    reportType: filter.reportType || undefined,
  })
  logs.value = r.data || []
}

async function openDetail(row) {
  detailVisible.value = true
  const r = await nationLogDetail(row.id)
  detail.value = r.data
}

async function onRetry(row) {
  await ElMessageBox.confirm(`重试上报日志 #${row.id}？`, '提示', { type: 'warning' })
  try {
    const r = await nationRetry(row.id)
    if (r.data.status === 'SUCCESS') ElMessage.success(`重试成功，回执 ${r.data.receiptNo}`)
    else ElMessage.warning(`重试仍失败：${r.data.errorMessage}`)
    loadLogs()
  } catch {}
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.result { margin-top: 16px; border-left: 4px solid #67c23a; }
.result.fail { border-left-color: #f56c6c; }
.result-head { display: flex; align-items: center; gap: 12px; }
.result-head .meta { color: #999; font-size: 12px; }
</style>
