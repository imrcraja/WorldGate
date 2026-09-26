'use strict';

const http = require('http');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT || 10000);
const MAX_PAIRS = Number(process.env.MAX_PAIRS || 25);
const HANDSHAKE_TIMEOUT = 10000;
const ROOM_TTL_MS = Number(process.env.ROOM_TTL_MS || 30 * 60 * 1000);
const MAX_PAYLOAD = Number(process.env.MAX_PAYLOAD || 4 * 1024 * 1024);
const MAX_CONNECTIONS_PER_IP_PER_MINUTE = Number(process.env.MAX_CONNECTIONS_PER_IP_PER_MINUTE || 30);
const SECURITY_BAN_MS = Number(process.env.SECURITY_BAN_MS || 60 * 60 * 1000);
const REQUIRED_PROTOCOL = 2;
const ALLOWED_MOD_SHA256 = String(process.env.WORLDGATE_ALLOWED_MOD_SHA256 || '').trim().toLowerCase();

const rooms = new Map();
const ipState = new Map();

function clientIp(ws) {
  return String(ws?._socket?.remoteAddress || 'unknown').replace(/^::ffff:/, '');
}

function registerViolation(ip) {
  const now = Date.now();
  const state = ipState.get(ip) || { windowStart: now, connections: 0, violations: 0, bannedUntil: 0 };
  if (now - state.windowStart >= 60_000) {
    state.windowStart = now;
    state.connections = 0;
    state.violations = 0;
  }
  state.violations += 1;
  if (state.violations >= 10) state.bannedUntil = now + SECURITY_BAN_MS;
  ipState.set(ip, state);
}

function allowConnection(ip) {
  const now = Date.now();
  const state = ipState.get(ip) || { windowStart: now, connections: 0, violations: 0, bannedUntil: 0 };
  if (state.bannedUntil > now) return false;
  if (now - state.windowStart >= 60_000) {
    state.windowStart = now;
    state.connections = 0;
    state.violations = 0;
  }
  if (state.connections >= MAX_CONNECTIONS_PER_IP_PER_MINUTE) {
    state.bannedUntil = now + Math.min(SECURITY_BAN_MS, 10 * 60 * 1000);
    ipState.set(ip, state);
    return false;
  }
  state.connections += 1;
  ipState.set(ip, state);
  return true;
}

function sendJson(ws, value) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(value));
}

function cleanup(roomCode, reason) {
  const room = rooms.get(roomCode);
  if (!room) return;
  for (const ws of [room.host, room.player, room.voiceHost, room.voicePlayer]) {
    if (ws && ws.readyState === ws.OPEN) ws.close();
  }
  rooms.delete(roomCode);
  console.log(`Room closed: ${roomCode} (${reason})`);
}

function activePairs() {
  let count = 0;
  for (const room of rooms.values()) if (room.host && room.player) count++;
  return count;
}

function validModHash(value) {
  return /^[a-f0-9]{64}$/.test(String(value || '').toLowerCase());
}

function integrityAllowed(modSha256) {
  if (!ALLOWED_MOD_SHA256) return true;
  return validModHash(modSha256) &&
    crypto.timingSafeEqual(Buffer.from(modSha256.toLowerCase()), Buffer.from(ALLOWED_MOD_SHA256));
}

function startPair(roomCode, room) {
  if (!room.host || !room.player) return;
  sendJson(room.host, { type: 'connected', protocol: REQUIRED_PROTOCOL });
  sendJson(room.player, { type: 'connected', protocol: REQUIRED_PROTOCOL });

  console.log(`WebSocket forwarding started: ${roomCode}`);

  room.host.on('message', (data, isBinary) => {
    if (room.player && room.player.readyState === room.player.OPEN) room.player.send(data, { binary: isBinary });
  });
  room.player.on('message', (data, isBinary) => {
    if (room.host && room.host.readyState === room.host.OPEN) room.host.send(data, { binary: isBinary });
  });

  const closePair = reason => {
    if (rooms.has(roomCode)) cleanup(roomCode, reason);
  };
  room.host.once('close', () => closePair('host disconnected'));
  room.player.once('close', () => closePair('player disconnected'));
  room.host.once('error', err => console.error(`Host error ${roomCode}: ${err.message}`));
  room.player.once('error', err => console.error(`Player error ${roomCode}: ${err.message}`));
}

function register(ws, message) {
  const ip = clientIp(ws);
  const role = message && message.role;
  const channel = String(message && message.channel || 'minecraft');
  const protocol = Number(message && message.protocol || 0);
  const uid = String(message && message.uid || '').trim();
  const modSha256 = String(message && message.modSha256 || '').trim().toLowerCase();
  const roomCode = String(message && message.room || '').trim().toUpperCase();

  if (protocol !== REQUIRED_PROTOCOL) {
    registerViolation(ip);
    sendJson(ws, { type: 'error', code: 'UNSUPPORTED_PROTOCOL' });
    ws.close();
    return;
  }
  if (!integrityAllowed(modSha256)) {
    registerViolation(ip);
    sendJson(ws, { type: 'error', code: 'MOD_INTEGRITY_REJECTED' });
    ws.close();
    return;
  }
  if (role !== 'host' && role !== 'player') {
    registerViolation(ip);
    sendJson(ws, { type: 'error', code: 'INVALID_ROLE' });
    ws.close();
    return;
  }
  if (!roomCode || !/^[A-Z0-9_-]{4,64}$/.test(roomCode)) {
    registerViolation(ip);
    sendJson(ws, { type: 'error', code: 'INVALID_ROOM' });
    ws.close();
    return;
  }
  if (!uid || uid.length > 256) {
    registerViolation(ip);
    sendJson(ws, { type: 'error', code: 'INVALID_UID' });
    ws.close();
    return;
  }
  if (role === 'player' && !rooms.has(roomCode) && activePairs() >= MAX_PAIRS) {
    sendJson(ws, { type: 'full' });
    ws.close();
    return;
  }

  let room = rooms.get(roomCode);
  if (!room) {
    room = { host: null, player: null, voiceHost: null, voicePlayer: null, createdAt: Date.now() };
    rooms.set(roomCode, room);
  }

  if (channel === 'voice') {
    const key = role === 'host' ? 'voiceHost' : 'voicePlayer';
    const peer = role === 'host' ? room.host : room.player;
    if (!peer || peer.readyState !== peer.OPEN) { sendJson(ws,{type:'error',code:'MINECRAFT_CHANNEL_REQUIRED'}); ws.close(); return; }
    if (peer.uid !== uid) { registerViolation(ip); sendJson(ws,{type:'error',code:'VOICE_IDENTITY_MISMATCH'}); ws.close(); return; }
    if (room[key]) { sendJson(ws,{type:'error',code:'VOICE_ALREADY_CONNECTED'}); ws.close(); return; }
    room[key]=ws; ws.roomCode=roomCode; ws.role=role; ws.channel='voice'; ws.uid=uid;
    sendJson(ws,{type:'voice-waiting',room:roomCode,protocol:REQUIRED_PROTOCOL});
    if(room.voiceHost&&room.voicePlayer){
      sendJson(room.voiceHost,{type:'voice-connected',protocol:REQUIRED_PROTOCOL});
      sendJson(room.voicePlayer,{type:'voice-connected',protocol:REQUIRED_PROTOCOL});
      room.voiceHost.on('message',(d,b)=>{if(room.voicePlayer.readyState===room.voicePlayer.OPEN)room.voicePlayer.send(d,{binary:b});});
      room.voicePlayer.on('message',(d,b)=>{if(room.voiceHost.readyState===room.voiceHost.OPEN)room.voiceHost.send(d,{binary:b});});
      const closeVoice=reason=>{if(rooms.has(roomCode)) cleanup(roomCode,reason);};
      room.voiceHost.once('close',()=>closeVoice('voice host disconnected'));
      room.voicePlayer.once('close',()=>closeVoice('voice player disconnected'));
    }
    return;
  }
  if (role === 'host') {
    if (room.host && room.host.readyState === room.host.OPEN) {
      registerViolation(ip);
      sendJson(ws, { type: 'error', code: 'HOST_ALREADY_CONNECTED' });
      ws.close();
      return;
    }
    room.host = ws;
    ws.roomCode = roomCode;
    ws.role = 'host';
    ws.uid = uid;
    ws.modSha256 = modSha256;
    sendJson(ws, { type: 'waiting', room: roomCode, protocol: REQUIRED_PROTOCOL });
    console.log(`Host registered: ${roomCode}`);
    if (room.player) startPair(roomCode, room);
    return;
  }

  if (!room.host || room.host.readyState !== room.host.OPEN) {
    sendJson(ws, { type: 'error', code: 'HOST_NOT_CONNECTED' });
    ws.close();
    rooms.delete(roomCode);
    return;
  }
  if (room.player && room.player.readyState === room.player.OPEN) {
    registerViolation(ip);
    sendJson(ws, { type: 'error', code: 'ROOM_BUSY' });
    ws.close();
    return;
  }

  room.player = ws;
  ws.roomCode = roomCode;
  ws.role = 'player';
  ws.uid = uid;
  ws.modSha256 = modSha256;
  console.log(`Player joined: ${roomCode}`);
  startPair(roomCode, room);
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ ok: true, service: 'worldgate-relay', protocol: REQUIRED_PROTOCOL }));
    return;
  }
  res.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' });
  res.end('WorldGate relay');
});

const wss = new WebSocketServer({
  server,
  path: '/ws',
  maxPayload: MAX_PAYLOAD,
  perMessageDeflate: false
});

wss.on('connection', (ws) => {
  const ip = clientIp(ws);
  if (!allowConnection(ip)) {
    sendJson(ws, { type: 'error', code: 'RATE_LIMITED' });
    ws.close();
    return;
  }

  let registered = false;
  const timer = setTimeout(() => {
    if (!registered) {
      registerViolation(ip);
      sendJson(ws, { type: 'error', code: 'HANDSHAKE_TIMEOUT' });
      ws.close();
    }
  }, HANDSHAKE_TIMEOUT);

  ws.once('message', (data, isBinary) => {
    if (isBinary) {
      registerViolation(ip);
      sendJson(ws, { type: 'error', code: 'HANDSHAKE_MUST_BE_TEXT' });
      ws.close();
      return;
    }
    let message;
    try {
      message = JSON.parse(data.toString('utf8'));
    } catch {
      registerViolation(ip);
      sendJson(ws, { type: 'error', code: 'BAD_HANDSHAKE' });
      ws.close();
      return;
    }
    registered = true;
    clearTimeout(timer);
    register(ws, message);
  });

  ws.on('error', err => console.error(`WebSocket error: ${err.message}`));
});

setInterval(() => {
  for (const ws of wss.clients) if (ws.readyState === ws.OPEN) ws.ping();
  const now = Date.now();
  for (const [ip, state] of ipState) {
    if (state.bannedUntil < now && now - state.windowStart > 60 * 60 * 1000) ipState.delete(ip);
  }
  for (const [roomCode, room] of rooms) {
    if (now - Number(room.createdAt || now) > ROOM_TTL_MS) cleanup(roomCode, 'ttl');
  }
}, 30000);

server.listen(PORT, '0.0.0.0', () => {
  console.log(`WorldGate WebSocket relay listening on ${PORT}`);
  console.log(`Maximum simultaneous pairs: ${MAX_PAIRS}`);
  console.log(`Protocol: ${REQUIRED_PROTOCOL}; integrity enforcement: ${ALLOWED_MOD_SHA256 ? 'enabled' : 'optional'}`);
});
