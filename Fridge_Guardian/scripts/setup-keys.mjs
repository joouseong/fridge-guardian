#!/usr/bin/env node
// 소셜 로그인·OpenAI 키를 .env에 안전하게 저장합니다. (Windows·macOS 공통)
//   npm run setup:social [-- google|kakao]     npm run setup:openai
// 비밀 값은 입력할 때 화면에 보이지 않고, 저장한 .env는 현재 사용자만 읽을 수 있게 제한합니다.
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import readline from 'node:readline'
import { Writable } from 'node:stream'
import { isWin, projectRoot } from './lib/proc.mjs'

const envFile = path.join(projectRoot, '.env')

function ensureIgnoredByGit() {
  const check = spawnSync('git', ['check-ignore', '--quiet', '--', envFile], { cwd: projectRoot })
  if (check.error) return // git이 없으면 건너뜁니다.
  const inRepo = spawnSync('git', ['rev-parse', '--is-inside-work-tree'], { cwd: projectRoot, encoding: 'utf8' })
  if (inRepo.status !== 0) return
  if (check.status !== 0) throw new Error('.env가 Git에서 제외되어 있지 않아요. .gitignore를 확인한 뒤 다시 실행하세요.')
  if (spawnSync('git', ['ls-files', '--', envFile], { cwd: projectRoot, encoding: 'utf8' }).stdout.trim()) {
    throw new Error('.env가 Git에 추적되고 있어요. 추적을 해제한 뒤 다시 실행하세요.')
  }
}

// 입력 줄을 큐에 모아 두고 질문마다 한 줄씩 꺼냅니다. (파이프로 여러 줄을 넣어도 유실되지 않아요)
let muted = false
const output = new Writable({ write(chunk, _enc, done) { if (!muted) process.stdout.write(chunk); done() } })
const rl = readline.createInterface({ input: process.stdin, output, terminal: !!process.stdin.isTTY })
const lines = []
const waiting = []
let closed = false
rl.on('line', (line) => { if (waiting.length) waiting.shift()(line); else lines.push(line) })
rl.on('close', () => { closed = true; while (waiting.length) waiting.shift()('') })

function ask(question, { hidden = false } = {}) {
  process.stdout.write(`${question}${hidden ? ' (입력이 보이지 않아요): ' : ': '}`)
  muted = hidden
  return new Promise((resolve) => {
    const done = (line) => { muted = false; if (hidden) process.stdout.write('\n'); resolve(line.trim()) }
    if (lines.length) done(lines.shift())
    else if (closed) done('')
    else waiting.push(done)
  })
}

/** 비밀 값을 쓰기 전에 파일 권한부터 제한합니다. */
function restrict(file) {
  if (!fs.existsSync(file)) fs.writeFileSync(file, '', { mode: 0o600 })
  if (isWin) spawnSync('icacls', [file, '/inheritance:r', '/grant:r', `${process.env.USERNAME}:F`], { stdio: 'ignore' })
  else fs.chmodSync(file, 0o600)
}

function save(removePattern, entries) {
  restrict(envFile)
  const kept = fs.readFileSync(envFile, 'utf8').split(/\r?\n/).filter((line) => line !== '' && !removePattern.test(line))
  fs.writeFileSync(envFile, `${[...kept, ...entries].join('\n')}\n`)
}

const [kind, arg] = process.argv.slice(2)
try {
  ensureIgnoredByGit()
  if (kind === 'social') {
    const provider = (arg || await ask('Provider (google 또는 kakao)')).toLowerCase()
    if (!['google', 'kakao'].includes(provider)) throw new Error('provider는 google 또는 kakao여야 해요.')
    const id = await ask(provider === 'google' ? 'Google OAuth 클라이언트 ID' : '카카오 REST API 키')
    if (!/^[A-Za-z0-9._-]{8,}$/.test(id)) throw new Error('클라이언트 ID 형식이 올바르지 않아요. 따옴표·공백 없이 붙여넣으세요.')
    const secret = await ask(`${provider} 클라이언트 시크릿`, { hidden: true })
    if (!/^[A-Za-z0-9._-]{8,}$/.test(secret)) throw new Error('시크릿 형식이 올바르지 않아요. 따옴표·공백 없이 붙여넣으세요.')
    const prefix = provider.toUpperCase()
    save(new RegExp(`^\\s*${prefix}_CLIENT_(ID|SECRET)\\s*=`), [`${prefix}_CLIENT_ID=${id}`, `${prefix}_CLIENT_SECRET=${secret}`])
    console.log(`${provider} 로그인 키를 .env에 저장했어요. 백엔드를 다시 시작하면 적용돼요.`)
  } else if (kind === 'openai') {
    const key = await ask('OpenAI API 키', { hidden: true })
    if (!/^sk-[A-Za-z0-9_-]{20,}$/.test(key)) throw new Error('키 형식이 올바르지 않아요. 따옴표·공백 없이 붙여넣으세요.')
    save(/^\s*OPENAI_(API_KEY|MODEL)\s*=/, [`OPENAI_API_KEY=${key}`, 'OPENAI_MODEL=gpt-5-mini'])
    console.log('서버 전용 OpenAI 설정(gpt-5-mini)을 .env에 저장했어요. 백엔드를 다시 시작하면 적용돼요.')
  } else {
    console.log('사용법: npm run setup:social [-- google|kakao]  /  npm run setup:openai')
    process.exit(1)
  }
} catch (error) { console.error(`\n✖ ${error.message}`); process.exit(1) }
rl.close()
