// Live-test harness: one throwaway Paper server on 127.0.0.1:25621, driven from its console, with mineflayer
// bots as players. Servers live in a fresh temp folder, made for one run and deleted after it.
import { spawn, execSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import mineflayer from 'mineflayer'

export const PORT = 25621
export const CLIENT = '26.1'
export const JAVA = process.env.VOIDWELL_TEST_JAVA ?? '/Library/Java/JavaVirtualMachines/openjdk-25.jdk/Contents/Home/bin/java'
export const ROOT = new URL('../', import.meta.url).pathname
const CACHE = path.join(ROOT, 'test/work/cache')
export const sleep = ms => new Promise(r => setTimeout(r, ms))

function jar(dir, re) {
  const found = existsSync(dir) ? readdirSync(dir).filter(f => re.test(f)) : []
  if (found.length !== 1) throw new Error(`Expected one ${re} in ${dir}, found ${found.length}. Run ./gradlew build first.`)
  return path.join(dir, found[0])
}

export class Server {
  constructor(version, base = process.env.VOIDWELL_TEST_TMP ?? os.tmpdir()) {
    this.version = version
    this.dir = path.join(base, `voidwell-live-${version}-${process.pid}`)
    this.log = ''
    this.proc = null
  }

  path(...p) { return path.join(this.dir, ...p) }

  /** A fresh folder: Paper, the plugins, 127.0.0.1 only, offline mode for bots, and the EULA for this throwaway server. */
  prepare({ config = {}, extraPlugins = [] } = {}) {
    const manifest = JSON.parse(readFileSync(path.join(CACHE, 'manifest.json'), 'utf8'))
    rmSync(this.dir, { recursive: true, force: true })
    mkdirSync(this.path('plugins/Voidwell'), { recursive: true })
    copyFileSync(manifest.paper[this.version], this.path('paper.jar'))
    // Paper's downloaded libraries are kept between runs (not a server copy: no worlds or player data).
    const libs = path.join(CACHE, `libs-${this.version}`)
    if (existsSync(libs)) execSync(`cp -cR "${libs}/." "${this.dir}/"`)
    for (const p of [...manifest.plugins, ...manifest.via]) copyFileSync(p, this.path('plugins', path.basename(p)))
    copyFileSync(jar(path.join(ROOT, 'build/libs'), /^Voidwell-.*\.jar$/), this.path('plugins/Voidwell.jar'))
    copyFileSync(jar(path.join(ROOT, 'test/standin/build/libs'), /^WaystationStandin.*\.jar$/), this.path('plugins/WaystationStandin.jar'))
    // HouseRules 0.1.1, from its GitHub release (see fetch.mjs).
    copyFileSync(path.join(CACHE, 'HouseRules-0.1.1.jar'), this.path('plugins/HouseRules.jar'))
    for (const p of extraPlugins) copyFileSync(p, this.path('plugins', path.basename(p)))
    writeFileSync(this.path('eula.txt'), 'eula=true\n')
    writeFileSync(this.path('server.properties'), [
      'server-ip=127.0.0.1', `server-port=${PORT}`, 'online-mode=false', 'level-type=minecraft\\:flat',
      'spawn-protection=0', 'enable-rcon=false', 'white-list=false', 'enforce-whitelist=false', 'enable-query=false', 'max-players=10', 'view-distance=4',
      'simulation-distance=4', 'gamemode=survival', 'difficulty=easy', 'spawn-monsters=false', 'allow-flight=true',
    ].join('\n') + '\n')
    mkdirSync(this.path('plugins/HouseRules'), { recursive: true })
    writeFileSync(this.path('plugins/HouseRules/config.yml'), 'config-version: 1\ncircles:\n  - kids\n  - adults\n')
    // Test settings: short powers, so they can be seen ending.
    const vw = readFileSync(path.join(ROOT, 'src/main/resources/config.yml'), 'utf8')
    let text = vw
    for (const [from, to] of Object.entries(config)) {
      if (!text.includes(from)) throw new Error(`config.yml has no "${from}"`)
      text = text.replace(from, to)
    }
    writeFileSync(this.path('plugins/Voidwell/config.yml'), text)
  }

  async start(timeout = 240000) {
    this.log = ''
    this.proc = spawn(JAVA, ['-Xms1G', '-Xmx2G', '-Dpaper.disableChannelLimit=true', '-jar', 'paper.jar', '--nogui'], { cwd: this.dir })
    this.proc.stdout.on('data', d => { this.log += d.toString().replace(/\x1b\[[0-9;]*m/g, '') })
    this.proc.stderr.on('data', d => { this.log += d.toString() })
    this.exited = new Promise(r => this.proc.on('exit', r))
    await this.waitFor(/Done \([\d.]+s\)!/, timeout)
    const libs = path.join(CACHE, `libs-${this.version}`)
    if (!existsSync(libs)) {
      mkdirSync(libs, { recursive: true })
      for (const f of ['libraries', 'versions', 'cache']) if (existsSync(this.path(f))) execSync(`cp -cR "${this.path(f)}" "${libs}/"`)
    }
  }

  cmd(c) { this.proc.stdin.write(c + '\n') }
  mark() { return this.log.length }
  since(m) { return this.log.slice(m) }

  async waitFor(re, timeout = 20000, from = 0) {
    const end = Date.now() + timeout
    while (Date.now() < end) {
      const m = this.log.slice(from).match(re)
      if (m) return m
      if (this.proc.exitCode !== null) throw new Error('Server exited while waiting for ' + re + '\n' + this.log.slice(-3000))
      await sleep(100)
    }
    throw new Error(`Timed out waiting for ${re}\n--- log tail ---\n${this.log.slice(-2500)}`)
  }

  /** Runs a console command and waits for a reply matching re. */
  async run(c, re, timeout = 15000) {
    const m = this.mark(); this.cmd(c)
    return this.waitFor(re, timeout, m)
  }

  async stop() {
    if (!this.proc || this.proc.exitCode !== null) return
    this.cmd('stop')
    await Promise.race([this.exited, sleep(60000)])
    if (this.proc.exitCode === null) this.proc.kill('SIGKILL')
  }

  /** A crash: no shutdown, no saving. */
  async kill9() {
    this.proc.kill('SIGKILL')
    await this.exited
  }

  errors() {
    return this.log.split('\n').filter(l => /ERROR|Exception|SEVERE/.test(l) && !/Via(Version|Backwards)/.test(l))
  }

  remove() { rmSync(this.dir, { recursive: true, force: true, maxRetries: 10, retryDelay: 1000 }) }
}

export async function bot(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: name, version: CLIENT, auth: 'offline', hideErrors: false })
  b.chatLog = []
  // The command names the server sent at login: what a real client tab-completes from.
  b.rootCommands = []
  b._client.on('declare_commands', p => {
    const root = p.nodes[p.rootIndex]
    b.rootCommands = (root?.children ?? []).map(i => p.nodes[i]?.extraNodeData?.name).filter(Boolean)
  })
  b.on('messagestr', m => b.chatLog.push(m))
  b.on('actionBar', m => b.chatLog.push('[bar] ' + m.toString()))
  await new Promise((res, rej) => {
    b.once('spawn', res)
    b.once('kicked', r => rej(new Error(name + ' kicked: ' + JSON.stringify(r))))
    b.once('error', rej)
  })
  await sleep(1500)
  return b
}

/** Waits until the bot's chat log shows a message matching re, after index from. */
export async function heard(b, re, timeout = 10000, from = 0) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const m = b.chatLog.slice(from).find(l => re.test(l))
    if (m) return m
    await sleep(100)
  }
  throw new Error(`${b.username} never heard ${re}. Last: ${b.chatLog.slice(-6).join(' | ')}`)
}

export function count(b, item) {
  return b.inventory.items().filter(i => !item || i.name === item).reduce((n, i) => n + i.count, 0)
}

export function summary(b) {
  const by = {}
  for (const i of b.inventory.items()) by[i.name] = (by[i.name] ?? 0) + i.count
  return by
}
