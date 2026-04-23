import request from './request'

/**
 * Download a file from a backend endpoint that returns binary; the JWT auth header is preserved.
 * Used for CSV exports.
 */
export async function downloadFile(url, params, fallbackName = 'export.csv') {
  // The response interceptor returns the raw AxiosResponse when the body isn't a `code/message/data` envelope.
  const resp = await request.get(url, { params, responseType: 'blob' })
  const blob = resp.data
  const a = document.createElement('a')
  const objectUrl = URL.createObjectURL(blob)
  a.href = objectUrl
  a.download = fallbackName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}
