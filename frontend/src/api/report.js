import request from '@/utils/request'

export const listReports = (params) => request.get('/enterprise/report', { params })
export const listMyAttempts = (params) => request.get('/enterprise/report/attempts', { params })
export const getReportDraft = (periodId) => request.get('/enterprise/report/draft', { params: { periodId } })
export const getReport = (id) => request.get(`/enterprise/report/${id}`)
export const saveReport = (data) => request.put('/enterprise/report', data)
export const submitReport = (data) => request.post('/enterprise/report/submit', data)

export const REPORT_STATUS = {
  DRAFT: { label: '草稿', tag: 'info' },
  '01': { label: '待市级审核', tag: 'warning' },
  '02': { label: '市级已通过', tag: '' },
  '03': { label: '市级退回', tag: 'danger' },
  '04': { label: '待省级审核', tag: 'warning' },
  '05': { label: '省级已通过', tag: 'success' },
  '06': { label: '省级退回', tag: 'danger' },
  '07': { label: '已汇总上报', tag: 'success' },
}

export const FILING_STATUS = {
  DRAFT:    { label: '未提交',  tag: 'info' },
  PENDING:  { label: '待审核',  tag: 'warning' },
  APPROVED: { label: '已备案',  tag: 'success' },
  REJECTED: { label: '退回修改', tag: 'danger' },
}
