<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <span>调查期数据填报</span>
          <el-tag v-if="report" :type="statusTag" effect="dark">{{ statusLabel }}</el-tag>
          <el-tag v-if="period" type="info" effect="plain">{{ periodTypeLabel }}</el-tag>
        </div>
        <el-select v-model="periodId" placeholder="选择调查期" style="width:240px" @change="onPeriodChange">
          <el-option v-for="p in periods" :key="p.id" :label="p.name" :value="p.id">
            <span style="float:left">{{ p.name }}</span>
            <span style="float:right; color:#999; font-size:12px">{{ p.periodType === 'HALF_MONTH' ? '半月报' : '月报' }}</span>
          </el-option>
        </el-select>
      </div>
    </template>

    <el-empty v-if="!periodId" description="请先选择调查期" />

    <template v-else>
      <el-alert v-if="report?.cityReturnReason && report?.status === '03'" type="error" show-icon :closable="false" style="margin-bottom:16px">
        <template #title>市级退回原因：{{ report.cityReturnReason }}</template>
      </el-alert>
      <el-alert v-if="report?.provReturnReason && report?.status === '06'" type="error" show-icon :closable="false" style="margin-bottom:16px">
        <template #title>省级退回原因：{{ report.provReturnReason }}</template>
      </el-alert>
      <el-alert v-if="!editable" type="warning" show-icon :closable="false" style="margin-bottom:16px"
        :title="`当前状态为「${statusLabel}」，不可编辑。如有异议请等待审核结果或退回。`" />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="160px" :disabled="!editable">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="建档期就业人数" prop="baseCount">
              <el-input-number v-model="form.baseCount" :min="0" :max="999999" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调查期就业人数" prop="currentCount">
              <el-input-number v-model="form.currentCount" :min="0" :max="999999" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert v-if="decreased" type="info" :closable="false" show-icon style="margin-bottom:16px"
          title="调查期人数小于建档期人数，「就业人数减少类型」与「主要原因」必填。" />

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="减少类型" prop="decreaseType" :required="decreased">
              <el-select v-model="form.decreaseType" placeholder="请选择" style="width:100%" clearable>
                <el-option v-for="o in dictStore.get('DECREASE_TYPE')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主要原因" prop="primaryReason" :required="decreased">
              <el-select v-model="form.primaryReason" placeholder="请选择" style="width:100%" clearable>
                <el-option v-for="o in dictStore.get('DECREASE_REASON')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="form.primaryReason === 'R99'">
            <el-form-item label="主要原因说明" prop="primaryReasonText" required>
              <el-input v-model="form.primaryReasonText" type="textarea" :rows="2" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="次要原因">
              <el-select v-model="form.secondaryReason" placeholder="可选" style="width:100%" clearable
                @change="onSecondaryChange">
                <el-option v-for="o in dictStore.get('DECREASE_REASON')" :key="o.itemCode"
                  :label="o.itemName" :value="o.itemCode"
                  :disabled="o.itemCode === form.primaryReason" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.secondaryReason">
            <el-form-item label="次要原因说明">
              <el-input v-model="form.secondaryReasonText" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="第三原因">
              <el-select v-model="form.thirdReason" :placeholder="form.secondaryReason ? '可选' : '请先填写次要原因'"
                style="width:100%" clearable :disabled="!form.secondaryReason">
                <el-option v-for="o in dictStore.get('DECREASE_REASON')" :key="o.itemCode"
                  :label="o.itemName" :value="o.itemCode"
                  :disabled="o.itemCode === form.primaryReason || o.itemCode === form.secondaryReason" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.thirdReason">
            <el-form-item label="第三原因说明">
              <el-input v-model="form.thirdReasonText" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>

          <el-col :span="24">
            <el-form-item label="其他说明">
              <el-input v-model="form.otherReason" type="textarea" :rows="2" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>

        <div v-if="editable" class="actions">
          <el-button :loading="saving" @click="onSave">保存草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="onSubmit">上报</el-button>
        </div>
      </el-form>
    </template>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import { listOpenPeriods } from '@/api/period'
import { getReportDraft, saveReport, submitReport, REPORT_STATUS } from '@/api/report'

const dictStore = useDictStore()
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const periods = ref([])
const periodId = ref(null)
const period = ref(null)
const report = ref(null)
const formRef = ref()
const form = reactive(blankForm())

function blankForm() {
  return {
    baseCount: null, currentCount: null,
    decreaseType: '', primaryReason: '', primaryReasonText: '',
    secondaryReason: '', secondaryReasonText: '',
    thirdReason: '', thirdReasonText: '',
    otherReason: '',
  }
}

const decreased = computed(() => form.baseCount != null && form.currentCount != null && form.currentCount < form.baseCount)
const editable = computed(() => report.value && ['DRAFT', '03', '06'].includes(report.value.status))
const statusLabel = computed(() => REPORT_STATUS[report.value?.status]?.label || '—')
const statusTag   = computed(() => REPORT_STATUS[report.value?.status]?.tag || 'info')
const periodTypeLabel = computed(() => period.value ? (period.value.periodType === 'HALF_MONTH' ? '半月报' : '月报') : '')

const rules = {
  baseCount:    [{ required: true, message: '请输入建档期就业人数', trigger: 'blur' }],
  currentCount: [{ required: true, message: '请输入调查期就业人数', trigger: 'blur' }],
  decreaseType: [{ validator: (_, v, cb) => decreased.value && !v ? cb(new Error('人数减少时必填')) : cb(), trigger: 'change' }],
  primaryReason:[{ validator: (_, v, cb) => decreased.value && !v ? cb(new Error('人数减少时必填')) : cb(), trigger: 'change' }],
  primaryReasonText: [{ validator: (_, v, cb) => form.primaryReason === 'R99' && !v ? cb(new Error('选择「其他」时必填')) : cb(), trigger: 'blur' }],
}

onMounted(async () => {
  loading.value = true
  try {
    await dictStore.load(['DECREASE_TYPE', 'DECREASE_REASON'])
    const r = await listOpenPeriods()
    periods.value = r.data || []
  } finally { loading.value = false }
})

async function onPeriodChange(id) {
  period.value = periods.value.find(p => p.id === id) || null
  loading.value = true
  try {
    const r = await getReportDraft(id)
    report.value = r.data
    Object.assign(form, blankForm(), {
      baseCount: r.data.baseCount,
      currentCount: r.data.currentCount,
      decreaseType: r.data.decreaseType || '',
      primaryReason: r.data.primaryReason || '',
      primaryReasonText: r.data.primaryReasonText || '',
      secondaryReason: r.data.secondaryReason || '',
      secondaryReasonText: r.data.secondaryReasonText || '',
      thirdReason: r.data.thirdReason || '',
      thirdReasonText: r.data.thirdReasonText || '',
      otherReason: r.data.otherReason || '',
    })
  } catch { report.value = null } finally { loading.value = false }
}

function buildPayload() {
  return { id: report.value.id, ...form }
}

// Clearing the secondary slot must also clear the third (it makes no sense without secondary).
function onSecondaryChange(val) {
  if (!val && form.thirdReason) {
    form.thirdReason = ''
    form.thirdReasonText = ''
  }
  // If new secondary collides with third, drop third.
  if (val && val === form.thirdReason) {
    form.thirdReason = ''
    form.thirdReasonText = ''
  }
}

// Changing the primary may collide with secondary/third — drop the offending downstream slot.
watch(() => form.primaryReason, (val) => {
  if (val && val === form.secondaryReason) {
    form.secondaryReason = ''
    form.secondaryReasonText = ''
    form.thirdReason = ''   // third becomes orphaned without secondary
    form.thirdReasonText = ''
  }
  if (val && val === form.thirdReason) {
    form.thirdReason = ''
    form.thirdReasonText = ''
  }
})

async function onSave() {
  saving.value = true
  try {
    const r = await saveReport(buildPayload())
    report.value = r.data
    ElMessage.success('已保存草稿')
  } catch {} finally { saving.value = false }
}

async function onSubmit() {
  await formRef.value.validate()
  await ElMessageBox.confirm('确认上报？上报后将进入市级审核流程，期间不可修改。', '提示', { type: 'warning' })
  submitting.value = true
  try {
    const r = await submitReport(buildPayload())
    report.value = r.data
    ElMessage.success('上报成功')
  } catch {} finally { submitting.value = false }
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; align-items: center; gap: 10px; }
.actions { text-align: right; padding-top: 8px; border-top: 1px solid #eee; }
</style>
