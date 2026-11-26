'use strict';
// Stand-in for an OpenAI-compatible model server, for testing the document assistant.
const http = require('node:http');
const PORT = Number(process.env.PORT || 9100);
http.createServer((req, res) => {
  let raw = '';
  req.on('data', (c) => (raw += c));
  req.on('end', () => {
    const send = (s, b) => { res.writeHead(s, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(b)); };
    if (req.method !== 'POST' || req.url !== '/v1/chat/completions') return send(404, {});
    if (req.headers.authorization !== 'Bearer test-llm-key') return send(401, { error: 'bad key' });
    const msgs = JSON.parse(raw).messages;
    const user = msgs[msgs.length - 1].content;
    const doc = (user.match(/<document>\n([\s\S]*?)\n<\/document>/) || [])[1] || '';
    const q = (user.match(/Question: (.*)$/m) || [])[1];
    send(200, { choices: [{ message: { content: `MOCK ${q ? 'ANSWER to "' + q + '"' : 'SUMMARY'} of: ${doc}` } }] });
  });
}).listen(PORT, () => console.log('mock llm on ' + PORT));
