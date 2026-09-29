#!/usr/bin/env node
// Windows·macOS·Linux 공통 개발 명령. 사용법은 `node scripts/dev.mjs help` 또는 루트의 `npm run <명령>`.
import fs from 'node:fs'
import path from 'node:path'
import { backendEnv, readConnection } from './lib/env.mjs'
import { startDb, stopDb } from './lib/db.mjs'
import { isWin, killTree, projectRoot, run, runSync } from './lib/proc.mjs'

const backendDir = path.join(projectRoot, 'backend')
const frontendDir = path.join(projectRoot, 'frontend')
const mvn = (...args) => (isWin ? [path.join(backendDir, 'mvnw.cmd'), args] : ['sh', [path.join(backendDir, 'mvnw'), ...args]])

function spawnBackend(goal) {
  const [cmd, args] = mvn(...goal)
  return run(cmd, args, { cwd: backendDir, env: backendEnv() })
}
function need(status, what) { if (status !== 0) { console.error(`\n✖ ${what} 실패`); process.exit(status || 1) } }
const npm = isWin ? 'npm.cmd' : 'npm'

const commands = {
  async db() { await startDb({ port: Number(process.env.MYSQL_PORT || readConnection()?.port || 3307) }) },
  async 'db-stop'() { await stopDb() },
  async backend() { const child = spawnBackend(['spring-boot:run']); child.on('exit', (code) => process.exit(code ?? 0)); forward(child) },
  async frontend() {
    if (!fs.existsSync(path.join(frontendDir, 'node_modules'))) need(runSync(npm, ['ci'], { cwd: frontendDir }).status, '프런트 의존성 설치')
    const child = run(npm, ['run', 'dev'], { cwd: frontendDir }); child.on('exit', (code) => process.exit(code ?? 0)); forward(child)
  },
  /** DB → 백엔드 → 프런트를 한 터미널에서. Ctrl+C로 백엔드·프런트를 함께 끕니다(DB는 유지). */
  async dev() {
    await commands.db()
    if (!fs.existsSync(path.join(frontendDir, 'node_modules'))) need(runSync(npm, ['ci'], { cwd: frontendDir }).status, '프런트 의존성 설치')
    const children = [spawnBackend(['spring-boot:run']), run(npm, ['run', 'dev'], { cwd: frontendDir })]
    forward(...children)
    await new Promise(() => {})
  },
  /** 백엔드 테스트(H2, MySQL 불필요) + 프런트 테스트·lint·빌드. */
  async verify() {
    const [cmd, args] = mvn('--batch-mode', '--no-transfer-progress', 'verify')
    need(runSync(cmd, args, { cwd: backendDir, env: backendEnv() }).status, '백엔드 검증')
    need(runSync(npm, ['ci'], { cwd: frontendDir }).status, '프런트 의존성 설치')
    for (const script of ['test', 'lint', 'build']) need(runSync(npm, script === 'test' ? ['test'] : ['run', script], { cwd: frontendDir }).status, `프런트 ${script}`)
    console.log('\n✔ 백엔드 검증과 프런트 테스트·lint·빌드가 모두 통과했어요.')
  },
  async 'test-backend'() { const [cmd, args] = mvn('--batch-mode', 'test'); process.exit(runSync(cmd, args, { cwd: backendDir, env: backendEnv() }).status ?? 1) },
  /** 브라우저 E2E. DB·백엔드·프런트가 떠 있어야 합니다. */
  async e2e() {
    const dir = path.join(projectRoot, 'e2e')
    if (!fs.existsSync(path.join(dir, 'node_modules'))) need(runSync(npm, ['ci'], { cwd: dir }).status, 'E2E 의존성 설치')
    process.exit(runSync(npm, ['test'], { cwd: dir }).status ?? 1)
  },
  help() {
    console.log(`명령 (npm run <명령> 또는 node scripts/dev.mjs <명령>)
  dev           DB + 백엔드(8080) + 프런트(5173)를 한 번에 실행
  db / db-stop  프로젝트 MySQL(3307) 시작 / 종료 (데이터 보존)
  backend       백엔드만 실행    frontend  프런트만 실행
  verify        전체 검증 (백엔드 테스트 + 프런트 test/lint/build)
  test-backend  백엔드 테스트만    e2e  브라우저 E2E`)
  },
}

function forward(...children) {
  const stop = () => { children.forEach(killTree); setTimeout(() => process.exit(0), 300) }
  process.on('SIGINT', stop); process.on('SIGTERM', stop)
  for (const child of children) child.on('exit', (code) => { if (code) { children.forEach(killTree); process.exit(code) } })
}

const name = process.argv[2] ?? 'help'
if (!commands[name]) { commands.help(); process.exit(1) }
Promise.resolve(commands[name]()).catch((error) => { console.error(`\n✖ ${error.message}`); process.exit(1) })
