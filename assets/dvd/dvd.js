(function () {
  'use strict';
  // KS supplies the plugin's rendering options in the URL fragment.
  var options = {};
  try { options = JSON.parse(decodeURIComponent(location.hash.slice(1))); } catch (_) {}
  function color(value, fallback) {
    return typeof value === 'string' && /^#[a-fA-F0-9]{6}$/.test(value) ? value : fallback;
  }
  var size = typeof options.logoSize === 'number' && isFinite(options.logoSize)
    ? Math.max(50, Math.min(200, options.logoSize)) : 100;
  document.documentElement.style.setProperty('--logo-scale', size / 100);
  document.documentElement.style.setProperty('--logo-color', color(options.logoColor, '#00D4FF'));
  document.documentElement.style.setProperty('--background-color', color(options.backgroundColor, '#000000'));

  var logo = document.getElementById('logo'), x = 24, y = 32, dx = 1, dy = 1, last = 0;
  function frame(now) {
    var dt = last ? Math.min((now - last) / 1000, 0.05) : 0;
    last = now;
    var maxX = Math.max(0, innerWidth - logo.offsetWidth);
    var maxY = Math.max(0, innerHeight - logo.offsetHeight);
    x += dx * 110 * dt;
    y += dy * 82 * dt;
    if (x >= maxX) { x = maxX; dx = -1; } else if (x <= 0) { x = 0; dx = 1; }
    if (y >= maxY) { y = maxY; dy = -1; } else if (y <= 0) { y = 0; dy = 1; }
    logo.style.transform = 'translate(' + x + 'px,' + y + 'px)';
    requestAnimationFrame(frame);
  }
  document.addEventListener('visibilitychange', function () { last = 0; });
  requestAnimationFrame(frame);
}());
