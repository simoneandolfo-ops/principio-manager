import{managerBasePath}from'./tenant'

const PREFIX='principio_manager_workspace_v1:'
function scope(){return `${PREFIX}${managerBasePath()}`}
function key(name){return `${scope()}:${name}`}

export function saveWorkspaceValue(name,value){try{sessionStorage.setItem(key(name),JSON.stringify(value));return true}catch{return false}}
export function loadWorkspaceValue(name,fallback=null){try{const raw=sessionStorage.getItem(key(name));if(!raw)return fallback;const value=JSON.parse(raw);return value??fallback}catch{return fallback}}
export function removeWorkspaceValue(name){try{sessionStorage.removeItem(key(name))}catch{}}
export function clearWorkspaceState(){try{const start=scope()+':';for(let i=sessionStorage.length-1;i>=0;i--){const k=sessionStorage.key(i);if(k&&k.startsWith(start))sessionStorage.removeItem(k)}}catch{}}
export function rememberRoute(location){if(!location)return;const path=String(location.pathname||'/');if(!path.startsWith('/'))return;saveWorkspaceValue('route',{pathname:path,search:String(location.search||''),at:Date.now()})}
export function recoveryRoute(){const row=loadWorkspaceValue('route',null);if(!row||typeof row!=='object')return null;const path=String(row.pathname||'');if(!path.startsWith('/')||path==='/'||path.startsWith('/login'))return null;return `${path}${String(row.search||'')}`}
