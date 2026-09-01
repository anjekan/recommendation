const $ = id => document.getElementById(id);
const labels = {CONSENTED: '동의', DECLINED: '미동의', NOT_ASKED: '미선택'};
const statusLabels = {NORMAL: '정상', CONGESTED: '혼잡', PAUSED: '추천 중지'};
const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, char => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'}[char]));
const localized = (value, language = 'ko') => value?.[language] || Object.values(value || {})[0] || '이름 없음';
const today = () => {
  const date = new Date(), offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
};
const delta = (id, current, previous, digits = 0) => {
  const value = current - previous, arrow = value > 0 ? '▲' : value < 0 ? '▼' : '–';
  $(id).textContent = `전일 대비 ${arrow} ${Math.abs(value).toLocaleString('ko-KR', {minimumFractionDigits: digits, maximumFractionDigits: digits})}`;
  $(id).className = `delta ${value > 0 ? 'up' : value < 0 ? 'down' : 'same'}`;
};

function renderLocations(config, counts, selectedTotal) {
  const countById = new Map(counts.map(item => [item.location_id, Number(item.count)]));
  const locations = (config.locations || []).map(location => ({
    ...location,
    count: countById.get(location.id) || 0,
  })).sort((left, right) => right.count - left.count || localized(left.name).localeCompare(localized(right.name), 'ko'));
  const max = Math.max(...locations.map(location => location.count), 1);
  $('locationSummary').textContent = `${locations.length}개 장소 · 선택일 ${selectedTotal.toLocaleString()}건`;
  $('locations').innerHTML = locations.length ? locations.map(location => {
    const ratio = selectedTotal ? Math.round(location.count / selectedTotal * 100) : 0;
    const status = location.status || 'NORMAL';
    return `<article class="location-card">
      <div class="location-heading"><div><strong>${escapeHtml(localized(location.name))}</strong><small>${escapeHtml(location.code)}</small></div><span class="status ${status.toLowerCase()}">${escapeHtml(statusLabels[status] || status)}</span></div>
      <div class="location-count"><strong>${location.count.toLocaleString()}</strong><span>건 · ${ratio}%</span></div>
      <div class="bar-track"><div class="bar-fill" style="width:${location.count / max * 100}%"></div></div>
    </article>`;
  }).join('') : '<p>프로젝트에 등록된 추천 장소가 없습니다.</p>';
}

async function load() {
  $('error').textContent = '';
  try {
    const code = $('project').value.trim(), date = $('date').value || today();
    const [dashboardResponse, configResponse] = await Promise.all([
      fetch(`/api/v1/admin/dashboard?projectCode=${encodeURIComponent(code)}&date=${encodeURIComponent(date)}`),
      fetch(`/api/v1/projects/${encodeURIComponent(code)}/config`),
    ]);
    if (!dashboardResponse.ok) throw new Error(`집계 API 오류 (${dashboardResponse.status})`);
    if (!configResponse.ok) throw new Error(`프로젝트 설정 오류 (${configResponse.status})`);
    const data = await dashboardResponse.json(), config = await configResponse.json();
    const summary = data.summary, previous = data.previous_summary, total = Math.max(summary.total, 1);
    const rate = Math.round(summary.consented / total * 100);
    $('overallTotal').textContent = data.overall_summary.total.toLocaleString();
    $('total').textContent = summary.total.toLocaleString();
    $('consented').textContent = summary.consented.toLocaleString();
    $('declined').textContent = summary.declined.toLocaleString();
    $('stress').textContent = Number(summary.average_stress).toFixed(1);
    $('consentRate').textContent = `${rate}%`;
    delta('totalDelta', summary.total, previous.total);
    delta('consentedDelta', summary.consented, previous.consented);
    delta('declinedDelta', summary.declined, previous.declined);
    delta('stressDelta', summary.average_stress, previous.average_stress, 1);
    $('summaryDate').textContent = `${data.date} 기준`;
    const emotionMax = Math.max(...data.emotions.map(item => item.count), 1);
    $('emotions').innerHTML = data.emotions.length ? data.emotions.map(item => `<div class="bar-row"><span>${escapeHtml(item.name)}</span><div class="bar-track"><div class="bar-fill" style="width:${item.count / emotionMax * 100}%"></div></div><strong>${item.count}</strong></div>`).join('') : '<p>아직 추천 데이터가 없습니다.</p>';
    const declined = Math.round(summary.declined / total * 100);
    $('consentChart').innerHTML = `<div><div class="donut" style="background:conic-gradient(#3c7655 0 ${rate}%,#e6a958 ${rate}% ${rate + declined}%,#d9ddd7 ${rate + declined}% 100%)"></div><div class="legend">● 동의 ${summary.consented}　● 미동의 ${summary.declined}<br>● 미선택 ${summary.not_asked}</div></div>`;
    renderLocations(config, data.locations || [], summary.total);
    $('recent').innerHTML = data.recent.map(item => `<tr><td>${new Date(item.occurred_at).toLocaleString('ko-KR')}</td><td>${escapeHtml(item.kiosk_id)}</td><td>${escapeHtml(item.participant_name || '–')}</td><td>${escapeHtml(item.participant_phone || '–')}</td><td>${escapeHtml(item.participant_birth_date || '–')}</td><td>${escapeHtml(item.participant_gender || '–')}</td><td>${escapeHtml(item.emotion_code)}</td><td><span class="badge ${item.consent_status === 'CONSENTED' ? 'yes' : item.consent_status === 'DECLINED' ? 'no' : ''}">${escapeHtml(labels[item.consent_status] || item.consent_status)}</span></td><td>${item.stress_score}</td><td>${escapeHtml(item.source)}</td></tr>`).join('');
    $('updated').textContent = `갱신 ${new Date().toLocaleTimeString('ko-KR')}`;
  } catch (error) {
    $('error').textContent = error.message;
  }
}

$('date').value = today();
$('refresh').addEventListener('click', load);
$('date').addEventListener('change', load);
$('project').addEventListener('keydown', event => { if (event.key === 'Enter') load(); });
load();
setInterval(load, 30000);
