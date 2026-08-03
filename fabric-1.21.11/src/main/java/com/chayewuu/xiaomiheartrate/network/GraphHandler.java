package com.chayewuu.xiaomiheartrate.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.heart.HeartRateManager;
import com.chayewuu.xiaomiheartrate.heart.HeartRateRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /graph} 处理器。
 * <p>返回一个现代化的 HTML 页面，使用 Canvas 绘制实时心率曲线，
 * 通过 SSE（{@code /graph/events}）接收实时心率推送。</p>
 *
 * <p>页面特性：</p>
 * <ul>
 *     <li>深色主题（默认深色，支持 {@code prefers-color-scheme} 切换浅色）；</li>
 *     <li>内联 CSS / JS，无外部依赖；</li>
 *     <li>Canvas 绘制心率曲线（最近 60 个数据点）；</li>
 *     <li>大字号显示当前心率、设备名、连接状态；</li>
 *     <li>响应式布局，中文界面。</li>
 * </ul>
 *
 * <p>初始数据：服务端渲染时将最近 60 条历史记录以 JSON 注入页面，
 * 前端加载后立即绘制，无需等待首个 SSE 事件。</p>
 */
public class GraphHandler implements HttpHandler {

    /** 历史数据点数量 */
    private static final int HISTORY_POINTS = 60;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        HeartRateManager mgr = HeartRateManager.getInstance();
        BleDevice device = mgr.getCurrentDevice();
        boolean connected = mgr.isConnected();
        int heartRate = mgr.getCurrentHeartRate();

        // 注入初始历史数据（最近 60 条，按时间从旧到新）
        List<HeartRateRecord> history = mgr.getStorage().getHistory(HISTORY_POINTS);
        List<long[]> points = new ArrayList<>(history.size());
        for (HeartRateRecord r : history) {
            // [timestamp_seconds, heartRate]
            points.add(new long[]{r.timestamp() / 1000L, r.heartRate()});
        }

        String initialData = HttpUtil.GSON.toJson(points);
        String deviceName = (connected && device != null) ? escapeJs(device.getName()) : "";
        String deviceAddress = (connected && device != null) ? escapeJs(device.getAddress()) : "";

        String html = buildHtml(initialData, heartRate, connected, deviceName, deviceAddress);
        HttpUtil.sendHtml(exchange, html);
    }

    /**
     * 转义字符串中的特殊字符，避免注入 HTML/JS 上下文。
     *
     * @param s 原始字符串
     * @return 转义后字符串
     */
    private static String escapeJs(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '<' -> sb.append("\\u003C");
                case '>' -> sb.append("\\u003E");
                case '&' -> sb.append("\\u0026");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 构建完整 HTML 页面。
     *
     * @param initialDataJson 初始历史数据 JSON（[[ts,hr],...]）
     * @param heartRate       当前心率
     * @param connected       是否已连接
     * @param deviceName      设备名（已转义）
     * @param deviceAddress   设备地址（已转义）
     * @return HTML 字符串
     */
    private static String buildHtml(String initialDataJson, int heartRate, boolean connected,
                                    String deviceName, String deviceAddress) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>心率监测 · 实时图表</title>
                <style>
                :root {
                  color-scheme: dark;
                  --bg: #0f1419;
                  --bg-grad-1: #1a1f2e;
                  --bg-grad-2: #0f1419;
                  --card: rgba(30, 38, 56, 0.75);
                  --card-border: rgba(120, 140, 200, 0.18);
                  --text: #e6edf3;
                  --text-dim: #8b97a8;
                  --accent: #ff4d6d;
                  --accent-2: #ff8fa3;
                  --accent-grad-1: #ff4d6d;
                  --accent-grad-2: #ff9a6c;
                  --grid: rgba(120, 140, 200, 0.08);
                  --ok: #4ade80;
                  --warn: #fbbf24;
                  --bad: #f87171;
                  --shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
                  --radius: 16px;
                }
                @media (prefers-color-scheme: light) {
                  :root {
                    color-scheme: light;
                    --bg: #f5f7fa;
                    --bg-grad-1: #ffffff;
                    --bg-grad-2: #eef2f7;
                    --card: rgba(255, 255, 255, 0.85);
                    --card-border: rgba(60, 80, 120, 0.15);
                    --text: #1a2233;
                    --text-dim: #5a6678;
                    --grid: rgba(60, 80, 120, 0.1);
                    --shadow: 0 8px 32px rgba(60, 80, 120, 0.12);
                  }
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body { height: 100%; }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
                               "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
                  background: radial-gradient(1200px 800px at 80% -10%, var(--bg-grad-1), var(--bg-grad-2));
                  color: var(--text);
                  min-height: 100vh;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  padding: 24px 16px;
                  transition: background 0.4s ease;
                }
                .container { width: 100%; max-width: 920px; }
                header {
                  display: flex; align-items: center; justify-content: space-between;
                  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
                }
                .title { display: flex; align-items: center; gap: 10px; }
                .title h1 { font-size: 20px; font-weight: 600; letter-spacing: 0.5px; }
                .heart-icon {
                  width: 24px; height: 24px;
                  filter: drop-shadow(0 0 6px var(--accent));
                  animation: beat 1.2s ease-in-out infinite;
                }
                @keyframes beat {
                  0%, 100% { transform: scale(1); }
                  25% { transform: scale(1.18); }
                  50% { transform: scale(0.96); }
                  75% { transform: scale(1.1); }
                }
                .badge {
                  display: inline-flex; align-items: center; gap: 6px;
                  padding: 6px 12px; border-radius: 999px;
                  font-size: 12px; font-weight: 500;
                  background: var(--card); border: 1px solid var(--card-border);
                  color: var(--text-dim);
                }
                .dot {
                  width: 8px; height: 8px; border-radius: 50%;
                  background: var(--text-dim); flex-shrink: 0;
                }
                .dot.ok { background: var(--ok); box-shadow: 0 0 8px var(--ok); }
                .dot.bad { background: var(--bad); box-shadow: 0 0 8px var(--bad); }
                .dot.warn { background: var(--warn); box-shadow: 0 0 8px var(--warn); }

                .grid { display: grid; grid-template-columns: 1fr; gap: 16px; }
                @media (min-width: 760px) {
                  .grid { grid-template-columns: 300px 1fr; }
                }

                .card {
                  background: var(--card);
                  border: 1px solid var(--card-border);
                  border-radius: var(--radius);
                  box-shadow: var(--shadow);
                  padding: 20px;
                  backdrop-filter: blur(12px);
                  -webkit-backdrop-filter: blur(12px);
                }

                .heart-card { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
                .heart-value {
                  font-size: 72px; font-weight: 700; line-height: 1;
                  background: linear-gradient(135deg, var(--accent-grad-1), var(--accent-grad-2));
                  -webkit-background-clip: text; background-clip: text;
                  -webkit-text-fill-color: transparent; color: transparent;
                  text-shadow: 0 0 32px rgba(255, 77, 109, 0.25);
                }
                .heart-unit { font-size: 16px; color: var(--text-dim); margin-top: 6px; letter-spacing: 1px; }
                .heart-meta { margin-top: 14px; display: flex; flex-direction: column; gap: 8px; width: 100%; }
                .meta-row { display: flex; justify-content: space-between; font-size: 13px; }
                .meta-label { color: var(--text-dim); }
                .meta-value { color: var(--text); font-weight: 500; }

                .chart-card { display: flex; flex-direction: column; min-height: 320px; }
                .chart-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
                .chart-title { font-size: 14px; font-weight: 600; color: var(--text); }
                .chart-sub { font-size: 12px; color: var(--text-dim); }
                .chart-wrap { flex: 1; position: relative; width: 100%; min-height: 260px; }
                canvas#chart { width: 100%; height: 100%; display: block; }

                footer {
                  margin-top: 18px; text-align: center; font-size: 12px; color: var(--text-dim);
                }
                footer code { color: var(--accent-2); background: rgba(255,255,255,0.05); padding: 1px 6px; border-radius: 4px; }
                @media (prefers-color-scheme: light) {
                  footer code { background: rgba(0,0,0,0.05); }
                }
                </style>
                </head>
                <body>
                  <div class="container">
                    <header>
                      <div class="title">
                        <svg class="heart-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <path d="M12 21s-7.5-4.6-10-9.2C0.5 8 2 4 6 4c2 0 3.5 1 6 3.5C14.5 5 16 4 18 4c4 0 5.5 4 4 7.8C19.5 16.4 12 21 12 21z"
                                fill="url(#hg)" stroke="rgba(255,255,255,0.2)" stroke-width="0.5"/>
                          <defs>
                            <linearGradient id="hg" x1="0" y1="0" x2="24" y2="24" gradientUnits="userSpaceOnUse">
                              <stop stop-color="#ff4d6d"/><stop offset="1" stop-color="#ff9a6c"/>
                            </linearGradient>
                          </defs>
                        </svg>
                        <h1>心率监测</h1>
                      </div>
                      <div class="badge">
                        <span id="connDot" class="dot"></span>
                        <span id="connText">连接中…</span>
                      </div>
                    </header>

                    <div class="grid">
                      <section class="card heart-card">
                        <div class="heart-value" id="hrValue">—</div>
                        <div class="heart-unit">BPM</div>
                        <div class="heart-meta">
                          <div class="meta-row"><span class="meta-label">设备</span><span class="meta-value" id="deviceName">—</span></div>
                          <div class="meta-row"><span class="meta-label">地址</span><span class="meta-value" id="deviceAddr">—</span></div>
                          <div class="meta-row"><span class="meta-label">更新时间</span><span class="meta-value" id="updateTime">—</span></div>
                          <div class="meta-row"><span class="meta-label">最近 60 点</span><span class="meta-value" id="ptCount">0</span></div>
                        </div>
                      </section>

                      <section class="card chart-card">
                        <div class="chart-head">
                          <span class="chart-title">实时心率曲线</span>
                          <span class="chart-sub" id="rangeLabel">—</span>
                        </div>
                        <div class="chart-wrap"><canvas id="chart"></canvas></div>
                      </section>
                    </div>

                    <footer>
                      数据来源 <code id="srcUrl">/graph/events</code> · SSE 实时推送
                    </footer>
                  </div>

                <script>
                (function () {
                  var MAX_POINTS = 60;
                  var data = [];
                  var connected = false;
                  var deviceName = "";
                  var deviceAddr = "";
                  var lastHr = 0;
                  var lastTs = 0;

                  // 服务端注入的初始历史数据 [[ts,hr],...]
                  try {
                    var init = JSON.parse('__INITIAL_DATA__');
                    for (var i = 0; i < init.length; i++) {
                      data.push({ ts: init[i][0], hr: init[i][1] });
                    }
                  } catch (e) { /* ignore */ }

                  // 注入初始状态
                  connected = __CONNECTED__;
                  deviceName = "__DEVICE_NAME__";
                  deviceAddr = "__DEVICE_ADDR__";
                  lastHr = __HEARTRATE__;

                  var hrEl = document.getElementById('hrValue');
                  var dotEl = document.getElementById('connDot');
                  var connText = document.getElementById('connText');
                  var nameEl = document.getElementById('deviceName');
                  var addrEl = document.getElementById('deviceAddr');
                  var timeEl = document.getElementById('updateTime');
                  var ptEl = document.getElementById('ptCount');
                  var rangeEl = document.getElementById('rangeLabel');
                  var canvas = document.getElementById('chart');
                  var ctx = canvas.getContext('2d');

                  function fmtTime(ts) {
                    if (!ts) return '—';
                    var d = new Date(ts * 1000);
                    var p = function (n) { return (n < 10 ? '0' : '') + n; };
                    return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
                  }

                  function updateMeta() {
                    hrEl.textContent = lastHr > 0 ? lastHr : '—';
                    nameEl.textContent = deviceName || '—';
                    addrEl.textContent = deviceAddr || '—';
                    timeEl.textContent = lastTs > 0 ? fmtTime(lastTs) : '—';
                    ptEl.textContent = data.length;
                    if (data.length > 0) {
                      rangeEl.textContent = fmtTime(data[0].ts) + ' → ' + fmtTime(data[data.length - 1].ts);
                    } else {
                      rangeEl.textContent = '—';
                    }
                    if (connected) {
                      dotEl.className = 'dot ok';
                      connText.textContent = '已连接';
                    } else {
                      dotEl.className = 'dot bad';
                      connText.textContent = '未连接';
                    }
                  }

                  function pushPoint(hr, ts) {
                    if (hr <= 0) return;
                    lastHr = hr; lastTs = ts;
                    // 去重：与最后一点相同且时间戳相同则跳过
                    if (data.length > 0 && data[data.length - 1].hr === hr && data[data.length - 1].ts === ts) return;
                    data.push({ ts: ts, hr: hr });
                    while (data.length > MAX_POINTS) data.shift();
                  }

                  function resizeCanvas() {
                    var dpr = window.devicePixelRatio || 1;
                    var rect = canvas.getBoundingClientRect();
                    canvas.width = Math.max(1, Math.floor(rect.width * dpr));
                    canvas.height = Math.max(1, Math.floor(rect.height * dpr));
                    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                  }

                  function draw() {
                    var rect = canvas.getBoundingClientRect();
                    var W = rect.width, H = rect.height;
                    var padL = 36, padR = 12, padT = 14, padB = 22;
                    var innerW = W - padL - padR;
                    var innerH = H - padT - padB;
                    ctx.clearRect(0, 0, W, H);

                    // 网格
                    ctx.strokeStyle = getCss('--grid');
                    ctx.lineWidth = 1;
                    ctx.font = '11px -apple-system, sans-serif';
                    ctx.fillStyle = getCss('--text-dim');
                    ctx.textBaseline = 'middle';
                    ctx.textAlign = 'right';

                    var yMin = 40, yMax = 180;
                    // 动态范围：根据数据调整
                    if (data.length > 0) {
                      var mn = 220, mx = 0;
                      for (var i = 0; i < data.length; i++) {
                        if (data[i].hr > 0) { if (data[i].hr < mn) mn = data[i].hr; if (data[i].hr > mx) mx = data[i].hr; }
                      }
                      if (mn > 220) mn = 40;
                      if (mx < 40) mx = 180;
                      yMin = Math.max(30, mn - 10);
                      yMax = Math.min(220, mx + 10);
                      if (yMax - yMin < 20) { yMax = yMin + 20; }
                    }
                    var ySteps = 4;
                    for (var s = 0; s <= ySteps; s++) {
                      var v = yMin + (yMax - yMin) * s / ySteps;
                      var y = padT + innerH - (innerH * s / ySteps);
                      ctx.beginPath();
                      ctx.moveTo(padL, y);
                      ctx.lineTo(W - padR, y);
                      ctx.stroke();
                      ctx.fillText(Math.round(v).toString(), padL - 6, y);
                    }

                    if (data.length === 0) {
                      ctx.fillStyle = getCss('--text-dim');
                      ctx.textAlign = 'center';
                      ctx.textBaseline = 'middle';
                      ctx.font = '13px -apple-system, sans-serif';
                      ctx.fillText('暂无数据', W / 2, H / 2);
                      return;
                    }

                    // X 轴刻度（首末时间）
                    ctx.textAlign = 'left';
                    ctx.textBaseline = 'top';
                    ctx.fillText(fmtTime(data[0].ts), padL, H - padB + 4);
                    ctx.textAlign = 'right';
                    ctx.fillText(fmtTime(data[data.length - 1].ts), W - padR, H - padB + 4);

                    // 计算坐标点
                    var n = data.length;
                    var pts = [];
                    for (var k = 0; k < n; k++) {
                      var x = padL + (n === 1 ? innerW / 2 : innerW * k / (n - 1));
                      var hr = data[k].hr;
                      var y = padT + innerH - ((hr - yMin) / (yMax - yMin)) * innerH;
                      if (y < padT) y = padT;
                      if (y > padT + innerH) y = padT + innerH;
                      pts.push({ x: x, y: y });
                    }

                    // 渐变填充
                    var grad = ctx.createLinearGradient(0, padT, 0, padT + innerH);
                    grad.addColorStop(0, 'rgba(255, 77, 109, 0.35)');
                    grad.addColorStop(1, 'rgba(255, 77, 109, 0.02)');
                    ctx.beginPath();
                    ctx.moveTo(pts[0].x, padT + innerH);
                    for (var p = 0; p < pts.length; p++) {
                      if (p === 0) ctx.lineTo(pts[p].x, pts[p].y);
                      else {
                        var prev = pts[p - 1], cur = pts[p];
                        var cpx = (prev.x + cur.x) / 2;
                        ctx.bezierCurveTo(cpx, prev.y, cpx, cur.y, cur.x, cur.y);
                      }
                    }
                    ctx.lineTo(pts[pts.length - 1].x, padT + innerH);
                    ctx.closePath();
                    ctx.fillStyle = grad;
                    ctx.fill();

                    // 曲线
                    ctx.beginPath();
                    ctx.moveTo(pts[0].x, pts[0].y);
                    for (var q = 1; q < pts.length; q++) {
                      var prev2 = pts[q - 1], cur2 = pts[q];
                      var cpx2 = (prev2.x + cur2.x) / 2;
                      ctx.bezierCurveTo(cpx2, prev2.y, cpx2, cur2.y, cur2.x, cur2.y);
                    }
                    var accent = getCss('--accent');
                    ctx.strokeStyle = accent;
                    ctx.lineWidth = 2.5;
                    ctx.lineJoin = 'round';
                    ctx.lineCap = 'round';
                    ctx.shadowColor = 'rgba(255, 77, 109, 0.5)';
                    ctx.shadowBlur = 8;
                    ctx.stroke();
                    ctx.shadowBlur = 0;

                    // 当前点高亮
                    var last = pts[pts.length - 1];
                    ctx.beginPath();
                    ctx.arc(last.x, last.y, 5, 0, Math.PI * 2);
                    ctx.fillStyle = accent;
                    ctx.fill();
                    ctx.beginPath();
                    ctx.arc(last.x, last.y, 9, 0, Math.PI * 2);
                    ctx.strokeStyle = accent;
                    ctx.globalAlpha = 0.3;
                    ctx.lineWidth = 2;
                    ctx.stroke();
                    ctx.globalAlpha = 1;
                  }

                  function getCss(name) {
                    return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#888';
                  }

                  function render() {
                    resizeCanvas();
                    draw();
                    updateMeta();
                  }

                  function connectSSE() {
                    try {
                      var es = new EventSource('events');
                      es.onmessage = function (ev) {
                        try {
                          var d = JSON.parse(ev.data);
                          if (d && typeof d.heartRate === 'number' && typeof d.timestamp === 'number') {
                            pushPoint(d.heartRate, d.timestamp);
                            render();
                          }
                        } catch (e) {}
                      };
                      es.addEventListener('device-connected', function (ev) {
                        try {
                          var d = JSON.parse(ev.data);
                          if (d) { connected = true; if (d.heartRate) { lastHr = d.heartRate; lastTs = d.timestamp; pushPoint(d.heartRate, d.timestamp); } }
                        } catch (e) {}
                        refreshStatus();
                      });
                      es.addEventListener('device-disconnected', function (ev) {
                        connected = false; render();
                      });
                      es.onerror = function () {
                        connected = false; render();
                        // 浏览器会自动重连，无需手动 close
                      };
                    } catch (e) {
                      connText.textContent = 'SSE 不可用';
                      dotEl.className = 'dot warn';
                    }
                  }

                  function refreshStatus() {
                    fetch('../status', { cache: 'no-store' }).then(function (r) {
                      return r.json();
                    }).then(function (s) {
                      connected = !!s.connected;
                      deviceName = s.deviceName || '';
                      deviceAddr = s.deviceAddress || '';
                      if (s.currentHeartRate && s.currentHeartRate > 0) {
                        lastHr = s.currentHeartRate;
                        lastTs = s.lastUpdate || 0;
                      }
                      render();
                    }).catch(function () {});
                  }

                  window.addEventListener('resize', render);
                  refreshStatus();
                  render();
                  connectSSE();
                })();
                </script>
                </body>
                </html>
                """
                .replace("__INITIAL_DATA__", initialDataJson)
                .replace("__CONNECTED__", String.valueOf(connected))
                .replace("__DEVICE_NAME__", deviceName)
                .replace("__DEVICE_ADDR__", deviceAddress)
                .replace("__HEARTRATE__", String.valueOf(heartRate));
    }
}
