import { useEffect, useId, useRef, useState } from 'react'

/**
 * 웹 카메라로 가공식품 바코드를 읽습니다(html5-qrcode). 카메라는 HTTPS 또는 localhost에서만 열려요.
 * 라이브러리는 스캔 화면을 열 때에만 불러와 첫 화면 용량을 늘리지 않습니다.
 */
export default function BarcodeScanner({ onDetected, onClose }) {
  const regionId = `barcode-${useId().replace(/[^a-zA-Z0-9]/g, '')}`
  const [error, setError] = useState('')
  const [manual, setManual] = useState('')
  // 부모가 다시 그려질 때마다 카메라를 새로 켜지 않도록 콜백은 ref로 들고 있습니다.
  const detected = useRef(onDetected)
  useEffect(() => { detected.current = onDetected })
  useEffect(() => {
    let scanner = null
    let cancelled = false
    let startSettled = false
    let stopPromise = null
    let shutdownPromise = null
    let scannerStates = null
    const stopScanner = () => {
      if (stopPromise) return stopPromise
      try {
        const state = scanner?.getState()
        if (state === scannerStates?.SCANNING || state === scannerStates?.PAUSED) stopPromise = Promise.resolve(scanner.stop()).catch(() => {})
        else stopPromise = Promise.resolve()
      } catch { stopPromise = Promise.resolve() }
      return stopPromise
    }
    const shutdown = () => {
      if (!shutdownPromise) shutdownPromise = stopScanner().finally(() => {
        try { scanner?.clear() } catch { /* 이미 정리됐거나 시작되지 않음 */ }
      })
      return shutdownPromise
    }
    ;(async () => {
      try {
        const { Html5Qrcode, Html5QrcodeSupportedFormats, Html5QrcodeScannerState } = await import('html5-qrcode')
        if (cancelled) return
        scannerStates = Html5QrcodeScannerState
        scanner = new Html5Qrcode(regionId, { verbose: false, formatsToSupport: [
          Html5QrcodeSupportedFormats.EAN_13, Html5QrcodeSupportedFormats.EAN_8,
          Html5QrcodeSupportedFormats.UPC_A, Html5QrcodeSupportedFormats.UPC_E] })
        await scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: { width: 260, height: 140 } },
          (code) => {
            if (cancelled || shutdownPromise) return
            void shutdown().finally(() => detected.current(code))
          }, () => {}) // 프레임마다 나오는 '인식 실패'는 무시합니다.
        startSettled = true
        if (cancelled) await shutdown()
      } catch {
        startSettled = true
        if (!cancelled) setError('카메라를 사용할 수 없어요. 카메라 권한을 확인하거나 아래에 바코드 숫자를 직접 입력해 주세요.')
        else await shutdown()
      }
    })()
    return () => {
      cancelled = true
      // start()가 진행 중일 때 stop()을 부르면 라이브러리가 동기 예외를 던집니다.
      // start가 끝난 뒤에만 정리하고, 아직 시작 중이면 위의 await 이후 정리합니다.
      if (scanner && startSettled) void shutdown()
    }
  }, [regionId])
  return <>
    <div className="dialog-body stack">
      <p className="muted">가공식품 뒷면의 바코드를 네모 칸에 맞춰 주세요.</p>
      <div id={regionId} className="scanner-region" aria-label="바코드 스캔 영역" />
      {error && <p className="error-text" role="alert">{error}</p>}
      <form className="inline-form" onSubmit={(event) => { event.preventDefault(); if (/^\d{6,14}$/.test(manual.trim())) onDetected(manual.trim()) }}>
        <label className="field">바코드 숫자 직접 입력<input inputMode="numeric" value={manual} onChange={(event) => setManual(event.target.value)} placeholder="예: 8801234567890" maxLength={14} /></label>
        <button className="button secondary" disabled={!/^\d{6,14}$/.test(manual.trim())}>조회</button>
      </form>
    </div>
    <div className="dialog-actions"><button type="button" className="button secondary" onClick={onClose}>닫기</button></div>
  </>
}
