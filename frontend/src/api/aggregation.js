import request from '@/utils/request'

export const aggregate = (periodId, dimension) => request.get('/province/aggregation', { params: { periodId, dimension } })
export const aggregationExportUrl = (periodId, dimension) =>
  `/api/province/aggregation/export?periodId=${periodId}&dimension=${dimension}`
