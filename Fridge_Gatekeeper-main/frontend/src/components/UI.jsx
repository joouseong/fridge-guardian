import { useEffect, useId, useRef } from 'react'
import { navigate } from '../lib/router.js'
import { statuses } from '../lib/format.js'

export function Link({ to, children, onClick, ...props }) {
  return <a href={to} {...props} onClick={(event) => {
    onClick?.(event)
    if (!event.defaultPrevented && (!props.target || props.target === '_self') && props.download === undefined && event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey && new URL(to, window.location.href).origin === window.location.origin) {
      event.preventDefault()
      navigate(to)
    }
  }}>{children}</a>
}
export function Loading({ label = '불러오는 중이에요…' }) { return <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />{label}</div> }
export function ErrorBox({ error, onRetry }) { return <div className="error-box" role="alert"><p>{error?.message || '잠시 문제가 생겼어요.'}</p>{error?.fieldErrors && Object.keys(error.fieldErrors).length > 0 && <ul>{Object.entries(error.fieldErrors).map(([field, message]) => <li key={field}>{message}</li>)}</ul>}{onRetry && <button type="button" className="button secondary small" onClick={onRetry}>다시 시도</button>}</div> }
export function EmptyState({ title, children, action }) { return <div className="empty-state"><span aria-hidden="true">🌱</span><h3>{title}</h3><p>{children}</p>{action}</div> }
export function StatusBadge({ status }) { return <span className={`status-badge ${status.toLowerCase()}`}>{statuses[status] || status}</span> }
export function PageHeading({ eyebrow, title, children, action }) { return <div className="page-heading"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{children && <p className="muted">{children}</p>}</div>{action && <div className="actions">{action}</div>}</div> }
export function ServingControl({ value, onChange, disabled = false }) {
  return <fieldset className="serving-control" disabled={disabled}><legend>인분 선택</legend>{[1, 2].map((count) => <button type="button" aria-pressed={value === count} className={value === count ? 'active' : ''} onClick={() => onChange(count)} key={count}>{count}인분</button>)}</fieldset>
}
export function SelectOptions({ values }) { return Object.entries(values).map(([value, label]) => <option value={value} key={value}>{label}</option>) }

// 브라우저 기본 dialog는 포커스를 안에 유지하고 Escape로 닫는 동작을 제공합니다.
export function Dialog({ title, children, onClose, busy = false, wide = false, className = '' }) {
  const ref = useRef(null)
  const titleId = useId()
  useEffect(() => {
    const dialog = ref.current
    const previousFocus = document.activeElement
    dialog.showModal()
    return () => { dialog.close(); previousFocus?.focus() }
  }, [])
  return <dialog ref={ref} className={`dialog ${wide ? 'wide' : ''} ${className}`} aria-labelledby={titleId} onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}>
    <div className="dialog-header"><h2 id={titleId}>{title}</h2><button className="icon-button" aria-label="닫기" onClick={onClose} disabled={busy}>×</button></div>{children}
  </dialog>
}
