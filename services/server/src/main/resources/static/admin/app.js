const $ = id => document.getElementById(id);
const labels = {CONSENTED: '동의', DECLINED: '미동의', NOT_ASKED: '미선택'};
const statusLabels = {NORMAL: '정상', PRIORITY: '우선 추천', CONGESTED: '혼잡', PAUSED: '추천 중지'};
const senseLabels = {INSIGHT: '통찰', SCENT: '향기', TASTE: '미식', LISTENING: '경청', ACTION: '실천', INTUITION: '직관'};
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

function renderLocations(config, counts, selectedTotal, operationalStatuses) {
  const countById = new Map(counts.map(item => [item.location_id, Number(item.count)]));
  const countByCode = new Map(counts.filter(item => item.location_code).map(item => [item.location_code, Number(item.count)]));
  const policyByCode = new Map(operationalStatuses.map(item => [item.location_code, item]));
  const configured = (config.locations || []).length ? config.locations : (config.rich_flow?.venue_operations || []).map(venue => ({
    ...venue,
    status: venue.active && venue.confirmation_status === 'CONFIRMED' ? 'NORMAL' : 'PAUSED',
  }));
  const enabled = location => policyByCode.has(location.code) ? policyByCode.get(location.code).enabled : location.status !== 'PAUSED';
  const enabledSenseCounts = new Map();
  configured.filter(enabled).forEach(location => (location.sense_codes || []).forEach(sense => enabledSenseCounts.set(sense, (enabledSenseCounts.get(sense) || 0) + 1)));
  const conditionNames = new Map((config.rich_flow?.condition_states || []).map(state => [state.code, localized(state.display_name)]));
  const mappings = config.rich_flow?.journey_mappings || [];
  const locations = configured.map(location => {
    const policy = policyByCode.get(location.code), priorityActive = policy?.enabled && policy.priority_share && policy.priority_until && new Date(policy.priority_until) > new Date();
    const senses = location.sense_codes || [];
    const affectedConditions = mappings.filter(mapping => mapping.sense_sequence?.some(sense => senses.includes(sense)))
      .map(mapping => conditionNames.get(mapping.condition_code) || mapping.condition_code);
    return {
      ...location,
      senses,
      affectedConditions: [...new Set(affectedConditions)],
      critical: senses.some(sense => enabledSenseCounts.get(sense) === 1),
      status: policy ? (!policy.enabled ? 'PAUSED' : priorityActive ? 'PRIORITY' : 'NORMAL') : location.status,
      priorityShare: priorityActive ? policy.priority_share : null,
      priorityUntil: priorityActive ? policy.priority_until : null,
      count: countById.get(location.id) || countByCode.get(location.code) || 0,
    };
  }).sort((left, right) => right.count - left.count || localized(left.name).localeCompare(localized(right.name), 'ko'));
  const max = Math.max(...locations.map(location => location.count), 1);
  $('locationSummary').textContent = `${locations.length}개 장소 · 선택일 ${selectedTotal.toLocaleString()}건`;
  $('locations').innerHTML = locations.length ? locations.map(location => {
    const ratio = selectedTotal ? Math.round(location.count / selectedTotal * 100) : 0;
    const status = location.status || 'NORMAL';
    const primarySense = location.senses[0] || 'NONE';
    const senseBadges = location.senses.map(sense => `<span class="sense-badge sense-${sense.toLowerCase()}">${escapeHtml(senseLabels[sense] || sense)}</span>`).join('');
    const emotionBadges = location.affectedConditions.map(name => `<span class="emotion-badge">${escapeHtml(name)}</span>`).join('');
    const priorityDescription = status === 'PRIORITY' ? `<small class="priority-description">${location.priorityShare}% · ${new Date(location.priorityUntil).toLocaleTimeString('ko-KR', {hour:'2-digit',minute:'2-digit'})}까지</small>` : '';
    return `<article class="location-card sense-card-${primarySense.toLowerCase()} ${status === 'PAUSED' ? 'is-paused' : ''}">
      <div class="location-heading"><div><strong>${escapeHtml(localized(location.name))}</strong><small>${escapeHtml(location.code)}</small></div><span class="status ${status.toLowerCase()}">${escapeHtml(statusLabels[status] || status)}</span></div>
      <div class="sense-list">${senseBadges}${location.critical ? '<span class="critical-badge">필수 장소</span>' : ''}</div>
      <div class="emotion-list">${emotionBadges}</div>
      ${priorityDescription}
      <div class="location-count"><strong>${location.count.toLocaleString()}</strong><span>건 · ${ratio}%</span></div>
      <div class="bar-track"><div class="bar-fill" style="width:${location.count / max * 100}%"></div></div>
      <div class="priority-settings"><select class="priority-share" aria-label="우선 추천 비율"><option value="20">20%</option><option value="30" selected>30%</option><option value="40">40%</option><option value="50">50%</option></select><select class="priority-minutes" aria-label="우선 추천 시간"><option value="30">30분</option><option value="60" selected>1시간</option><option value="120">2시간</option></select></div>
      <div class="location-actions"><button class="location-mode priority-action" data-location-code="${escapeHtml(location.code)}" data-mode="PRIORITY">우선 추천</button><button class="location-mode" data-location-code="${escapeHtml(location.code)}" data-mode="${status === 'PAUSED' || status === 'PRIORITY' ? 'NORMAL' : 'PAUSED'}">${status === 'PAUSED' || status === 'PRIORITY' ? '정상 전환' : '추천 중지'}</button></div>
    </article>`;
  }).join('') : '<p>프로젝트에 등록된 추천 장소가 없습니다.</p>';
}

function renderOperations(data) {
  const max = Math.max(...data.hourly.map(item => item.count), 1);
  $('hourly').innerHTML = data.hourly.map(item => `<div class="hour-column" title="${item.hour}시 ${item.count}건"><div class="hour-bar" style="height:${Math.max(item.count / max * 100, item.count ? 5 : 0)}%"></div><span>${item.hour}</span></div>`).join('');
  $('kioskSummary').textContent = `${data.kiosks.length}대 활동`;
  $('kiosks').innerHTML = data.kiosks.length ? data.kiosks.map(item => `<div class="kiosk-row"><div><strong>${escapeHtml(item.kiosk_id)}</strong><small>마지막 ${new Date(item.last_activity_at).toLocaleTimeString('ko-KR')}</small></div><span>${item.count.toLocaleString()}건</span></div>`).join('') : '<p>선택일의 키오스크 활동이 없습니다.</p>';
}

async function load() {
  $('error').textContent = '';
  try {
    const code = $('project').value.trim(), date = $('date').value || today();
    const [dashboardResponse, configResponse, statusResponse] = await Promise.all([
      fetch(`/api/v1/admin/dashboard?projectCode=${encodeURIComponent(code)}&date=${encodeURIComponent(date)}`),
      fetch(`/api/v1/projects/${encodeURIComponent(code)}/config`),
      fetch(`/api/v1/admin/location-statuses?projectCode=${encodeURIComponent(code)}`),
    ]);
    if (!dashboardResponse.ok) throw new Error(`집계 API 오류 (${dashboardResponse.status})`);
    if (!configResponse.ok) throw new Error(`프로젝트 설정 오류 (${configResponse.status})`);
    if (!statusResponse.ok) throw new Error(`운영 상태 API 오류 (${statusResponse.status})`);
    const data = await dashboardResponse.json(), config = await configResponse.json(), operationalStatuses = await statusResponse.json();
    const projectName = localized(config.theme?.name) || code;
    $('projectEyebrow').textContent = `${code} · OPERATIONS`;
    $('dashboardTitle').textContent = `${projectName} 운영 대시보드`;
    document.title = `${projectName} 운영 대시보드`;
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
    renderLocations(config, data.locations || [], summary.total, operationalStatuses);
    renderOperations(data);
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
$('locations').addEventListener('click', async event => {
  const button = event.target.closest('.location-mode');
  if (!button) return;
  button.disabled = true;
  try {
    const projectCode = $('project').value.trim(), locationCode = button.dataset.locationCode, mode = button.dataset.mode;
    const card = button.closest('.location-card');
    const share = Number(card.querySelector('.priority-share').value), minutes = Number(card.querySelector('.priority-minutes').value);
    const body = mode === 'PRIORITY'
      ? {enabled: true, priority_share: share, priority_until: new Date(Date.now() + minutes * 60000).toISOString()}
      : {enabled: mode === 'NORMAL'};
    if (mode === 'PAUSED' && !confirm('이 장소를 추천 중지하시겠습니까? 추천 여정에서 즉시 제외됩니다.')) { button.disabled = false; return; }
    const response = await fetch(`/api/v1/admin/location-statuses/${encodeURIComponent(locationCode)}?projectCode=${encodeURIComponent(projectCode)}`, {
      method: 'PUT', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new Error(`운영 상태 변경 오류 (${response.status})`);
    await load();
  } catch (error) {
    $('error').textContent = error.message;
    button.disabled = false;
  }
});
async function initialize() {
  try {
    const response = await fetch('/api/v1/admin/context');
    if (!response.ok) throw new Error(`관리자 설정 오류 (${response.status})`);
    const context = await response.json();
    $('projectCodes').innerHTML = (context.project_codes || []).map(code => `<option value="${escapeHtml(code)}"></option>`).join('');
    $('project').value = context.default_project_code || context.project_codes?.[0] || '';
  } catch (error) {
    $('error').textContent = error.message;
  }
  if ($('project').value) await load();
}
initialize();
setInterval(() => { if ($('project').value) load(); }, 30000);
