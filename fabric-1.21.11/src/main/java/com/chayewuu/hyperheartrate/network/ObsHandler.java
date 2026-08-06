package com.chayewuu.hyperheartrate.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.chayewuu.hyperheartrate.heart.HeartRateManager;

import java.io.IOException;

/**
 * {@code GET /obs} 处理器。
 * <p>返回专为 OBS 浏览器源优化的极简心率叠层页面：
 * 左侧一个跳动的心形图标，右侧显示当前心率数值。</p>
 *
 * <p>页面特性：</p>
 * <ul>
 *     <li>透明背景，适合直接作为 OBS 浏览器源叠加；</li>
 *     <li>左侧 SVG 心形图标，根据心率自动调整跳动频率；</li>
 *     <li>右侧大字号心率数值 + BPM 单位；</li>
 *     <li>通过 SSE（{@code /graph/events}）实时接收心率推送；</li>
 *     <li>未连接时心率显示为 "—"，心形以默认频率跳动；</li>
 *     <li>内联 CSS / JS，无外部依赖。</li>
 * </ul>
 *
 * <p>与 {@link GraphHandler}（{@code /graph}）的区别：
 * Graph 页面包含图表、设备信息、历史曲线等完整监测面板；
 * Obs 页面仅保留心率核心展示，适合直播叠层场景。</p>
 */
public class ObsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        HeartRateManager mgr = HeartRateManager.getInstance();
        boolean connected = mgr.isConnected();
        int heartRate = mgr.getCurrentHeartRate();

        String html = buildHtml(heartRate, connected);
        HttpUtil.sendHtml(exchange, html);
    }

    /**
     * 构建 OBS 叠层 HTML 页面。
     *
     * @param heartRate 当前心率
     * @param connected 是否已连接
     * @return HTML 字符串
     */
    private static String buildHtml(int heartRate, boolean connected) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>心率 · OBS 叠层</title>
                <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body {
                  height: 100%;
                  background: transparent;
                  overflow: hidden;
                }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
                               "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                .wrap {
                  display: flex;
                  align-items: center;
                  gap: 18px;
                  padding: 16px 28px;
                  filter: drop-shadow(0 2px 8px rgba(0, 0, 0, 0.5));
                }
                .heart-svg {
                  width: 72px;
                  height: 72px;
                  flex-shrink: 0;
                  transform-origin: center;
                  animation: beat 1s ease-in-out infinite;
                }
                @keyframes beat {
                  0%, 100% { transform: scale(1); }
                  15% { transform: scale(1.22); }
                  30% { transform: scale(0.95); }
                  45% { transform: scale(1.12); }
                  60% { transform: scale(1); }
                }
                .hr-value {
                  font-size: 88px;
                  font-weight: 800;
                  line-height: 1;
                  color: #ffffff;
                  text-shadow: 0 0 12px rgba(255, 77, 109, 0.6), 0 2px 6px rgba(0, 0, 0, 0.8);
                  letter-spacing: -2px;
                  min-width: 100px;
                  text-align: left;
                }
                .hr-unit {
                  font-size: 24px;
                  font-weight: 600;
                  color: #ff8fa3;
                  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.8);
                  margin-left: 6px;
                  align-self: flex-end;
                  padding-bottom: 10px;
                  letter-spacing: 1px;
                }
                .disconnected .heart-svg {
                  animation-duration: 1.6s;
                  opacity: 0.55;
                }
                .disconnected .hr-value {
                  color: #cccccc;
                  text-shadow: 0 2px 6px rgba(0, 0, 0, 0.8);
                }
                </style>
                </head>
                <body>
                  <div class="wrap" id="wrap">
                    <svg class="heart-svg" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M12 21s-7.5-4.6-10-9.2C0.5 8 2 4 6 4c2 0 3.5 1 6 3.5C14.5 5 16 4 18 4c4 0 5.5 4 4 7.8C19.5 16.4 12 21 12 21z"
                            fill="url(#obsHg)" stroke="rgba(255,255,255,0.25)" stroke-width="0.5"/>
                      <defs>
                        <linearGradient id="obsHg" x1="0" y1="0" x2="24" y2="24" gradientUnits="userSpaceOnUse">
                          <stop stop-color="#ff4d6d"/><stop offset="1" stop-color="#ff9a6c"/>
                        </linearGradient>
                      </defs>
                    </svg>
                    <div style="display:flex; align-items:flex-end;">
                      <span class="hr-value" id="hrValue">—</span>
                      <span class="hr-unit" id="hrUnit">BPM</span>
                    </div>
                  </div>

                <script>
                (function () {
                  var wrap = document.getElementById('wrap');
                  var hrEl = document.getElementById('hrValue');
                  var heartSvg = document.querySelector('.heart-svg');
                  var connected = __CONNECTED__;
                  var lastHr = __HEARTRATE__;

                  function updateDisplay() {
                    hrEl.textContent = lastHr > 0 ? lastHr : '—';
                    if (connected && lastHr > 0) {
                      wrap.classList.remove('disconnected');
                      // 根据心率调整跳动频率：60000ms / hr = 每跳周期
                      var beatDuration = Math.max(0.4, 60 / lastHr);
                      heartSvg.style.animationDuration = beatDuration.toFixed(2) + 's';
                    } else {
                      wrap.classList.add('disconnected');
                      heartSvg.style.animationDuration = '1.6s';
                    }
                  }

                  function connectSSE() {
                    try {
                      var es = new EventSource('../graph/events');
                      es.onmessage = function (ev) {
                        try {
                          var d = JSON.parse(ev.data);
                          if (d && typeof d.heartRate === 'number') {
                            if (d.heartRate > 0) {
                              lastHr = d.heartRate;
                              connected = true;
                            }
                            updateDisplay();
                          }
                        } catch (e) {}
                      };
                      es.addEventListener('device-connected', function (ev) {
                        connected = true;
                        try {
                          var d = JSON.parse(ev.data);
                          if (d && d.heartRate > 0) { lastHr = d.heartRate; }
                        } catch (e) {}
                        updateDisplay();
                      });
                      es.addEventListener('device-disconnected', function () {
                        connected = false;
                        updateDisplay();
                      });
                      es.onerror = function () {
                        connected = false;
                        updateDisplay();
                      };
                    } catch (e) {}
                  }

                  // 初始拉取一次状态
                  fetch('../status', { cache: 'no-store' }).then(function (r) {
                    return r.json();
                  }).then(function (s) {
                    connected = !!s.connected;
                    if (s.currentHeartRate && s.currentHeartRate > 0) {
                      lastHr = s.currentHeartRate;
                    }
                    updateDisplay();
                  }).catch(function () {});

                  updateDisplay();
                  connectSSE();
                })();
                </script>
                </body>
                </html>
                """
                .replace("__CONNECTED__", String.valueOf(connected))
                .replace("__HEARTRATE__", String.valueOf(heartRate));
    }
}
