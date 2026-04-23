import request from '@/utils/request'

// ===== Users =====
export const listUsers = (params) => request.get('/system/user', { params })
export const getUser = (id) => request.get(`/system/user/${id}`)
export const createUser = (data) => request.post('/system/user', data)
export const updateUser = (id, data) => request.put(`/system/user/${id}`, data)
export const deleteUser = (id) => request.delete(`/system/user/${id}`)
export const resetUserPassword = (id) => request.post(`/system/user/${id}/reset-password`)

// ===== Roles =====
export const listRoles = () => request.get('/system/role')
export const getRole = (id) => request.get(`/system/role/${id}`)
export const createRole = (data) => request.post('/system/role', data)
export const updateRole = (id, data) => request.put(`/system/role/${id}`, data)
export const deleteRole = (id) => request.delete(`/system/role/${id}`)
export const listPermissions = () => request.get('/system/role/permissions')

// ===== Monitor =====
export const monitorSnapshot = () => request.get('/system/monitor')

// ===== Operation log =====
export const listSysLogs = (params) => request.get('/system/log', { params })

// ===== Nation API =====
export const nationUpload = (periodId) => request.post('/province/nation/upload', null, { params: { periodId } })
export const nationRetry = (logId) => request.post(`/province/nation/retry/${logId}`)
export const nationLogs = (params) => request.get('/province/nation/log', { params })
export const nationLogDetail = (id) => request.get(`/province/nation/log/${id}`)
