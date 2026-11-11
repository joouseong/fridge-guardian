import { useEffect, useState } from 'react'
import BrandMark from './components/BrandMark.jsx'
import NotificationBell from './components/NotificationBell.jsx'
import { ErrorBox, Link, Loading } from './components/UI.jsx'
import { logout as logoutRequest, refreshSession } from './services/api.js'
import { navigate, usePath } from './lib/router.js'
import AuthPage from './pages/AuthPage.jsx'
import AiRecipePage from './pages/AiRecipePage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import IngredientsPage from './pages/IngredientsPage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'
import RecipesPage from './pages/RecipesPage.jsx'
import ChatPage from './pages/ChatPage.jsx'
import ShoppingPage from './pages/ShoppingPage.jsx'

const navigation = [{ to: '/', label: '대시보드', icon: '◫' }, { to: '/ingredients', label: '내 냉장고', icon: '▤' }, { to: '/recipes', label: '오늘의 레시피', icon: '♧' }, { to: '/ai-recipes', label: 'AI 레시피', icon: '✦' }, { to: '/chat', label: 'AI 요리 도우미', icon: '✧' }, { to: '/shopping', label: '장바구니', icon: '🛒' }, { to: '/profile', label: '내 설정', icon: '⚙' }]

export default function App() {
  const path = usePath()
  const [session, setSession] = useState({ loading: true, user: null, error: null })
  const [authNotice, setAuthNotice] = useState('')
  const [authEmail, setAuthEmail] = useState('')
  const [logoutError, setLogoutError] = useState(null)
  const [loggingOut, setLoggingOut] = useState(false)
  const [attempt, setAttempt] = useState(0)
  // 화면을 열거나 새로고침하면 HttpOnly 쿠키의 Refresh Token으로 로그인 상태를 복구합니다.
  // 소셜 로그인 뒤 /oauth/callback으로 돌아왔을 때도 같은 방식으로 Access Token을 받아 옵니다.
  useEffect(() => {
    let cancelled = false
    refreshSession().then(
      (user) => { if (!cancelled) setSession({ loading: false, user, error: null }) },
      (error) => { if (!cancelled) setSession({ loading: false, user: null, error: error.status === 401 ? null : error }) },
    )
    return () => { cancelled = true }
  }, [attempt])
  useEffect(() => {
    if (session.loading || path !== '/oauth/callback') return
    if (session.user) navigate('/', { replace: true })
    else navigate('/login?error=oauth', { replace: true })
  }, [session, path])
  useEffect(() => {
    const expired = () => {
      setSession({ loading: false, user: null, error: null })
      setAuthNotice('로그인 시간이 만료되었어요. 다시 로그인해 주세요.')
      navigate('/login')
    }
    window.addEventListener('session-expired', expired)
    return () => window.removeEventListener('session-expired', expired)
  }, [])
  // 좁은 화면에서는 메뉴가 가로로 스크롤되므로 현재 메뉴가 항상 보이도록 맞춥니다.
  useEffect(() => {
    document.querySelector('.nav-link.active')?.scrollIntoView({ inline: 'center', block: 'nearest' })
  }, [path, session.user])
  function onLogin(user) {
    setSession({ loading: false, user, error: null })
    setAuthNotice('')
    setAuthEmail('')
    setLogoutError(null)
    navigate('/')
  }
  function onRegistered(email) {
    setAuthEmail(email)
    setAuthNotice('가입이 완료되었어요. 입력한 이메일과 비밀번호로 로그인해 주세요.')
    navigate('/login', { replace: true })
  }
  async function logout() {
    setLoggingOut(true)
    setLogoutError(null)
    try {
      await logoutRequest()
      setSession({ loading: false, user: null, error: null })
      setAuthNotice('안전하게 로그아웃했어요.')
      navigate('/login')
    } catch (error) { setLogoutError(error) } finally { setLoggingOut(false) }
  }
  if (session.loading || path === '/oauth/callback') return <div className="auth-form-wrap"><Loading label={path === '/oauth/callback' ? '로그인을 마무리하고 있어요…' : '내 냉장고를 준비하고 있어요…'} /></div>
  if (session.error) return <div className="auth-form-wrap"><div className="auth-card"><h1>냉장고 문지기</h1><ErrorBox error={session.error} onRetry={() => { setSession({ loading: true, user: null, error: null }); setAttempt((value) => value + 1) }} /></div></div>
  if (!session.user) return <AuthPage key={path === '/signup' ? 'signup' : 'login'} signup={path === '/signup'} onLogin={onLogin} onRegistered={onRegistered} initialEmail={authEmail} notice={authNotice} />
  const current = navigation.find((item) => item.to === path)
  let page
  if (path === '/ingredients') page = <IngredientsPage />
  else if (path === '/recipes') page = <RecipesPage />
  else if (path === '/ai-recipes') page = <AiRecipePage />
  else if (path === '/chat') page = <ChatPage />
  else if (path === '/shopping') page = <ShoppingPage />
  else if (path === '/profile') page = <ProfilePage user={session.user} onUserChange={(user) => setSession({ loading: false, user, error: null })} />
  else if (['/', '/login', '/signup'].includes(path)) page = <DashboardPage user={session.user} />
  else page = <div className="empty-state"><h1>페이지를 찾을 수 없어요</h1><Link className="button" to="/">대시보드로 돌아가기</Link></div>
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">본문 바로가기</a>
    <aside className="sidebar"><Link className="brand" to="/"><BrandMark /><span>냉장고 문지기<small className="brand-subtitle">FRIDGE GUARDIAN</small></span></Link>
      <nav aria-label="주 메뉴">{navigation.map((item) => <Link key={item.to} to={item.to} className={`nav-link ${item.to === path || (item.to === '/' && ['/login', '/signup'].includes(path)) ? 'active' : ''}`} aria-current={item.to === path ? 'page' : undefined}><span aria-hidden="true">{item.icon}</span>{item.label}</Link>)}</nav>
      <div className="sidebar-foot"><span aria-hidden="true">🌿</span><p>식재료는 알뜰하게,<br />오늘의 한 끼는 맛있게.</p></div>
    </aside>
    <div className="workspace"><header className="topbar"><p>{current?.label || '나의 작은 주방'}</p><div className="row-actions"><NotificationBell /><span className="user-pill">{session.user.nickname}님</span><button className="button ghost small" onClick={logout} disabled={loggingOut}>{loggingOut ? '로그아웃 중…' : '로그아웃'}</button></div></header>
      <main id="main-content" className="main-content" tabIndex={-1}>{logoutError && <ErrorBox error={logoutError} onRetry={logout} />}{page}</main>
      <footer className="auth-footer">냉장고 문지기 · 날짜와 영양 정보를 살펴 더 알뜰한 한 끼를 준비해요.</footer>
    </div>
  </div>
}
