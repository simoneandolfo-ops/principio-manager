import{managerBasePath,operatorStorageKey}from'./tenant'
let csrf=''
const LONG_TIMEOUTS={getTenantBackup:60000,validateTenantBackup:60000,restoreTenantBackup:120000,saveSettings:45000}
const MUTATIONS=new Set(['createBooking','updateBooking','confirmBooking','saveSettings','saveRooms','createTable','updateTable','disableTable','saveOperator','setOperatorEnabled','regenerateOperatorPin','sendTestEmail','restoreTenantBackup','tabletMarkAccommodated','tabletUndoAccommodated','tabletMarkOrderTaken','tabletMarkReleased'])
const IN_FLIGHT_MUTATIONS=new Map()
let ACTIVE_MUTATIONS=0
const RETRYABLE_READS=new Set(['getCurrentOperator','getBootstrapData','getDashboard','getBookingsForDate','getBookingById','getBookingAuditTrail','getMapData','getPendingCustomerRequests','getStatistics','searchReservations','listRooms','listTables','listOperators','getSystemInfo','getChangeLog','getTabletBoard','getDurationInsights','analyzeService','simulateServicePlan','getServicePlanningRevision'])

const CLIENT_ISSUE_KEY='principio_manager_client_issues_v1'
function recordClientIssue(kind,fn,rid,detail=''){
 try{
  const row={at:new Date().toISOString(),kind:String(kind||'api'),fn:String(fn||''),reference:String(rid||'').slice(0,12),detail:String(detail||'').slice(0,500)}
  const prev=JSON.parse(localStorage.getItem(CLIENT_ISSUE_KEY)||'[]')
  const rows=Array.isArray(prev)?prev:[];rows.unshift(row);localStorage.setItem(CLIENT_ISSUE_KEY,JSON.stringify(rows.slice(0,20)))
 }catch{}
}
function friendlyMessage(kind,raw){
 if(kind==='auth')return'Sessione operatore scaduta. Accedi di nuovo: i dati non salvati restano sul dispositivo.'
 if(kind==='permission')return'Il tuo operatore non ha i permessi per questa operazione.'
 if(kind==='conflict')return'Questi dati sono stati modificati da un altro operatore. Ricarica prima di salvare di nuovo.'
 if(kind==='rate-limit')return'Troppe richieste ravvicinate. Attendi qualche secondo e riprova.'
 if(kind==='server')return'Il Manager non riesce a completare la richiesta in questo momento. I dati inseriti non sono stati cancellati.'
 return String(raw||'Operazione non completata.')
}
export class ManagerApiError extends Error{
 constructor(message,{status=0,requestId='',kind='api',fn=''}={}){super(message);this.name='ManagerApiError';this.status=status;this.requestId=requestId;this.kind=kind;this.fn=fn;this.reference=requestId?String(requestId).slice(0,12):''}
}
export function operatorToken(){try{return localStorage.getItem(operatorStorageKey())||''}catch{return''}}
export function setOperatorToken(token){try{token?localStorage.setItem(operatorStorageKey(),token):localStorage.removeItem(operatorStorageKey())}catch{}}
export function setCsrf(value){csrf=String(value||'')}
function requestId(){try{return crypto.randomUUID()}catch{return `pm-${Date.now()}-${Math.random().toString(16).slice(2)}`}}
function errorKind(status,message){
 const text=String(message||'')
 if(status===401||/sessione operatore|accesso operatore|sessione.*non valida|token operatore/i.test(text))return'auth'
 if(status===403||/riservata all.amministratore|permesso|non autorizzat/i.test(text))return'permission'
 if(status===409||/modificata da un altro operatore|conflitto|versione.*vecchia/i.test(text))return'conflict'
 if(status===429||/troppi tentativi|rate limit/i.test(text))return'rate-limit'
 if(status>=500)return'server'
 return'api'
}
function emit(name,detail){try{window.dispatchEvent(new CustomEvent(name,{detail}))}catch{}}
function mutationState(delta,fn){ACTIVE_MUTATIONS=Math.max(0,ACTIVE_MUTATIONS+delta);emit('manager:mutation-state',{active:ACTIVE_MUTATIONS,busy:ACTIVE_MUTATIONS>0,fn})}
function uncertainMutationError(fn,rid,cause){
 const action=fn==='createBooking'?'La prenotazione':fn==='updateBooking'?'La modifica della prenotazione':'L’operazione'
 const message=`${action} potrebbe essere stata salvata dal server, ma il Manager non ha ricevuto la conferma. Verifica i dati prima di riprovare.`
 const err=new ManagerApiError(message,{requestId:rid,kind:'uncertain',fn})
 emit('manager:mutation-uncertain',{fn,cause,requestId:rid,reference:err.reference,message})
 return err
}
async function managerApiRequest(fn,...args){
 const token=operatorToken(),rid=requestId(),maxAttempts=RETRYABLE_READS.has(fn)?2:1
 for(let attempt=1;attempt<=maxAttempts;attempt++){
  const controller=new AbortController(),timeout=LONG_TIMEOUTS[fn]||20000
  const timer=setTimeout(()=>controller.abort(),timeout)
  try{
   const r=await fetch(`${managerBasePath()}api.php`,{method:'POST',credentials:'same-origin',cache:'no-store',signal:controller.signal,headers:{'Content-Type':'application/json','X-Request-ID':rid,...(csrf?{'X-CSRF-Token':csrf}:{})},body:JSON.stringify({fn,args,operatorToken:token})})
   const responseId=r.headers.get('X-Request-ID')||rid
   const type=String(r.headers.get('content-type')||'')
   const x=type.includes('application/json')?await r.json().catch(()=>({})):{}
   if(!r.ok||x?.ok===false){
    const message=x?.error||(`Errore server ${r.status}`)
    const kind=errorKind(r.status,message)
    if(attempt<maxAttempts&&kind==='server'&&[502,503,504].includes(r.status)){
      await new Promise(resolve=>setTimeout(resolve,250*attempt));continue
    }
    const err=new ManagerApiError(friendlyMessage(kind,message),{status:r.status,requestId:responseId,kind,fn});err.technicalMessage=message;recordClientIssue(kind,fn,responseId,message)
    if(kind==='auth')emit('manager:session-expired',{fn,status:r.status,requestId:responseId,message})
    else if(kind==='permission')emit('manager:permission-denied',{fn,status:r.status,requestId:responseId,message})
    else if(kind==='conflict')emit('manager:conflict',{fn,status:r.status,requestId:responseId,message})
    throw err
   }
   return x?.result??x
  }catch(e){
   if(e instanceof ManagerApiError)throw e
   const transient=e?.name==='AbortError'?'timeout':'network'
   if(attempt<maxAttempts){await new Promise(resolve=>setTimeout(resolve,250*attempt));continue}
   if(MUTATIONS.has(fn)){
    emit('manager:network-error',{fn,kind:transient,requestId:rid});recordClientIssue(transient,fn,rid,e?.message||'')
    throw uncertainMutationError(fn,rid,transient)
   }
   if(transient==='timeout'){
    recordClientIssue('timeout',fn,rid,e?.message||'');const err=new ManagerApiError('Il server sta impiegando troppo tempo. Riprova.',{requestId:rid,kind:'timeout',fn});emit('manager:network-error',{fn,kind:'timeout',requestId:rid});throw err
   }
   recordClientIssue('network',fn,rid,e?.message||'');const err=new ManagerApiError((typeof navigator==='undefined'||navigator.onLine)?'Connessione al server non riuscita. Riprova.':'Sei offline. Controlla la connessione.',{requestId:rid,kind:'network',fn});emit('manager:network-error',{fn,kind:'network',requestId:rid});throw err
  }finally{clearTimeout(timer)}
 }
 throw new ManagerApiError('Richiesta non completata.',{requestId:rid,kind:'api',fn})
}

export function managerApi(fn,...args){
 if(!MUTATIONS.has(fn))return managerApiRequest(fn,...args)
 if(typeof navigator!=='undefined'&&!navigator.onLine){const rid=requestId();recordClientIssue('offline',fn,rid,'Mutazione bloccata prima dell’invio');return Promise.reject(new ManagerApiError('Sei offline. Il salvataggio non è stato inviato: i dati inseriti restano disponibili.',{requestId:rid,kind:'offline',fn}))}
 let key
 try{key=fn+'|'+JSON.stringify(args)}catch{key=fn+'|'+String(args)}
 const pending=IN_FLIGHT_MUTATIONS.get(key)
 if(pending)return pending
 mutationState(1,fn)
 const request=managerApiRequest(fn,...args).finally(()=>{if(IN_FLIGHT_MUTATIONS.get(key)===request)IN_FLIGHT_MUTATIONS.delete(key);mutationState(-1,fn)})
 IN_FLIGHT_MUTATIONS.set(key,request)
 return request
}
