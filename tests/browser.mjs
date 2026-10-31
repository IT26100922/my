// Browser verification over Chrome DevTools Protocol. No npm dependencies.
// Requires a local headless Chrome/Edge launched with --remote-debugging-port=9222.
import fs from 'node:fs';
const base=process.env.FF_TEST_BASE||'http://127.0.0.1:8080';
const targets=await(await fetch('http://127.0.0.1:9222/json/list')).json();
const target=targets.find(t=>t.type==='page');
if(!target)throw new Error('Launch Chrome/Edge with a remote debugging port first.');
const ws=new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{ws.addEventListener('open',resolve,{once:true});ws.addEventListener('error',reject,{once:true});});
let nextId=0;const pending=new Map(),errors=[];let checks=0;
ws.addEventListener('message',event=>{const value=JSON.parse(event.data);if(value.id){const p=pending.get(value.id);if(p){pending.delete(value.id);value.error?p.reject(new Error(value.error.message)):p.resolve(value.result);}}else if(value.method==='Runtime.exceptionThrown')errors.push(value.params.exceptionDetails.text+': '+(value.params.exceptionDetails.exception?.description||''));else if(value.method==='Log.entryAdded'&&value.params.entry.level==='error')errors.push(value.params.entry.text);});
function command(method,params={}){const id=++nextId;return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(new Error('CDP timeout: '+method));},15000);pending.set(id,{resolve:value=>{clearTimeout(timer);resolve(value);},reject:error=>{clearTimeout(timer);reject(error);}});ws.send(JSON.stringify({id,method,params}));});}
async function evaluate(expression){const result=await command('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw new Error(result.exceptionDetails.text);return result.result.value;}
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function navigate(path){await command('Page.navigate',{url:base+path});await delay(600);await evaluate('document.fonts.ready.then(()=>true)');}
function check(value,label){if(!value)throw new Error(label);checks++;console.log('PASS',label);}
await command('Page.enable');await command('Runtime.enable');await command('Log.enable');
await command('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
fs.mkdirSync('test-results',{recursive:true});
try{
  for(const width of [1440,768,390]){
    await command('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:width===390});
    await navigate('/');
    check(await evaluate('document.querySelector("h1").textContent.includes("FIND YOUR PEOPLE")'),'Landing renders at '+width+'px');
    check(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'),'No horizontal overflow at '+width+'px');
    check(await evaluate('document.querySelectorAll(".topic-card").length===10'),'Ten topic cards at '+width+'px');
    const shot=await command('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync('test-results/landing-'+width+'.png',Buffer.from(shot.data,'base64'));
  }
  check(await evaluate('!document.querySelector(".cursor")'),'Mobile disables custom cursor');
  await command('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
  await navigate('/');
  const position=await evaluate('(()=>{const r=document.querySelector(".landing-nav .btn").getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()');
  await command('Input.dispatchMouseEvent',{type:'mouseMoved',x:position.x,y:position.y});await delay(100);
  check(await evaluate('document.body.classList.contains("cursor-ready")&&document.querySelector(".cursor").classList.contains("hover")'),'Desktop cursor follows pointer and expands over controls');
  await command('Emulation.setDeviceMetricsOverride',{width:390,height:1000,deviceScaleFactor:1,mobile:true});
  await navigate('/register.html');
  check(await evaluate('document.querySelector("form").checkValidity()===false'),'Empty registration form blocks submit');
  check(await evaluate('Array.from(document.querySelectorAll("input")).every(i=>document.querySelector(`label[for="${i.id}"]`))'),'Registration inputs have labels');
  check(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'),'Registration mobile layout fits');
  await navigate('/login.html');
  check(await evaluate('document.querySelector("#login")&&document.querySelector("#password")'),'Login form renders');
  await command('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  await navigate('/');
  check(await evaluate('getComputedStyle(document.querySelector(".orb")).animationName==="none"'),'Reduced motion disables hero animation');
  check(await evaluate('!document.querySelector(".cursor")'),'Reduced motion disables custom cursor');
  check(errors.length===0,'No JavaScript exceptions or browser console errors on public pages');
  console.log('PASS:',checks,'public browser checks');
}finally{ws.close();}
