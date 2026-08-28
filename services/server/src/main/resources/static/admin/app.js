const $=id=>document.getElementById(id);const labels={CONSENTED:'동의',DECLINED:'미동의',NOT_ASKED:'미선택'};
const today=()=>{const d=new Date(),offset=d.getTimezoneOffset();return new Date(d.getTime()-offset*60000).toISOString().slice(0,10)};
const delta=(id,current,previous,digits=0)=>{const value=current-previous,arrow=value>0?'▲':value<0?'▼':'–';$(id).textContent=`전일 대비 ${arrow} ${Math.abs(value).toLocaleString('ko-KR',{minimumFractionDigits:digits,maximumFractionDigits:digits})}`;$(id).className=`delta ${value>0?'up':value<0?'down':'same'}`};
async function load(){
  $('error').textContent='';
  try{
    const code=$('project').value.trim(),date=$('date').value||today();
    const response=await fetch(`/api/v1/admin/dashboard?projectCode=${encodeURIComponent(code)}&date=${encodeURIComponent(date)}`);
    if(!response.ok)throw new Error(`집계 API 오류 (${response.status})`);
    const data=await response.json(),s=data.summary,p=data.previous_summary,total=Math.max(s.total,1),rate=Math.round(s.consented/total*100);
    $('overallTotal').textContent=data.overall_summary.total.toLocaleString();
    $('total').textContent=s.total.toLocaleString();$('consented').textContent=s.consented.toLocaleString();
    $('declined').textContent=s.declined.toLocaleString();$('stress').textContent=Number(s.average_stress).toFixed(1);$('consentRate').textContent=`${rate}%`;
    delta('totalDelta',s.total,p.total);delta('consentedDelta',s.consented,p.consented);delta('declinedDelta',s.declined,p.declined);delta('stressDelta',s.average_stress,p.average_stress,1);
    $('summaryDate').textContent=`${data.date} 기준`;
    const max=Math.max(...data.emotions.map(x=>x.count),1);$('emotions').innerHTML=data.emotions.length?data.emotions.map(x=>`<div class="bar-row"><span>${x.name}</span><div class="bar-track"><div class="bar-fill" style="width:${x.count/max*100}%"></div></div><strong>${x.count}</strong></div>`).join(''):'<p>아직 추천 데이터가 없습니다.</p>';
    const declined=Math.round(s.declined/total*100),unknown=Math.max(0,100-rate-declined);$('consentChart').innerHTML=`<div><div class="donut" style="background:conic-gradient(#3c7655 0 ${rate}%,#e6a958 ${rate}% ${rate+declined}%,#d9ddd7 ${rate+declined}% 100%)"></div><div class="legend">● 동의 ${s.consented}　● 미동의 ${s.declined}<br>● 미선택 ${s.not_asked}</div></div>`;
    $('recent').innerHTML=data.recent.map(x=>`<tr><td>${new Date(x.occurred_at).toLocaleString('ko-KR')}</td><td>${x.kiosk_id}</td><td>${x.participant_name||'–'}</td><td>${x.participant_phone||'–'}</td><td>${x.participant_birth_date||'–'}</td><td>${x.participant_gender||'–'}</td><td>${x.emotion_code}</td><td><span class="badge ${x.consent_status==='CONSENTED'?'yes':x.consent_status==='DECLINED'?'no':''}">${labels[x.consent_status]||x.consent_status}</span></td><td>${x.stress_score}</td><td>${x.source}</td></tr>`).join('');
    $('updated').textContent=`갱신 ${new Date().toLocaleTimeString('ko-KR')}`;
  }catch(e){$('error').textContent=e.message;}
}
$('date').value=today();$('refresh').addEventListener('click',load);$('date').addEventListener('change',load);$('project').addEventListener('keydown',e=>{if(e.key==='Enter')load()});load();setInterval(load,30000);
