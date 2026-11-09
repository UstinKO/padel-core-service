(() => {
'use strict';
const root = document.getElementById('social-draft');
if (!root) return;
const $ = id => root.querySelector('#social-' + id);
const msg = key => root.getAttribute('data-msg-' + key.toLowerCase());
const slots = ['winners', 'finalists'];
const cards = [...slots, 'bracket'];
let draft;
let eventDraft;
let savedVersion;
let saving = false;
let readingSaved = false;
let imageNames;
let previews = [];
let generation = 0;
let loading = false;
let addedLink = '';

const isPaired = data => data.format === 'TEAM_PLAYOFF';
const categoryLabel = data => (data.category || '').replaceAll('_', '/');
const labels = {
  AMERICANO: ['Americano', 'Американо'],
  AMERICANO_TEAMS: ['Americano por parejas', 'Парный Американо'],
  KING_OF_COURT: ['Rey de la Cancha', 'Король корта'],
  CANCHA_ABIERTA: ['Cancha Abierta', 'Открытый корт'],
  PADEL_CLINIC: ['Partidos + Entrenamientos', 'Тренировка + игры']
};
const filenamesByFormat = {
  TEAM_PLAYOFF: ['01-campeones.jpg', '02-subcampeones.jpg', '03-cuadro-final.jpg'],
  KING_OF_COURT: ['01-rey-de-la-cancha.jpg'],
  AMERICANO: ['01-americano.jpg'],
  GENERAL: ['01-evento.jpg']
};
const photoLabels = {
  '01-campeones.jpg': 'winnersCard', '02-subcampeones.jpg': 'finalistsCard',
  '03-cuadro-final.jpg': 'bracketCard', '01-rey-de-la-cancha.jpg': 'group',
  '01-americano.jpg': 'group', '01-evento.jpg': 'group'
};
const eventLabels = data => labels[data.type] || [data.name || 'Evento de pádel', data.name || 'Падель'];
const packagePath = root.dataset.endpoint + '/package';

function buildCaptions(data) {
  const date = data.date.split('-').reverse().join('.');
  const category = categoryLabel(data);
  const location = [data.club, date].filter(Boolean).join(' · ');
  const [es, ru] = eventLabels(data);
  const results = data.resultsPath ? new URL(data.resultsPath, window.location.origin).href : '';
  let instagram;
  let telegram;
  if (data.format === 'GENERAL') {
    const suffix = category ? ' · ' + category : '';
    const title = data.name && data.name.toLocaleLowerCase() !== es.toLocaleLowerCase() ? data.name : '';
    const details = [title, '📍 ' + location].filter(Boolean);
    instagram = ['🎾 ' + es.toUpperCase() + suffix, ...details, '',
      'Compartimos una foto de la jornada. ¡Gracias por venir a jugar con nosotros!'].join('\n');
    telegram = ['🎾 ' + ru.toUpperCase() + suffix, ...details, '',
      'Делимся фото с мероприятия. Спасибо, что пришли поиграть!'].join('\n');
  } else if (!isPaired(data)) {
    const [first, second] = data.places;
    const isAmericano = data.format === 'AMERICANO';
    const titleEs = isAmericano ? 'AMERICANO' : 'REY DE LA CANCHA';
    const titleRu = isAmericano ? 'АМЕРИКАНО' : 'КОРОЛЬ КОРТА';
    const unitEs = data.rankingByWins ? 'victorias' : 'puntos';
    const unitRu = data.rankingByWins ? 'победы' : 'очки';
    const categoryPart = category ? ' — ' + category : '';
    instagram = [
      '🎾 ' + titleEs + categoryPart + ' · INDIVIDUAL',
      '📍 ' + location,
      '', '🥇 ' + first.name + ' — ' + first.points + ' ' + unitEs,
      '🥈 ' + second.name + ' — ' + second.points + ' ' + unitEs, '',
      'Jugamos con parejas rotativas y clasificación individual. ¡Gracias a todos por participar!'
    ].join('\n');
    telegram = [
      '🎾 ' + titleRu + categoryPart + ' · ИНДИВИДУАЛЬНЫЙ',
      '📍 ' + location,
      '', '🥇 1-е место: ' + first.name + ' · ' + unitRu + ': ' + first.points,
      '🥈 2-е место: ' + second.name + ' · ' + unitRu + ': ' + second.points,
      '', 'Партнёры менялись, а результаты шли в личный зачёт. Спасибо всем за игру!',
      '', '📊 Итоговая таблица: ' + results
    ].join('\n');
  } else {
    const score = data.winnerGames + ':' + data.finalistGames;
    instagram = [
      '🎾 TORNEO ' + category + ' — AMERICANO EN PAREJAS',
      '📍 ' + location,
      '', '¡Felicitaciones!',
      '🥇 Campeones: ' + data.winners.join(' y '), '🥈 Subcampeones: ' + data.finalists.join(' y '),
      '🏆 Final: ' + score, '',
      'Jugamos con parejas fijas: primero la clasificación y después el cuadro eliminatorio. ¡Gracias a todos por participar!'
    ].join('\n');
    telegram = [
      '🎾 ТУРНИР ' + category + ' — ПАРНЫЙ АМЕРИКАНО',
      '📍 ' + location,
      '', 'Поздравляем победителей и финалистов!',
      '🥇 1-е место: ' + data.winners.join(' и '), '🥈 2-е место: ' + data.finalists.join(' и '),
      '🏆 Финал: ' + score, '',
      'Играли в постоянных парах: сначала квалификация, затем плей-офф. Спасибо всем за игру!',
      '', '📊 Все результаты: ' + results
    ].join('\n');
  }
  const next = data.upcoming;
  if (next) {
    const labels = {
      AMERICANO:['Americano','Американо'], AMERICANO_TEAMS:['Americano','Американо'],
      KING_OF_COURT:['Rey de la Cancha','Король корта'],
      CANCHA_ABIERTA:['Cancha Abierta','Открытый корт'], PADEL_CLINIC:['Clínica de Pádel','Тренировка']
    };
    const [es, ru] = labels[next.type] || ['', ''];
    const pair = next.modality === 'DOBLES';
    const time = next.time.slice(0, 5);
    const start = new Date(next.date + 'T12:00:00');
    const esDate = new Intl.DateTimeFormat('es-AR', {day:'numeric',month:'long'}).format(start);
    const ruDate = new Intl.DateTimeFormat('ru-RU', {day:'numeric',month:'long'}).format(start);
    instagram += '\n\n📅 Próximo ' + category + ': ' + es + (pair ? ' por parejas' : ' individual') +
      ' · ' + esDate + ', ' + time + ' h · ' + next.club + '.\n📲 Inscripción en 1-padel.com (link en la bio).';
    telegram += '\n\n📅 Ближайший ' + category + ': ' + ru + (pair ? ' (парный)' : ' (индивидуальный)') +
      ' · ' + ruDate + ', ' + time + ' · ' + next.club + '.\n📲 Запись: ' +
      new URL(next.registrationPath, window.location.origin).href;
  }
  return {instagram, telegram};
}

function showMode(data) {
  draft = data;
  addedLink = '';
  const captions = buildCaptions(data);
  const single = !isPaired(data);
  imageNames = filenamesByFormat[data.format];
  $('finalist-slot').hidden = single;
  $('finalists-card').hidden = single;
  $('bracket-input').hidden = single;
  $('photo-label').textContent = msg(single ? 'group' : 'winners');
  $('primary-title').textContent = msg(single ? 'group' : 'winnersCard');
  $('winners-preview').alt = $('primary-title').textContent;
  $('winner-names').textContent = single
    ? data.places.map(place => place.rank + '. ' + place.name).join(' · ')
    : data.winners.join(' · ');
  $('finalist-names').textContent = single ? '' : data.finalists.join(' · ');
  $('instagram').value = captions.instagram;
  $('telegram').value = captions.telegram;
  $('links').hidden = single && !data.resultsPath;
  $('results-link').hidden = !data.resultsPath;
  if (data.resultsPath) $('results-link').href = data.resultsPath;
  $('editor').hidden = false;
  refresh();
}

root.addEventListener('toggle', async () => {
  if (!root.open) return;
  loadSaved();
  if (eventDraft || loading) return;
  loading = true;
  $('load-status').textContent = msg('loading');
  try {
    const response = await fetch(root.dataset.endpoint + '?extended=true', {headers:{Accept:'application/json'},cache:'no-store'});
    if (!response.ok) {
      $('load-status').textContent = msg(response.status === 409 ? 'incomplete' : 'unavailable');
      return;
    }
    eventDraft = await response.json();
    $('mode-picker').hidden = eventDraft.format === 'GENERAL';
    showMode(eventDraft);
    $('load-status').textContent = '';
  } catch {
    $('load-status').textContent = msg('unavailable');
  } finally {
    loading = false;
  }
});

$('mode').addEventListener('change', () => {
  const general = $('mode').value === 'general';
  showMode(general ? {...eventDraft, format:'GENERAL', places:[], resultsPath:null, upcoming:null} : eventDraft);
});

function rounded(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x+r,y); ctx.arcTo(x+w,y,x+w,y+h,r);
  ctx.arcTo(x+w,y+h,x,y+h,r); ctx.arcTo(x,y+h,x,y,r);
  ctx.arcTo(x,y,x+w,y,r); ctx.closePath(); ctx.fill();
}
// CSP страницы разрешает изображения data:, а blob: оставляем только для скачивания.
function readDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error(msg('decode')));
    reader.readAsDataURL(file);
  });
}
function loadPhoto(url) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(msg('decode')));
    img.src = url;
  });
}
function fit(ctx, text, max, size, font) {
  for (let point=size; point>=18; point--) {
    ctx.font = font.replace('{size}', point);
    if (ctx.measureText(text).width <= max) return;
  }
  throw new Error(msg('renderError'));
}
function drawCover(ctx, img, width, height, bottomBias = .5) {
  const scale = Math.max(width / img.naturalWidth, height / img.naturalHeight);
  const sw = width / scale, sh = height / scale;
  ctx.drawImage(img, (img.naturalWidth - sw) / 2, (img.naturalHeight - sh) * bottomBias,
    sw, sh, 0, 0, width, height);
}

function drawHeading(ctx, single) {
  const first = draft.format === 'GENERAL' ? eventLabels(draft)[0].toUpperCase()
    : draft.format === 'KING_OF_COURT' ? 'REY DE LA CANCHA' : 'AMERICANO';
  const category = categoryLabel(draft);
  const second = draft.format === 'GENERAL' ? category
    : [category, single ? 'INDIVIDUAL' : 'POR PAREJAS'].filter(Boolean).join(' · ');
  const font = 'italic 900 {size}px "Avenir Next", system-ui, sans-serif';
  ctx.fillStyle = '#d84629';
  ctx.font = font.replace('{size}', 56);
  for (let size = 55; size >= 18 && ctx.measureText(first).width > 890; size--) {
    ctx.font = font.replace('{size}', size);
  }
  const headingFont = ctx.font;
  const firstWidth = Math.min(ctx.measureText(first).width, 890) + 76;
  rounded(ctx, 540 - firstWidth / 2, 52, firstWidth, 86, 18);
  if (second) {
    fit(ctx, second, 890, 42, font);
    const secondWidth = ctx.measureText(second).width + 76;
    rounded(ctx, 540 - secondWidth / 2, 119, secondWidth, 65, 17);
  }
  ctx.textAlign = 'center';
  ctx.fillStyle = '#fff7ef';
  ctx.font = headingFont; ctx.fillText(first, 540, 114, 890);
  if (second) {
    fit(ctx, second, 890, 42, font); ctx.fillText(second, 540, 164);
  }
  ctx.textAlign = 'left';
}

function drawPairPlate(ctx, index) {
  const role = index === 0 ? 'CAMPEONES' : 'SUBCAMPEONES';
  const names = (index === 0 ? draft.winners : draft.finalists).join(' · ');
  fit(ctx, names, 898, 34, 'bold {size}px Arial, sans-serif');
  const width = Math.max(560, Math.ceil(ctx.measureText(names).width) + 70);
  const x = (1080 - width) / 2;
  ctx.fillStyle = 'rgba(29,25,25,.91)';
  rounded(ctx, x, 1216, width, 87, 20);
  ctx.fillStyle = '#fff7ef';
  ctx.fillText(names, x + 26, 1277);
  ctx.font = 'bold 22px Arial, sans-serif';
  const tab = Math.ceil(ctx.measureText(role).width) + 34;
  ctx.fillStyle = '#d84629'; rounded(ctx, x + 22, 1194, tab, 41, 10);
  ctx.fillStyle = '#fff7ef'; ctx.fillText(role, x + 39, 1222);
}

async function makeCard(src, index) {
  const img = await loadPhoto(src);
  const canvas = document.createElement('canvas');
  const single = !isPaired(draft);
  if (single) {
    const photoScale = Math.min(1, 2048 / Math.max(img.naturalWidth, img.naturalHeight));
    canvas.width = Math.max(1, Math.round(img.naturalWidth * photoScale));
    canvas.height = Math.max(1, Math.round(img.naturalHeight * photoScale));
  } else {
    canvas.width = 1080;
    canvas.height = 1350;
  }
  const ctx = canvas.getContext('2d', {alpha:false});
  ctx.imageSmoothingQuality = 'high';
  if (single) {
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    const headingScale = Math.min(canvas.width / 1080, canvas.height / 400);
    ctx.translate((canvas.width - 1080 * headingScale) / 2, 0);
    ctx.scale(headingScale, headingScale);
    drawHeading(ctx, true);
  } else {
    drawCover(ctx, img, 1080, 1350, .91);
    drawHeading(ctx, false);
    drawPairPlate(ctx, index);
  }
  return toJPEG(canvas);
}

async function toJPEG(canvas) {
  const encode = quality => new Promise((resolve, reject) => canvas.toBlob(
    blob => blob ? resolve(blob) : reject(new Error(msg('renderError'))), 'image/jpeg', quality));
  for (const quality of [.94, .85, .72, .60]) {
    const image = await encode(quality);
    if (image.size <= 2 * 1024 * 1024) return image;
  }
  throw new Error(msg('packageTooLarge'));
}

async function makeBracket(src) {
  const img = await loadPhoto(src);
  const canvas = document.createElement('canvas');
  canvas.width = 1080; canvas.height = 1350;
  const ctx = canvas.getContext('2d', {alpha:false});
  ctx.fillStyle='#1b191a'; ctx.fillRect(0,0,1080,1350);
  ctx.fillStyle='#ff6a43'; ctx.fillRect(40,56,56,5);
  ctx.fillStyle='#fff7ef'; ctx.font='bold 56px Arial, sans-serif'; ctx.fillText('CUADRO FINAL',40,131);
  ctx.fillStyle='#ff6a43'; ctx.font='bold 29px Arial, sans-serif'; ctx.fillText('AMERICANO · ' + categoryLabel(draft) + ' · POR PAREJAS',40,183);
  const scale=Math.min(956/img.naturalWidth,986/img.naturalHeight);
  const w=img.naturalWidth*scale, h=img.naturalHeight*scale;
  const x=540-w/2, y=745-h/2;
  ctx.fillStyle='#fff'; rounded(ctx,x-22,y-22,w+44,h+44,20);
  ctx.imageSmoothingQuality='high'; ctx.drawImage(img,x,y,w,h);
  return toJPEG(canvas);
}
function setStatus(message, error=false) {
  $('status').textContent = message;
  $('status').classList.toggle('error', error);
}
function resetPreviews() {
  for (const p of previews) URL.revokeObjectURL(p.url);
  previews = [];
  $('cards').hidden = true;
  $('bracket-card').hidden = true;
  $('bundle').disabled = true;
  updateSaveButton();
}
async function generate(inputs) {
  const job = ++generation;
  resetPreviews();
  if (!inputs.every(Boolean)) {
    setStatus(msg(isPaired(draft) ? 'chooseBoth' : 'chooseGroup'));
    return;
  }
  const maxSize = 25 * 1024 * 1024;
  if (inputs.some(x => x instanceof File && x.size > maxSize)) {
    setStatus(msg('maxSize'), true);
    return;
  }
  setStatus(msg('rendering'));
  try {
    const blobs = [];
    for (let i = 0; i < inputs.length; i++) {
      if (job !== generation) return;
      const src = await readDataUrl(inputs[i]);
      blobs.push(await (i === 2 ? makeBracket(src) : makeCard(src, i)));
    }
    const rendered = await Promise.all(blobs.map(readDataUrl));
    if (job !== generation) return;
    previews = blobs.map((blob, i) => ({
      blob, url:URL.createObjectURL(blob), filename:imageNames[i]
    }));
    previews.forEach((p,i) => {
      $(cards[i]+'-preview').src = rendered[i];
      const a = $(cards[i]+'-save');
      a.href = p.url; a.download = p.filename;
    });
    $('cards').hidden = false;
    $('bracket-card').hidden = previews.length < 3;
    $('bundle').disabled = false;
    updateSaveButton();
    setStatus(msg('ready'));
  } catch (error) {
    if (job === generation) setStatus(error.message || msg('renderError'), true);
  }
}
function refresh() {
  const images = isPaired(draft) ? slots.map(id => $(id).files[0]) : [$('winners').files[0]];
  const bracket = isPaired(draft) && $('bracket').files[0];
  $('clear-bracket').hidden = !bracket;
  if (bracket) images.push(bracket);
  generate(images);
}
for (const slot of slots) $(slot).addEventListener('change', refresh);
$('bracket').addEventListener('change', refresh);
$('clear-bracket').addEventListener('click', () => { $('bracket').value=''; refresh(); });
function save(blob, name) {
  const a = document.createElement('a');
  const url = URL.createObjectURL(blob);
  a.href=url; a.download=name;
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
function caption(id) {return $(id).value;}
const filenames = {instagram:'instagram-es.txt',telegram:'telegram-ru.txt'};
for (const button of root.querySelectorAll('[data-copy]')) {
  button.addEventListener('click', async () => {
    const field = $(button.dataset.copy);
    try {
      await navigator.clipboard.writeText(field.value);
      $('action-status').textContent = msg('copied');
    } catch {
      field.focus(); field.select();
      $('action-status').textContent = document.execCommand('copy') ? msg('copied') : msg('copyHelp');
    }
  });
}
for (const button of root.querySelectorAll('[data-save]')) {
  button.addEventListener('click', () => {
    const id = button.dataset.save;
    save(new Blob([caption(id)], {type:'text/plain;charset=utf-8'}), filenames[id]);
  });
}
$('insert-url').addEventListener('click', () => {
  try {
    const url = new URL($('ig-url').value.trim());
    if (url.protocol !== 'https:' || !['www.instagram.com','instagram.com'].includes(url.hostname) ||
        url.username || url.password || url.port || !/^\/p\/[a-zA-Z0-9_-]+\/?$/.test(url.pathname)) throw new Error();
    const link = '📸 Пост в Instagram: https://www.instagram.com' + url.pathname.replace(/\/?$/, '/');
    const base = $('telegram').value.trimEnd();
    $('telegram').value = (addedLink && base.includes(addedLink) ? base.replace(addedLink, link) : base + '\n\n' + link) + '\n';
    addedLink = link;
    $('action-status').textContent = msg('linkAdded');
  } catch {
    $('action-status').textContent = msg('badLink');
  }
});
// ZIP без сжатия: JPEG уже сжат, а файлы остаются читаемыми обычным архиватором.
function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit=0; bit<8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
async function zip(files) {
  const parts=[], directory=[];
  const enc=new TextEncoder();
  let offset=0, dirSize=0;
  function record(length) {
    const bytes=new Uint8Array(length), view=new DataView(bytes.buffer);
    return {bytes, u16:(i,v)=>view.setUint16(i,v,true), u32:(i,v)=>view.setUint32(i,v,true)};
  }
  for (const {name,blob} of files) {
    const n=enc.encode(name), data=new Uint8Array(await blob.arrayBuffer()), crc=crc32(data);
    const l=record(30), c=record(46);
    l.u32(0,0x04034b50); l.u16(4,20); l.u16(12,33); l.u32(14,crc);
    l.u32(18,data.length); l.u32(22,data.length); l.u16(26,n.length);
    c.u32(0,0x02014b50); c.u16(4,20); c.u16(6,20); c.u16(14,33); c.u32(16,crc);
    c.u32(20,data.length); c.u32(24,data.length); c.u16(28,n.length); c.u32(42,offset);
    parts.push(l.bytes,n,data); directory.push(c.bytes,n);
    offset+=30+n.length+data.length; dirSize+=46+n.length;
  }
  const end=record(22);
  end.u32(0,0x06054b50); end.u16(8,files.length); end.u16(10,files.length);
  end.u32(12,dirSize); end.u32(16,offset);
  return new Blob([...parts,...directory,end.bytes],{type:'application/zip'});
}
$('bundle').addEventListener('click', async () => {
  if (previews.length < (isPaired(draft) ? 2 : 1)) return;
  const photos = previews.map(p => ({name:p.filename,blob:p.blob}));
  const captions = Object.entries(filenames).map(([id,name]) => ({name,blob:new Blob([caption(id)])}));
  $('bundle').disabled = true;
  const job = generation;
  try { save(await zip([...photos,...captions]), '1padel-' + draft.id + '-social.zip'); }
  catch { $('action-status').textContent=msg('zipError'); }
  finally { if (job === generation) $('bundle').disabled = false; }
});
function updateSaveButton() {
  $('save-package').disabled = saving || readingSaved || savedVersion === undefined || !previews.length;
  $('save-package').textContent = msg(savedVersion > 0 ? 'replacePackage' : 'savePackage');
}

function renderSaved(metadata) {
  savedVersion = metadata?.version ?? 0;
  $('saved').hidden = !metadata;
  $('saved-cards').replaceChildren();
  updateSaveButton();
  if (!metadata) return;

  const date = new Intl.DateTimeFormat(document.documentElement.lang || 'es', {
    dateStyle:'medium', timeStyle:'short'
  }).format(new Date(metadata.savedAt));
  $('saved-at').textContent = msg('savedAt').replace('%DATE%', () => date).replace('%NAME%', () => metadata.savedBy);
  const versionPath = packagePath + '/' + metadata.version;
  $('saved-zip').href = versionPath + '/zip';
  for (const name of metadata.cards) {
    const title = msg(photoLabels[name] || 'group');
    const card = document.createElement('div');
    card.className = 'card';
    const label = document.createElement('h3');
    label.textContent = title;
    const img = document.createElement('img');
    img.alt = title;
    img.loading = 'lazy';
    img.src = versionPath + '/cards/' + encodeURIComponent(name);
    const link = document.createElement('a');
    link.className = 'button';
    link.href = img.src + '?download=true';
    link.textContent = msg('downloadJpg');
    card.append(label, img, link);
    $('saved-cards').append(card);
  }
}

async function loadSaved() {
  if (readingSaved || saving) return;
  readingSaved = true;
  updateSaveButton();
  try {
    const response = await fetch(packagePath, {headers:{Accept:'application/json'},cache:'no-store'});
    if (!response.ok) throw new Error();
    renderSaved(response.status === 204 ? null : await response.json());
    $('saved-load-status').textContent = '';
  } catch {
    $('saved-load-status').textContent = msg('savedLoadError');
  } finally {
    readingSaved = false;
    updateSaveButton();
  }
}

$('save-package').addEventListener('click', async () => {
  if (saving || readingSaved || savedVersion === undefined || !previews.length) return;
  const form = new FormData();
  form.set('version', savedVersion);
  form.set('format', draft.format);
  form.set('instagram', caption('instagram'));
  form.set('telegram', caption('telegram'));
  previews.forEach(({blob, filename}, index) =>
    form.set(['primary', 'finalist', 'bracket'][index], blob, filename));
  saving = true;
  updateSaveButton();
  const status = $('package-status');
  status.textContent = msg('savingPackage');
  let conflict = false;
  try {
    const response = await fetch(packagePath, {method:'PUT', body:form, headers:{Accept:'application/json'}});
    if (response.redirected) throw new Error();
    if (response.status === 409) {
      conflict = true;
      status.textContent = msg('packageConflict');
    } else if (!response.ok) {
      const key = response.status === 413 ? 'packageTooLarge' : response.status === 400 ? 'packageInvalid' : 'packageError';
      status.textContent = msg(key);
    } else {
      renderSaved(await response.json());
      status.textContent = msg('packageSaved');
    }
  } catch {
    status.textContent = msg('packageError');
  } finally {
    saving = false;
    if (conflict) {
      savedVersion = undefined;
      await loadSaved();
    }
    updateSaveButton();
  }
});
})();
