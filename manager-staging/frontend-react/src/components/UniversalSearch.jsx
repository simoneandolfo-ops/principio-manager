import React,{useEffect,useRef,useState}from'react'
import{Search,CalendarDays,UsersRound,MapPin,ChevronRight,X,AlertTriangle}from'lucide-react'
import{useNavigate}from'react-router-dom'
import{managerApi}from'../lib/api'
import{confirmGlobalNavigation}from'../lib/useUnsavedChanges'

export default function UniversalSearch(){
 const nav=useNavigate(),box=useRef(null),requestSeq=useRef(0)
 const[q,setQ]=useState(''),[rows,setRows]=useState([]),[open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('')
 useEffect(()=>{const close=e=>{if(box.current&&!box.current.contains(e.target))setOpen(false)};document.addEventListener('pointerdown',close);return()=>document.removeEventListener('pointerdown',close)},[])
 useEffect(()=>{
  const query=q.trim(),seq=++requestSeq.current
  if(query.length<2){setRows([]);setOpen(false);setError('');setBusy(false);return}
  const t=setTimeout(async()=>{
   setBusy(true);setError('')
   try{const x=await managerApi('searchReservations',query);if(seq!==requestSeq.current)return;setRows(Array.isArray(x)?x:[]);setOpen(true)}
   catch(e){if(seq!==requestSeq.current)return;setRows([]);setError(e.message||'Ricerca non disponibile.');setOpen(true)}
   finally{if(seq===requestSeq.current)setBusy(false)}
  },220)
  return()=>clearTimeout(t)
 },[q])
 const clear=()=>{requestSeq.current++;setQ('');setRows([]);setError('');setOpen(false);setBusy(false)}
 const go=r=>{if(!confirmGlobalNavigation())return;clear();nav(`/booking/${encodeURIComponent(r.id)}`)}
 return <div className="universal-search" ref={box} role="search"><div className={`universal-input ${open?'active':''}`}><Search size={16}/><input aria-label="Ricerca universale prenotazioni" aria-expanded={open} value={q} onFocus={()=>q.trim().length>=2&&setOpen(true)} onChange={e=>setQ(e.target.value)} placeholder="Cerca cliente, telefono, tavolo o ID…"/>{q&&<button aria-label="Cancella ricerca" onClick={clear}><X size={14}/></button>}</div>{open&&<div className="search-popover" role="region" aria-label="Risultati ricerca"><div className="search-popover-head"><span>Ricerca prenotazioni</span>{busy&&<b>Ricerca…</b>}</div>{error&&<div className="search-error" role="alert"><AlertTriangle size={14}/><span>{error}</span></div>}{!error&&rows.map(r=><button className="search-result" key={`${r.id}-${r.date}`} onClick={()=>go(r)}><div className="search-result-main"><strong>{r.name||r.id}</strong><span><CalendarDays size={11}/>{r.date} · {r.start||'—'}</span></div><div className="search-result-meta"><span><UsersRound size={11}/>{r.people}</span><span><MapPin size={11}/>{r.tables||r.room||'Da assegnare'}</span></div><ChevronRight size={15}/></button>)}{!error&&!busy&&!rows.length&&<div className="search-empty">Nessun risultato.</div>}</div>}</div>
}
