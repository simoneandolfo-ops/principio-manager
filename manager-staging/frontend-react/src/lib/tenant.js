export function tenantSlug(){
 const p=location.pathname.split('/').filter(Boolean)
 const i=p.findIndex(x=>x.toLowerCase()==='manager')
 return i>=0&&p[i+1]?p[i+1].toLowerCase():'root'
}
export function managerBasePath(){
 const p=location.pathname.split('/').filter(Boolean)
 const i=p.findIndex(x=>x.toLowerCase()==='manager')
 return i>=0?`/${p.slice(0,i+2).join('/')}/`:'./'
}
export function routerBasePath(){
 const base=managerBasePath()
 return base==='./'?'/':base.replace(/\/$/,'')
}
export function operatorStorageKey(){
 const base=managerBasePath()==='./'?'root':managerBasePath().replace(/\/+$/,'').toLowerCase()
 return `manager_operator_token:${base}`
}
