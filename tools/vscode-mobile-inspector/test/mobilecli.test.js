const test = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const { MobileCli, RpcError, isLoopback } = require('../out/mobilecli.js');

/** A tiny mobilecli JSON-RPC server: handlers by method name; records every call. */
function fakeServer(handlers) {
    const calls = [];
    const server = http.createServer((req, res) => {
        let body = '';
        req.on('data', c => (body += c));
        req.on('end', () => {
            const msg = JSON.parse(body);
            calls.push({ path: req.url, method: msg.method, params: msg.params });
            const h = handlers[msg.method];
            const reply = !h ? { error: { code: -32601, message: 'Method not found' } }
                : (() => { try { return { result: h(msg.params) }; } catch (e) { return { error: { code: -32000, message: 'Server error', data: e.message } }; } })();
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ jsonrpc: '2.0', id: msg.id, ...reply }));
        });
    });
    return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve({ server, calls, url: `http://127.0.0.1:${server.address().port}` })));
}

test('reads devices, screen and tree, and acts on the device', async () => {
    const tree = [{ type: 'Window', ref: '@e1', rect: { x: 0, y: 0, width: 390, height: 844 }, children: [{ type: 'Button', ref: '@e2', label: 'Go' }] }];
    const { server, calls, url } = await fakeServer({
        'server.info': () => ({ version: '1.0.20' }),
        'devices.list': () => ({ devices: [{ id: 'sim-1', name: 'iPhone 17', platform: 'ios', type: 'simulator', state: 'online' }] }),
        'device.info': () => ({ device: { id: 'sim-1', screenSize: { width: 390, height: 844, scale: 3 } } }),
        'device.dump.ui': p => (p.format === 'text' ? { text: '@e1 [Window]\n  @e2 [Button] "Go"' } : { elements: tree }),
        'device.screenshot': () => ({ format: 'png', data: 'data:image/png;base64,AAAA' }),
        'device.io.tap': () => null,
        'device.io.text': () => null,
        'device.io.button': () => null,
        'device.io.swipe': () => null,
    });
    try {
        const cli = new MobileCli(url + '/', 'mobilecli', false);
        assert.strictEqual(await cli.ensureServer(), '1.0.20');
        assert.deepStrictEqual((await cli.devices()).map(d => d.id), ['sim-1']);
        assert.deepStrictEqual(await cli.screenSize('sim-1'), { width: 390, height: 844, scale: 3 });
        assert.strictEqual((await cli.dump('sim-1'))[0].children[0].ref, '@e2');
        assert.match(await cli.snapshotText('sim-1'), /@e2 \[Button\]/);
        assert.strictEqual(await cli.screenshot('sim-1'), 'data:image/png;base64,AAAA');
        await cli.tap('sim-1', 10.4, 20.6);
        await cli.typeText('sim-1', 'ada');
        await cli.pressButton('sim-1', 'HOME');
        await cli.swipe('sim-1', 1, 2, 3, 4);

        assert.ok(calls.every(c => c.path === '/rpc'), 'JSON-RPC at /rpc');
        assert.deepStrictEqual(calls.find(c => c.method === 'device.io.tap').params, { deviceId: 'sim-1', x: 10, y: 21 });
        assert.deepStrictEqual(calls.find(c => c.method === 'device.io.text').params, { deviceId: 'sim-1', text: 'ada' });
    } finally {
        server.close();
    }
});

test('server errors keep mobilecli\'s message; an unreachable server is reported as such', async () => {
    const { server, url } = await fakeServer({ 'device.dump.ui': () => { throw new Error('agent not installed'); } });
    try {
        const cli = new MobileCli(url, 'mobilecli', false);
        await assert.rejects(cli.dump('x'), e => e instanceof RpcError && /agent not installed/.test(e.message) && !e.unreachable);
    } finally {
        server.close();
    }
    const down = new MobileCli('http://127.0.0.1:1', 'mobilecli', false);
    await assert.rejects(down.ensureServer(), e => e instanceof RpcError && e.unreachable);
});

test('auto-start only for local servers and reports a missing mobilecli', async () => {
    assert.ok(isLoopback('http://localhost:12000'));
    assert.ok(isLoopback('http://127.0.0.1:12000'));
    assert.ok(!isLoopback('https://lab.example.com'));
    const cli = new MobileCli('http://127.0.0.1:1', '/nonexistent/mobilecli', true);
    await assert.rejects(cli.ensureServer(2000), e => /Could not run|did not answer/.test(e.message));
    cli.dispose();
});
