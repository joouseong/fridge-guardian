import { useState } from 'react'
import useResource from '../hooks/useResource.js'
import { formatDate } from '../lib/format.js'
import { api } from '../services/api.js'
import { Dialog, EmptyState, ErrorBox, Link, Loading } from './UI.jsx'

const messages = { EXPIRING: '유통기한이 임박했어요', EXPIRED: '유통기한이 지났어요' }

/** 자정 배치가 알림 큐에 넣은 유통기한 알림을 보여 주는 종 모양 버튼과 알림함입니다. */
export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const inbox = useResource('/notifications')
  const [error, setError] = useState(null)
  async function readAll() {
    setError(null)
    try { await api('/notifications/read-all', { method: 'PUT' }); inbox.reload() } catch (failure) { setError(failure) }
  }
  async function read(id) {
    try { await api(`/notifications/${id}/read`, { method: 'PUT' }); inbox.reload() } catch (failure) { setError(failure) }
  }
  const unread = inbox.data?.unreadCount || 0
  return <>
    <button className="bell-button" aria-label={unread ? `알림 ${unread}개 확인하기` : '알림함 열기'} onClick={() => { setOpen(true); inbox.reload() }}>
      <span aria-hidden="true">🔔</span>{unread > 0 && <span className="bell-count">{unread > 99 ? '99+' : unread}</span>}
    </button>
    {open && <Dialog title="유통기한 알림" onClose={() => setOpen(false)}>
      <div className="dialog-body stack">
        {inbox.loading && <Loading />}
        {inbox.error && <ErrorBox error={inbox.error} onRetry={inbox.reload} />}
        {error && <ErrorBox error={error} />}
        {inbox.data && (inbox.data.items.length ? <ul className="notification-list">{inbox.data.items.map((item) => <li key={item.id} className={item.status === 'READ' ? 'read' : ''}>
          <span className={`status-badge ${item.type === 'EXPIRED' ? 'expired' : 'soon'}`}>{item.type === 'EXPIRED' ? '만료' : '임박'}</span>
          <div><strong>{item.ingredientName}</strong><p className="helper">{messages[item.type]} · {formatDate(item.notifyDate)}</p></div>
          {item.status !== 'READ' && <button className="button ghost small" onClick={() => read(item.id)}>확인</button>}
        </li>)}</ul> : <EmptyState title="새 알림이 없어요">매일 자정에 유통기한이 임박했거나 지난 재료를 알려 드려요.</EmptyState>)}
      </div>
      <div className="dialog-actions"><Link className="button ghost" to="/ingredients?status=SOON" onClick={() => setOpen(false)}>임박 재료 보기</Link><button className="button secondary" onClick={readAll} disabled={!unread}>모두 읽음</button></div>
    </Dialog>}
  </>
}
