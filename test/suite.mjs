// Voidwell acceptance suite. Usage: node suite.mjs <26.2|26.3> [test names...]
// Runs on a fresh throwaway server on 127.0.0.1:25621, which is deleted afterwards.
import { writeFileSync, readFileSync, existsSync } from 'node:fs'
import { Server, bot, heard, count, summary, sleep } from './lib.mjs'

const version = process.argv[2] ?? '26.2'
const only = process.argv.slice(3)
const results = []
let s

const TEST_CONFIG = {
  '    one-in: 3': '    one-in: 1',
  'within-seconds: 6': 'within-seconds: 6',
  "  featherfall:\n    enabled: true\n    minutes: 5": "  featherfall:\n    enabled: true\n    minutes: 1",
  "  void-safe:\n    enabled: true\n    # While it lasts, falling into the void lifts the player back to their bell instead.\n    minutes: 2":
    "  void-safe:\n    enabled: true\n    # While it lasts, falling into the void lifts the player back to their bell instead.\n    minutes: 1",
  "  bountiful-block:\n    enabled: true\n    # Double drops from the player's own one block.\n    minutes: 5":
    "  bountiful-block:\n    enabled: true\n    # Double drops from the player's own one block.\n    minutes: 1",
  "  island-travel:\n    enabled: true\n    minutes: 5": "  island-travel:\n    enabled: true\n    minutes: 1",
}

function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`)
}

async function where(name) {
  const dim = (await s.run(`data get entity ${name} Dimension`, /entity data: "([^"]+)"/))[1]
  const pos = (await s.run(`data get entity ${name} Pos`, /entity data: \[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]/)).slice(1, 4).map(Number)
  return { dim, x: pos[0], y: pos[1], z: pos[2] }
}

async function bellOf(name) {
  const m = await s.run(`voidwell bell ${name}`, /way bell is at (-?\d+) (-?\d+) (-?\d+)|has no island/)
  return m[1] === undefined ? null : { x: +m[1], y: +m[2], z: +m[3] }
}

async function isBlock(x, y, z, block, world = 'oneblock') {
  const m = await s.run(`execute in minecraft:${world} if block ${x} ${y} ${z} ${block}`, /Test (passed|failed)/)
  return m[1] === 'passed'
}

function meterOf(b) {
  const f = s.path(`plugins/Voidwell/players/${b.player.uuid}.yml`)
  if (!existsSync(f)) return 0
  const m = readFileSync(f, 'utf8').match(/points: ([\d.]+)/)
  return m ? +m[1] : 0
}

async function gate(name) {
  await s.run(`standin gate ${name} oneblock`, /standin gate \S+ handled=(true|false)/)
  await sleep(2500)
}

/** Ring the bell: right-click it as the bot, n times, gap ms apart. */
async function ring(b, bell, n, gap = 700) {
  const block = b.blockAt(new (await import('vec3')).Vec3(bell.x, bell.y, bell.z))
  for (let i = 0; i < n; i++) {
    await b.activateBlock(block)
    await sleep(gap)
  }
}

async function setup() {
  s = new Server(version)
  s.prepare({ config: TEST_CONFIG })
  await s.start()
  await s.run('mv create oneblock normal -g Oneblock', /World 'oneblock' created/, 60000)
  await s.run('ob set 500 0 100 0 oneblock', /island diameter/)
  await s.run('ob autojoin false', /autojoin is now disabled/)
  await s.run('ob rebirth_on_the_island false', /rebirth_on_the_island is now disabled/)
  await s.stop()
  // Multiverse-Inventories groups, written while the server is down so it can't save over them.
  writeFileSync(s.path('plugins/Multiverse-Inventories/groups.yml'),
    'groups:\n  Lobby:\n    worlds:\n    - world\n    shares:\n    - all\n  OneBlock:\n    worlds:\n    - oneblock\n    shares:\n    - all\n')
  await s.start()
  await s.run('gamerule minecraft:immediate_respawn true', /./)
  await s.run('execute in minecraft:oneblock run gamerule minecraft:immediate_respawn true', /./)
  const status = await s.run('voidwell status', /No problems found|Waystation|OneBlock|Lantern|circles/)
  console.log('status:', s.log.slice(-800).split('\n').filter(l => /Voidwell|- /.test(l)).slice(-5).join(' / '))
}

function uuidInts(u) {
  const h = u.replace(/-/g, '')
  return [0, 8, 16, 24].map(i => { const v = parseInt(h.slice(i, i + 8), 16); return v > 0x7fffffff ? v - 0x100000000 : v })
}

/** How many item entities are within r of a spot in the OneBlock world, and how many items they hold. */
async function itemsNear(x, y, z, r) {
  const m = await s.run(`execute in minecraft:oneblock if entity @e[type=item,x=${x},y=${y},z=${z},distance=..${r}]`, /Test (passed|failed).*/)
  console.log('   itemsNear:', m[0])
  const c = m[0].match(/count:? (\d+)/i)
  return m[1] === 'passed' ? +(c?.[1] ?? 1) : 0
}

async function platform(on = true) {
  await s.run(`execute in minecraft:oneblock run fill -6 100 -6 6 100 6 ${on ? 'minecraft:stone replace minecraft:air' : 'minecraft:air replace minecraft:stone'}`, /Successfully|No blocks|filled|Changed/)
}

async function home(b) {
  let w = await where(b.username)
  if (w.dim !== 'minecraft:oneblock') { await gate(b.username); await sleep(1000) }
}

async function window(b, timeout = 8000) {
  return new Promise((res, rej) => {
    const t = setTimeout(() => rej(new Error(b.username + ': no menu opened')), timeout)
    b.once('windowOpen', w => { clearTimeout(t); setTimeout(() => res(w), 400) })
  })
}

function titleOf(w) {
  const t = w.title
  return typeof t === 'string' ? t : JSON.stringify(t)
}

async function click(b, slot) {
  await b.clickWindow(slot, 0, 0)
  await sleep(600)
}

async function sneakRing(b, bell) {
  const opened = window(b)
  b.setControlState('sneak', true)
  await sleep(300)
  await ring(b, bell, 1, 200)
  b.setControlState('sneak', false)
  return opened
}

async function takeReward(b, id) {
  await s.run(`voidwell offer ${b.username} ${id}`, /offered/)
  const w = window(b)
  b.chat('/voidwell reward')
  await w
  await click(b, 11)
  const again = window(b).catch(() => null)
  await sleep(300)
  await again
  await click(b, 11)
  await sleep(1000)
}

async function effects(name) {
  const m = await s.run(`data get entity ${name} active_effects`, /entity data: (.*)|no entity data|Found no elements/)
  return m[1] ?? ''
}

async function invData(name) {
  return (await s.run(`data get entity ${name} Inventory`, /entity data: (.*)|Found no elements/))[1] ?? ''
}

const tests = {
  /** Server-only checks, for versions bots can't stay connected to (26.3 through ViaVersion). */
  async serverOnly() {
    check('server: Voidwell enabled', /Enabling Voidwell v/.test(s.log) && !/Error occurred while enabling Voidwell/.test(s.log))
    await sleep(12000) // OneBlock loads its world a few seconds after startup
    const m = s.mark(); s.cmd('voidwell status'); await sleep(2000)
    const out = s.since(m)
    check('server: OneBlock world found and Waystation landing ready', /OneBlock world: oneblock/.test(out) && /Waystation landing: ready/.test(out), out.split('\n').filter(l => / - /.test(l)).join(' / '))
    check('server: no problems reported', /No problems found/.test(out))
    const r = await s.run('standin gate Nobody oneblock', /no such player|handled=/)
    check('server: hand-over command path loads', !!r)
    const startup = s.log.split('\n').filter(l => /\[Voidwell\]/.test(l) && /WARN/.test(l))
    check('server: no startup warnings once set up', startup.length === 0, startup.join(' / '))
  },

  async firstVisit() {
    const a = await bot('Ash')
    await s.run('give Ash diamond 5', /Gave/)
    await sleep(500)
    const lobbyBefore = summary(a)
    await gate('Ash')
    const w = await where('Ash')
    check('first visit: in the OneBlock world', w.dim === 'minecraft:oneblock', JSON.stringify(w))
    check('first visit: standing on the island', Math.abs(w.x - 0.5) < 2 && Math.abs(w.z - 0.5) < 2 && w.y > 100 && w.y < 103, `${w.x},${w.y},${w.z}`)
    check('first visit: empty OneBlock inventory', count(a) === 0, JSON.stringify(summary(a)))
    const bell = await bellOf('Ash')
    check('first visit: way bell placed', bell && await isBlock(bell.x, bell.y, bell.z, 'minecraft:bell'), JSON.stringify(bell))
    check('first visit: lobby items stayed behind', lobbyBefore.diamond === 5)
    await heard(a, /This is your island/, 5000).then(() => check('first visit: welcome message', true)).catch(e => check('first visit: welcome message', false, e.message))
    a.quit(); await sleep(1000)
  },

  async islandDeath() {
    const a = await bot('Ash')
    await home(a)
    await platform(true)
    await s.run('execute in minecraft:oneblock run forceload add -16 -16 15 15', /./)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5', /Teleported/)
    await s.run('clear Ash', /Removed|No items/)
    await s.run('give Ash dirt 7', /Gave/); await s.run('give Ash stone_axe 1', /Gave/)
    await s.run('execute in minecraft:oneblock run spawnpoint Ash 1 101 1', /[Ss]pawn/)
    await sleep(1000)
    check('island death: starting items', count(a, 'dirt') === 7 && count(a, 'stone_axe') === 1)
    await s.run('kill Ash', /Killed/)
    await sleep(4000)
    const w = await where('Ash')
    check('island death: respawned outside the gate despite the respawn point', w.dim === 'minecraft:overworld' && Math.abs(w.x - 0.5) < 1 && Math.abs(w.z - 3.5) < 1, JSON.stringify(w))
    check('island death: lobby inventory, no OneBlock items', summary(a).diamond === 5 && !summary(a).dirt && !summary(a).stone_axe, JSON.stringify(summary(a)))
    const n = await itemsNear(0.5, 101, 0.5, 8)
    const probe = async label => { const m = s.mark(); s.cmd('execute as @e[type=item] run data get entity @s'); await sleep(1500); console.log('   ' + label + ':\n' + s.since(m).split('\n').filter(l => /entity data/.test(l)).map(l => '     ' + (l.match(/Pos: \[[^\]]*\]/)?.[0] ?? '') + ' ' + (l.match(/Age: -?\d+s/)?.[0] ?? '') + ' ' + (l.match(/id: "minecraft:[a-z_]+"/)?.[0] ?? '') + ' ' + (l.match(/voidwell:[a-z-]+": [^,}]+/)?.[0] ?? '')).join('\n')) }
    if (process.env.PROBE) await probe('after death')
    check('island death: both items dropped on the island, and no extra copies', n === 2, `${n} item entities`)
    const m = s.mark(); s.cmd('tick sprint 30000'); await s.waitFor(/Sprint completed/, 600000, m)
    if (process.env.PROBE) await probe('after 25 min')
    const n25 = await itemsNear(0.5, 101, 0.5, 8)
    check('island death: items still there after 25 minutes', n25 === n, `${n25} of ${n}`)
    const m2 = s.mark(); s.cmd('tick sprint 7000'); await s.waitFor(/Sprint completed/, 600000, m2)
    const n31 = await itemsNear(0.5, 101, 0.5, 8)
    check('island death: gone after about 30 minutes', n31 === 0, `${n31} left`)
    await s.run('execute in minecraft:oneblock run forceload remove -16 -16 15 15', /./)
    a.quit(); await sleep(1000)
  },

  async voidDeath() {
    const a = await bot('Ash')
    await home(a)
    await platform(false)
    await s.run('clear Ash', /Removed|No items/)
    await s.run('give Ash diamond_sword 1', /Gave/); await s.run('give Ash iron_chestplate 1', /Gave/); await s.run('give Ash cobblestone 64', /Gave/)
    await sleep(800)
    const before = meterOf(a)
    const m = a.chatLog.length
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 -75 0.5', /Teleported/)
    await sleep(5000)
    const after = meterOf(a)
    const gain = after - before
    check('void death: meter charged by the agreed values (14 + 16 + about 0.2)', gain > 30 && gain < 30.5, gain.toFixed(3))
    await heard(a, /The void took what you carried/, 3000, m).then(() => check('void death: told it counted', true)).catch(e => check('void death: told it counted', false, e.message))
    const w = await where('Ash')
    check('void death: respawned outside the gate', w.dim === 'minecraft:overworld', JSON.stringify(w))
    check('void death: nothing reached the lobby inventory', JSON.stringify(summary(a)) === JSON.stringify({ diamond: 5 }), JSON.stringify(summary(a)))
    a.quit(); await sleep(1000)
  },

  async throwing() {
    const a = await bot('Ash')
    await home(a)
    await platform(true)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5 0 0', /Teleported/)
    await s.run('clear Ash', /Removed|No items/)
    await s.run('give Ash diamond_sword 1', /Gave/); await s.run('give Ash iron_sword 1', /Gave/); await s.run('give Ash cobblestone 64', /Gave/)
    await sleep(1000)
    // Thrown onto the platform and picked back up: never gone, never counts.
    let m0 = meterOf(a)
    await a.toss(a.registry.itemsByName.diamond_sword.id, null, 1)
    await sleep(1500)
    await s.run('execute in minecraft:oneblock as @e[type=item,limit=1,sort=nearest,x=0,y=101,z=0] at @s run tp Ash ~ ~ ~', /Teleported/)
    await sleep(2000)
    check('throwing: picked back up', count(a, 'diamond_sword') === 1)
    await sleep(3000)
    check('throwing: picking it back up doesn\'t count', meterOf(a) === m0, `${m0} -> ${meterOf(a)}`)
    // Over the edge: counts once, after it's gone.
    await platform(false)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5 0 0', /Teleported/)
    await sleep(800)
    m0 = meterOf(a)
    await a.toss(a.registry.itemsByName.diamond_sword.id, null, 1)
    await sleep(1500)
    check('throwing: not counted while still falling', meterOf(a) === m0)
    await sleep(9000)
    const g = meterOf(a) - m0
    check('throwing: counted once it was gone (diamond sword, 2nd today: 12.6)', Math.abs(g - 12.6) < 0.01, g.toFixed(3))
    m0 = meterOf(a)
    await a.toss(a.registry.itemsByName.iron_sword.id, null, 1)
    await sleep(11000)
    check('throwing: iron sword counted once (4)', Math.abs(meterOf(a) - m0 - 4) < 0.01, (meterOf(a) - m0).toFixed(3))
    // Abuse: a stack of cobblestone.
    m0 = meterOf(a)
    await a.toss(a.registry.itemsByName.cobblestone.id, null, 64)
    await sleep(11000)
    const cg = meterOf(a) - m0
    check('abuse: a stack of cobblestone barely moves it', cg < 0.25, cg.toFixed(3))
    a.quit(); await sleep(1000)
  },

  async otherPlayersItems() {
    const a = await bot('Ash'); const b = await bot('Birch')
    await home(a); await home(b)
    const ints = uuidInts(b.player.uuid)
    const nbt = `{Item:{id:"minecraft:golden_apple",count:1},Thrower:[I;${ints.join(',')}],PickupDelay:0}`
    let ma = meterOf(a), mb = meterOf(b)
    await s.run(`execute in minecraft:oneblock run summon item 0.5 95 0.5 ${nbt}`, /Summoned/)
    await sleep(11000)
    check('abuse: an item thrown by another player charges only them', meterOf(a) === ma && Math.abs(meterOf(b) - mb - 6) < 0.01, `Ash +${(meterOf(a) - ma).toFixed(2)}, Birch +${(meterOf(b) - mb).toFixed(2)}`)
    // Birch's item, picked up by Ash and thrown in by Ash: Ash's.
    await platform(true)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5 0 0', /Teleported/)
    await s.run(`execute in minecraft:oneblock run summon item 0.5 101.2 0.5 ${nbt}`, /Summoned/)
    await sleep(2000)
    check('abuse: Ash picked up Birch\'s apple', count(a, 'golden_apple') === 1)
    await platform(false)
    ma = meterOf(a); mb = meterOf(b)
    await a.toss(a.registry.itemsByName.golden_apple.id, null, 1)
    await sleep(11000)
    check('abuse: thrown by Ash, it charges Ash, not Birch', Math.abs(meterOf(a) - ma - 6) < 0.01 && meterOf(b) === mb, `Ash +${(meterOf(a) - ma).toFixed(2)}, Birch +${(meterOf(b) - mb).toFixed(2)}`)
    a.quit(); b.quit(); await sleep(1000)
  },

  async commands() {
    const a = await bot('Ash')
    await home(a)
    const before = await where('Ash')
    const m = a.chatLog.length
    a.chat('/ob j')
    await heard(a, /OneBlock has no commands here/, 5000, m).then(() => check('commands: players are told /ob isn\'t a command here', true)).catch(e => check('commands: players are told /ob isn\'t a command here', false, e.message))
    a.chat('/oneblock:oneblock visit Birch')
    await sleep(1500)
    const after = await where('Ash')
    check('commands: /ob did nothing', Math.hypot(after.x - before.x, after.z - before.z) < 1 && after.dim === before.dim)
    const roots = a.rootCommands.filter(c => /^(oneblock:)?(ob|oneblock)/.test(c))
    check('commands: /ob hidden from the client\'s command list', a.rootCommands.length > 0 && roots.length === 0, `${a.rootCommands.length} commands sent; OneBlock ones: ${JSON.stringify(roots)}`)
    await s.run('lp user Ash permission set voidwell.oneblock-commands true', /Set|already/)
    await sleep(1000)
    const m2 = a.chatLog.length
    a.chat('/ob help')
    await sleep(1500)
    check('commands: an admin can still use /ob', !a.chatLog.slice(m2).some(l => /no commands here/.test(l)), a.chatLog.slice(m2).join(' | ').slice(0, 200))
    await s.run('lp user Ash permission unset voidwell.oneblock-commands', /Unset|does not have/)
    a.quit(); await sleep(1000)
  },

  async payouts() {
    const a = await bot('Ash')
    await home(a)
    await platform(true)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5 0 0', /Teleported/)
    await sleep(800)
    // The menu: three choices, a click to choose and a second to confirm.
    await s.run('voidwell offer Ash featherfall void-safe bountiful-block', /offered/)
    let w = window(a); a.chat('/voidwell reward'); const menu = await w
    const names = [11, 13, 15].map(i => JSON.stringify(menu.slots[i]?.customName ?? '').match(/"text":\{"type":"string","value":"([^"]+)"/g)?.map(x => x.split('"').at(-2)).join(' ') ?? '')
    check('payouts: menu offers three rewards', names.filter(Boolean).length === 3, names.join(' | ').slice(0, 200))
    await click(a, 11)
    let p = await effects('Ash')
    check('payouts: one click only chooses, it doesn\'t take', !/slow_falling/.test(p))
    a.closeWindow(a.currentWindow ?? menu); await sleep(500)
    // Featherfall, by the menu.
    await takeReward(a, 'featherfall')
    p = await effects('Ash')
    check('payouts: featherfall gives slow falling', /slow_falling/.test(p), p.slice(0, 120))
    // Void-safe.
    await takeReward(a, 'void-safe')
    await s.run('clear Ash', /Removed|No items/); await s.run('give Ash emerald 3', /Gave/)
    await platform(false)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 60 0.5', /Teleported/)
    await sleep(3000)
    let pos = await where('Ash')
    check('payouts: void-safe lifts a falling player back to the island', pos.dim === 'minecraft:oneblock' && pos.y > 99, JSON.stringify(pos))
    check('payouts: void-safe keeps what they carry', count(a, 'emerald') === 3)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 -80 0.5', /Teleported/)
    await sleep(3000)
    pos = await where('Ash')
    check('payouts: void-safe also catches a drop straight into the void', pos.dim === 'minecraft:oneblock' && pos.y > 99, JSON.stringify(pos))
    // Bountiful block: the one block drops double.
    await platform(true)
    await takeReward(a, 'bountiful-block')
    await s.run('execute in minecraft:oneblock run tp Ash 1.5 101 0.5', /Teleported/)
    await s.run('execute in minecraft:oneblock run setblock 0 100 0 minecraft:dirt', /Changed/)
    await s.run('clear Ash', /Removed|No items/); await s.run('give Ash diamond_shovel 1', /Gave/)
    await sleep(800)
    await a.equip(a.registry.itemsByName.diamond_shovel.id, 'hand')
    const before = count(a, 'dirt')
    try { await a.dig(a.blockAt(new (await import('vec3')).Vec3(0, 100, 0)), true) } catch (e) { console.log('   dig:', e.message) }
    await sleep(2500)
    await s.run('execute in minecraft:oneblock as @e[type=item,distance=..8,x=0,y=101,z=0] run tp @s Ash', /Teleported|No entity/).catch(() => {})
    await sleep(2500)
    check('payouts: bountiful block drops double', count(a, 'dirt') - before === 2, `${count(a, 'dirt') - before} dirt`)
    // Ending on time: featherfall was 1 minute.
    await heard(a, /Featherfall has worn off/, 70000).then(() => check('payouts: featherfall ends on time, and says so', true)).catch(e => check('payouts: featherfall ends on time, and says so', false, e.message))
    p = await effects('Ash')
    check('payouts: slow falling gone', !/slow_falling/.test(p), p.slice(0, 100))
    await heard(a, /Void-safe has worn off/, 30000).then(() => check('payouts: void-safe ends', true)).catch(e => check('payouts: void-safe ends', false, e.message))
    // The void gives back. Give it two swords first; the test config makes the chance 1 in 1.
    await platform(false)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5 0 0', /Teleported/)
    await s.run('give Ash golden_sword 1', /Gave/); await s.run('give Ash stone_sword 1', /Gave/); await sleep(800)
    await a.toss(a.registry.itemsByName.golden_sword.id, null, 1); await a.toss(a.registry.itemsByName.stone_sword.id, null, 1)
    await sleep(11000)
    await platform(true)
    const gearBefore = count(a)
    await takeReward(a, 'void-gives-back')
    await sleep(1500)
    const inv = await invData('Ash')
    const back = count(a) - gearBefore
    check('payouts: the void gives back half the sacrificed gear', back >= 1, `${back} items back; chat: ${a.chatLog.filter(l => /gives back/.test(l)).slice(-1)}`)
    const over = [...inv.matchAll(/"minecraft:([a-z_]+)": (\d+)/g)].some(([, e, l]) => +l > ({ sharpness: 5, protection: 4, unbreaking: 3, efficiency: 5, smite: 5, bane_of_arthropods: 5, looting: 3, fire_aspect: 2, knockback: 2, sweeping_edge: 3, fortune: 3, projectile_protection: 4, blast_protection: 4, fire_protection: 4, thorns: 3 }[e] ?? 99))
    check('payouts: each returned item has an enchantment past the usual most', over && /Void-touched/.test(inv), inv.match(/enchantments[^\]]{0,160}/)?.[0])
    a.quit(); await sleep(1000)
  },

  async travelAndTeams() {
    const a = await bot('Ash'); const b = await bot('Birch'); const c = await bot('Cedar'); const d = await bot('Dell')
    for (const [n, circle] of [['Ash', 'kids'], ['Birch', 'kids'], ['Dell', 'kids'], ['Cedar', 'adults']])
      await s.run(`lp user ${n} permission set houserules.circle.${circle} true`, /Set|already/)
    await sleep(1500)
    await home(a); await home(b); await home(c)
    await platform(true)
    const birchBell = await bellOf('Birch'), cedarBell = await bellOf('Cedar')
    const bPos = await where('Birch'), cPos = await where('Cedar')
    await s.run(`execute in minecraft:oneblock run tp Birch ${birchBell.x + 0.5} ${birchBell.y} ${birchBell.z + 2.5}`, /Teleported/)
    await s.run(`execute in minecraft:oneblock run tp Cedar ${cedarBell.x + 0.5} ${cedarBell.y} ${cedarBell.z + 2.5}`, /Teleported/)
    await sleep(1500)
    // Both owners open visits from their bell's menu.
    for (const [bb, bell] of [[b, birchBell], [c, cedarBell]]) {
      const m = await sneakRing(bb, bell)
      check(`travel: ${bb.username}'s sneak-ring opens the island menu`, /Your island/.test(titleOf(m)), titleOf(m))
      await click(bb, 12)
      await sleep(500)
      bb.closeWindow(bb.currentWindow ?? m)
    }
    await sleep(500)
    // A kid with island travel sees the kid's island, never the adult's.
    await takeReward(a, 'island-travel')
    await sleep(800)
    const menu = a.currentWindow
    const listed = (menu?.slots ?? []).slice(0, 27).filter(Boolean).map(i => JSON.stringify(i.customName ?? i.name))
    check('travel: a kid sees a kid\'s open island', listed.some(n => /Birch/.test(n)), listed.join(' '))
    check('travel: a kid never sees an adult\'s island', !listed.some(n => /Cedar/.test(n)))
    await click(a, listed.findIndex(n => /Birch/.test(n)))
    await sleep(3000)
    let pos = await where('Ash')
    check('travel: visiting Birch\'s island', Math.hypot(pos.x - bPos.x, pos.z - bPos.z) < 20, JSON.stringify(pos))
    await heard(b, /Ash.*is visiting your island/, 4000).then(() => check('travel: the owner is told', true)).catch(e => check('travel: the owner is told', false, e.message))
    // Look, don't touch.
    const target = a.blockAt(new (await import('vec3')).Vec3(Math.floor(bPos.x), 100, Math.floor(bPos.z)))
    const kind = target?.name
    try { await a.dig(target, true) } catch {}
    await sleep(1000)
    const still = a.blockAt(target.position)?.name
    const server = await isBlock(target.position.x, 100, target.position.z, 'minecraft:air')
    check('travel: a visitor can\'t break blocks (server\'s view)', !server && kind !== 'air', `bot saw ${kind} -> ${still}; server air: ${server}`)
    // The owner sends visitors home.
    const m = await sneakRing(b, birchBell)
    await click(b, 13)
    await sleep(3000)
    pos = await where('Ash')
    check('travel: the owner can send visitors home', Math.hypot(pos.x, pos.z) < 20, JSON.stringify(pos))
    // An adult with travel never sees kids' islands.
    await takeReward(c, 'island-travel')
    await sleep(800)
    const cm = c.currentWindow
    const cl = (cm?.slots ?? []).slice(0, 27).filter(Boolean).map(i => JSON.stringify(i.customName ?? i.name))
    check('travel: an adult never sees kids\' islands', !cl.some(n => /Birch|Ash/.test(n)), cl.join(' '))
    c.closeWindow(c.currentWindow ?? cm)
    // Travel ends by itself (1 minute in the test config).
    await takeReward(a, 'island-travel')
    await sleep(800)
    const tm = a.currentWindow
    const ti = (tm?.slots ?? []).slice(0, 27).findIndex(x => x && /Birch/.test(JSON.stringify(x.customName ?? x.name)))
    if (ti >= 0) await click(a, ti)
    await sleep(2500)
    pos = await where('Ash')
    const away = Math.hypot(pos.x - bPos.x, pos.z - bPos.z) < 20
    await heard(a, /Island travel has ended/, 70000).then(() => check('travel: ends by itself and comes home', true)).catch(e => check('travel: ends by itself and comes home', false, e.message))
    await sleep(2500)
    pos = await where('Ash')
    check('travel: home after it ended', Math.hypot(pos.x, pos.z) < 20 && away, JSON.stringify({ pos, wasAway: away }))
    // Teams: Birch invites Dell, a kid who has no island yet, from the bell menu.
    await s.run('give Dell apple 3', /Gave/)
    await sleep(500)
    const im = await sneakRing(b, birchBell)
    await click(b, 14)
    const picker = await window(b).catch(() => b.currentWindow)
    const people = (picker?.slots ?? []).slice(0, 27).map(x => x ? JSON.stringify(x.customName ?? x.name) : '')
    check('teams: an adult isn\'t offered as a kid\'s teammate', !people.some(n => /Cedar/.test(n)), people.filter(Boolean).join(' '))
    const di = people.findIndex(n => /Dell/.test(n))
    check('teams: a kid in the same circle can be invited', di >= 0)
    if (di >= 0) await click(b, di)
    await heard(d, /Birch.*invites you/, 5000).then(() => check('teams: the invite arrives, with buttons', true)).catch(e => check('teams: the invite arrives, with buttons', false, e.message))
    d.chat('/voidwell team accept Birch')
    await sleep(4000)
    pos = await where('Dell')
    check('teams: Dell joined Birch\'s island', pos.dim === 'minecraft:oneblock' && Math.hypot(pos.x - bPos.x, pos.z - bPos.z) < 20, JSON.stringify(pos))
    const dellBell = await bellOf('Dell')
    check('teams: same island, same bell', dellBell && dellBell.x === birchBell.x && dellBell.z === birchBell.z)
    // Out by the shared bell; the lobby apples are still in the lobby.
    await s.run(`execute in minecraft:oneblock run tp Dell ${birchBell.x + 0.5} ${birchBell.y} ${birchBell.z + 2.5}`, /Teleported/)
    await sleep(1000)
    await ring(d, birchBell, 3)
    await sleep(3000)
    check('teams: a teammate rings the shared bell to leave, and the lobby inventory is intact', count(d, 'apple') === 3, JSON.stringify(summary(d)))
    for (const x of [a, b, c, d]) x.quit()
    await sleep(1000)
  },

  async crashes() {
    // A crash right after an island death.
    let a = await bot('Ash')
    await home(a)
    await platform(true)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5', /Teleported/)
    await s.run('clear Ash', /Removed|No items/); await s.run('give Ash gold_ingot 9', /Gave/)
    await sleep(1000)
    await s.run('kill Ash', /Killed/)
    await sleep(1000)
    await s.kill9()
    await s.start()
    await s.run('execute in minecraft:oneblock run forceload add -16 -16 15 15', /./)
    await sleep(2000)
    a = await bot('Ash')
    await sleep(2000)
    let n = await itemsNear(0.5, 101, 0.5, 8)
    const lobby = summary(a)
    await gate('Ash'); await sleep(1500)
    check('crash after a death: no duplicate, no loss', n === 1 && count(a, 'gold_ingot') === 0 && lobby.diamond === 5, `items on island: ${n}, OneBlock inventory: ${JSON.stringify(summary(a))}, lobby: ${JSON.stringify(lobby)}`)
    await s.run('execute in minecraft:oneblock run kill @e[type=item]', /Killed|No entity/)
    // A crash right after the way bell.
    await s.run('give Ash iron_ingot 4', /Gave/)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5', /Teleported/)
    await sleep(1200)
    await ring(a, await bellOf('Ash'), 3)
    await sleep(1500)
    await s.kill9(); await s.start(); await sleep(2000)
    a = await bot('Ash'); await sleep(1500)
    const where1 = await where('Ash'), lobby2 = summary(a)
    await gate('Ash'); await sleep(1500)
    check('crash after the way bell: at the station, nothing duplicated or lost', where1.dim === 'minecraft:overworld' && lobby2.diamond === 5 && !lobby2.iron_ingot && count(a, 'iron_ingot') === 4,
      `${where1.dim}, lobby ${JSON.stringify(lobby2)}, OneBlock ${JSON.stringify(summary(a))}`)
    // A crash in the middle of a payout: "the void gives back" with one-in 1.
    await s.run('clear Ash', /Removed|No items/)
    await s.run('give Ash netherite_sword 1', /Gave/); await sleep(500)
    await platform(false)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5 0 0', /Teleported/); await sleep(500)
    await a.toss(a.registry.itemsByName.netherite_sword.id, null, 1)
    await sleep(11000)
    await s.run('voidwell offer Ash void-gives-back', /offered/)
    const w = window(a); a.chat('/voidwell reward'); await w
    await click(a, 11); await a.clickWindow(11, 0, 0)
    await sleep(150)
    await s.kill9(); await s.start(); await sleep(2000)
    a = await bot('Ash'); await sleep(1500)
    await home(a); await sleep(3000)
    const gear = a.inventory.items().filter(i => /sword|chestplate/.test(i.name)).length
    const file = readFileSync(s.path(`plugins/Voidwell/players/${a.player.uuid}.yml`), 'utf8')
    check('crash mid-payout: the returned gear arrives exactly once', gear === 1 && !/deliveries:\s*\n\s+\S/.test(file), `${gear} pieces; ${JSON.stringify(summary(a))}`)
    a.quit(); await sleep(1000)
  },

  async restarts() {
    let a = await bot('Ash')
    await home(a)
    await s.run('voidwell meter Ash 40', /meter is now/)
    await takeReward(a, 'featherfall').catch(() => {})
    await s.run('voidwell meter Ash 40', /meter is now/)
    const bell = await bellOf('Ash')
    a.quit(); await sleep(1000)
    await s.stop(); await s.start(); await sleep(2000)
    a = await bot('Ash'); await sleep(2500)
    check('restart: meter kept', Math.abs(meterOf(a) - 40) < 0.01, String(meterOf(a)))
    const b2 = await bellOf('Ash')
    check('restart: way bell kept', b2 && b2.x === bell.x && b2.z === bell.z && await isBlock(b2.x, b2.y, b2.z, 'minecraft:bell'))
    const p = await effects('Ash')
    check('restart: a running power carries on with the time it had left', /slow_falling/.test(p), p.slice(0, 120))
    a.quit(); await sleep(1000)
  },

  async wayBell() {
    const a = await bot('Ash')
    await sleep(1000)
    let w = await where('Ash')
    if (w.dim !== 'minecraft:oneblock') { await gate('Ash'); w = await where('Ash') }
    const bell = await bellOf('Ash')
    await s.run('give Ash dirt 10', /Gave/); await s.run('give Ash iron_sword 1', /Gave/)
    await s.run('xp set Ash 7 levels', /./); await s.run('effect give Ash minecraft:hunger 5 50', /./)
    await s.run('damage Ash 5', /./)
    await sleep(5500)
    const before = { inv: summary(a), xp: a.experience.level, hp: Math.round(a.health), food: a.food }
    // Things that must do nothing.
    const start = await where('Ash')
    await ring(a, bell, 1)
    await sleep(7000)
    check('way bell: one ring does nothing', (await where('Ash')).dim === 'minecraft:oneblock')
    await ring(a, bell, 2, 7000) // two rings, too far apart
    await sleep(1000)
    check('way bell: two slow rings do nothing', (await where('Ash')).dim === 'minecraft:oneblock')
    a.swingArm(); try { await a.dig(a.blockAt(new (await import('vec3')).Vec3(bell.x, bell.y, bell.z)), true) } catch {}
    await sleep(1000)
    check('way bell: hitting it does nothing and it survives', (await where('Ash')).dim === 'minecraft:oneblock' && await isBlock(bell.x, bell.y, bell.z, 'minecraft:bell'))
    // Stand aside on a pillar so the blast doesn't drop the bot into the void, then put the island back.
    await s.run('execute in minecraft:oneblock run setblock 8 100 8 minecraft:obsidian', /Changed|Could not/)
    await s.run('execute in minecraft:oneblock run tp Ash 8.5 101 8.5', /Teleported/)
    await sleep(1000)
    await s.run(`execute in minecraft:oneblock run summon tnt ${bell.x} ${bell.y + 1} ${bell.z} {fuse:1}`, /Summoned/)
    await sleep(3000)
    await s.run('execute in minecraft:oneblock run setblock 0 100 0 minecraft:grass_block', /Changed|Could not/)
    await s.run('execute in minecraft:oneblock run tp Ash 0.5 101 0.5', /Teleported/)
    await sleep(1500)
    check('way bell: survives an explosion', await isBlock(bell.x, bell.y, bell.z, 'minecraft:bell') && await isBlock(bell.x, bell.y - 1, bell.z, 'minecraft:crying_obsidian'))
    check('way bell: explosion didn\'t send anyone anywhere', (await where('Ash')).dim === 'minecraft:oneblock')
    // Health may have dropped from the TNT; record what it is now.
    await sleep(500)
    const mid = { inv: summary(a), xp: a.experience.level, hp: Math.round(a.health), food: a.food }
    // Ring from a spot of its own, so coming back to the island's centre wouldn't pass by accident.
    await s.run('execute in minecraft:oneblock run setblock 2 100 0 minecraft:stone', /Changed|Could not/)
    await s.run('execute in minecraft:oneblock run tp Ash 2.5 101 0.5 90 0', /Teleported/)
    await sleep(1500)
    const spot = await where('Ash')
    await ring(a, bell, 3)
    await sleep(3000)
    const out = await where('Ash')
    check('way bell: three rings go to the Waystation', out.dim === 'minecraft:overworld' && Math.abs(out.x - 0.5) < 1 && Math.abs(out.z - 3.5) < 1, JSON.stringify(out))
    check('way bell: lobby inventory there', summary(a).diamond === 5 && !summary(a).dirt, JSON.stringify(summary(a)))
    await gate('Ash')
    await sleep(1500)
    const back = await where('Ash')
    check('way bell: back where the bell was rung', back.dim === 'minecraft:oneblock' && Math.hypot(back.x - spot.x, back.z - spot.z) < 0.6 && Math.abs(back.y - spot.y) < 0.6, JSON.stringify({ back, spot }))
    const after = { inv: summary(a), xp: a.experience.level, hp: Math.round(a.health), food: a.food }
    check('way bell: same inventory, XP, health and hunger', JSON.stringify(after) === JSON.stringify(mid), JSON.stringify({ mid, after }))
    a.quit(); await sleep(1000)
  },
}

try {
  await setup()
  const botless = process.env.SERVER_ONLY === '1'
  for (const [name, fn] of Object.entries(tests)) {
    if (botless ? name !== 'serverOnly' : (only.length ? !only.includes(name) : name === 'serverOnly' && false)) continue
    console.log(`--- ${name}`)
    try { await fn() } catch (e) { check(name + ' (ran to the end)', false, e.message.slice(0, 600)) }
  }
} finally {
  const errs = s ? s.errors() : []
  console.log('server errors:', errs.length ? '\n' + errs.slice(0, 15).join('\n') : 'none')
  if (s) { await s.stop(); s.remove() }
  const failed = results.filter(r => !r.ok)
  console.log(`\n${version}: ${results.length - failed.length} passed, ${failed.length} failed`)
  process.exit(failed.length ? 1 : 0)
}
