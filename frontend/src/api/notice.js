import request from '@/utils/request'

export const listVisibleNotices = (keyword) => request.get('/notice', { params: keyword ? { keyword } : {} })
export const listMyNotices = () => request.get('/notice/mine')
export const getNotice = (id) => request.get(`/notice/${id}`)
export const createNotice = (data) => request.post('/notice', data)
export const updateNotice = (id, data) => request.put(`/notice/${id}`, data)
export const deleteNotice = (id) => request.delete(`/notice/${id}`)
