(() => {
'use strict';
const root = document.getElementById('social-draft');
if (!root) return;
const $ = id => root.querySelector('#social-' + id);
const msg = key => root.getAttribute('data-msg-' + key.toLowerCase());
const slots = ['winners', 'finalists'];
const cards = [...slots, 'bracket'];
let draft;
let imageNames;
let previews = [];
let generation = 0;
let loading = false;
let addedLink = '';

const isIndividual = data => data.format === 'KING_OF_COURT';
const categoryLabel = data => data.category.replaceAll('_', '/');

function buildCaptions(data) {
  const date = data.date.split('-').reverse().join('.');
  const category = categoryLabel(data);
  const results = new URL(data.resultsPath, window.location.origin).href;
  let instagram;
  let telegram;
  if (isIndividual(data)) {
    const [first, second] = data.places;
    instagram = [
      '🎾 REY DE LA CANCHA — ' + category + ' · INDIVIDUAL',
      '📍 ' + data.club + ' · ' + date,
      '', '🥇 ' + first.name + ' — ' + first.points + ' puntos',
      '🥈 ' + second.name + ' — ' + second.points + ' puntos', '',
      'Jugamos con parejas rotativas y clasificación individual. ¡Gracias a todos por participar!'
    ].join('\n');
    telegram = [
      '🎾 КОРОЛЬ КОРТА — ' + category + ' · ИНДИВИДУАЛЬНЫЙ',
      '📍 ' + data.club + ' · ' + date,
      '', '🥇 1-е место: ' + first.name + ' · очки: ' + first.points,
      '🥈 2-е место: ' + second.name + ' · очки: ' + second.points,
      '', 'Партнёры менялись, а очки шли в личный зачёт. Спасибо всем за игру!',
      '', '📊 Итоговая таблица: ' + results
    ].join('\n');
  } else {
    const score = data.winnerGames + ':' + data.finalistGames;
    instagram = [
      '🎾 TORNEO ' + category + ' — AMERICANO EN PAREJAS',
      '📍 ' + data.club + ' · ' + date,
      '', '¡Felicitaciones!',
      '🥇 Campeones: ' + data.winners.join(' y '), '🥈 Subcampeones: ' + data.finalists.join(' y '),
      '🏆 Final: ' + score, '',
      'Jugamos con parejas fijas: primero la clasificación y después el cuadro eliminatorio. ¡Gracias a todos por participar!'
    ].join('\n');
    telegram = [
      '🎾 ТУРНИР ' + category + ' — ПАРНЫЙ АМЕРИКАНО',
      '📍 ' + data.club + ' · ' + date,
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

root.addEventListener('toggle', async () => {
  if (!root.open || draft || loading) return;
  loading = true;
  $('load-status').textContent = msg('loading');
  try {
    const response = await fetch(root.dataset.endpoint, {headers:{Accept:'application/json'},cache:'no-store'});
    if (!response.ok) {
      $('load-status').textContent = msg(response.status === 409 ? 'incomplete' : response.status === 422 ? 'unsupported' : 'unavailable');
      return;
    }
    const data = await response.json();
    const captions = buildCaptions(data);
    draft = data;
    const single = isIndividual(draft);
    imageNames = single ? ['01-rey-de-la-cancha.jpg'] : ['01-campeones.jpg','02-subcampeones.jpg','03-cuadro-final.jpg'];
    $('finalist-slot').hidden = single;
    $('finalists-card').hidden = single;
    $('bracket-input').hidden = single;
    if (single) {
      $('photo-label').textContent = msg('group');
      $('primary-title').textContent = msg('group');
      $('winners-preview').alt = msg('group');
      $('winner-names').textContent = draft.places.map(p => p.rank + '. ' + p.name).join(' · ');
      setStatus(msg('chooseGroup'));
    } else {
      $('winner-names').textContent = draft.winners.join(' · ');
      $('finalist-names').textContent = draft.finalists.join(' · ');
    }
    $('instagram').value = captions.instagram;
    $('telegram').value = captions.telegram;
    $('results-link').href = draft.resultsPath;
    $('load-status').textContent = '';
    $('editor').hidden = false;
  } catch {
    $('load-status').textContent = msg('unavailable');
  } finally {
    loading = false;
  }
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

function drawGroup(ctx, img) {
  if (img.naturalWidth / img.naturalHeight <= .84) {
    drawCover(ctx, img, 1080, 1350);
    return;
  }
  // У широкого общего кадра сохраняем всех людей, а поля продолжаем фоном того же снимка.
  const background = document.createElement('canvas');
  background.width = 22; background.height = 28;
  drawCover(background.getContext('2d'), img, 22, 28);
  ctx.drawImage(background, 0, 0, 1080, 1350);
  ctx.fillStyle = 'rgba(27,24,23,.18)'; ctx.fillRect(0, 0, 1080, 1350);
  const layer = document.createElement('canvas');
  layer.width = 1080;
  layer.height = Math.round(1080 * img.naturalHeight / img.naturalWidth);
  const foreground = layer.getContext('2d');
  foreground.imageSmoothingQuality = 'high';
  foreground.drawImage(img, 0, 0, layer.width, layer.height);
  foreground.globalCompositeOperation = 'destination-in';
  const edge = foreground.createLinearGradient(0, 0, 0, layer.height);
  edge.addColorStop(0, 'transparent'); edge.addColorStop(.12, '#000');
  edge.addColorStop(.88, '#000'); edge.addColorStop(1, 'transparent');
  foreground.fillStyle = edge; foreground.fillRect(0, 0, layer.width, layer.height);
  ctx.drawImage(layer, 0, (1350 - layer.height) / 2);
}

function drawHeading(ctx, single) {
  const first = single ? 'REY DE LA CANCHA' : 'AMERICANO';
  const second = categoryLabel(draft) + (single ? ' · INDIVIDUAL' : ' · POR PAREJAS');
  const font = 'italic 900 {size}px "Avenir Next", system-ui, sans-serif';
  fit(ctx, first, 730, 56, font);
  const firstWidth = ctx.measureText(first).width + 76;
  fit(ctx, second, 730, 42, font);
  const secondWidth = ctx.measureText(second).width + 76;
  ctx.fillStyle = '#d84629';
  rounded(ctx, 540 - firstWidth / 2, 52, firstWidth, 86, 18);
  rounded(ctx, 540 - secondWidth / 2, 119, secondWidth, 65, 17);
  ctx.textAlign = 'center'; ctx.fillStyle = '#fff7ef';
  fit(ctx, first, 730, 56, font); ctx.fillText(first, 540, 114);
  fit(ctx, second, 730, 42, font); ctx.fillText(second, 540, 164);
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

function drawIndividualPlate(ctx) {
  const names = draft.places.map(place => place.name);
  ctx.font = 'bold 32px Arial, sans-serif';
  const rankWidth = Math.ceil(ctx.measureText('1.º').width);
  const nameWidths = names.map(name => Math.ceil(ctx.measureText(name).width));
  const width = 99 + (rankWidth + 13) * 2 + nameWidths[0] + nameWidths[1];
  const stacked = width > 984;
  const panelWidth = stacked ? Math.min(984, 94 + rankWidth + Math.max(...nameWidths)) : width;
  const x = (1080 - panelWidth) / 2;
  ctx.fillStyle = 'rgba(29,25,25,.91)';
  rounded(ctx, x, stacked ? 1189 : 1233, panelWidth, stacked ? 114 : 70, 18);
  let start = x + 25;
  for (const [i, name] of names.entries()) {
    const y = stacked ? 1231 + i * 46 : 1279;
    const row = stacked ? x + 25 : start;
    ctx.font = 'bold 32px Arial, sans-serif';
    ctx.fillStyle = '#ff744d'; ctx.fillText((i + 1) + '.º', row, y);
    ctx.fillStyle = '#fff7ef';
    fit(ctx, name, panelWidth - rankWidth - 64, 32, 'bold {size}px Arial, sans-serif');
    ctx.fillText(name, row + rankWidth + 13, y);
    if (!stacked && i === 0) {
      start += rankWidth + 13 + nameWidths[0] + 24;
      ctx.fillStyle = 'rgba(255,246,237,.45)'; ctx.fillRect(start, 1253, 1, 30);
      start += 25;
    }
  }
}

async function makeCard(src, index) {
  const img = await loadPhoto(src);
  const canvas = document.createElement('canvas');
  canvas.width = 1080; canvas.height = 1350;
  const ctx = canvas.getContext('2d', {alpha:false});
  ctx.imageSmoothingQuality = 'high';
  const single = isIndividual(draft);
  if (single) drawGroup(ctx, img);
  else drawCover(ctx, img, 1080, 1350, .91);
  drawHeading(ctx, single);
  if (single) drawIndividualPlate(ctx);
  else drawPairPlate(ctx, index);
  return toJPEG(canvas);
}
function toJPEG(canvas) {
  return new Promise((resolve,reject) => canvas.toBlob(
    (blob) => blob ? resolve(blob) : reject(new Error(msg('renderError'))), 'image/jpeg', .94));
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
}
async function generate(inputs) {
  const job = ++generation;
  resetPreviews();
  if (!inputs.every(Boolean)) {
    setStatus(msg(isIndividual(draft) ? 'chooseGroup' : 'chooseBoth'));
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
    setStatus(msg('ready'));
  } catch (error) {
    if (job === generation) setStatus(error.message || msg('renderError'), true);
  }
}
function refresh() {
  const images = isIndividual(draft) ? [$('winners').files[0]] : slots.map(id => $(id).files[0]);
  const bracket = !isIndividual(draft) && $('bracket').files[0];
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
  if (previews.length < (isIndividual(draft) ? 1 : 2)) return;
  const photos = previews.map(p => ({name:p.filename,blob:p.blob}));
  const captions = Object.entries(filenames).map(([id,name]) => ({name,blob:new Blob([caption(id)])}));
  $('bundle').disabled = true;
  const job = generation;
  try { save(await zip([...photos,...captions]), '1padel-' + draft.id + '-social.zip'); }
  catch { $('action-status').textContent=msg('zipError'); }
  finally { if (job === generation) $('bundle').disabled = false; }
});
})();
