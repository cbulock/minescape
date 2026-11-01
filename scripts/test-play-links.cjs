const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync(require('node:path').join(__dirname, '../docs/play/play.js'), 'utf8');
function board(columns, rows, mines) {
  const size = Math.ceil(columns * rows / 8);
  const data = Buffer.alloc(12 + size * 2);
  data[0] = columns; data[1] = rows;
  data.writeUInt16BE(mines, 2); data.writeUInt16BE(mines, 4); data.writeUInt16BE(size, 6);
  for (let i=0; i<mines; i++) data[8+(i>>3)] |= 1<<(i%8);
  data[8+size+(mines>>3)] |= 1<<(mines%8);
  let crc = 0xffffffff;
  for (const byte of data.subarray(0,-4)) { crc ^= byte; for (let b=0;b<8;b++) crc = (crc>>>1)^((crc&1)?0xedb88320:0); }
  data.writeUInt32BE((crc^0xffffffff)>>>0, data.length-4);
  return 'b1.'+data.toString('base64url');
}
function render(fragment) {
  const nodes = new Map();
  const document = {getElementById(id) { if (!nodes.has(id)) nodes.set(id,{hidden:true,textContent:'',focus(){},select(){}}); return nodes.get(id); }};
  vm.runInNewContext(source, {document, window:{location:{hash:'#'+fragment},addEventListener(){}}, navigator:{},
    Uint8Array, DataView, Date, atob: s=>Buffer.from(s,'base64').toString('binary')});
  return Object.fromEntries(nodes);
}
for (const [c,r,m] of [[9,9,10],[10,18,63],[50,50,875]]) {
  const fragment = board(c,r,m), nodes=render(fragment);
  assert.equal(nodes['open-app'].hidden,false,`${c}x${r}: ${nodes.description.textContent}`);
  assert.equal(nodes['open-app'].href,'minescape://play/#'+fragment);
}
assert.equal(render('d1.2026-01-01')['open-app'].hidden,false);
for (const bad of ['', 'd1.2026-02-30', 'd1.9999-01-01', 'd2.2026-01-01', 'b1.AAAA', board(9,9,10).slice(0,-5)+'AAAAA', 'b1.'+'a'.repeat(1400)]) {
  assert.equal(render(bad)['open-app'].hidden,true,bad);
}
(async()=>{
  const nodes=render(board(9,9,10));
  await nodes['copy-link'].onclick();
  assert.equal(nodes['manual-link'].hidden,false);
  assert.ok(nodes['manual-link'].value.startsWith('https://cbulock.github.io/minescape/play/#b1.'));
  console.log('Play-link tests passed: exact links, maximum density, corruption, versions, dates, copy fallback.');
})();
