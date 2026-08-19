const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.resolve(__dirname,'..'),dist=path.resolve(root,'../public/react-dist');
const index=path.join(dist,'index.html'),manifest=path.join(dist,'.vite','manifest.json');
let bad=[];
if(!fs.existsSync(index))bad.push('react-dist/index.html missing');
if(!fs.existsSync(manifest))bad.push('react-dist/.vite/manifest.json missing');
if(fs.existsSync(index)){
 const html=fs.readFileSync(index,'utf8');
 if(!html.includes('/__PM_BASE__/react-dist/'))bad.push('deploy base placeholder missing from built index');
 for(const m of html.matchAll(/(?:src|href)="([^"]+)"/g)){
  const u=m[1];
  if(!u.includes('/__PM_BASE__/react-dist/'))continue;
  const rel=u.split('/__PM_BASE__/react-dist/')[1];
  if(rel&&!fs.existsSync(path.join(dist,rel)))bad.push('built asset missing: '+rel);
 }
}
if(bad.length){console.error('MANAGER VITE BUILD VERIFY: FAIL\n'+bad.join('\n'));process.exit(1)}
const hash=crypto.createHash('sha256').update(fs.readFileSync(index)).digest('hex');
console.log(`MANAGER VITE BUILD VERIFY: PASS (${hash.slice(0,16)})`);
