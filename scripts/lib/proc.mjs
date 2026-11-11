// Windows·macOS·Linux 공통 프로세스 도우미. 외부 패키지 없이 Node 기본 모듈만 씁니다.
import { spawn, spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const isWin = process.platform === 'win32'
export const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const quote = (value) => (/[\s"&|<>^()]/.test(value) ? `"${value.replaceAll('"', '\\"')}"` : value)

/** Windows의 .cmd 파일(mvnw.cmd, npm.cmd)은 셸을 거쳐야 실행되므로 한 줄 명령으로 만들어 넘깁니다. */
function prepare(command, args) {
  return isWin ? { file: [command, ...args].map(quote).join(' '), args: [], shell: true } : { file: command, args, shell: false }
}

export function run(command, args = [], options = {}) {
  const { file, args: finalArgs, shell } = prepare(command, args)
  return spawn(file, finalArgs, { stdio: 'inherit', shell, ...options })
}

/** 끝날 때까지 기다립니다. 실패하면 종료 코드를 그대로 돌려줍니다. */
export function runSync(command, args = [], options = {}) {
  const { file, args: finalArgs, shell } = prepare(command, args)
  return spawnSync(file, finalArgs, { stdio: 'inherit', shell, ...options })
}

/** 출력만 필요할 때(버전 확인 등). 실패하면 null. */
export function capture(command, args = []) {
  const { file, args: finalArgs, shell } = prepare(command, args)
  const result = spawnSync(file, finalArgs, { encoding: 'utf8', shell })
  if (result.error || result.status !== 0) return null
  return `${result.stdout ?? ''}${result.stderr ?? ''}`
}

/** 자식과 그 하위 프로세스까지 종료합니다. */
export function killTree(child) {
  if (!child || child.exitCode !== null || !child.pid) return
  if (isWin) spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
  else child.kill('SIGTERM')
}
