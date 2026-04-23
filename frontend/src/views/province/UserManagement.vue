<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-header">
        <span>用户管理</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>
    </template>

    <el-form inline>
      <el-form-item label="用户类型">
        <el-select v-model="filter.userType" clearable placeholder="全部" style="width:140px" @change="refresh">
          <el-option label="省级" value="province" />
          <el-option label="市级" value="city" />
          <el-option label="企业" value="enterprise" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属地区">
        <el-select v-model="filter.regionCode" clearable placeholder="全部" style="width:140px" @change="refresh">
          <el-option v-for="o in dictStore.get('REGION')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input v-model="filter.keyword" placeholder="账号或姓名" clearable style="width:200px" @keyup.enter="refresh" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="refresh">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" border stripe>
      <el-table-column prop="username" label="登录账号" width="120" />
      <el-table-column prop="realName" label="姓名" min-width="140" />
      <el-table-column label="用户类型" width="100">
        <template #default="{row}">
          <el-tag :type="TYPE_TAG[row.userType]">{{ TYPE_LABEL[row.userType] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="regionName" label="所属地区" width="120">
        <template #default="{row}">{{ row.regionName || '—' }}</template>
      </el-table-column>
      <el-table-column label="关联角色" min-width="220">
        <template #default="{row}">
          <el-tag v-for="rid in row.roleIds" :key="rid" size="small" effect="plain" style="margin-right:4px">
            {{ roleMap.get(rid)?.name || rid }}
          </el-tag>
          <span v-if="!row.roleIds?.length" style="color:#999">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{row}">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="primary" @click="onResetPwd(row)">重置密码</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" :title="form.id ? '修改用户' : '新增用户'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="全系统唯一" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码">
          <el-input v-model="form.password" placeholder="留空则使用 123456" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="用户类型" prop="userType">
          <el-select v-model="form.userType" :disabled="!!form.id" style="width:100%">
            <el-option label="省级" value="province" />
            <el-option label="市级" value="city" />
            <el-option label="企业" value="enterprise" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.userType !== 'province'" label="所属地区" prop="regionCode">
          <el-select v-model="form.regionCode" filterable style="width:100%" @change="onRegionChange">
            <el-option v-for="o in dictStore.get('REGION')" :key="o.itemCode" :label="o.itemName" :value="o.itemCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="为该用户分配角色" style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :label="1">启用</el-radio-button>
            <el-radio-button :label="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDictStore } from '@/stores/dict'
import { listUsers, createUser, updateUser, deleteUser, resetUserPassword, listRoles } from '@/api/system'

const dictStore = useDictStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const roles = ref([])
const filter = reactive({ userType: '', regionCode: '', keyword: '' })

const editVisible = ref(false)
const formRef = ref()
const form = reactive(blank())
function blank() {
  return { id: null, username: '', password: '', realName: '', userType: 'city', regionCode: '', regionName: '', status: 1, roleIds: [] }
}

const TYPE_LABEL = { province: '省级', city: '市级', enterprise: '企业' }
const TYPE_TAG = { province: 'danger', city: 'warning', enterprise: 'success' }

const roleMap = computed(() => new Map(roles.value.map(r => [r.id, r])))

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }],
  regionCode: [{
    validator: (_, v, cb) => (form.userType !== 'province' && !v) ? cb(new Error('市级 / 企业用户必须选择地区')) : cb(),
    trigger: 'change',
  }],
  roleIds: [{ validator: (_, v, cb) => (!v || !v.length) ? cb(new Error('至少选择 1 个角色')) : cb(), trigger: 'change' }],
}

onMounted(async () => {
  await dictStore.load(['REGION'])
  await Promise.all([refresh(), refreshRoles()])
})

async function refresh() {
  loading.value = true
  try {
    const r = await listUsers({
      userType: filter.userType || undefined,
      regionCode: filter.regionCode || undefined,
      keyword: filter.keyword || undefined,
    })
    rows.value = r.data || []
  } finally { loading.value = false }
}

async function refreshRoles() {
  const r = await listRoles()
  roles.value = r.data || []
}

function reset() { Object.assign(filter, { userType: '', regionCode: '', keyword: '' }); refresh() }

function openCreate() { Object.assign(form, blank()); editVisible.value = true }
function openEdit(row) {
  Object.assign(form, { ...blank(), ...row, password: '' })
  editVisible.value = true
}
function onRegionChange(code) {
  const item = dictStore.get('REGION').find(o => o.itemCode === code)
  form.regionName = item ? item.itemName : ''
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) await updateUser(form.id, form)
    else await createUser(form)
    ElMessage.success(form.id ? '已修改' : '已创建')
    editVisible.value = false
    refresh()
  } catch {} finally { saving.value = false }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确认删除用户 [${row.username}]？\n删除不可恢复。`, '请二次确认', { type: 'error', confirmButtonText: '确认删除' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  refresh()
}

async function onResetPwd(row) {
  await ElMessageBox.confirm(`将 [${row.username}] 的密码重置为 123456？`, '提示', { type: 'warning' })
  await resetUserPassword(row.id)
  ElMessage.success('密码已重置为 123456')
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
