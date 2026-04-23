import request from '@/utils/request'

export const listProvFilings = (params) => request.get('/province/filing', { params })
export const getProvFiling = (id) => request.get(`/province/filing/${id}`)
export const provApproveFiling = (id) => request.post(`/province/filing/${id}/approve`)
export const provRejectFiling = (id, reason) => request.post(`/province/filing/${id}/reject`, { reason })
export const exportFilingsUrl = (params) => {
  const qs = new URLSearchParams(params).toString()
  return `/api/province/filing/export?${qs}`
}
