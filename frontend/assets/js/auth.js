import {api,submit} from './common.js';
const form=document.querySelector('form');
form.addEventListener('submit',event=>{event.preventDefault();submit(form,async data=>{
  const register=document.body.dataset.page==='register';
  const result=await api(register?'/register':'/login',data);
  location.href=register||(!result.user.onboarded && result.user.role!=='ADMIN')?'/questionnaire.html':result.user.dashboard;
});});
