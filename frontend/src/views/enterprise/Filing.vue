<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>企业备案信息</span>
        <el-tag v-if="data" :type="statusTag" effect="dark">{{ statusLabel }}</el-tag>
      </div>
    </template>

    <el-alert v-if="data?.filingStatus === 'REJECTED' && data.rejectReason" type="error" :closable="false" show-icon style="margin-bottom:16px">
      <template #title>退回原因：{{ data.rejectReason }}</template>
    </el-alert>
    <el-alert v-if="data?.filingStatus === 'PENDING'" type="warning" :closable="false" show-icon style="margin-bottom:16px"
      title="备案信息已提交，正在等待省级审核。审核期间不可修改。" />
    <el-alert v-if="data?.filingStatus === 'APPROVED'" type="success" :closable="false" show-icon style="margin-bottom:16px"
      title="备案审核已通过。如需修改信息，请重新编辑并提交，审核状态将回到「待审核」。" />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" :disabled="readonly">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="所属地区">
            <el-input :model-value="`${data?.regionName || '-'} (${data?.regionCode || '-'})`" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="组织机构代码" prop="orgCode">
            <el-input v-model="form.orgCode" placeholder="字母/数字，≤9 位" maxlength="9" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="企业名称" prop="name">
            <el-input v-model="form.name" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="企业性质" prop="nature">
            <el-select v-model="form.nature" placeholder="请选择" style="width:100%">
              <el-option v-for="o in dictStore.get('ENT_NATURE')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属行业" prop="industry">
            <el-select v-model="form.industry" placeholder="请选择" style="width:100%">
              <el-option v-for="o in dictStore.get('INDUSTRY')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="主要经营业务" prop="mainBusiness">
            <el-input v-model="form.mainBusiness" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系人" prop="contact">
            <el-input v-model="form.contact" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="邮政编码" prop="postcode">
            <el-input v-model="form.postcode" maxlength="6" placeholder="6 位数字" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="联系地址" prop="address">
            <el-input v-model="form.address" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="联系电话" prop="phone">
            <el-input v-model="form.phone" placeholder="区号-电话 或 手机号" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="传真" prop="fax">
            <el-input v-model="form.fax" placeholder="区号-电话" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="电子邮箱" prop="email">
            <el-input v-model="form.email" placeholder="选填" />
          </el-form-item>
        </el-col>
      </el-row>

      <div v-if="!readonly" class="actions">
        <el-button :loading="saving" @click="onSave">保存草稿</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ submitLabel }}</el-button>
      </div>
      <div v-else class="actions">
        <el-button v-if="data?.filingStatus === 'APPROVED'" type="primary" @click="readonly = false">重新编辑信息</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import { getMyFiling, saveFiling, submitFiling } from '@/api/filing'
import { FILING_STATUS } from '@/api/report'

const dictStore = useDictStore()
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const readonly = ref(false)
const data = ref(null)
const formRef = ref()
const form = reactive({
  orgCode: '', name: '', nature: '', industry: '', mainBusiness: '',
  contact: '', address: '', postcode: '', phone: '', fax: '', email: '',
})

const rules = {
  orgCode:      [{ required: true, message: '请输入组织机构代码', trigger: 'blur' },
                 { pattern: /^[A-Za-z0-9]{1,9}$/, message: '仅字母数字，≤9 位', trigger: 'blur' }],
  name:         [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  nature:       [{ required: true, message: '请选择企业性质', trigger: 'change' }],
  industry:     [{ required: true, message: '请选择所属行业', trigger: 'change' }],
  mainBusiness: [{ required: true, message: '请填写主要经营业务', trigger: 'blur' }],
  contact:      [{ required: true, message: '请填写联系人', trigger: 'blur' }],
  address:      [{ required: true, message: '请填写联系地址', trigger: 'blur' }],
  postcode:     [{ required: true, message: '请填写邮政编码', trigger: 'blur' },
                 { pattern: /^\d{6}$/, message: '6 位数字', trigger: 'blur' }],
  phone:        [{ required: true, message: '请填写联系电话', trigger: 'blur' },
                 { pattern: /^(\d{3,4}[- ]?\d{7,8}|1\d{10})$/, message: '区号-电话 或 11 位手机号', trigger: 'blur' }],
  fax:          [{ required: true, message: '请填写传真', trigger: 'blur' },
                 { pattern: /^\d{3,4}[- ]?\d{7,8}$/, message: '区号-电话', trigger: 'blur' }],
  email:        [{ pattern: /^$|^[^@\s]+@[^@\s]+\.[^@\s]+$/, message: '邮箱格式不正确', trigger: 'blur' }],
}

const statusLabel = computed(() => FILING_STATUS[data.value?.filingStatus]?.label || '—')
const statusTag   = computed(() => FILING_STATUS[data.value?.filingStatus]?.tag || 'info')
const submitLabel = computed(() => data.value?.filingStatus === 'APPROVED' ? '提交变更申请' : '提交备案')

onMounted(async () => {
  loading.value = true
  try {
    await dictStore.load(['ENT_NATURE', 'INDUSTRY'])
    await refresh()
  } finally { loading.value = false }
})

async function refresh() {
  const r = await getMyFiling()
  data.value = r.data
  Object.assign(form, {
    orgCode: r.data.orgCode || '',
    name: r.data.name || '',
    nature: r.data.nature || '',
    industry: r.data.industry || '',
    mainBusiness: r.data.mainBusiness || '',
    contact: r.data.contact || '',
    address: r.data.address || '',
    postcode: r.data.postcode || '',
    phone: r.data.phone || '',
    fax: r.data.fax || '',
    email: r.data.email || '',
  })
  // PENDING/APPROVED 状态下默认只读
  readonly.value = r.data.filingStatus === 'PENDING' || r.data.filingStatus === 'APPROVED'
}

async function onSave() {
  saving.value = true
  try {
    await saveFiling(form)
    ElMessage.success('保存成功')
    await refresh()
  } catch {} finally { saving.value = false }
}

async function onSubmit() {
  await formRef.value.validate()
  await ElMessageBox.confirm('确认提交？提交后审核期间不可修改。', '提示', { type: 'warning' })
  submitting.value = true
  try {
    await submitFiling(form)
    ElMessage.success('已提交备案，等待省级审核')
    await refresh()
  } catch {} finally { submitting.value = false }
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.actions { text-align: right; padding-top: 8px; border-top: 1px solid #eee; }
</style>
