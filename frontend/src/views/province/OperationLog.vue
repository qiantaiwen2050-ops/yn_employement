<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>操作日志</span>
        <el-button :icon="Refresh" @click="refresh">刷新</el-button>
      </div>
    </template>

    <el-form inline :model="filter" @submit.prevent="refresh">
      <el-form-item label="操作类型">
        <el-select v-model="filter.action" clearable placeholder="全部" style="width:200px" @change="refresh">
          <el-option v-for="o in actionOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="filter.username" placeholder="账号关键字" clearable style="width:160px" @keyup.enter="refresh" />
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="filter.dateRange" type="daterange" value-format="YYYY-MM-DD"
                        range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
                        style="width:280px" @change="refresh" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="refresh">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="80" align="right" />
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column prop="username" label="操作人" width="120" />
      <el-table-column label="操作类型" width="180">
        <template #default="{row}">
          <el-tag :type="actionTag(row.action)" size="small">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="target" label="操作对象" width="160" />
      <el-table-column prop="detail" label="详情" min-width="280" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="140" />
    </el-table>

    <div v-if="rows.length === 200" style="margin-top:8px; color:#999; font-size:12px; text-align:center">
      仅显示最近 200 条；更早的请用日期/类型过滤
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listSysLogs } from '@/api/system'

const loading = ref(false)
const rows = ref([])
const filter = reactive({ action: '', username: '', dateRange: null })

const ACTION_META = {
  LOGIN:           { label: '登录', tag: '' },
  CHANGE_PASSWORD: { label: '修改密码', tag: '' },
  SUBMIT_FILING:   { label: '提交备案', tag: 'success' },
  APPROVE_FILING:  { label: '通过备案', tag: 'success' },
  REJECT_FILING:   { label: '退回备案', tag: 'danger' },
  SUBMIT_REPORT:   { label: '上报报表', tag: 'success' },
  CITY_APPROVE:    { label: '市级通过', tag: 'success' },
  CITY_RETURN:     { label: '市级退回', tag: 'danger' },
  CITY_TO_PROVINCE:{ label: '上报省级', tag: 'warning' },
  PROV_APPROVE:    { label: '省级通过', tag: 'success' },
  PROV_RETURN:     { label: '省级退回', tag: 'danger' },
  REVISE_REPORT:   { label: '修改报表', tag: 'warning' },
  DELETE_REPORT:   { label: '删除报表', tag: 'danger' },
  NATION_UPLOAD:   { label: '国家上报', tag: 'warning' },
  NOTICE_CREATE:   { label: '发布通知', tag: '' },
  NOTICE_UPDATE:   { label: '修改通知', tag: '' },
  NOTICE_DELETE:   { label: '删除通知', tag: 'danger' },
  PERIOD_GENERATE: { label: '生成调查期', tag: 'info' },
  PERIOD_TOGGLE:   { label: '调查期开关', tag: 'info' },
  USER_CREATE:     { label: '新增用户', tag: '' },
  USER_UPDATE:     { label: '修改用户', tag: '' },
  USER_DELETE:     { label: '删除用户', tag: 'danger' },
  USER_RESET_PWD:  { label: '重置密码', tag: 'warning' },
  ROLE_CREATE:     { label: '新增角色', tag: '' },
  ROLE_UPDATE:     { label: '修改角色', tag: '' },
  ROLE_DELETE:     { label: '删除角色', tag: 'danger' },
}

const actionOptions = computed(() =>
  Object.entries(ACTION_META).map(([value, m]) => ({ value, label: m.label })))

function actionLabel(a) { return ACTION_META[a]?.label || a }
function actionTag(a)   { return ACTION_META[a]?.tag   || 'info' }

onMounted(refresh)

async function refresh() {
  loading.value = true
  try {
    const params = { limit: 200 }
    if (filter.action) params.action = filter.action
    if (filter.username) params.username = filter.username
    if (filter.dateRange?.length === 2) {
      params.startDate = filter.dateRange[0]
      params.endDate = filter.dateRange[1]
    }
    const r = await listSysLogs(params)
    rows.value = r.data || []
  } finally { loading.value = false }
}

function reset() {
  filter.action = ''
  filter.username = ''
  filter.dateRange = null
  refresh()
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
