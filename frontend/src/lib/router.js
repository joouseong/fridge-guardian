import { useSyncExternalStore } from 'react'

const subscribe = (callback) => {
  window.addEventListener('popstate', callback)
  return () => window.removeEventListener('popstate', callback)
}
export function usePath() { return useSyncExternalStore(subscribe, () => window.location.pathname) }
export function useSearch() { return useSyncExternalStore(subscribe, () => window.location.search) }
export function navigate(path, { replace = false, scroll = true } = {}) {
  const target = new URL(path, window.location.href)
  if (target.href !== window.location.href) window.history[replace ? 'replaceState' : 'pushState'](null, '', target)
  window.dispatchEvent(new PopStateEvent('popstate'))
  if (scroll) window.scrollTo({ top: 0 })
}
