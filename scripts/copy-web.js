const fs=require('fs'); const path=require('path');
const src=path.join(__dirname,'..'); const dest=path.join(__dirname,'..','www');
if(!fs.existsSync(dest)) fs.mkdirSync(dest,{recursive:true});
for(const f of ['index.html','icon.png']){ const s=path.join(src,f); if(fs.existsSync(s)) fs.copyFileSync(s,path.join(dest,f)); }
for(const d of ['fonts']){ const s=path.join(src,d); if(fs.existsSync(s)) fs.cpSync(s,path.join(dest,d),{recursive:true}); }
console.log('Copied to www/');
