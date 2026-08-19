const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..');
const pkgPath=path.join(root,'package.json');
const lockPath=path.join(root,'package-lock.json');
let bad=[];
if(!fs.existsSync(pkgPath)) bad.push('package.json missing');
if(!fs.existsSync(lockPath)) bad.push('package-lock.json missing: release build must be reproducible');
if(fs.existsSync(pkgPath)){
  const pkg=JSON.parse(fs.readFileSync(pkgPath,'utf8'));
  for(const section of ['dependencies','devDependencies']){
    for(const [name,version] of Object.entries(pkg[section]||{})){
      if(!/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(version)) bad.push(`${section}.${name} is not exact: ${version}`);
    }
  }
  if(!pkg.scripts?.['build:verify']) bad.push('build:verify script missing');
}
if(fs.existsSync(lockPath)){
  try{
    const lock=JSON.parse(fs.readFileSync(lockPath,'utf8'));
    if(Number(lock.lockfileVersion)<3) bad.push('package-lock.json must use lockfileVersion >= 3');
    const rootPkg=lock.packages?.[''];
    if(!rootPkg) bad.push('package-lock root package missing');
    else if(fs.existsSync(pkgPath)){
      const pkg=JSON.parse(fs.readFileSync(pkgPath,'utf8'));
      for(const section of ['dependencies','devDependencies']){
        for(const [name,version] of Object.entries(pkg[section]||{})){
          if((rootPkg[section]||{})[name]!==version) bad.push(`lock mismatch for ${name}`);
        }
      }
    }
  }catch(e){bad.push('package-lock.json invalid JSON')}
}
if(bad.length){console.error('MANAGER RELEASE INPUT CHECK: FAIL\n'+bad.map(x=>' - '+x).join('\n'));process.exit(1)}
console.log('MANAGER RELEASE INPUT CHECK: PASS');
