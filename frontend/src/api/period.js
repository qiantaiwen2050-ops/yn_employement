import request from '@/utils/request'

export const listPeriods = (year) => request.get('/period', { params: year ? { year } : {} })
export const listOpenPeriods = () => request.get('/period/open')
export const generatePeriods = (year) => request.post('/period/generate', null, { params: { year } })
export const updatePeriodStatus = (id, status) => request.put(`/period/${id}/status`, null, { params: { status } })
