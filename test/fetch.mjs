// Downloads the server and plugin jars the live tests need, each from its official source, and checks each
// against the hash that source publishes. Nothing is used unless its hash matches.
// Usage: node fetch.mjs   (writes to test/work/cache, which git ignores)
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const CACHE = new URL('./work/cache/', import.meta.url).pathname
mkdirSync(CACHE, { recursive: true })
const UA = { 'User-Agent': 'onevoid-live-tests (ExtantApe/OneVoid)' }

async function json(url) {
  const r = await fetch(url, { headers: UA })
  if (!r.ok) throw new Error(`${url}: ${r.status}`)
  return r.json()
}

async function get(name, url, algo, expected) {
  const file = path.join(CACHE, name)
  if (existsSync(file) && createHash(algo).update(readFileSync(file)).digest('hex') === expected) return file
  const r = await fetch(url, { headers: UA })
  if (!r.ok) throw new Error(`${url}: ${r.status}`)
  const buf = Buffer.from(await r.arrayBuffer())
  const got = createHash(algo).update(buf).digest('hex')
  if (got !== expected) throw new Error(`${name}: ${algo} mismatch, expected ${expected}, got ${got}`)
  writeFileSync(file, buf)
  console.log(`ok ${name} (${algo} ${got.slice(0, 16)}…)`)
  return file
}

async function paper(version) {
  const b = await json(`https://fill.papermc.io/v3/projects/paper/versions/${version}/builds/latest`)
  if (b.channel !== 'STABLE') throw new Error(`Paper ${version} build ${b.id} isn't stable`)
  const d = b.downloads['server:default']
  return get(`paper-${version}.jar`, d.url, 'sha256', d.checksums.sha256)
}

async function modrinth(project, versionNumber, as) {
  const versions = await json(`https://api.modrinth.com/v2/project/${project}/version`)
  const v = versions.find(v => v.version_number === versionNumber)
  if (!v) throw new Error(`${project} ${versionNumber} not on Modrinth`)
  const f = v.files.find(f => f.primary) ?? v.files[0]
  return get(as, f.url, 'sha512', f.hashes.sha512)
}

const out = {
  paper: { '26.2': await paper('26.2'), '26.3': await paper('26.3') },
  plugins: [
    await modrinth('gMMDcC2d', '1.6.4', 'OneBlock-1.6.4.jar'),
    await modrinth('multiverse-core', '5.8.2', 'Multiverse-Core-5.8.2.jar'),
    await modrinth('multiverse-inventories', '5.3.6', 'Multiverse-Inventories-5.3.6.jar'),
    await modrinth('luckperms', 'v5.5.71-bukkit', 'LuckPerms-Bukkit-5.5.71.jar'),
  ],
  via: [
    await modrinth('viaversion', '5.12.3-SNAPSHOT+1119', 'ViaVersion.jar'),
    await modrinth('viabackwards', '5.12.3-SNAPSHOT+668', 'ViaBackwards.jar'),
  ],
}
writeFileSync(path.join(CACHE, 'manifest.json'), JSON.stringify(out, null, 2))
console.log('all jars checked')
// Mayor isn't on Modrinth yet. Fetch its release jar with: gh release download v0.1.1 -R ExtantApe/Mayor -p '*.jar' -D test/work/cache
if (!existsSync(path.join(CACHE, 'Mayor-0.1.1.jar'))) console.log('Missing Mayor-0.1.1.jar: see the note at the end of fetch.mjs')
