'use strict';

const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT || 10000);
const MAX_PAIRS = Number(process.env.MAX_PAIRS || 25);
const HANDSHAKE_TIMEOUT = 10000;

const rooms = new Map();

function sendJson(ws, value) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(value));
  }
}

function cleanup(roomCode, reason) {
  const room = rooms.get(roomCode);
  if (!room) return;

  if (room.host && room.host.readyState === room.host.OPEN) {
    room.host.close();
  }

  if (room.player && room.player.readyState === room.player.OPEN) {
    room.player.close();
  }

  rooms.delete(roomCode);
  console.log(`Room closed: ${roomCode} (${reason})`);
}

function activePairs() {
  let count = 0;

  for (const room of rooms.values()) {
    if (room.host && room.player) {
      count++;
    }
  }

  return count;
}

function startPair(roomCode, room) {
  if (!room.host || !room.player) return;

  sendJson(room.host, { type: 'connected' });
  sendJson(room.player, { type: 'connected' });

  console.log(`WebSocket forwarding started: ${roomCode}`);

  room.host.on('message', (data, isBinary) => {
    if (room.player && room.player.readyState === room.player.OPEN) {
      room.player.send(data, { binary: isBinary });
    }
  });

  room.player.on('message', (data, isBinary) => {
    if (room.host && room.host.readyState === room.host.OPEN) {
      room.host.send(data, { binary: isBinary });
    }
  });

  const closePair = (reason) => {
    if (!rooms.has(roomCode)) return;
    cleanup(roomCode, reason);
  };

  room.host.once('close', () => closePair('host disconnected'));
  room.player.once('close', () => closePair('player disconnected'));

  room.host.once('error', (err) => {
    console.error(`Host error ${roomCode}: ${err.message}`);
  });

  room.player.once('error', (err) => {
    console.error(`Player error ${roomCode}: ${err.message}`);
  });
}

function register(ws, message) {
  const role = message && message.role;
  const roomCode = String(
    message && message.room || ''
  ).trim().toUpperCase();

  if (role !== 'host' && role !== 'player') {
    sendJson(ws, {
      type: 'error',
      code: 'INVALID_ROLE'
    });
    ws.close();
    return;
  }

  if (!roomCode || roomCode.length > 64) {
    sendJson(ws, {
      type: 'error',
      code: 'INVALID_ROOM'
    });
    ws.close();
    return;
  }

  if (
    role === 'player' &&
    !rooms.has(roomCode) &&
    activePairs() >= MAX_PAIRS
  ) {
    sendJson(ws, { type: 'full' });
    ws.close();
    return;
  }

  let room = rooms.get(roomCode);

  if (!room) {
    room = {
      host: null,
      player: null
    };

    rooms.set(roomCode, room);
  }

  if (role === 'host') {
    if (room.host && room.host.readyState === room.host.OPEN) {
      sendJson(ws, {
        type: 'error',
        code: 'HOST_ALREADY_CONNECTED'
      });
      ws.close();
      return;
    }

    room.host = ws;
    ws.roomCode = roomCode;
    ws.role = 'host';

    sendJson(ws, {
      type: 'waiting',
      room: roomCode
    });

    console.log(`Host registered: ${roomCode}`);

    if (room.player) {
      startPair(roomCode, room);
    }

    return;
  }

  if (!room.host || room.host.readyState !== room.host.OPEN) {
    sendJson(ws, {
      type: 'error',
      code: 'HOST_NOT_CONNECTED'
    });

    ws.close();
    rooms.delete(roomCode);
    return;
  }

  if (room.player && room.player.readyState === room.player.OPEN) {
    sendJson(ws, {
      type: 'error',
      code: 'ROOM_BUSY'
    });

    ws.close();
    return;
  }

  room.player = ws;
  ws.roomCode = roomCode;
  ws.role = 'player';

  console.log(`Player joined: ${roomCode}`);

  startPair(roomCode, room);
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, {
      'content-type': 'text/plain; charset=utf-8'
    });

    res.end('WorldGate relay OK');
    return;
  }

  res.writeHead(200, {
    'content-type': 'text/plain; charset=utf-8'
  });

  res.end('WorldGate relay');
});

const wss = new WebSocketServer({
  server,
  path: '/ws'
});

wss.on('connection', (ws) => {
  let registered = false;

  const timer = setTimeout(() => {
    if (!registered) {
      sendJson(ws, {
        type: 'error',
        code: 'HANDSHAKE_TIMEOUT'
      });

      ws.close();
    }
  }, HANDSHAKE_TIMEOUT);

  ws.once('message', (data, isBinary) => {
    if (isBinary) {
      sendJson(ws, {
        type: 'error',
        code: 'HANDSHAKE_MUST_BE_TEXT'
      });

      ws.close();
      return;
    }

    let message;

    try {
      message = JSON.parse(data.toString('utf8'));
    } catch {
      sendJson(ws, {
        type: 'error',
        code: 'BAD_HANDSHAKE'
      });

      ws.close();
      return;
    }

    registered = true;
    clearTimeout(timer);

    register(ws, message);
  });

  ws.on('error', (err) => {
    console.error(`WebSocket error: ${err.message}`);
  });
});

setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.readyState === ws.OPEN) {
      ws.ping();
    }
  }
}, 30000);

server.listen(PORT, '0.0.0.0', () => {
  console.log(`WorldGate WebSocket relay listening on ${PORT}`);
  console.log(`Maximum simultaneous pairs: ${MAX_PAIRS}`);
});
