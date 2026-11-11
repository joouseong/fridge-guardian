// 프로젝트 전용 MySQL(기본 3307)을 Windows·macOS·Linux에서 같은 방식으로 켜고 끕니다.
// 데이터는 .local/mysql/data(Git 제외). Windows의 start-db.ps1이 만든 데이터·설정(my.ini, admin.cnf)도 그대로 이어서 씁니다.
import { spawn } from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import { capture, isWin, killTree, runSync } from './proc.mjs'
import { jdbcUrl, mysqlDir, readConnection } from './env.mjs'

const exe = (name) => (isWin ? `${name}.exe` : name)
const dataDir = path.join(mysqlDir, 'data')
const iniFile = path.join(mysqlDir, 'my.ini')          // Windows 스크립트가 만든 설정(있으면 그대로 사용)
const adminFile = path.join(mysqlDir, 'admin.cnf')
const logFile = path.join(mysqlDir, 'server.log')
const pidFile = path.join(mysqlDir, 'mysqld.pid')

function findBin(explicit) {
  const candidates = [explicit, process.env.MYSQL_BIN, readConnection()?.bin]
  if (isWin) {
    for (const base of [process.env.ProgramFiles, 'C:/Program Files'].filter(Boolean)) {
      for (const version of ['8.4', '8.0']) candidates.push(path.join(base, 'MySQL', `MySQL Server ${version}`, 'bin'))
    }
  } else {
    for (const prefix of ['/opt/homebrew/opt', '/usr/local/opt']) for (const name of ['mysql@8.4', 'mysql@8.0', 'mysql']) candidates.push(`${prefix}/${name}/bin`)
    candidates.push('/usr/sbin', '/usr/bin', '/usr/local/mysql/bin')
  }
  return candidates.filter(Boolean).find((dir) => fs.existsSync(path.join(dir, exe('mysqld')))) ?? null
}

const listening = (port) => new Promise((resolve) => {
  const socket = net.connect({ port, host: '127.0.0.1' })
  socket.once('connect', () => { socket.destroy(); resolve(true) })
  socket.once('error', () => resolve(false))
})
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
// MySQL 설정 파일과 SQL에 넣을 경로는 항상 슬래시로 씁니다.
const slash = (value) => value.replaceAll('\\', '/')

export async function startDb({ port = 3307, bin } = {}) {
  const binDir = findBin(bin)
  if (!binDir) {
    throw new Error('MySQL 8을 찾지 못했어요. macOS: brew install mysql@8.4 / Windows: MySQL 8.4 설치 후 다시 실행하세요. (MYSQL_BIN=<bin 폴더>로 직접 지정할 수도 있어요.)')
  }
  fs.mkdirSync(mysqlDir, { recursive: true })
  const url = jdbcUrl(port)
  const save = () => fs.writeFileSync(path.join(mysqlDir, 'connection.json'), JSON.stringify({ port, bin: binDir, url }, null, 2))
  if (await listening(port)) {
    save()
    console.log(`포트 ${port}에서 이미 MySQL이 실행 중이에요. 그대로 사용해요.`)
    return url
  }
  const mysqld = path.join(binDir, exe('mysqld'))
  const options = fs.existsSync(iniFile)
    ? [`--defaults-file=${iniFile}`]
    : [`--datadir=${slash(dataDir)}`, `--port=${port}`, '--bind-address=127.0.0.1', '--mysqlx=OFF', '--default-time-zone=+09:00',
        '--character-set-server=utf8mb4', '--collation-server=utf8mb4_0900_ai_ci', `--pid-file=${slash(pidFile)}`, `--log-error=${slash(logFile)}`,
        ...(isWin ? [] : [`--socket=${slash(path.join(mysqlDir, 'mysql.sock'))}`])]
  const extra = []
  if (!fs.existsSync(path.join(dataDir, 'mysql'))) {
    if (fs.existsSync(dataDir) && fs.readdirSync(dataDir).length > 0) throw new Error(`${dataDir}에 초기화가 끝나지 않은 파일이 있어요. 직접 확인한 뒤 다시 시도하세요. (기존 파일은 지우지 않았어요.)`)
    // 처음 한 번만: 데이터 폴더를 만들고, 서버를 켤 때 DB·앱 계정·root 비밀번호를 설정합니다.
    const rootPassword = crypto.randomBytes(24).toString('hex')
    fs.writeFileSync(adminFile, `[client]\nuser=root\npassword=${rootPassword}\nhost=127.0.0.1\nport=${port}\nprotocol=TCP\n`, { mode: 0o600 })
    const initSql = path.join(mysqlDir, 'initialize.sql')
    fs.writeFileSync(initSql, [
      'CREATE DATABASE IF NOT EXISTS fridge_guardian CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;',
      ...['127.0.0.1', 'localhost'].flatMap((host) => [
        `CREATE USER IF NOT EXISTS 'fridge_app'@'${host}' IDENTIFIED BY 'fridge_dev_password';`,
        `GRANT ALL PRIVILEGES ON fridge_guardian.* TO 'fridge_app'@'${host}';`]),
      `ALTER USER 'root'@'localhost' IDENTIFIED BY '${rootPassword}';`, ''].join('\n'))
    console.log('MySQL 데이터를 처음 만드는 중… (1분 정도 걸릴 수 있어요)')
    const init = runSync(mysqld, [...options, '--initialize-insecure'], { stdio: 'ignore' })
    if (init.status !== 0) throw new Error(`MySQL 초기화에 실패했어요. ${logFile}을 확인하세요.`)
    extra.push(`--init-file=${slash(initSql)}`)
  }
  save()
  const child = spawn(mysqld, [...options, ...extra], { detached: true, stdio: 'ignore', windowsHide: true })
  child.unref()
  for (let attempt = 0; attempt < 90; attempt++) {
    if (await listening(port)) {
      const initSql = path.join(mysqlDir, 'initialize.sql')
      await sleep(500)
      fs.rmSync(initSql, { force: true })
      console.log(`프로젝트 MySQL 준비 완료: 127.0.0.1:${port}  (데이터: ${dataDir})`)
      return url
    }
    if (child.exitCode !== null) break
    await sleep(1000)
  }
  killTree(child)
  throw new Error(`MySQL이 시작되지 않았어요. ${logFile}을 확인하세요.`)
}

export async function stopDb() {
  const connection = readConnection()
  const port = connection?.port ?? 3307
  if (!(await listening(port))) { console.log('프로젝트 MySQL이 실행 중이 아니에요.'); return }
  const binDir = findBin()
  const admin = binDir && path.join(binDir, exe('mysqladmin'))
  if (!admin || !fs.existsSync(admin)) throw new Error('mysqladmin을 찾지 못해 안전하게 종료할 수 없어요. MYSQL_BIN을 지정하세요.')
  const args = fs.existsSync(adminFile)
    ? [`--defaults-extra-file=${adminFile}`, `--port=${port}`, 'shutdown']
    : ['--protocol=TCP', '-h127.0.0.1', `--port=${port}`, '-uroot', 'shutdown']
  const result = runSync(admin, args)
  if (result.status !== 0) throw new Error('MySQL을 종료하지 못했어요. (데이터는 그대로 있어요.)')
  console.log('프로젝트 MySQL을 종료했어요. 데이터는 보존돼요.')
}
