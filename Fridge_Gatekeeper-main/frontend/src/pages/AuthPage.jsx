import { useState } from 'react'
import BrandMark from '../components/BrandMark.jsx'
import { ErrorBox, Link } from '../components/UI.jsx'
import { api, refreshCsrf } from '../services/api.js'

export default function AuthPage({ signup: isSignup, onLogin, onRegistered, initialEmail, notice }) {
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  async function submit(event) {
    event.preventDefault()
    setError(null)
    const data = Object.fromEntries(new FormData(event.currentTarget))
    data.email = data.email.trim()
    if (isSignup) data.nickname = data.nickname.trim()
    if (isSignup && new TextEncoder().encode(data.password).length > 72) {
      setError(new Error('비밀번호는 UTF-8 기준 72바이트 이하여야 해요. 한글·특수문자는 여러 바이트일 수 있어요.'))
      return
    }
    setBusy(true)
    try {
      if (isSignup) {
        await api('/auth/register', { method: 'POST', body: data, silentAuth: true })
        onRegistered(data.email)
      } else {
        const user = await api('/auth/login', { method: 'POST', body: { email: data.email, password: data.password }, silentAuth: true })
        await refreshCsrf().catch(() => {})
        onLogin(user)
      }
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  return <div className="auth-layout">
    <section className="auth-story"><Link className="brand" to="/login"><BrandMark /><span>냉장고 지킴이</span></Link><div><p className="eyebrow">A LITTLE CARE, A BETTER MEAL</p><h1>냉장고 속 재료가<br /><span>맛있는 한 끼로.</span></h1><p className="muted">무엇이 남았는지, 무엇부터 먹을지.<br />우리 집 냉장고를 함께 돌봐요.</p><div className="auth-art" aria-hidden="true"><span>🥬</span><span>🥕</span><span>🥚</span><span>🍅</span><BrandMark /></div><div className="auth-feature"><span>01</span><p>한눈에 살펴보는 식재료와 유통기한</p></div><div className="auth-feature"><span>02</span><p>내 재료로 만드는 1·2인분 레시피</p></div><div className="auth-feature"><span>03</span><p>오늘의 메뉴를 함께 고민하는 요리 도우미</p></div></div><p className="auth-footer">작은 관리가 만드는, 낭비 없는 일상.</p></section>
    <main className="auth-form-wrap"><div className="auth-card"><p className="eyebrow">WELCOME TO YOUR KITCHEN</p><h2>{isSignup ? '나만의 냉장고 시작하기' : '다시 만나 반가워요'}</h2><p className="muted">{isSignup ? '간단한 가입으로 식재료 관리를 시작하세요.' : '로그인하고 오늘의 냉장고를 확인해 보세요.'}</p>
      {!isSignup && notice && <p className="success-box" role="status">{notice}</p>}
      {error && <ErrorBox error={error} />}
      <form className="stack" onSubmit={submit}>
        {isSignup && <label className="field">닉네임<input name="nickname" autoComplete="nickname" placeholder="어떻게 불러드릴까요?" required maxLength={30} disabled={busy} /></label>}
        <label className="field">이메일<input name="email" type="email" autoComplete="username" defaultValue={isSignup ? '' : initialEmail} placeholder="you@example.com" required maxLength={254} disabled={busy} /></label>
        <label className="field">비밀번호<input name="password" type="password" autoComplete={isSignup ? 'new-password' : 'current-password'} placeholder={isSignup ? '8자 이상 입력해 주세요' : '비밀번호를 입력해 주세요'} minLength={isSignup ? 8 : undefined} maxLength={72} required disabled={busy} /></label>
        {isSignup && <p className="helper">비밀번호는 8자 이상, UTF-8 기준 72바이트 이하로 입력해 주세요.</p>}
        <button className="button full-width" disabled={busy}>{busy ? '잠시만 기다려 주세요…' : isSignup ? '회원가입' : '로그인'}</button>
      </form><p className="auth-switch">{isSignup ? '이미 계정이 있나요?' : '아직 계정이 없나요?'} <Link className="text-link" to={isSignup ? '/login' : '/signup'}>{isSignup ? '로그인' : '회원가입'}</Link></p>
    </div></main>
  </div>
}
