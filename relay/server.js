'use strict';

const net = require('net');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 3000);

// Maximum simultaneous Minecraft connections.
// A pair = 1 host socket + 1 player socket.
const MAX_PAIRS = Number(process.env.MAX_PAIRS || 25);

const HANDSHAKE_TIMEOUT = 10000;
const MAX_HANDSHAKE_BYTES = 4096;

// roomCode -> { host, player, createdAt }
const rooms = new Map();

function makeRoomId() {
  return crypto.randomBytes(16).toString('hex');
}

function sendLine(socket, object) {
  socket.write(JSON.stringify(object) + '\n');
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

function cleanupRoom(roomCode) {
  const room = rooms.get(roomCode);

  if (!room) {
    return;
  }

  if (room.host && !room.host.destroyed) {
    room.host.destroy();
  }

  if (room.player && !room.player.destroyed) {
    room.player.destroy();
  }

  rooms.delete(roomCode);

  console.log(`Room closed: ${roomCode}`);
}

function startForwarding(roomCode, room) {
  const host = room.host;
  const player = room.player;

  if (!host || !player) {
    return;
  }

  console.log(`TCP forwarding started: ${roomCode}`);

  sendLine(host, {
    type: 'connected'
  });

  sendLine(player, {
    type: 'connected'
  });

  // Everything after the handshake is now Minecraft TCP traffic.
  host.pipe(player);
  player.pipe(host);

  let closed = false;

  function closePair(reason) {
    if (closed) {
      return;
    }

    closed = true;

    console.log(`TCP forwarding closed: ${roomCode} (${reason})`);

    host.unpipe(player);
    player.unpipe(host);

    if (!host.destroyed) {
      host.destroy();
    }

    if (!player.destroyed) {
      player.destroy();
    }

    rooms.delete(roomCode);
  }

  host.on('close', () => closePair('host disconnected'));
  player.on('close', () => closePair('player disconnected'));

  host.on('error', (err) => {
    console.error(`Host socket error ${roomCode}: ${err.message}`);
    closePair('host error');
  });

  player.on('error', (err) => {
    console.error(`Player socket error ${roomCode}: ${err.message}`);
    closePair('player error');
  });
}

function handleHandshake(socket, line, remaining) {
  let message;

  try {
    message = JSON.parse(line);
  } catch {
    sendLine(socket, {
      type: 'error',
      code: 'BAD_HANDSHAKE'
    });

    socket.destroy();
    return;
  }

  if (!message || typeof message !== 'object') {
    sendLine(socket, {
      type: 'error',
      code: 'BAD_HANDSHAKE'
    });

    socket.destroy();
    return;
  }

  const role = message.role;
  const roomCode = String(message.room || '').trim().toUpperCase();

  if (role !== 'host' && role !== 'player') {
    sendLine(socket, {
      type: 'error',
      code: 'INVALID_ROLE'
    });

    socket.destroy();
    return;
  }

  if (!roomCode || roomCode.length > 64) {
    sendLine(socket, {
      type: 'error',
      code: 'INVALID_ROOM'
    });

    socket.destroy();
    return;
  }

  // Count only rooms that are already paired.
  // This gives the player an immediate FULL response instead of queueing.
  if (role === 'player' && !rooms.has(roomCode) && activePairs() >= MAX_PAIRS) {
    sendLine(socket, {
      type: 'full'
    });

    socket.end();
    return;
  }

  let room = rooms.get(roomCode);

  if (!room) {
    room = {
      host: null,
      player: null,
      createdAt: Date.now()
    };

    rooms.set(roomCode, room);
  }

  if (role === 'host') {
    if (room.host && !room.host.destroyed) {
      sendLine(socket, {
        type: 'error',
        code: 'HOST_ALREADY_CONNECTED'
      });

      socket.destroy();
      return;
    }

    room.host = socket;

    sendLine(socket, {
      type: 'waiting',
      room: roomCode
    });

    console.log(`Host registered: ${roomCode}`);

    if (room.player && !room.player.destroyed) {
      startForwarding(roomCode, room);
    }

    return;
  }

  // Player
  if (!room.host || room.host.destroyed) {
    sendLine(socket, {
      type: 'error',
      code: 'HOST_NOT_CONNECTED'
    });

    socket.end();
    rooms.delete(roomCode);
    return;
  }

  if (room.player && !room.player.destroyed) {
    sendLine(socket, {
      type: 'error',
      code: 'ROOM_BUSY'
    });

    socket.destroy();
    return;
  }

  room.player = socket;

  console.log(`Player joined: ${roomCode}`);

  if (remaining && remaining.length > 0) {
    // The player should normally send no Minecraft data before
    // receiving "connected", but preserve any bytes just in case.
    socket.unshift(remaining);
  }

  startForwarding(roomCode, room);
}

const server = net.createServer((socket) => {
  socket.setNoDelay(true);
  socket.setKeepAlive(true, 30000);

  let handshakeBuffer = Buffer.alloc(0);
  let handshakeComplete = false;

  const timer = setTimeout(() => {
    if (!handshakeComplete) {
      sendLine(socket, {
        type: 'error',
        code: 'HANDSHAKE_TIMEOUT'
      });

      socket.destroy();
    }
  }, HANDSHAKE_TIMEOUT);

  function onHandshakeData(chunk) {
    if (handshakeComplete) {
      return;
    }

    handshakeBuffer = Buffer.concat([handshakeBuffer, chunk]);

    if (handshakeBuffer.length > MAX_HANDSHAKE_BYTES) {
      clearTimeout(timer);

      sendLine(socket, {
        type: 'error',
        code: 'HANDSHAKE_TOO_LARGE'
      });

      socket.destroy();
      return;
    }

    const newlineIndex = handshakeBuffer.indexOf(0x0a);

    if (newlineIndex === -1) {
      return;
    }

    handshakeComplete = true;
    clearTimeout(timer);

    socket.removeListener('data', onHandshakeData);

    const line = handshakeBuffer
      .subarray(0, newlineIndex)
      .toString('utf8')
      .trim();

    const remaining = handshakeBuffer.subarray(newlineIndex + 1);

    handleHandshake(socket, line, remaining);
  }

  socket.on('data', onHandshakeData);

  socket.on('close', () => {
    clearTimeout(timer);

    for (const [roomCode, room] of rooms) {
      if (room.host === socket) {
        console.log(`Host disconnected before forwarding: ${roomCode}`);
        cleanupRoom(roomCode);
      } else if (room.player === socket) {
        console.log(`Player disconnected before forwarding: ${roomCode}`);
        cleanupRoom(roomCode);
      }
    }
  });

  socket.on('error', (err) => {
    console.error(`Relay socket error: ${err.message}`);
  });
});

server.on('error', (err) => {
  console.error(`Relay server error: ${err.message}`);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`WorldGate relay listening on ${PORT}`);
  console.log(`Maximum simultaneous pairs: ${MAX_PAIRS}`);
});
