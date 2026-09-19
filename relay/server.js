'use strict';

const net = require('net');
const http = require('http');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 3000);
const MAX_SESSIONS = Number(process.env.MAX_SESSIONS || 50);

const sessions = new Map();

function makeId() {
  return crypto.randomBytes(12).toString('hex');
}

const server = net.createServer((socket) => {
  socket.setNoDelay(true);

  if (sessions.size >= MAX_SESSIONS) {
    socket.end('WORLDGATE_FULL');
    return;
  }

  const id = makeId();

  sessions.set(id, {
    id,
    socket,
    createdAt: Date.now()
  });

  console.log(`Relay connection opened: ${id}`);

  socket.on('close', () => {
    sessions.delete(id);
    console.log(`Relay connection closed: ${id}`);
  });

  socket.on('error', (err) => {
    console.error(`Relay error ${id}: ${err.message}`);
    sessions.delete(id);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`WorldGate relay listening on ${PORT}`);
  console.log(`Maximum sessions: ${MAX_SESSIONS}`);
});

setInterval(() => {
  for (const [id, session] of sessions) {
    if (session.socket.destroyed) {
      sessions.delete(id);
    }
  }
}, 30000);
