// Debug-only demo and self-test (launch with -autopilot). Clicks through every feature with a
// visible finger, logs PASS/FAIL checks and "SHOT:name" marks for screenshots. Never shipped.
(async function () {
  const $ = id => document.getElementById(id);
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const log = m => { try { window.webkit.messageHandlers.autopilot.postMessage(String(m)); } catch (e) {} };
  let pass = 0, fail = 0;
  const check = (ok, what) => { ok ? pass++ : fail++; log((ok ? 'PASS ' : 'FAIL ') + what); };
  const shot = async name => { log('SHOT:' + name); await sleep(900); };

  // The finger and a caption so the video is easy to follow.
  const css = document.createElement('style');
  css.textContent = `#ap-finger{position:fixed;z-index:99999;width:38px;height:38px;margin:-19px 0 0 -19px;border-radius:50%;
    background:rgba(255,255,255,.35);box-shadow:0 0 0 2px rgba(255,255,255,.9),0 4px 14px rgba(0,0,0,.5);pointer-events:none;
    transition:left .45s cubic-bezier(.3,.8,.3,1),top .45s cubic-bezier(.3,.8,.3,1),transform .15s;left:50%;top:110%}
    #ap-finger.down{transform:scale(.72);background:rgba(255,255,255,.6)}
    #ap-cap{position:fixed;z-index:99998;left:50%;top:calc(10px + env(safe-area-inset-top,0px));transform:translateX(-50%);max-width:86%;
    padding:8px 14px;border-radius:999px;background:rgba(0,0,0,.72);color:#fff;font:600 14px/1.3 system-ui;text-align:center;pointer-events:none}`;
  document.head.append(css);
  const finger = Object.assign(document.createElement('div'), { id: 'ap-finger' });
  const cap = Object.assign(document.createElement('div'), { id: 'ap-cap' });
  document.body.append(finger, cap);
  const say = t => { cap.textContent = t; log('STEP ' + t); };
  const fingerTo = async (x, y, ms = 480) => { finger.style.transition = ''; finger.style.left = x + 'px'; finger.style.top = y + 'px'; await sleep(ms); };

  async function tap(el, wait = 650) {
    if (typeof el === 'string') el = $(el) || document.querySelector(el);
    if (!el) { check(false, 'element exists'); return; }
    el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    await sleep(450);
    const r = el.getBoundingClientRect();
    await fingerTo(r.left + r.width / 2, r.top + r.height / 2);
    finger.classList.add('down'); await sleep(130);
    el.click();
    finger.classList.remove('down');
    await sleep(wait);
  }
  async function slide(id, to) {   // drags a slider to a fraction of its range
    const input = $(id);
    input.scrollIntoView({ block: 'center', behavior: 'smooth' }); await sleep(450);
    const r = input.getBoundingClientRect(), y = r.top + r.height / 2, min = +input.min, max = +input.max;
    const from = (input.value - min) / (max - min);
    await fingerTo(r.left + 11 + from * (r.width - 22), y);
    finger.classList.add('down');
    for (let i = 1; i <= 14; i++) {
      const t = from + (to - from) * i / 14;
      finger.style.transition = 'none';
      finger.style.left = (r.left + 11 + t * (r.width - 22)) + 'px';
      input.value = String(min + t * (max - min));
      input.dispatchEvent(new Event('input'));
      await sleep(40);
    }
    finger.classList.remove('down'); finger.style.transition = '';
    await sleep(500);
  }
  // A real touch stroke on the fluid: pointer events along a path, the finger following.
  const fluid = () => document.getElementById('fluid');
  async function stroke(pts, ms = 900, id = 7) {
    const W = innerWidth, H = innerHeight, steps = Math.round(ms / 16);
    const at = t => { const f = t * (pts.length - 1), i = Math.min(Math.floor(f), pts.length - 2), u = f - i;
      return [ (pts[i][0] + (pts[i + 1][0] - pts[i][0]) * u) * W, (pts[i][1] + (pts[i + 1][1] - pts[i][1]) * u) * H ]; };
    const ev = (type, [x, y]) => fluid().dispatchEvent(new PointerEvent(type, { pointerId: id, clientX: x, clientY: y, bubbles: true, pointerType: 'touch', isPrimary: true }));
    let p = at(0);
    await fingerTo(p[0], p[1], 350);
    finger.classList.add('down'); finger.style.transition = 'none';
    ev('pointerdown', p);
    for (let s = 1; s <= steps; s++) { p = at(s / steps); finger.style.left = p[0] + 'px'; finger.style.top = p[1] + 'px'; ev('pointermove', p); await sleep(16); }
    ev('pointerup', p);
    finger.classList.remove('down'); finger.style.transition = '';
  }
  async function tapScreen(x, y) {
    const W = innerWidth, H = innerHeight;
    await fingerTo(x * W, y * H, 350);
    finger.classList.add('down');
    const o = { pointerId: 9, clientX: x * W, clientY: y * H, bubbles: true, pointerType: 'touch' };
    fluid().dispatchEvent(new PointerEvent('pointerdown', o)); await sleep(90);
    fluid().dispatchEvent(new PointerEvent('pointerup', o));
    finger.classList.remove('down'); await sleep(250);
  }
  async function paint() {
    await stroke([[0.15, 0.3], [0.5, 0.42], [0.85, 0.3]]);
    await stroke([[0.8, 0.62], [0.5, 0.5], [0.2, 0.7]]);
    await stroke([[0.5, 0.15], [0.62, 0.5], [0.45, 0.85]]);
  }
  const playing = () => $('menu').classList.contains('away');
  async function play() { await tap('play', 900); check(playing(), 'Play hides the menu'); }
  async function back() { say('Swipe in from the left edge: back'); await fingerTo(4, innerHeight / 2, 250); finger.classList.add('down'); await fingerTo(innerWidth * 0.6, innerHeight / 2, 350); finger.classList.remove('down'); window.chromaBack(); await sleep(900); }
  const shapesDrawn = () => { const c = $('shapes'), d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; for (let i = 3; i < d.length; i += 4 * 97) if (d[i] > 0) return true; return false; };
  const openPlayOptions = async () => { if (!$('sec-play').open) await tap(document.querySelector('#sec-play summary')); };

  await sleep(2500);
  log('START');
  say('Chroma Flow on iPhone'); await sleep(2000); await shot('01-menu');

  // Styles: every card, the fluid behind the menu previews it
  say('10 styles: tap a card to switch');
  for (const id of ['smoke', 'ocean', 'waves', 'fire', 'ink', 'aurora', 'lava', 'galaxy', 'chrome', 'neon']) {
    await tap('style-' + id, 1100);
    check($('play-style').textContent.toLowerCase() === id, 'style ' + id + ' selected');
  }
  await shot('02-styles');

  say('Play: draw with your finger');
  await play(); await paint(); await shot('03-play-neon');
  await back(); check(!playing(), 'back returns to the menu');

  say('Ocean, played'); await tap('style-ocean', 900); await play(); await paint(); await shot('04-play-ocean'); await back();
  say('Fire, played'); await tap('style-fire', 900); await play(); await paint(); await sleep(600); await shot('05-play-fire'); await back();

  // Colors
  say('Colors: Rainbow');
  await tap('style-neon', 800); await tap('src-rainbow');
  check($('src-rainbow').getAttribute('aria-pressed') === 'true', 'Rainbow colors on');
  say('Colors: My colors');
  await tap('src-custom');
  check(!$('my-colors').hidden, 'My color swatches shown');
  for (const [i, c] of [[0, '#ff2a6d'], [1, '#00e5ff'], [2, '#ffd23f'], [3, '#7c4dff']]) { const el = $('c' + i); await tap(el, 250); el.value = c; el.dispatchEvent(new Event('input')); }
  await shot('06-my-colors'); await play(); await paint(); await shot('07-play-my-colors'); await back();
  await tap('src-style');

  // Play options
  say('Play options: symmetry');
  await openPlayOptions(); check($('sec-play').open, 'Play options open');
  await tap('sym-2'); await play(); await paint(); await shot('08-mirror'); await back();
  await openPlayOptions(); await tap('sym-4'); await play(); await paint(); await shot('09-4-way'); await back();
  await openPlayOptions(); await tap('sym-6'); await play(); await paint(); await shot('10-kaleido'); await back();
  check(/Kaleido/.test($('play-sum').textContent), 'summary says Kaleido');

  say('Auto-stir: moves with no touch');
  await openPlayOptions(); await tap('sym-1'); await tap('t-auto');
  await play(); await sleep(3500); await shot('11-auto-stir'); await back();
  await openPlayOptions(); await tap('t-auto');

  say('Tap to splash');
  await play(); for (const [x, y] of [[0.3, 0.3], [0.7, 0.45], [0.4, 0.7]]) await tapScreen(x, y); await sleep(500); await shot('12-tap-splash'); await back();

  // Touch shapes
  const shapes = [['hearts', 'rainbow', 'Hearts, rainbow colors'], ['bubbles', 'fluid', 'Bubbles, fluid colors'], ['stars', 'rainbow', 'Stars'], ['sparkles', 'custom', 'Sparkles, my color']];
  for (const [k, colors, label] of shapes) {
    say('Touch shapes: ' + label);
    await openPlayOptions(); await tap('shape-' + k); await tap('shc-' + colors);
    if (colors === 'custom') { $('shape-hex').value = '#ffd23f'; $('shape-hex').dispatchEvent(new Event('input')); }
    await play(); await paint(); await tapScreen(0.5, 0.5);
    check(shapesDrawn(), k + ' are drawn');
    await shot('13-shapes-' + k); await back();
  }
  say('Touch shapes with Kaleido');
  await openPlayOptions(); await tap('shape-hearts'); await tap('shc-rainbow'); await tap('sym-6');
  await play(); await paint(); await shot('14-hearts-kaleido'); await back();
  await openPlayOptions(); await tap('shape-off'); await tap('sym-1');
  check($('shape-opts').hidden, 'Fluid hides the shape colors');

  // Fine-tune
  say('Fine-tune: sliders change the fluid live');
  await tap('style-galaxy', 900);
  if (!$('tune').open) await tap(document.querySelector('#tune summary'));
  check($('tune-edited').hidden, 'fresh style is not Edited');
  await slide('s-bright', 0.6); await slide('s-bloom', 0.5); await slide('s-curl', 1); await slide('s-radius', 0.6);
  await tap('t-shading');
  check(!$('tune-edited').hidden, 'Edited tag after a change');
  await shot('15-fine-tune-edited');
  say('Red reset: tap twice');
  await tap('reset', 500); check($('reset').classList.contains('armed'), 'first tap arms reset'); await shot('16-reset-armed');
  await tap('reset', 800); check($('tune-edited').hidden, 'reset clears Edited'); await shot('17-reset-done');

  // Settings
  say('Settings');
  $('menu').scrollTo({ top: 0, behavior: 'smooth' }); await sleep(600);
  await tap('open-settings', 900);
  check(!$('settings').classList.contains('away'), 'Settings open');
  check($('set-wp').hidden, 'no live wallpaper section on iPhone');
  await shot('18-settings');
  say('Quality'); await tap('q-low'); await tap('q-ultra'); await tap('q-high');
  say('Reset everything: tap twice'); await tap('reset-all', 500); await shot('19-reset-all-armed'); await tap('reset-all', 1200);
  say('Credits to Kothi 🐒'); await tap(document.querySelector('.credit-line'), 1500); await shot('20-credits');
  await tap('settings-close', 900); check($('settings').classList.contains('away'), 'Settings closed');

  // Finale
  say('Galaxy with Kaleido'); await tap('style-galaxy', 900);
  await openPlayOptions(); await tap('sym-6'); await play(); await paint(); await paint(); await shot('21-finale'); await back();
  await openPlayOptions(); await tap('sym-1');

  say(fail ? `Done: ${pass} passed, ${fail} failed` : `All ${pass} checks passed`);
  log(`RESULT pass=${pass} fail=${fail}`);
  await sleep(2500);
  if (fail) return;  // the UI test treats "the app stayed open" as a failure
  say('Website link opens Safari');
  await tap('open-settings', 900);
  await tap(document.querySelector('.rows a'), 1500);
  log('DONE');
})();
