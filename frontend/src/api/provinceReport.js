import request from '@/utils/request'

export const listProvReports = (params) => request.get('/province/report', { params })
export const getProvReport = (id) => request.get(`/province/report/${id}`)
export const provApproveReport = (id) => request.post(`/province/report/${id}/approve`)
export const provReturnReport = (id, reason) => request.post(`/province/report/${id}/return`, { reason })
export const provSubmitNation = (ids) => request.post('/province/report/submit-nation', { ids })
export const provDeleteReport = (id) => request.delete(`/province/report/${id}`)
export const provReviseReport = (id, dto) => request.post(`/province/report/${id}/revise`, dto)
export const provGetRevisions = (id) => request.get(`/province/report/${id}/revisions`)
export const exportReportsUrl = (params) => {
  const qs = new URLSearchParams(params).toString()
  return `/api/province/report/export?${qs}`
}
