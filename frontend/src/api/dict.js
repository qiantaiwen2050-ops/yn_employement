import request from '@/utils/request'

export const getDict = (type) => request.get(`/dict/${type}`)
export const getBulkDict = (types) => request.get('/dict', {
  params: { types },
  paramsSerializer: { indexes: null },
})
