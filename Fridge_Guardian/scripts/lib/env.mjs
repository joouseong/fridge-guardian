// .env 읽기, JDK 찾기, DB 주소 정하기. (start-backend.ps1과 같은 규칙)
import fs from 'node:fs'
import path from 'node:path'
import { capture, isWin, projectRoot } from './proc.mjs'

// .env에서 읽을 이름만 허용합니다. (MYSQL_* 같은 Docker용 변수가 백엔드로 새지 않게)
const ALLOWED = new Set(['DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT', 'SERVER_ADDRESS', 'COOKIE_SECURE', 'FRONTEND_URL', 'JWT_SECRET',
  'GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET', 'KAKAO_CLIENT_ID', 'KAKAO_CLIENT_SECRET', 'CACHE_TYPE', 'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD',
  'UPLOAD_DIR', 'FOODSAFETY_API_KEY', 'FOODSAFETY_BASE_URL', 'SCHEDULER_ENABLED', 'OPENAI_API_KEY', 'OPENAI_MODEL', 'OPENAI_BASE_URL',
  'AI_USER_REQUESTS_PER_MINUTE', 'AI_REQUESTS_PER_MINUTE', 'AI_REQUESTS_PER_DAY', 'AI_CONCURRENT_REQUESTS'])

export const localDir = path.join(projectRoot, '.local')
export const mysqlDir = path.join(localDir, 'mysql')

export function readDotEnv() {
  const file = path.join(projectRoot, '.env')
  const values = {}
  if (!fs.existsSync(file)) return values
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const entry = line.trim()
    if (!entry || entry.startsWith('#')) continue
    const index = entry.indexOf('=')
    if (index < 0) continue
    const key = entry.slice(0, index).trim()
    if (!ALLOWED.has(key)) continue
    let value = entry.slice(index + 1).trim()
    if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'")))) value = value.slice(1, -1)
    values[key] = value
  }
  return values
}

export function readConnection() {
  try { return JSON.parse(fs.readFileSync(path.join(mysqlDir, 'connection.json'), 'utf8').replace(/^﻿/, '')) } catch { return null }
}

export function jdbcUrl(port) { return `jdbc:mysql://127.0.0.1:${port}/fridge_guardian?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` }

const javaName = isWin ? 'java.exe' : 'java'
const isJdk21 = (home) => {
  const java = path.join(home, 'bin', javaName)
  if (!fs.existsSync(java)) return false
  const out = capture(java, ['-version'])
  return !!out && /version "(2[1-9]|[3-9]\d)\./.test(out)
}

/** JDK 21 이상을 찾습니다: JAVA_HOME → 프로젝트 .local/jdk-21* → Homebrew → java_home → 흔한 설치 위치. */
export function findJavaHome() {
  const candidates = []
  if (process.env.JAVA_HOME) candidates.push(process.env.JAVA_HOME)
  if (fs.existsSync(localDir)) for (const name of fs.readdirSync(localDir)) if (/^jdk-2\d/.test(name)) candidates.push(path.join(localDir, name))
  if (process.platform === 'darwin') {
    candidates.push('/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home', '/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home')
    const viaTool = capture('/usr/libexec/java_home', ['-v', '21+'])
    if (viaTool) candidates.push(viaTool.trim().split('\n').pop())
  } else if (isWin) {
    for (const base of [process.env.ProgramFiles, process.env['ProgramFiles(x86)']].filter(Boolean)) {
      for (const vendor of ['Eclipse Adoptium', 'Java', 'Microsoft', 'Zulu', 'Amazon Corretto']) {
        const dir = path.join(base, vendor)
        if (fs.existsSync(dir)) for (const name of fs.readdirSync(dir)) if (/(jdk|openjdk).*2\d/i.test(name)) candidates.push(path.join(dir, name))
      }
    }
  } else {
    for (const dir of ['/usr/lib/jvm']) if (fs.existsSync(dir)) for (const name of fs.readdirSync(dir)) if (/2\d/.test(name)) candidates.push(path.join(dir, name))
  }
  return candidates.find((home) => home && isJdk21(home)) ?? null
}

/** 백엔드에 넘길 환경변수: 프로젝트 DB 주소 → .env(우선) 순서로 덮어씁니다. */
export function backendEnv() {
  const env = { ...process.env }
  const javaHome = findJavaHome()
  if (!javaHome) {
    throw new Error('JDK 21 이상을 찾지 못했어요. macOS: brew install openjdk@21 / Windows: winget install EclipseAdoptium.Temurin.21.JDK 후 다시 실행하거나 JAVA_HOME을 지정하세요.')
  }
  env.JAVA_HOME = javaHome
  // Windows는 'Path', 그 외는 'PATH'라서 이미 있는 이름 하나만 갱신합니다.
  const pathKey = Object.keys(env).find((key) => key.toLowerCase() === 'path') ?? 'PATH'
  env[pathKey] = `${path.join(javaHome, 'bin')}${path.delimiter}${env[pathKey] ?? ''}`
  const connection = readConnection()
  if (!env.DB_URL && connection?.url) env.DB_URL = connection.url
  Object.assign(env, readDotEnv())
  return env
}
