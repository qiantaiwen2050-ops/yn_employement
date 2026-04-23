import request from '@/utils/request'

export const listCityReviews = (params) => request.get('/city/review', { params })
export const getCityReview = (id) => request.get(`/city/review/${id}`)
export const cityApprove = (id) => request.post(`/city/review/${id}/approve`)
export const cityReturn = (id, reason) => request.post(`/city/review/${id}/return`, { reason })
export const cityBatchApprove = (ids) => request.post('/city/review/batch-approve', { ids })
export const citySubmitToProvince = (ids) => request.post('/city/review/submit-province', { ids })
