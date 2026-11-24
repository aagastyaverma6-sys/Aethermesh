(() => {
  "use strict";

  const RADAR_RADIUS_M = 5000;
  const HYPRANGE_MULTIPLIER = 1.45;
  const PRIMARY_AES_KEY = "AETHER-TAC-K128!";
  const ROGUE_AES_KEY = "ROGUE-INTRUDER99";

  // =========================================================================
  // 1. MESH TOPOLOGY & ADAPTIVE REROUTING RADAR SANDBOX
  // =========================================================================
  const INITIAL_NODES = [
    { id: "YOU", label: "YOU (Phone)", x: 0, y: 0, fixed: true, online: true, role: "Host" },
    { id: "TAB-01", label: "TAB-01", x: 1150, y: -900, fixed: false, online: true, role: "Tablet" },
    { id: "RELAY-A", label: "RELAY-A", x: 1550, y: 780, fixed: false, online: true, role: "Relay" },
    { id: "RELAY-B", label: "RELAY-B", x: -1350, y: 1200, fixed: false, online: true, role: "Relay" },
    { id: "GATEWAY", label: "GATEWAY", x: 2450, y: 2150, fixed: false, online: true, role: "Gateway" },
  ];

  const radarState = {
    nodes: structuredClone(INITIAL_NODES),
    baseRangeM: 2200,
    hypRangeEnabled: false,
    draggingId: null,
    lastRouteKey: "",
    pulse: null, // { pathIds, progress, color }
  };

  const radarCanvas = document.getElementById("radarCanvas");
  const radarCtx = radarCanvas ? radarCanvas.getContext("2d") : null;
  const rangeSlider = document.getElementById("rangeSlider");
  const rangeLabel = document.getElementById("rangeLabel");
  const radarHorizonTag = document.getElementById("radarHorizonTag");
  const radarRouteBadge = document.getElementById("radarRouteBadge");
  const radarEfficiencyBadge = document.getElementById("radarEfficiencyBadge");
  const radarLed = document.getElementById("radarLed");
  const routeStatusChip = document.getElementById("routeStatusChip");
  const metricEff = document.getElementById("metricEff");
  const metricPathDesc = document.getElementById("metricPathDesc");
  const metricPeers = document.getElementById("metricPeers");
  const metricLatency = document.getElementById("metricLatency");
  const nodeToggleList = document.getElementById("nodeToggleList");
  const sandboxLog = document.getElementById("sandboxLog");
  const toggleHypRangeBtn = document.getElementById("toggleHypRangeBtn");
  const hypRangeState = document.getElementById("hypRangeState");
  const toggleBlackoutBtn = document.getElementById("toggleBlackoutBtn");
  const broadcastSosBtn = document.getElementById("broadcastSosBtn");
  const resetRadarBtn = document.getElementById("resetRadarBtn");

  function nowTime() {
    const d = new Date();
    return d.toTimeString().slice(0, 8);
  }

  function appendSandboxLog(tag, tagClass, message) {
    if (!sandboxLog) return;
    const row = document.createElement("div");
    row.className = "log-line";
    const ts = document.createElement("span");
    ts.className = "t-ts";
    ts.textContent = nowTime() + " ";

    const badge = document.createElement("span");
    badge.className = tagClass;
    badge.textContent = `[${tag}] `;

    const body = document.createElement("span");
    body.textContent = message;

    row.append(ts, badge, body);
    sandboxLog.prepend(row);
    while (sandboxLog.children.length > 24) {
      sandboxLog.removeChild(sandboxLog.lastChild);
    }
  }

  function effectiveRangeM() {
    const raw = radarState.hypRangeEnabled
      ? Math.round(radarState.baseRangeM * HYPRANGE_MULTIPLIER)
      : radarState.baseRangeM;
    return Math.min(RADAR_RADIUS_M, raw);
  }

  function distanceMeters(a, b) {
    return Math.hypot(a.x - b.x, a.y - b.y);
  }

  function edgeLatencyMs(distM) {
    return Math.max(4, Math.round(4 + (distM / 1000) * 8));
  }

  function computeMeshTopology() {
    const horizon = effectiveRangeM();
    const onlineNodes = radarState.nodes.filter((n) => n.online);
    const edges = [];

    for (let i = 0; i < onlineNodes.length; i++) {
      for (let j = i + 1; j < onlineNodes.length; j++) {
        const a = onlineNodes[i];
        const b = onlineNodes[j];
        const d = distanceMeters(a, b);
        if (d <= horizon) {
          edges.push({ a: a.id, b: b.id, distM: d, latencyMs: edgeLatencyMs(d) });
        }
      }
    }

    // Dijkstra shortest-latency path from YOU to GATEWAY (or furthest reachable peer)
    const distMap = new Map();
    const prevMap = new Map();
    const unvisited = new Set(onlineNodes.map((n) => n.id));
    onlineNodes.forEach((n) => distMap.set(n.id, n.id === "YOU" ? 0 : Infinity));

    while (unvisited.size > 0) {
      let currentId = null;
      let bestDist = Infinity;
      for (const id of unvisited) {
        const val = distMap.get(id);
        if (val < bestDist) {
          bestDist = val;
          currentId = id;
        }
      }
      if (!currentId || bestDist === Infinity) break;
      unvisited.delete(currentId);

      for (const edge of edges) {
        const neighbor =
          edge.a === currentId ? edge.b : edge.b === currentId ? edge.a : null;
        if (!neighbor || !unvisited.has(neighbor)) continue;
        const alt = bestDist + edge.latencyMs;
        if (alt < distMap.get(neighbor)) {
          distMap.set(neighbor, alt);
          prevMap.set(neighbor, currentId);
        }
      }
    }

    const gateway = radarState.nodes.find((n) => n.id === "GATEWAY");
    let targetId = "GATEWAY";
    if (!gateway || !gateway.online || distMap.get("GATEWAY") === Infinity) {
      // Pick reachable peer with highest distance/latency if GATEWAY unreachable
      let fallbackBest = -1;
      targetId = null;
      for (const n of onlineNodes) {
        if (n.id === "YOU") continue;
        const l = distMap.get(n.id);
        if (l !== undefined && l < Infinity && l >= fallbackBest) {
          fallbackBest = l;
          targetId = n.id;
        }
      }
    }

    const path = [];
    if (targetId && distMap.get(targetId) < Infinity) {
      let cur = targetId;
      while (cur) {
        path.unshift(cur);
        cur = prevMap.get(cur);
      }
    }

    const totalLatency =
      path.length >= 2 ? distMap.get(path[path.length - 1]) : 0;
    const hops = Math.max(0, path.length - 1);
    const gatewayReached = path[path.length - 1] === "GATEWAY";

    const efficiency =
      hops > 0
        ? Math.max(18, Math.min(99, Math.round(100 - (hops - 1) * 7 - totalLatency / 7)))
        : 0;

    return {
      horizon,
      edges,
      path,
      hops,
      totalLatency,
      efficiency,
      gatewayReached,
      onlinePeers: Math.max(0, onlineNodes.length - 1),
      totalPeers: radarState.nodes.length - 1,
    };
  }

  function updateRadarUI(announceReroute = true) {
    const topo = computeMeshTopology();
    const horizonText = `${topo.horizon}m${radarState.hypRangeEnabled ? " (HYP 1.45×)" : ""}`;

    if (rangeLabel) rangeLabel.textContent = `BASE RANGE: ${radarState.baseRangeM}m`;
    if (radarHorizonTag) radarHorizonTag.textContent = `HORIZON: ${horizonText}`;
    if (hypRangeState) hypRangeState.textContent = radarState.hypRangeEnabled ? "ON" : "OFF";

    if (toggleHypRangeBtn) {
      toggleHypRangeBtn.style.borderColor = radarState.hypRangeEnabled
        ? "rgba(124, 58, 237, 0.55)"
        : "";
      toggleHypRangeBtn.style.background = radarState.hypRangeEnabled
        ? "rgba(124, 58, 237, 0.12)"
        : "";
      toggleHypRangeBtn.style.color = radarState.hypRangeEnabled ? "#6d28d9" : "";
    }

    const routeStr = topo.path.length >= 2 ? topo.path.join(" → ") : "NO REACHABLE PATH";
    const isRerouted = topo.gatewayReached && topo.path.includes("RELAY-B");

    if (metricEff) {
      metricEff.textContent = topo.hops > 0 ? `${topo.efficiency}%` : "0%";
      metricEff.className = `metric-value ${
        topo.efficiency >= 80 ? "emerald" : "cyan"
      }`;
    }
    if (radarEfficiencyBadge) {
      radarEfficiencyBadge.textContent = `EFF: ${topo.efficiency}%`;
      radarEfficiencyBadge.className = `chip ${
        topo.efficiency >= 80 ? "chip-emerald" : topo.hops > 0 ? "chip-amber" : "chip-crimson"
      }`;
    }
    if (metricPathDesc) metricPathDesc.textContent = routeStr;
    if (metricPeers) metricPeers.textContent = `${topo.onlinePeers} / ${topo.totalPeers}`;
    if (metricLatency) {
      metricLatency.textContent =
        topo.hops > 0 ? `${topo.totalLatency} ms total latency` : "Path broken — expand range or revive relay";
    }

    if (radarRouteBadge) {
      radarRouteBadge.textContent =
        topo.hops > 0
          ? `ROUTE: ${topo.hops} HOP${topo.hops > 1 ? "S" : ""}${isRerouted ? " (REROUTED)" : ""}`
          : "ROUTE: ISOLATED";
      radarRouteBadge.style.color = isRerouted
        ? "#d97706"
        : topo.hops > 0
          ? "#059669"
          : "#dc2626";
    }

    if (routeStatusChip && radarLed) {
      if (!topo.gatewayReached && topo.hops === 0) {
        routeStatusChip.textContent = "ISOLATED";
        routeStatusChip.className = "chip chip-crimson";
        radarLed.className = "led led-crimson";
      } else if (!topo.gatewayReached) {
        routeStatusChip.textContent = "PARTIAL";
        routeStatusChip.className = "chip chip-amber";
        radarLed.className = "led led-amber";
      } else if (isRerouted) {
        routeStatusChip.textContent = "REROUTED";
        routeStatusChip.className = "chip chip-amber";
        radarLed.className = "led led-amber";
      } else {
        routeStatusChip.textContent = "OPTIMAL";
        routeStatusChip.className = "chip chip-emerald";
        radarLed.className = "led led-emerald";
      }
    }

    const relayA = radarState.nodes.find((n) => n.id === "RELAY-A");
    if (toggleBlackoutBtn && relayA) {
      toggleBlackoutBtn.textContent = relayA.online
        ? "Simulate Relay Outage"
        : "Restore RELAY-A";
    }

    if (announceReroute && radarState.lastRouteKey && radarState.lastRouteKey !== routeStr) {
      if (topo.gatewayReached && isRerouted) {
        appendSandboxLog(
          "REROUTE",
          "log-tag-warn",
          `Link break detected — rerouted via ${routeStr} (${topo.totalLatency} ms, EFF ${topo.efficiency}%)`
        );
      } else if (topo.gatewayReached) {
        appendSandboxLog(
          "ROUTE_OK",
          "log-tag-ok",
          `Active path converged: ${routeStr} (${topo.totalLatency} ms)`
        );
      } else {
        appendSandboxLog(
          "LINK_LOSS",
          "log-tag-crit",
          `GATEWAY unreachable with current horizon (${topo.horizon}m). Enable HypRange or revive a relay.`
        );
      }
    }
    radarState.lastRouteKey = routeStr;

    renderNodePills();
    drawRadarCanvas(topo);
  }

  function renderNodePills() {
    if (!nodeToggleList) return;
    nodeToggleList.innerHTML = "";
    radarState.nodes
      .filter((n) => !n.fixed)
      .forEach((node) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = `node-pill${node.online ? "" : " offline"}`;
        btn.textContent = `${node.id} • ${node.online ? "ONLINE" : "MUTED"}`;
        btn.addEventListener("click", () => {
          node.online = !node.online;
          appendSandboxLog(
            node.online ? "NODE_UP" : "NODE_OUT",
            node.online ? "log-tag-ok" : "log-tag-crit",
            `${node.id} switched ${node.online ? "ONLINE" : "OFFLINE (blackout)"}`
          );
          updateRadarUI(true);
        });
        nodeToggleList.appendChild(btn);
      });
  }

  function drawRadarCanvas(topoInput) {
    if (!radarCanvas || !radarCtx) return;
    const topo = topoInput || computeMeshTopology();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = radarCanvas.getBoundingClientRect();
    const size = Math.max(260, Math.round(rect.width || 560));

    if (radarCanvas.width !== size * dpr || radarCanvas.height !== size * dpr) {
      radarCanvas.width = size * dpr;
      radarCanvas.height = size * dpr;
    }

    const ctx = radarCtx;
    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, size, size);

    const cx = size / 2;
    const cy = size / 2;
    const scale = (size * 0.43) / RADAR_RADIUS_M;

    const toPx = (node) => ({
      x: cx + node.x * scale,
      y: cy + node.y * scale,
    });

    // Subtle distance rings
    ctx.lineWidth = 1;
    ctx.font = '500 10px "JetBrains Mono", monospace';
    ctx.textAlign = "center";

    [0.25, 0.5, 0.75, 1].forEach((ratio) => {
      const rPx = RADAR_RADIUS_M * ratio * scale;
      ctx.beginPath();
      ctx.arc(cx, cy, rPx, 0, Math.PI * 2);
      ctx.strokeStyle = "rgba(15, 23, 42, 0.08)";
      ctx.stroke();

      ctx.fillStyle = "rgba(100, 116, 139, 0.75)";
      const kmLabel = `${((RADAR_RADIUS_M * ratio) / 1000).toFixed(ratio === 1 ? 0 : 2)}km`;
      ctx.fillText(kmLabel, cx, cy - rPx + 12);
    });

    // Crosshairs
    ctx.beginPath();
    ctx.moveTo(cx - RADAR_RADIUS_M * scale, cy);
    ctx.lineTo(cx + RADAR_RADIUS_M * scale, cy);
    ctx.moveTo(cx, cy - RADAR_RADIUS_M * scale);
    ctx.lineTo(cx, cy + RADAR_RADIUS_M * scale);
    ctx.strokeStyle = "rgba(15, 23, 42, 0.06)";
    ctx.stroke();

    // Effective horizon ring from YOU
    const horizonPx = topo.horizon * scale;
    ctx.beginPath();
    ctx.arc(cx, cy, horizonPx, 0, Math.PI * 2);
    ctx.fillStyle = radarState.hypRangeEnabled
      ? "rgba(124, 58, 237, 0.06)"
      : "rgba(16, 185, 129, 0.06)";
    ctx.fill();
    ctx.setLineDash([6, 5]);
    ctx.lineWidth = 1.6;
    ctx.strokeStyle = radarState.hypRangeEnabled
      ? "rgba(124, 58, 237, 0.55)"
      : "rgba(5, 150, 105, 0.45)";
    ctx.stroke();
    ctx.setLineDash([]);

    // All mesh edges
    const pathPairs = new Set();
    for (let i = 0; i < topo.path.length - 1; i++) {
      const u = topo.path[i];
      const v = topo.path[i + 1];
      pathPairs.add(`${u}->${v}`);
      pathPairs.add(`${v}->${u}`);
    }

    const isRerouted = topo.gatewayReached && topo.path.includes("RELAY-B");

    topo.edges.forEach((edge) => {
      const na = radarState.nodes.find((n) => n.id === edge.a);
      const nb = radarState.nodes.find((n) => n.id === edge.b);
      if (!na || !nb) return;
      const pa = toPx(na);
      const pb = toPx(nb);
      const onPath = pathPairs.has(`${edge.a}->${edge.b}`);

      ctx.beginPath();
      ctx.moveTo(pa.x, pa.y);
      ctx.lineTo(pb.x, pb.y);
      if (onPath) {
        ctx.lineWidth = 3;
        ctx.strokeStyle = isRerouted ? "#d97706" : "#059669";
      } else {
        ctx.lineWidth = 1.25;
        ctx.strokeStyle = "rgba(2, 132, 199, 0.25)";
      }
      ctx.stroke();

      // Latency badge at midpoint
      const mx = (pa.x + pb.x) / 2;
      const my = (pa.y + pb.y) / 2;
      ctx.fillStyle = onPath ? "#0b1220" : "rgba(71, 85, 105, 0.78)";
      ctx.font = '600 9.5px "JetBrains Mono", monospace';
      ctx.fillText(`${edge.latencyMs}ms`, mx, my - 4);
    });

    // Animated SOS Pulse
    if (radarState.pulse && radarState.pulse.pathIds.length >= 2) {
      const ids = radarState.pulse.pathIds;
      const segments = ids.length - 1;
      const scaled = Math.min(segments - 0.0001, radarState.pulse.progress * segments);
      const segIdx = Math.floor(scaled);
      const t = scaled - segIdx;
      const n1 = radarState.nodes.find((n) => n.id === ids[segIdx]);
      const n2 = radarState.nodes.find((n) => n.id === ids[segIdx + 1]);
      if (n1 && n2) {
        const p1 = toPx(n1);
        const p2 = toPx(n2);
        const px = p1.x + (p2.x - p1.x) * t;
        const py = p1.y + (p2.y - p1.y) * t;

        ctx.beginPath();
        ctx.arc(px, py, 11, 0, Math.PI * 2);
        ctx.fillStyle = "rgba(220, 38, 38, 0.22)";
        ctx.fill();

        ctx.beginPath();
        ctx.arc(px, py, 5, 0, Math.PI * 2);
        ctx.fillStyle = "#dc2626";
        ctx.fill();
      }
    }

    // Draw Nodes
    radarState.nodes.forEach((node) => {
      const p = toPx(node);
      const inPath = topo.path.includes(node.id);
      const isHost = node.id === "YOU";
      const isTarget = node.id === "GATEWAY";

      let fill = "#0284c7";
      if (!node.online) fill = "#dc2626";
      else if (isHost) fill = "#059669";
      else if (isTarget) fill = "#7c3aed";
      else if (inPath && isRerouted) fill = "#d97706";
      else if (inPath) fill = "#059669";

      ctx.beginPath();
      ctx.arc(p.x, p.y, isHost ? 14 : 11, 0, Math.PI * 2);
      ctx.fillStyle = node.online
        ? isHost
          ? "rgba(5, 150, 105, 0.16)"
          : "rgba(2, 132, 199, 0.14)"
        : "rgba(220, 38, 38, 0.14)";
      ctx.fill();

      ctx.beginPath();
      ctx.arc(p.x, p.y, isHost ? 7.5 : 6, 0, Math.PI * 2);
      ctx.fillStyle = fill;
      ctx.fill();
      ctx.lineWidth = 2;
      ctx.strokeStyle = "#ffffff";
      ctx.stroke();

      ctx.font = '600 11px "JetBrains Mono", monospace';
      ctx.fillStyle = node.online ? "#0b1220" : "#b91c1c";
      ctx.fillText(node.online ? node.id : `${node.id} [X]`, p.x, p.y + 20);
    });

    ctx.restore();
  }

  function pointerToMeters(clientX, clientY) {
    const rect = radarCanvas.getBoundingClientRect();
    const cx = rect.width / 2;
    const cy = rect.height / 2;
    const scale = (rect.width * 0.43) / RADAR_RADIUS_M;
    return {
      x: (clientX - rect.left - cx) / scale,
      y: (clientY - rect.top - cy) / scale,
    };
  }

  if (radarCanvas) {
    radarCanvas.addEventListener("pointerdown", (e) => {
      const m = pointerToMeters(e.clientX, e.clientY);
      let hit = null;
      let bestDist = 420;
      for (const node of radarState.nodes) {
        if (node.fixed) continue;
        const d = Math.hypot(node.x - m.x, node.y - m.y);
        if (d < bestDist) {
          bestDist = d;
          hit = node;
        }
      }
      if (hit) {
        radarState.draggingId = hit.id;
        radarCanvas.setPointerCapture(e.pointerId);
      }
    });

    radarCanvas.addEventListener("pointermove", (e) => {
      if (!radarState.draggingId) return;
      const node = radarState.nodes.find((n) => n.id === radarState.draggingId);
      if (!node) return;
      const m = pointerToMeters(e.clientX, e.clientY);
      const r = Math.hypot(m.x, m.y);
      const maxR = RADAR_RADIUS_M * 0.94;
      if (r > maxR) {
        node.x = Math.round((m.x / r) * maxR);
        node.y = Math.round((m.y / r) * maxR);
      } else {
        node.x = Math.round(m.x);
        node.y = Math.round(m.y);
      }
      updateRadarUI(true);
    });

    const endDrag = () => {
      radarState.draggingId = null;
    };
    radarCanvas.addEventListener("pointerup", endDrag);
    radarCanvas.addEventListener("pointercancel", endDrag);
    window.addEventListener("resize", () => drawRadarCanvas());
  }

  if (rangeSlider) {
    rangeSlider.addEventListener("input", () => {
      radarState.baseRangeM = Number(rangeSlider.value);
      updateRadarUI(true);
    });
  }

  if (toggleHypRangeBtn) {
    toggleHypRangeBtn.addEventListener("click", () => {
      radarState.hypRangeEnabled = !radarState.hypRangeEnabled;
      appendSandboxLog(
        "HYPRANGE",
        radarState.hypRangeEnabled ? "log-tag-ok" : "log-tag-info",
        radarState.hypRangeEnabled
          ? "BLE 5+ Coded PHY S=8 active — link horizon boosted 1.45×"
          : "HypRange Coded PHY disabled — standard 1M PHY horizon restored"
      );
      updateRadarUI(true);
    });
  }

  if (toggleBlackoutBtn) {
    toggleBlackoutBtn.addEventListener("click", () => {
      const relayA = radarState.nodes.find((n) => n.id === "RELAY-A");
      if (!relayA) return;
      relayA.online = !relayA.online;
      appendSandboxLog(
        relayA.online ? "RECOVERY" : "BLACKOUT",
        relayA.online ? "log-tag-ok" : "log-tag-warn",
        relayA.online
          ? "RELAY-A restored online"
          : "Simulated blackout knocked out RELAY-A"
      );
      updateRadarUI(true);
    });
  }

  if (broadcastSosBtn) {
    broadcastSosBtn.addEventListener("click", () => {
      const topo = computeMeshTopology();
      if (topo.path.length < 2) {
        appendSandboxLog(
          "TX_FAIL",
          "log-tag-crit",
          "No reachable mesh path — move a relay closer or enable HypRange"
        );
        return;
      }
      appendSandboxLog(
        "TX_SOS",
        "log-tag-ok",
        `Encrypted AETHER_AES128_V1 SOS dispatched via ${topo.path.join(" → ")} (${topo.totalLatency} ms)`
      );
      radarState.pulse = { pathIds: [...topo.path], progress: 0 };
      const start = performance.now();
      const durationMs = 1150;
      const tick = (now) => {
        const p = Math.min(1, (now - start) / durationMs);
        if (!radarState.pulse) return;
        radarState.pulse.progress = p;
        drawRadarCanvas();
        if (p < 1) {
          requestAnimationFrame(tick);
        } else {
          radarState.pulse = null;
          drawRadarCanvas();
        }
      };
      requestAnimationFrame(tick);
    });
  }

  if (resetRadarBtn) {
    resetRadarBtn.addEventListener("click", () => {
      radarState.nodes = structuredClone(INITIAL_NODES);
      radarState.baseRangeM = 2200;
      radarState.hypRangeEnabled = false;
      radarState.pulse = null;
      if (rangeSlider) rangeSlider.value = "2200";
      appendSandboxLog("RESET", "log-tag-info", "Sandbox topology restored to initial coordinates");
      updateRadarUI(false);
    });
  }

  // =========================================================================
  // 2. 4-RADIO HARDWARE TRANSPORT STACK EXPLORER
  // =========================================================================
  const RADIO_SPECS = {
    lan: {
      badge: "PRIORITY 1 • ZERO-PAIRING",
      badgeClass: "chip-emerald",
      cls: "LanMeshTransport.kt",
      title: "LAN & Mobile Hotspot Mesh",
      desc: "Instant high-throughput communication between phones and tablets connected to the same Wi-Fi router or a phone-hosted mobile hotspot, with zero external internet required.",
      bullets: [
        "UDP broadcast peer discovery beacons on port 45872",
        "Direct TCP packet delivery sockets on port 45873",
        "Automatic IP refresh and stale-peer pruning with real RTT latency checks",
      ],
      codeTitle: "LAN DISCOVERY & SOCKET PROFILE",
      code: `object LanMeshConfig {\n  const val DISCOVERY_UDP_PORT = 45872\n  const val MESSAGE_TCP_PORT = 45873\n  const val BEACON_INTERVAL_MS = 2_000L\n  const val WIRE_ENVELOPE = "AETHER_AES128_V1"\n}`,
    },
    hyprange: {
      badge: "PRIORITY 2 • LONG-RANGE BLE",
      badgeClass: "chip-violet",
      cls: "BleMeshTransport.kt",
      title: "HypRange (BLE 5+ Coded PHY S=8)",
      desc: "When toggled on supported Bluetooth 5+ chipsets, HypRange switches GATT advertising and scanning to PHY_LE_CODED for long-range emergency signal reach across walls and terrain.",
      bullets: [
        "GATT Mesh Service UUID: 0000AE74-0000-1000-8000-00805F9B34FB",
        "Encrypted Packet Characteristic: 0000AE75-0000-1000-8000-00805F9B34FB",
        "Extends link horizon by 1.45× inside MeshRouter with real RSSI estimation",
      ],
      codeTitle: "HYPRANGE CODED PHY ADVERTISING",
      code: `AdvertisingSetParameters.Builder()\n  .setLegacyMode(false)\n  .setPrimaryPhy(BluetoothDevice.PHY_LE_CODED)\n  .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)\n  .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)\n  .build()`,
    },
    btclassic: {
      badge: "PRIORITY 3 • LEGACY COMPATIBLE",
      badgeClass: "chip-cyan",
      cls: "BtClassicMeshTransport.kt",
      title: "Bluetooth Classic RFCOMM / SPP",
      desc: "Provides resilient device-to-device streaming sockets across older Android 7–11 hardware and mixed phone/tablet combinations where BLE Extended Advertising is unavailable.",
      bullets: [
        "Insecure RFCOMM SPP Service UUID: 0000AE76-0000-1000-8000-00805F9B34FB",
        "Active Bluetooth discovery combined with bonded-device probing",
        "Delimited UTF-8 AES-128 wire envelopes over bidirectional streams",
      ],
      codeTitle: "BT CLASSIC RFCOMM SERVER SOCKET",
      code: `val sppUuid = UUID.fromString(\n  "0000AE76-0000-1000-8000-00805F9B34FB"\n)\nadapter.listenUsingInsecureRfcommWithServiceRecord(\n  "AetherMeshSPP", sppUuid\n)`,
    },
    wifidirect: {
      badge: "PRIORITY 4 • PURE P2P WI-FI",
      badgeClass: "chip-amber",
      cls: "WifiP2pMeshTransport.kt",
      title: "Wi-Fi Direct (WifiP2pManager)",
      desc: "Establishes a direct peer-to-peer wireless group without an access point or hotspot—one device negotiates Group Owner while the partner routes packets to its P2P interface.",
      bullets: [
        "Uses Android WifiP2pManager peer discovery and BroadcastReceiver intents",
        "Automatic Group Owner IP resolution for P2P TCP delivery",
        "Zero location dependency on Android 13+ via NEARBY_WIFI_DEVICES (neverForLocation)",
      ],
      codeTitle: "WI-FI DIRECT PEER DISCOVERY",
      code: `wifiP2pManager.discoverPeers(channel, object : ActionListener {\n  override fun onSuccess() = log("P2P scan active")\n  override fun onFailure(reason: Int) = log("P2P err=$reason")\n})`,
    },
  };

  const radioTabs = document.querySelectorAll(".radio-tab");
  const radioBadge = document.getElementById("radioBadge");
  const radioClass = document.getElementById("radioClass");
  const radioTitle = document.getElementById("radioTitle");
  const radioDesc = document.getElementById("radioDesc");
  const radioBullets = document.getElementById("radioBullets");
  const radioCodeTitle = document.getElementById("radioCodeTitle");
  const radioCode = document.getElementById("radioCode");

  function selectRadioTab(key) {
    const spec = RADIO_SPECS[key] || RADIO_SPECS.lan;
    radioTabs.forEach((btn) => {
      const active = btn.dataset.radio === key;
      btn.classList.toggle("active", active);
      btn.setAttribute("aria-selected", active ? "true" : "false");
    });

    if (radioBadge) {
      radioBadge.textContent = spec.badge;
      radioBadge.className = `chip ${spec.badgeClass}`;
    }
    if (radioClass) radioClass.textContent = spec.cls;
    if (radioTitle) radioTitle.textContent = spec.title;
    if (radioDesc) radioDesc.textContent = spec.desc;
    if (radioCodeTitle) radioCodeTitle.textContent = spec.codeTitle;
    if (radioCode) radioCode.textContent = spec.code;
    if (radioBullets) {
      radioBullets.innerHTML = "";
      spec.bullets.forEach((item) => {
        const li = document.createElement("li");
        li.textContent = item;
        radioBullets.appendChild(li);
      });
    }
  }

  radioTabs.forEach((tab) => {
    tab.addEventListener("click", () => selectRadioTab(tab.dataset.radio));
  });

  // =========================================================================
  // 3. REAL WEBCRYPTO AES-128-CBC PACKET INSPECTOR
  // =========================================================================
  const cryptoInput = document.getElementById("cryptoInput");
  const encryptBtn = document.getElementById("encryptBtn");
  const toggleTamperBtn = document.getElementById("toggleTamperBtn");
  const cryptoFingerprint = document.getElementById("cryptoFingerprint");
  const cryptoVerifyBadge = document.getElementById("cryptoVerifyBadge");
  const cryptoHexOut = document.getElementById("cryptoHexOut");
  const cryptoJsonLabel = document.getElementById("cryptoJsonLabel");
  const cryptoJsonOut = document.getElementById("cryptoJsonOut");

  let useRogueKey = false;
  let lastCipherPacket = null; // { iv: Uint8Array, cipher: Uint8Array }

  function formatGroupedHex(bytes) {
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0").toUpperCase());
    const lines = [];
    for (let i = 0; i < hex.length; i += 8) {
      lines.push(hex.slice(i, i + 8).join(" "));
    }
    return lines.join("\n");
  }

  async function importAesKey(keyText) {
    const enc = new TextEncoder();
    const raw = enc.encode(keyText);
    return window.crypto.subtle.importKey(
      "raw",
      raw,
      { name: "AES-CBC" },
      false,
      ["encrypt", "decrypt"]
    );
  }

  async function decryptAndRender() {
    if (!lastCipherPacket) return;
    const activeKeyStr = useRogueKey ? ROGUE_AES_KEY : PRIMARY_AES_KEY;

    if (cryptoFingerprint) {
      cryptoFingerprint.textContent = useRogueKey
        ? `KEY#ROGUE // ${ROGUE_AES_KEY} (MISMATCH TEST)`
        : `KEY#TAC-01 // ${PRIMARY_AES_KEY}`;
    }

    try {
      const key = await importAesKey(activeKeyStr);
      const plainBuf = await window.crypto.subtle.decrypt(
        { name: "AES-CBC", iv: lastCipherPacket.iv },
        key,
        lastCipherPacket.cipher
      );
      const decoded = new TextDecoder().decode(plainBuf);
      const parsed = JSON.parse(decoded);

      if (cryptoVerifyBadge) {
        cryptoVerifyBadge.textContent = "VERIFIED";
        cryptoVerifyBadge.className = "chip chip-emerald";
      }
      if (cryptoJsonLabel) {
        cryptoJsonLabel.textContent = "DECRYPTED JSON PAYLOAD (AETHER_AES128_V1)";
        cryptoJsonLabel.className = "mono-tiny label-emerald";
      }
      if (cryptoJsonOut) {
        cryptoJsonOut.textContent = JSON.stringify(parsed, null, 2);
      }
    } catch (_err) {
      if (cryptoVerifyBadge) {
        cryptoVerifyBadge.textContent = "REJECTED";
        cryptoVerifyBadge.className = "chip chip-crimson";
      }
      if (cryptoJsonLabel) {
        cryptoJsonLabel.textContent = "DECRYPTION FAILED — BAD KEY OR TAMPERED PKCS#7 PADDING";
        cryptoJsonLabel.className = "mono-tiny label-crimson";
      }
      if (cryptoJsonOut) {
        cryptoJsonOut.textContent =
          `ERROR: AES-128-CBC decrypt rejected with key "${ROGUE_AES_KEY}".\nWire packet discarded before reaching mesh routing layer.`;
      }
    }
  }

  async function runWebCryptoEncrypt() {
    if (!window.crypto || !window.crypto.subtle) {
      if (cryptoHexOut) {
        cryptoHexOut.textContent = "WebCrypto subtle API requires HTTPS or localhost context.";
      }
      return;
    }

    const msg = (cryptoInput?.value || "SOS").trim() || "SOS";
    const payload = {
      v: "AETHER_AES128_V1",
      type: "SOS_TACTICAL",
      sender: "YOU",
      msg,
      ts: new Date().toISOString(),
    };

    const iv = window.crypto.getRandomValues(new Uint8Array(16));
    const key = await importAesKey(PRIMARY_AES_KEY);
    const plainBytes = new TextEncoder().encode(JSON.stringify(payload));
    const cipherBuf = await window.crypto.subtle.encrypt(
      { name: "AES-CBC", iv },
      key,
      plainBytes
    );
    const cipherBytes = new Uint8Array(cipherBuf);

    const combined = new Uint8Array(iv.length + cipherBytes.length);
    combined.set(iv, 0);
    combined.set(cipherBytes, iv.length);

    lastCipherPacket = { iv, cipher: cipherBytes };
    if (cryptoHexOut) {
      cryptoHexOut.textContent = formatGroupedHex(combined);
    }
    await decryptAndRender();
  }

  if (encryptBtn) {
    encryptBtn.addEventListener("click", () => {
      runWebCryptoEncrypt();
    });
  }

  if (cryptoInput) {
    cryptoInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        runWebCryptoEncrypt();
      }
    });
  }

  if (toggleTamperBtn) {
    toggleTamperBtn.addEventListener("click", () => {
      useRogueKey = !useRogueKey;
      toggleTamperBtn.textContent = useRogueKey
        ? "Use Shared Key (TAC-01)"
        : "Test Rogue Key";
      decryptAndRender();
    });
  }

  // =========================================================================
  // 4. REAL WEB AUDIO API ACOUSTIC FSK FALLBACK MODEM
  // =========================================================================
  const MORSE_MAP = {
    A: ".-", B: "-...", C: "-.-.", D: "-..", E: ".", F: "..-.", G: "--.",
    H: "....", I: "..", J: ".---", K: "-.-", L: ".-..", M: "--", N: "-.",
    O: "---", P: ".--.", Q: "--.-", R: ".-.", S: "...", T: "-", U: "..-",
    V: "...-", W: ".--", X: "-..-", Y: "-.--", Z: "--..",
    "0": "-----", "1": ".----", "2": "..---", "3": "...--", "4": "....-",
    "5": ".....", "6": "-....", "7": "--...", "8": "---..", "9": "----.",
    " ": "/",
  };

  const FSK_BANDS = {
    audible: { spaceHz: 1200, markHz: 2200, label: "AUDIBLE 1.2 / 2.2 kHz" },
    highband: { spaceHz: 6800, markHz: 8200, label: "HIGH-BAND 6.8 / 8.2 kHz" },
  };

  const fskInput = document.getElementById("fskInput");
  const bandAudibleBtn = document.getElementById("bandAudibleBtn");
  const bandHighBtn = document.getElementById("bandHighBtn");
  const transmitFskBtn = document.getElementById("transmitFskBtn");
  const stopFskBtn = document.getElementById("stopFskBtn");
  const fskStatusBadge = document.getElementById("fskStatusBadge");
  const fskLed = document.getElementById("fskLed");
  const fskBinaryOut = document.getElementById("fskBinaryOut");
  const fskMorseOut = document.getElementById("fskMorseOut");
  const fskWaveform = document.getElementById("fskWaveform");
  const fskCtx = fskWaveform ? fskWaveform.getContext("2d") : null;

  let currentBand = "audible";
  let audioCtx = null;
  let activeOsc = null;
  let activeGain = null;
  let fskAnimFrame = null;
  let fskStopTimer = null;

  function encodeBinaryBits(text) {
    const clean = (text || "SOS").toUpperCase();
    const bytes = [];
    for (let i = 0; i < clean.length; i++) {
      const code = clean.charCodeAt(i) & 0xff;
      bytes.push(code.toString(2).padStart(8, "0"));
    }
    return bytes;
  }

  function encodeMorse(text) {
    const clean = (text || "SOS").toUpperCase();
    return Array.from(clean)
      .map((ch) => MORSE_MAP[ch] || "")
      .filter(Boolean)
      .join(" ");
  }

  function drawFskWaveform(bitsString = "", activeBitIdx = -1, phaseShift = 0) {
    if (!fskWaveform || !fskCtx) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = fskWaveform.getBoundingClientRect();
    const w = Math.max(240, Math.round(rect.width || 520));
    const h = 104;

    if (fskWaveform.width !== w * dpr || fskWaveform.height !== h * dpr) {
      fskWaveform.width = w * dpr;
      fskWaveform.height = h * dpr;
    }

    const ctx = fskCtx;
    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, w, h);

    // Center line
    ctx.beginPath();
    ctx.moveTo(0, h / 2);
    ctx.lineTo(w, h / 2);
    ctx.strokeStyle = "rgba(15, 23, 42, 0.08)";
    ctx.lineWidth = 1;
    ctx.stroke();

    const previewBits = bitsString.slice(0, 64) || "01010101";
    const bitW = w / previewBits.length;

    ctx.beginPath();
    for (let x = 0; x < w; x++) {
      const bitIdx = Math.min(previewBits.length - 1, Math.floor(x / bitW));
      const isMark = previewBits[bitIdx] === "1";
      const cyclesPerBit = isMark ? 2.4 : 1.2;
      const localU = (x % bitW) / bitW;
      const amp = activeBitIdx >= 0 ? 34 : 20;
      const y =
        h / 2 +
        Math.sin((localU * cyclesPerBit + phaseShift) * Math.PI * 2) * amp;
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }

    ctx.strokeStyle = currentBand === "highband" ? "#7c3aed" : "#0284c7";
    ctx.lineWidth = 2;
    ctx.stroke();

    // Active playhead indicator
    if (activeBitIdx >= 0 && activeBitIdx < previewBits.length) {
      const px = (activeBitIdx + 0.5) * bitW;
      ctx.fillStyle = "rgba(5, 150, 105, 0.14)";
      ctx.fillRect(activeBitIdx * bitW, 0, bitW, h);
      ctx.beginPath();
      ctx.moveTo(px, 0);
      ctx.lineTo(px, h);
      ctx.strokeStyle = "#059669";
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    ctx.restore();
  }

  function refreshFskPreview() {
    const text = fskInput?.value || "SOS";
    const byteStrings = encodeBinaryBits(text);
    if (fskBinaryOut) fskBinaryOut.textContent = byteStrings.join(" ");
    if (fskMorseOut) fskMorseOut.textContent = encodeMorse(text);
    drawFskWaveform(byteStrings.join(""), -1, 0);
  }

  function stopFskAudio() {
    if (fskStopTimer) {
      clearTimeout(fskStopTimer);
      fskStopTimer = null;
    }
    if (fskAnimFrame) {
      cancelAnimationFrame(fskAnimFrame);
      fskAnimFrame = null;
    }
    if (activeOsc) {
      try {
        activeOsc.stop();
        activeOsc.disconnect();
      } catch (_e) {}
      activeOsc = null;
    }
    if (activeGain) {
      try {
        activeGain.disconnect();
      } catch (_e) {}
      activeGain = null;
    }
    if (fskStatusBadge) {
      fskStatusBadge.textContent = "STANDBY";
      fskStatusBadge.className = "chip chip-cyan";
    }
    if (fskLed) fskLed.className = "led led-amber";
    refreshFskPreview();
  }

  async function startFskTransmission() {
    stopFskAudio();

    const AudioCtor = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtor) {
      if (fskStatusBadge) fskStatusBadge.textContent = "NO WEBAUDIO";
      return;
    }

    if (!audioCtx) {
      audioCtx = new AudioCtor();
    }
    if (audioCtx.state === "suspended") {
      await audioCtx.resume();
    }

    const byteStrings = encodeBinaryBits(fskInput?.value || "SOS");
    const bits = byteStrings.join("");
    const band = FSK_BANDS[currentBand];
    const bitDurationSec = 0.016; // 62.5 bps matching AcousticFskModem.kt
    const startTime = audioCtx.currentTime + 0.03;

    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = "sine";
    gain.gain.setValueAtTime(0.0001, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.18, startTime + 0.01);

    for (let i = 0; i < bits.length; i++) {
      const freq = bits[i] === "1" ? band.markHz : band.spaceHz;
      osc.frequency.setValueAtTime(freq, startTime + i * bitDurationSec);
    }

    const totalSec = bits.length * bitDurationSec;
    gain.gain.setValueAtTime(0.18, startTime + Math.max(0, totalSec - 0.015));
    gain.gain.exponentialRampToValueAtTime(0.0001, startTime + totalSec);

    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start(startTime);
    osc.stop(startTime + totalSec + 0.02);

    activeOsc = osc;
    activeGain = gain;

    if (fskStatusBadge) {
      fskStatusBadge.textContent = `TX ${band.label}`;
      fskStatusBadge.className = "chip chip-emerald";
    }
    if (fskLed) fskLed.className = "led led-emerald";

    const animStart = performance.now();
    const previewLen = Math.min(64, bits.length);
    const totalMs = totalSec * 1000;

    const animate = (now) => {
      const elapsed = now - animStart;
      const frac = Math.min(1, elapsed / totalMs);
      const activeIdx = Math.floor(frac * previewLen);
      drawFskWaveform(bits, activeIdx, elapsed * 0.01);
      if (frac < 1 && activeOsc) {
        fskAnimFrame = requestAnimationFrame(animate);
      }
    };
    fskAnimFrame = requestAnimationFrame(animate);

    fskStopTimer = setTimeout(() => {
      stopFskAudio();
    }, totalMs + 80);
  }

  if (bandAudibleBtn && bandHighBtn) {
    bandAudibleBtn.addEventListener("click", () => {
      currentBand = "audible";
      bandAudibleBtn.classList.add("active");
      bandHighBtn.classList.remove("active");
      refreshFskPreview();
    });
    bandHighBtn.addEventListener("click", () => {
      currentBand = "highband";
      bandHighBtn.classList.add("active");
      bandAudibleBtn.classList.remove("active");
      refreshFskPreview();
    });
  }

  if (fskInput) {
    fskInput.addEventListener("input", refreshFskPreview);
  }
  if (transmitFskBtn) {
    transmitFskBtn.addEventListener("click", startFskTransmission);
  }
  if (stopFskBtn) {
    stopFskBtn.addEventListener("click", stopFskAudio);
  }

  // =========================================================================
  // BOOTSTRAP ALL MODULES
  // =========================================================================
  appendSandboxLog(
    "SANDBOX",
    "log-tag-info",
    "Browser routing lab initialized — drag peers or simulate relay outage."
  );
  updateRadarUI(false);
  selectRadioTab("lan");
  runWebCryptoEncrypt();
  refreshFskPreview();
})();
