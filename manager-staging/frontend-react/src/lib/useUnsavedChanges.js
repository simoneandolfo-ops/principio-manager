import{useCallback,useEffect}from'react'

const DEFAULT_MESSAGE='Hai modifiche non salvate. Vuoi uscire senza salvarle?'

export function confirmGlobalNavigation(){
 const fn=typeof window!=='undefined'?window.__pmConfirmNavigation:null
 return typeof fn==='function'?fn():true
}

export function useUnsavedChanges(dirty,message=DEFAULT_MESSAGE){
 const confirm=useCallback(()=>!dirty||window.confirm(message),[dirty,message])
 useEffect(()=>{if(!dirty)return;const before=e=>{e.preventDefault();e.returnValue=''};window.addEventListener('beforeunload',before);return()=>window.removeEventListener('beforeunload',before)},[dirty])
 useEffect(()=>{if(!dirty)return;const click=e=>{if(e.defaultPrevented||e.button!==0||e.metaKey||e.ctrlKey||e.shiftKey||e.altKey)return;const a=e.target?.closest?.('a[href]');if(!a||a.target==='_blank'||a.hasAttribute('download'))return;if(!confirm()){e.preventDefault();e.stopPropagation()}};document.addEventListener('click',click,true);return()=>document.removeEventListener('click',click,true)},[dirty,confirm])
 useEffect(()=>{if(!dirty)return;const previous=window.__pmConfirmNavigation;window.__pmConfirmNavigation=confirm;return()=>{if(window.__pmConfirmNavigation===confirm)window.__pmConfirmNavigation=previous}},[dirty,confirm])
 return confirm
}
