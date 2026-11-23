export function formatPrice(value) {
  const num = Number(value || 0)
  return num.toFixed(2)
}

export const ORDER_STATUS = {
  PENDING: { text: '待发货', type: 'warning' },
  SHIPPED: { text: '已发货', type: 'primary' },
  COMPLETED: { text: '已完成', type: 'success' },
  CANCELLED: { text: '已取消', type: 'info' },
}

export function orderStatusText(status) {
  return ORDER_STATUS[status]?.text || status
}

export function orderStatusType(status) {
  return ORDER_STATUS[status]?.type || 'info'
}

export function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(
    d.getHours(),
  )}:${pad(d.getMinutes())}`
}
