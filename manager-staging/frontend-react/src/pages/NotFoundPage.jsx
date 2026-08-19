import React from'react'
import{ArrowLeft,Home}from'lucide-react'
import{useNavigate}from'react-router-dom'
export default function NotFoundPage(){const nav=useNavigate();return <section className="page not-found-page" data-testid="manager-not-found"><article className="panel not-found-card"><div className="not-found-code">404</div><div className="eyebrow">Principio Manager</div><h1>Pagina non trovata</h1><p>Il collegamento non corrisponde a una schermata del Manager. Nessun dato è stato modificato.</p><div className="not-found-actions"><button className="small-action" onClick={()=>nav(-1)}><ArrowLeft size={15}/> Indietro</button><button className="primary compact-primary" onClick={()=>nav('/')}><Home size={15}/> Dashboard</button></div></article></section>}
