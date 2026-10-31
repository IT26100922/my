export const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
export const initials = name => String(name || '?').split(/\s+/).slice(0,2).map(part => part[0]).join('').toUpperCase();
export const avatar = (name,large=false) => `<div class="avatar${large?' large':''}" aria-hidden="true">${esc(initials(name))}</div>`;
export const tags = values => `<div class="tags">${values.map(value=>`<span class="tag">${esc(typeof value==='object'?value.name:value)}</span>`).join('')}</div>`;
export const empty = text => `<div class="empty">${esc(text)}</div>`;
export const date = value => { if(!value)return ''; const source=String(value).replace(' ','T');const parsed=new Date(/[Zz]$|[+-]\d\d:\d\d$/.test(source)?source:source+'Z'); return Number.isNaN(parsed.getTime())?String(value):parsed.toLocaleString(undefined,{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'}); };
export async function api(path,data) {
  const response=await fetch('/api'+path,{method:data?'POST':'GET',credentials:'same-origin',headers:data?{'Content-Type':'application/json'}:{},body:data?JSON.stringify(data):undefined});
  const result=await response.json();
  if(!response.ok){const error=new Error(result.error||'Something went wrong');error.status=response.status;throw error;}
  return result;
}
export function notice(message,success=false,target=document.querySelector('#notice')){if(target){target.className='notice'+(success?' success':'');target.textContent=message;}}
export function toast(message){let box=document.querySelector('#toast');if(!box){box=document.createElement('div');box.id='toast';box.className='toast notice success';box.setAttribute('role','status');document.body.append(box);}box.textContent=message;clearTimeout(box.timer);box.timer=setTimeout(()=>box.textContent='',4500);}
export async function submit(form,work){const button=form.querySelector('button[type=submit]');const target=form.closest('dialog')?.querySelector('.notice')||document.querySelector('#notice');if(button)button.disabled=true;notice('',false,target);try{await work(Object.fromEntries(new FormData(form)));}catch(error){notice(error.message,false,target);}finally{if(button)button.disabled=false;}}
export function setupMotion(){
  if(!matchMedia('(min-width:901px) and (pointer:fine) and (prefers-reduced-motion:no-preference)').matches)return;
  const cursor=document.createElement('div');cursor.className='cursor';document.body.append(cursor);
  let x=0,y=0,px=0,py=0,started=false;
  document.addEventListener('pointermove',event=>{x=event.clientX;y=event.clientY;if(!started){px=x;py=y;started=true;document.body.classList.add('cursor-ready');}cursor.style.opacity='1';cursor.classList.toggle('hover',!!event.target.closest('a,button,input,select,textarea,label'));});
  document.addEventListener('pointerout',event=>{if(!event.relatedTarget)cursor.style.opacity='0';});
  function frame(){px+=(x-px)*.25;py+=(y-py)*.25;cursor.style.left=px+'px';cursor.style.top=py+'px';requestAnimationFrame(frame);}requestAnimationFrame(frame);
}
setupMotion();
