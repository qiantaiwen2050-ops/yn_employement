import request from '@/utils/request'

export const sampling = (regionCode) => request.get('/province/analysis/sampling', { params: regionCode ? { regionCode } : {} })
export const multidim = (periodId, dimensions) =>
  request.get('/province/analysis/multidim', { params: { periodId, dimensions: dimensions.join(',') } })
export const compare = (periodIdA, periodIdB, dimension) =>
  request.get('/province/analysis/compare', { params: { periodIdA, periodIdB, dimension } })
export const trend = (periodIds, dimension) =>
  request.get('/province/analysis/trend', { params: { periodIds: periodIds.join(','), ...(dimension ? { dimension } : {}) } })
