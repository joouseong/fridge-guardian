import { useId, useState } from 'react'

/** 알레르기·비선호 재료처럼 짧은 이름 여러 개를 칩으로 추가·삭제하는 입력입니다. */
export default function TagInput({ label, hint, values, onChange, placeholder, max = 30, disabled = false }) {
  const [text, setText] = useState('')
  const inputId = useId()
  function add() {
    const name = text.trim()
    if (!name) return
    if (values.some((value) => value.replace(/\s+/g, '') === name.replace(/\s+/g, ''))) { setText(''); return }
    if (values.length >= max) return
    onChange([...values, name])
    setText('')
  }
  return <div className="tag-input">
    <label className="field" htmlFor={inputId}>{label}</label>
    <div className="inline-form">
      <input id={inputId} value={text} maxLength={80} placeholder={placeholder} disabled={disabled}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); add() } }} />
      <button type="button" className="button secondary" onClick={add} disabled={disabled || !text.trim() || values.length >= max}>추가</button>
    </div>
    {hint && <p className="helper">{hint}</p>}
    {values.length > 0 && <ul className="tag-list" aria-label={`${label} 목록`}>{values.map((value) => <li key={value} className="tag">{value}<button type="button" aria-label={`${value} 삭제`} disabled={disabled} onClick={() => onChange(values.filter((item) => item !== value))}>×</button></li>)}</ul>}
  </div>
}
