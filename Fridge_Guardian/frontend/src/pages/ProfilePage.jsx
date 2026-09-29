import { useState } from 'react'
import TagInput from '../components/TagInput.jsx'
import { ErrorBox, Loading, PageHeading } from '../components/UI.jsx'
import useResource from '../hooks/useResource.js'
import { spicyLevels } from '../lib/format.js'
import { api } from '../services/api.js'

/** 알레르기·비선호 재료·매운맛 선호. 저장하면 레시피 추천과 AI 레시피 생성에 바로 반영돼요. */
export default function ProfilePage({ user }) {
  const { data, loading, error, reload } = useResource('/users/me/preferences')
  // 저장 뒤 다시 불러오면 폼이 새로 만들어지므로 성공 안내는 폼 밖(이 컴포넌트)에서 보관합니다.
  const [message, setMessage] = useState('')
  return <>
    <PageHeading eyebrow="MY PREFERENCES" title="내 설정">{user.nickname}님의 식습관에 맞춰 레시피를 걸러 드려요.</PageHeading>
    <section className="panel">
      {message && <p className="success-box" role="status">{message}</p>}
      {loading && <Loading />}{error && <ErrorBox error={error} onRetry={reload} />}
      {/* 서버에서 받은 값이 바뀌면(저장 뒤 다시 불러오면) 폼을 새로 만들어 그 값으로 시작합니다. */}
      {data && <PreferencesForm key={JSON.stringify(data)} initial={data} onSaved={() => { setMessage('설정을 저장했어요. 이제 추천과 AI 레시피에 반영돼요.'); reload() }} onEdit={() => setMessage('')} />}
    </section>
  </>
}

function PreferencesForm({ initial, onSaved, onEdit }) {
  const [form, setForm] = useState({ spicyLevel: initial.spicyLevel, allergies: initial.allergies, dislikes: initial.dislikes })
  const [state, setState] = useState({ busy: false, error: null })
  async function save(event) {
    event.preventDefault()
    onEdit()
    setState({ busy: true, error: null })
    try {
      await api('/users/me/preferences', { method: 'PUT', body: form })
      setState({ busy: false, error: null })
      onSaved()
    } catch (failure) { setState({ busy: false, error: failure }) }
  }
  return <form className="stack" onSubmit={save}>
    <label className="field">먹을 수 있는 매운맛
      <select value={form.spicyLevel} onChange={(event) => setForm({ ...form, spicyLevel: Number(event.target.value) })} disabled={state.busy}>
        {Object.entries(spicyLevels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
    <p className="helper">고른 단계보다 매운 레시피는 추천 목록에서 빠져요.</p>
    <TagInput label="알레르기 재료" hint="예: 우유(치즈·버터 포함), 계란, 땅콩, 새우. 대표 이름을 넣으면 관련 재료도 함께 걸러요." values={form.allergies} onChange={(allergies) => setForm({ ...form, allergies })} placeholder="알레르기가 있는 재료" disabled={state.busy} />
    <TagInput label="비선호 재료" hint="예: 오이, 해산물. 재료 이름에 들어 있으면 그 레시피는 추천에서 빠져요." values={form.dislikes} onChange={(dislikes) => setForm({ ...form, dislikes })} placeholder="먹고 싶지 않은 재료" disabled={state.busy} />
    <p className="notice">재료 이름을 기준으로 거르기 때문에 모든 성분을 보장하지 않아요. 알레르기가 있다면 조리 전에 원재료를 꼭 확인해 주세요.</p>
    {state.error && <ErrorBox error={state.error} />}
    <div className="row-actions"><button className="button" disabled={state.busy}>{state.busy ? '저장 중…' : '설정 저장'}</button></div>
  </form>
}
