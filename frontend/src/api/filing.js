import request from '@/utils/request'

export const getMyFiling = () => request.get('/enterprise/filing')
export const saveFiling = (data) => request.put('/enterprise/filing', data)
export const submitFiling = (data) => request.post('/enterprise/filing/submit', data)
