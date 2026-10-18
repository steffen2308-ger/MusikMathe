(function(token){
 'use strict';
 if(window.top!==window||location.protocol!=='https:')return;
 if(window.__mmPasswords&&window.__mmPasswords.token===token)return;
 function visible(e){return !!e&&!e.disabled&&!e.readOnly&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden'&&getComputedStyle(e).display!=='none';}
 function form(scope){
  scope=scope||document;
  var passwords=Array.from(scope.querySelectorAll('input[type=password]')).filter(visible);
  if(passwords.length!==1||passwords[0].autocomplete==='new-password')return null;
  var p=passwords[0],root=p.form||scope;
  var users=Array.from(root.querySelectorAll('input')).filter(function(e){return visible(e)&&e!==p&&['text','email','tel'].indexOf(e.type)>=0;});
  var u=users.find(function(e){return e.autocomplete==='username';})||users.find(function(e){return /user|email|login|benutzer/i.test(e.name+' '+e.id);})||users[users.length-1];
  return {user:u,password:p};
 }
 function read(scope){var f=form(scope);return f?{token:token,username:f.user?f.user.value:'',password:f.password.value}:null;}
 function send(action,scope){try{var data=read(scope);if(data&&window.musikPasswords){data.action=action;if(action==='focus')delete data.password;window.musikPasswords.postMessage(JSON.stringify(data));}}catch(e){}}
 document.addEventListener('submit',function(e){send('save',e.target);},true);
 document.addEventListener('click',function(e){var b=e.target.closest&&e.target.closest('button,input[type=submit],[role=button]');if(e.isTrusted&&b&&(/^(anmelden|einloggen|login|log in|sign in|weiter|continue)$/i.test((b.textContent||b.value||'').trim())))send('save',b.form||document);},true);
 document.addEventListener('keydown',function(e){if(e.isTrusted&&e.key==='Enter'&&e.target.type==='password')send('save',e.target.form||document);},true);
 document.addEventListener('focusin',function(e){if(e.target.matches&&e.target.matches('input[type=password],input[autocomplete=username]'))send('focus',e.target.form||document);},true);
 window.__mmPasswords={token:token,read:function(){return read(document);}};
})(__MM_TOKEN__)
