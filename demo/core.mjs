// Explicit browser subset of the Java CSV / exact-money contract.
export const CURRENCIES = Object.freeze({ CAD:2, USD:2, EUR:2, GBP:2, JPY:0, KWD:3 });
export const STATUSES = ['MATCHED','AMOUNT_MISMATCH','CURRENCY_MISMATCH','MISSING_LEDGER','MISSING_PROCESSOR','DUPLICATE_LEDGER','DUPLICATE_PROCESSOR','DUPLICATE_BOTH'];
export function parseCSV(source) {
  if (typeof source !== 'string' || source.length > 100000) throw Error('Each CSV must be at most 100,000 characters.');
  source = source.replace(/^\uFEFF/, '').replace(/\r\n?/g,'\n');
  const rows=[]; let fields=[], field='', quoted=false, closed=false, started=false, line=1, recordLine=1;
  const problem = message => Error(`Line ${line}: ${message}`);
  const addField = () => {fields.push(field);field='';if(fields.length>4)throw problem('Expected exactly four columns.');};
  const addRow = () => {rows.push({line:recordLine,fields});fields=[];if(rows.length>201)throw problem('Maximum 200 transactions per file.');};
  for (const c of source) {
    if (quoted) {if(c==='"'){quoted=false;closed=true;}else field+=c;}
    else if(closed && c==='"'){field+='"';quoted=true;closed=false;}
    else if(c===','||c==='\n'){addField();closed=false;started=false;if(c==='\n'){addRow();recordLine=line+1;}}
    else if(closed) throw problem('Unexpected character after closing quote.');
    else if(c==='"'){if(started)throw problem('Quote in an unquoted field.');quoted=true;started=true;}
    else{field+=c;started=true;}
    if(field.length>16384)throw problem('Field exceeds 16,384 characters.');
    if(c==='\n')line++;
  }
  if(quoted)throw problem('Unclosed quoted field.');
  if(started||closed||fields.length){addField();addRow();}
  const header=['transaction_id','currency','amount','description'];
  if(!rows.length||JSON.stringify(rows[0].fields)!==JSON.stringify(header))throw Error('Expected header: '+header.join(','));
  return rows.slice(1).map(row=>{
    if(row.fields.length!==4)throw Error(`Line ${row.line}: expected exactly four columns.`);
    const [id,currency,raw,description]=row.fields;
    if(!/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/.test(id))throw Error(`Line ${row.line}: invalid transaction ID.`);
    if(!Object.hasOwn(CURRENCIES,currency))throw Error(`Line ${row.line}: browser demo supports ${Object.keys(CURRENCIES).join(', ')}.`);
    const units=parseMoney(raw,CURRENCIES[currency]);
    return {id,currency,units,amount:formatMoney(units,CURRENCIES[currency]),description,line:row.line};
  });
}
export function parseMoney(raw,scale) {
  if(!/^-?[0-9]{1,30}(\.[0-9]{1,6})?$/.test(raw))throw Error('Amount must be a plain decimal: up to 30 integer and 6 fractional digits.');
  const negative=raw.startsWith('-'); const [whole,fraction='']=raw.replace(/^-/,'').split('.');
  if(/[1-9]/.test(fraction.slice(scale)))throw Error('Amount has nonzero digits below the currency minor unit; no rounding is applied.');
  const value=BigInt(whole)*10n**BigInt(scale)+BigInt((fraction.slice(0,scale).padEnd(scale,'0'))||'0');
  return negative?-value:value;
}
export function formatMoney(units,scale){const digits=(units<0n?-units:units).toString().padStart(scale+1,'0');return (units<0n?'-':'')+(scale?digits.slice(0,-scale)+'.'+digits.slice(-scale):digits);}
export function reconcile(left,right) {
  const index=rows=>{const map=new Map();for(const r of rows){if(!map.has(r.id))map.set(r.id,[]);map.get(r.id).push(r);}return map;};
  const l=index(left),r=index(right),ids=[...new Set([...l.keys(),...r.keys()])].sort();
  const evidence=rows=>rows.map(({line,currency,amount,description})=>({line,currency,amount,description}));
  const counts=Object.fromEntries(STATUSES.map(s=>[s,0]));
  const entries=ids.map(id=>{const a=l.get(id)||[],b=r.get(id)||[];
    const status=a.length>1&&b.length>1?'DUPLICATE_BOTH':a.length>1?'DUPLICATE_LEDGER':b.length>1?'DUPLICATE_PROCESSOR':!a.length?'MISSING_LEDGER':!b.length?'MISSING_PROCESSOR':a[0].currency!==b[0].currency?'CURRENCY_MISMATCH':a[0].units!==b[0].units?'AMOUNT_MISMATCH':'MATCHED';
    counts[status]++;return {transactionId:id,status,ledger:evidence(a),processor:evidence(b)};});
  const currencies=[...new Set([...left,...right].map(r=>r.currency))].sort();
  const sum=(rows,c)=>rows.filter(r=>r.currency===c).reduce((s,r)=>s+r.units,0n);
  const totals=currencies.map(currency=>{const a=sum(left,currency),b=sum(right,currency),scale=CURRENCIES[currency];return {currency,ledger:formatMoney(a,scale),processor:formatMoney(b,scale),delta:formatMoney(a-b,scale)};});
  const exceptionKeys=entries.length-counts.MATCHED;
  return {schemaVersion:1,status:exceptionKeys?'MISMATCH':'MATCH',ledgerRows:left.length,processorRows:right.length,exceptionKeys,counts,totals,entries};
}
