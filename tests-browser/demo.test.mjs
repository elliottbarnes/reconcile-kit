import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {parseCSV,reconcile,parseMoney,formatMoney} from '../demo/core.mjs';
import {fixtures} from '../demo/fixtures.mjs';
const header='transaction_id,currency,amount,description\n';

test('browser report matches installed Java CLI including money and source-line evidence',()=>{
  const dir=mkdtempSync(join(tmpdir(),'reconcile-parity-'));
  const cases=[...Object.values(fixtures),{ledger:header+'dup,CAD,1,a\ndup,CAD,1,b\nonly,CAD,1,c\n',processor:header+'dup,CAD,2,a\ndup,CAD,3,b\nnew,CAD,1,c\n'},{ledger:'\ufeff'+header.replace(/\n/,'\r\n')+'quoted,CAD,1.000,"two\r\nlines"\r\n',processor:header+'quoted,CAD,1,"quote ""text"""\n'},{ledger:header,processor:header}];
  try{for(const c of cases){const left=join(dir,'ledger.csv'),right=join(dir,'processor.csv');writeFileSync(left,c.ledger);writeFileSync(right,c.processor);const run=spawnSync(resolve('build/install/reconcile-kit/bin/reconcile-kit'),[left,right,'--format','json'],{encoding:'utf8',timeout:20000});assert.ok(run.status===0||run.status===1,run.stderr);assert.deepEqual(reconcile(parseCSV(c.ledger),parseCSV(c.processor)),JSON.parse(run.stdout));}}finally{rmSync(dir,{recursive:true,force:true});}
});
test('strict quoting, columns, IDs and lossless minor units are enforced',()=>{
  for(const text of ['',header+'bad,CAD,1.001,x',header+'id,CAD,1,extra,column',header+'id,CAD,1,"unclosed',header+'id,CAD,1,"x"z',header+'id,CAD,1e3,x',header+'id,XYZ,1,x',header+'id,JPY,1.1,x',header+' bad,CAD,1,x'])assert.throws(()=>parseCSV(text),text);
  assert.equal(formatMoney(parseMoney('999999999999999999999999999999.99',2),2),'999999999999999999999999999999.99');assert.equal(formatMoney(parseMoney('-0.00',2),2),'0.00');assert.equal(formatMoney(parseMoney('1.000',0),0),'1');
  assert.throws(()=>parseCSV(header+Array.from({length:201},(_,i)=>`a${i},CAD,1,x`).join('\n')));assert.throws(()=>parseCSV('x'.repeat(100001)));
});
test('duplicate classification takes precedence and totals include all occurrences',()=>{
  const report=reconcile(parseCSV(header+'x,CAD,1,a\nx,CAD,2,b'),[]);assert.equal(report.entries[0].status,'DUPLICATE_LEDGER');assert.equal(report.totals[0].ledger,'3.00');
});
