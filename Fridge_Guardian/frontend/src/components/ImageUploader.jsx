import { useEffect, useId, useMemo, useState } from 'react'
import { DEFAULT_IMAGE } from '../lib/format.js'
import { IMAGE_TYPES, validateImage } from '../lib/image.js'

/**
 * 식재료 사진 선택. 새 파일을 고르지 않으면 기존 사진이 유지되고(file=null, removed=false),
 * 사진 없음(기본 이미지)으로 돌리려면 removed=true로 저장합니다.
 */
export default function ImageUploader({ currentUrl, file, removed, onFile, onRemoved, disabled = false }) {
  const inputId = useId()
  const [error, setError] = useState('')
  const preview = useMemo(() => (file ? URL.createObjectURL(file) : null), [file])
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])
  const shown = preview || (removed ? null : currentUrl) || DEFAULT_IMAGE
  function choose(event) {
    const chosen = event.target.files?.[0]
    event.target.value = ''
    if (!chosen) return
    const problem = validateImage(chosen)
    setError(problem)
    if (problem) return
    onRemoved(false)
    onFile(chosen)
  }
  return <div className="image-uploader">
    <img src={shown} alt="식재료 사진 미리보기" width="96" height="96" />
    <div className="stack">
      <label className="button secondary small" htmlFor={inputId}>{file || (currentUrl && !removed) ? '사진 바꾸기' : '사진 선택'}</label>
      <input id={inputId} className="sr-only" type="file" accept={IMAGE_TYPES.join(',')} onChange={choose} disabled={disabled} />
      {(file || (currentUrl && !removed)) && <button type="button" className="button ghost small" disabled={disabled} onClick={() => { onFile(null); if (currentUrl) onRemoved(true) }}>{file ? '선택 취소' : '사진 지우기'}</button>}
      <p className="helper">5MB 이하 JPG·PNG·WEBP. 고르지 않으면 {currentUrl ? '기존 사진이 유지돼요.' : '기본 이미지를 써요.'}</p>
      {error && <p className="error-text" role="alert">{error}</p>}
    </div>
  </div>
}
