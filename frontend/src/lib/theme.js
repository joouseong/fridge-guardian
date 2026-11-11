// 화면 테마: 시스템 설정 / 라이트 / 다크. 선택은 이 브라우저(localStorage)에만 저장합니다.
// 인증 토큰이 아니라 화면 취향이라 localStorage에 두어도 괜찮고, 서버 API는 바꾸지 않습니다.
const KEY = 'fridge-guardian-theme'
export const themes = { system: '시스템 설정', light: '라이트', dark: '다크' }

export function getThemePreference() {
  try {
    const value = localStorage.getItem(KEY)
    return Object.hasOwn(themes, value) ? value : 'system'
  } catch { return 'system' }
}

export function applyTheme(preference) {
  const dark = preference === 'dark' || (preference === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0b1416' : '#f1f6f4')
}

export function setThemePreference(preference) {
  try { localStorage.setItem(KEY, preference) } catch { /* 저장이 막힌 브라우저에서도 이번 화면에는 적용됩니다. */ }
  applyTheme(preference)
}

// '시스템 설정'을 고른 상태에서 OS 테마가 바뀌면 따라갑니다.
export function watchSystemTheme() {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (getThemePreference() === 'system') applyTheme('system')
  })
}
