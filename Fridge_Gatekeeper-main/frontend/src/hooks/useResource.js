import { useCallback, useEffect, useState } from 'react'
import { api } from '../services/api.js'

// URL이나 새로고침 번호가 바뀌면 다시 조회하고, 이전 화면의 요청은 취소합니다.
export default function useResource(path) {
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState({ key: null, data: null, error: null })
  const key = `${path}:${revision}`
  useEffect(() => {
    if (!path) return
    const controller = new AbortController()
    api(path, { signal: controller.signal }).then(
      (data) => { if (!controller.signal.aborted) setResult({ key, data, error: null }) },
      (error) => { if (!controller.signal.aborted && error.name !== 'AbortError') setResult({ key, data: null, error }) },
    )
    return () => controller.abort()
  }, [path, key])
  const reload = useCallback(() => setRevision((value) => value + 1), [])
  return { data: result.key === key ? result.data : null, error: result.key === key ? result.error : null, loading: Boolean(path) && result.key !== key, reload }
}
