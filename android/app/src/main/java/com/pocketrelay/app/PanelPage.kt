package com.pocketrelay.app

object PanelPage {
    private const val STYLE = """
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
:root{--bg:#f6f6f4;--card:#fff;--fg:#111;--mut:#777;--line:#e4e4e0;--acc:#111}
@media(prefers-color-scheme:dark){:root{--bg:#0e0e0e;--card:#171717;--fg:#ededed;--mut:#9a9a96;--line:#2b2b2b;--acc:#ededed}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:16px -apple-system,system-ui,sans-serif}
main{max-width:460px;margin:0 auto;padding:26px 18px 60px}
h1{font-size:22px;font-weight:700;margin:0 0 4px}
h2{font-size:12px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--mut);margin:26px 0 10px}
.s{color:var(--mut);font-size:14px;margin:0 0 20px;min-height:20px}
button,input,textarea{width:100%;padding:13px;margin:0 0 10px;font:inherit;color:var(--fg);background:var(--card);border:1px solid var(--line);border-radius:12px}
button:active{border-color:var(--acc)}
textarea{min-height:84px;resize:vertical}
.g2{display:flex;gap:10px}.g3{display:flex;gap:8px}.g2 button,.g3 button{flex:1;min-width:0}
.g2 button,.g3 button{margin:0}
select{width:100%;padding:13px;margin:0 0 10px;font:inherit;color:var(--fg);background:var(--card);border:1px solid var(--line);border-radius:12px}
.np{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px;margin:0 0 10px}.np b{display:block;font-weight:600}.np span{color:var(--mut);font-size:13px}
.g3 button{padding:12px 4px;font-size:14px}
.on{border-color:var(--acc);font-weight:600}
.m{color:var(--mut);font-size:13px;min-height:18px;margin:8px 0 0}
label{display:block;font-size:13px;color:var(--mut);margin:12px 0 0}
input[type=range]{padding:0;border:0;margin:6px 0 2px;background:none;accent-color:var(--acc)}
details{background:var(--card);border:1px solid var(--line);border-radius:12px;margin:0 0 10px;padding:0 14px}
summary{cursor:pointer;padding:14px 0;font-weight:600;list-style:none}
summary::-webkit-details-marker{display:none}
details[open] summary{border-bottom:1px solid var(--line);margin-bottom:8px}
.row{padding:9px 0;border-bottom:1px solid var(--line)}.row:last-child{border:0}
.row b{font-weight:600}.row span{display:block;color:var(--mut);font-size:13px;margin-top:2px;overflow-wrap:anywhere}
.row .t{color:var(--fg);font-size:14px}
.kv{display:flex;justify-content:space-between;gap:12px;padding:9px 0;border-bottom:1px solid var(--line);font-size:14px}.kv:last-child{border:0}.kv span:first-child{color:var(--mut)}
</style>"""

    const val LOGIN = """<!doctype html><html><head><title>Pocket Relay</title>$STYLE</head><body><main>
<h1>Pocket Relay</h1><p class="s">Enter the PIN shown in the app.</p>
<form method="post" action="/login"><input name="pin" type="password" inputmode="numeric" autocomplete="off" autofocus placeholder="PIN">
<button>Unlock</button></form><p class="m">MSG</p></main></body></html>"""

    const val MAIN = """<!doctype html><html><head><title>Pocket Relay</title>$STYLE</head><body><main>
<h1>Pocket Relay</h1><p class="s" id="s">Loading...</p>

<div class="g2"><button id="ring" data-f="ring" onclick="act('ring')">Ring phone</button><button id="torch" data-f="flashlight" onclick="act('flashlight')">Flashlight</button></div>

<div data-f="toggles"><h2>Toggles</h2>
<div class="g3"><button id="t-bluetooth" onclick="tog('bluetooth')">Bluetooth</button><button id="t-wifi" onclick="tog('wifi')">Wi-Fi</button><button id="t-data" onclick="tog('data')">Mobile data</button></div>
<button style="margin-top:10px" onclick="act('restart-data')">Restart mobile data</button></div>

<div data-f="sound"><h2>Sound</h2>
<label>Media <span id="v-media"></span></label><input type="range" min="0" max="100" id="r-media" onchange="vol('media',this.value)">
<label>Ring <span id="v-ring"></span></label><input type="range" min="0" max="100" id="r-ring" onchange="vol('ring',this.value)">
<label>Alarm <span id="v-alarm"></span></label><input type="range" min="0" max="100" id="r-alarm" onchange="vol('alarm',this.value)">
<div class="g3" style="margin-top:10px"><button id="m-normal" onclick="ringer('normal')">Normal</button><button id="m-vibrate" onclick="ringer('vibrate')">Vibrate</button><button id="m-silent" onclick="ringer('silent')">Silent</button></div></div>

<div data-f="music"><h2>Music</h2>
<div class="np" id="np">Loading...</div>
<div class="g3"><button onclick="mc('previous')">Previous</button><button id="pp" onclick="mc('toggle')">Play</button><button onclick="mc('next')">Next</button></div>
</div>

<p class="m" id="m"></p>

<div data-any="messages,send,calls"><h2>Messages and calls</h2>
<details data-f="messages" id="d-sms" ontoggle="if(this.open)loadSms()"><summary>Recent messages</summary><div id="sms"></div></details>
<details data-f="send" id="d-send"><summary>Send a text</summary>
<label>To</label><input id="to" type="tel" placeholder="+92300..." autocomplete="off">
<label>Message</label><textarea id="text" maxlength="480"></textarea>
<button onclick="send()">Send</button></details>
<details data-f="calls" id="d-calls" ontoggle="if(this.open)loadCalls()"><summary>Recent calls</summary><div id="calls"></div></details></div>

<div data-any="devices,info"><h2>Phone</h2>
<details data-f="devices" id="d-dev" ontoggle="if(this.open)devices()"><summary id="dh">Devices on this hotspot</summary><div id="d"></div></details>
<details data-f="info" id="d-info" ontoggle="if(this.open)loadInfo()"><summary>Signal, data, storage</summary><div id="info"></div></details></div>

<script>
var st={};
function el(id){return document.getElementById(id);}
function api(path,method){return fetch('/api/'+path,{method:method||'GET'}).then(function(r){if(r.status==401){location.reload();}return r.json();});}
function say(t){el('m').textContent=t;}
function when(ms){var d=new Date(ms);return d.toLocaleString([], {month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function add(parent,cls,text){var e=document.createElement('div');if(cls)e.className=cls;e.textContent=text;parent.appendChild(e);return e;}

function features(){
  var f=st.features||{};
  document.querySelectorAll('[data-f]').forEach(function(e){e.style.display=f[e.getAttribute('data-f')]?'':'none';});
  document.querySelectorAll('[data-any]').forEach(function(e){
    var on=e.getAttribute('data-any').split(',').some(function(k){return f[k];});
    e.style.display=on?'':'none';
  });
}
function show(){
  el('s').textContent=st.battery+'%'+(st.charging?' charging':'')+' · '+st.network+' · data '+(st.data?'on':'off');
  el('ring').textContent=st.ringing?'Stop ringing':'Ring phone';
  el('torch').textContent=st.torch?'Flashlight off':'Flashlight on';
  ['bluetooth','wifi','data'].forEach(function(n){var b=el('t-'+n);if(!b.getAttribute('data-n'))b.setAttribute('data-n',b.textContent);b.className=st[n]?'on':'';b.textContent=b.getAttribute('data-n')+(st[n]?' · on':' · off');});
  ['media','ring','alarm'].forEach(function(n){if(document.activeElement!==el('r-'+n)){el('r-'+n).value=st[n];}el('v-'+n).textContent=st[n]+'%';});
  ['normal','vibrate','silent'].forEach(function(n){el('m-'+n).className=(st.ringer==n)?'on':'';});
  features();
}
function refresh(){
  api('status').then(function(j){st=j;show();if(j.features&&j.features.music)music();});
}
function act(a){
  say('Working...');
  var p=a;
  if(a=='ring')p='ring?on='+(st.ringing?0:1);
  if(a=='flashlight')p='flashlight?on='+(st.torch?0:1);
  api(p,'POST').then(function(j){say(j.message||'Done');refresh();});
}
function tog(n){say('Working... (needs the phone unlocked for Wi-Fi and mobile data)');api('toggle?name='+n+'&on='+(st[n]?0:1),'POST').then(function(j){say(j.message||'Done');refresh();});}
function vol(n,v){api('volume?stream='+n+'&level='+v,'POST').then(function(j){say(j.message||'');refresh();});}
function ringer(m){api('ringer?mode='+m,'POST').then(function(j){say(j.message||'');refresh();});}

function music(){
  api('music').then(function(j){
    var np=el('np');np.textContent='';
    if(!j.title){np.textContent=j.access?'Nothing playing. Play resumes your last music app.':'Turn on notification access in the app to see what is playing. The buttons still work.';}
    else{var b=document.createElement('b');b.textContent=j.title;np.appendChild(b);var s=document.createElement('span');s.textContent=(j.artist?j.artist+' · ':'')+j.app;np.appendChild(s);}
    el('pp').textContent=j.playing?'Pause':'Play';
  });
}
function mc(a){api('music-control?action='+a,'POST').then(function(j){say(j.message||'');setTimeout(music,700);});}
function loadSms(){
  var d=el('sms');d.textContent='Loading...';
  api('sms').then(function(j){
    d.textContent='';
    if(!j.items.length)add(d,'m','No messages');
    j.items.forEach(function(x){
      var r=add(d,'row','');r.style.cursor='pointer';
      var b=document.createElement('b');b.textContent=(x.sent?'To ':'')+(x.name||x.number);r.appendChild(b);
      add(r,'',when(x.date)+(x.name?' · '+x.number:''));add(r,'t',x.body);
      r.onclick=function(){el('to').value=x.number;el('d-send').open=true;el('text').focus();};
    });
  });
}
function loadCalls(){
  var d=el('calls');d.textContent='Loading...';
  api('calls').then(function(j){
    d.textContent='';
    if(!j.items.length)add(d,'m','No calls');
    j.items.forEach(function(x){
      var r=add(d,'row','');
      var b=document.createElement('b');b.textContent=x.name||x.number||'Unknown';r.appendChild(b);
      add(r,'',x.type+' · '+when(x.date)+(x.seconds?' · '+x.seconds+'s':''));
    });
  });
}
function send(){
  say('Sending...');
  api('sms-send?to='+encodeURIComponent(el('to').value)+'&text='+encodeURIComponent(el('text').value),'POST').then(function(j){
    say(j.message||'');if((j.message||'').indexOf('Sent')==0){el('text').value='';}
  });
}
function loadInfo(){
  var d=el('info');d.textContent='Loading...';
  api('info').then(function(j){
    d.textContent='';
    [['Operator',j.operator],['Network',j.type],['Signal',j.signal],['Mobile data used since boot',j.dataUsedMb+' MB'],
     ['Battery temperature',j.temperature+' °C'],['Storage',j.storage],['Memory',j.memory],['Uptime',j.uptimeHours.toFixed(1)+' h']].forEach(function(kv){
      var r=add(d,'kv','');add(r,'',kv[0]);add(r,'',String(kv[1]));
    });
  });
}
function devices(){
  api('devices').then(function(j){
    var d=el('d');d.textContent='';
    el('dh').textContent='Devices on this hotspot ('+j.devices.length+')';
    if(!j.devices.length)add(d,'m','None found');
    j.devices.forEach(function(x){
      var row=add(d,'row','');row.style.cursor='pointer';
      var b=document.createElement('b');b.textContent=(x.name||'Unnamed device')+(x.you?' (this device)':'');row.appendChild(b);
      add(row,'',x.ip+(x.mac?'  ·  '+x.mac:''));
      row.onclick=function(){
        if(!x.mac)return;
        var n=prompt('Name for this device',x.name||'');
        if(n===null)return;
        api('name?mac='+encodeURIComponent(x.mac)+'&name='+encodeURIComponent(n),'POST').then(devices);
      };
    });
  });
}
refresh();setInterval(refresh,5000);
</script></main></body></html>"""
}
