package com.wavestream.plugins.js

/**
 * JS polyfill builder — mirrors NuvioMobile's `com.nuvio.app.features.plugins.runtime.js.JsBindings`.
 *
 * Builds the JS polyfill code that's evaluated before the user's plugin code runs.
 * These polyfills implement browser APIs that plugin authors expect:
 *   - `fetch` (delegates to native `__native_fetch`)
 *   - `URL` / `URLSearchParams` (delegates to native `__parse_url`)
 *   - `atob` / `btoa`
 *   - `console.log/error/warn/info/debug` (delegates to native logger)
 *   - `AbortController` / `AbortSignal`
 *   - `TextEncoder` / `TextDecoder`
 *   - `require` (CommonJS shim)
 *   - Array/Object/String polyfills
 */
object JsBindings {
    fun buildPolyfillCode(scraperIdJson: String, settingsJson: String): String = """
        globalThis.SCRAPER_ID = $scraperIdJson;
        globalThis.SCRAPER_SETTINGS = $settingsJson;
        if (typeof globalThis.global === 'undefined') globalThis.global = globalThis;
        if (typeof globalThis.window === 'undefined') globalThis.window = globalThis;
        if (typeof globalThis.self === 'undefined') globalThis.self = globalThis;

        ${fetchPolyfill()}
        ${abortControllerPolyfill()}
        ${base64Polyfill()}
        ${urlPolyfill()}
        ${consolePolyfill()}
        ${promisePolyfill()}
        ${requirePolyfill()}
        ${textEncoderPolyfill()}
    """.trimIndent()

    private fun fetchPolyfill() = """
        function __normalize_fetch_headers(headers) {
            var out = {};
            if (!headers) return out;
            if (typeof headers.forEach === 'function') {
                headers.forEach(function(value, key) { out[key] = String(value); });
                return out;
            }
            if (Array.isArray(headers)) {
                headers.forEach(function(pair) {
                    if (pair && pair.length >= 2) out[pair[0]] = String(pair[1]);
                });
                return out;
            }
            Object.keys(headers).forEach(function(key) { out[key] = String(headers[key]); });
            return out;
        }

        var fetch = function(url, options) {
            options = options || {};
            var method = (options.method || 'GET').toUpperCase();
            var headers = __normalize_fetch_headers(options.headers);
            var body = options.body || '';
            var followRedirects = options.redirect !== 'manual';
            var result = __native_fetch(url, method, JSON.stringify(headers), body, followRedirects);
            var parsed = JSON.parse(result);
            return {
                ok: parsed.ok,
                status: parsed.status,
                statusText: parsed.statusText,
                url: parsed.url,
                headers: {
                    get: function(name) { return parsed.headers[name.toLowerCase()] || null; }
                },
                text: function() { return Promise.resolve(parsed.body); },
                json: function() {
                    try {
                        if (!parsed.body) return Promise.resolve(null);
                        return Promise.resolve(JSON.parse(parsed.body));
                    } catch (e) { return Promise.resolve(null); }
                }
            };
        };
    """.trimIndent()

    private fun abortControllerPolyfill() = """
        if (typeof AbortSignal === 'undefined') {
            var AbortSignal = function() { this.aborted = false; this.reason = undefined; this._listeners = []; };
            AbortSignal.prototype.addEventListener = function(type, listener) {
                if (type !== 'abort' || typeof listener !== 'function') return;
                this._listeners.push(listener);
            };
            AbortSignal.prototype.removeEventListener = function(type, listener) {
                if (type !== 'abort') return;
                this._listeners = this._listeners.filter(function(l) { return l !== listener; });
            };
            AbortSignal.prototype.dispatchEvent = function(event) {
                if (!event || event.type !== 'abort') return true;
                for (var i = 0; i < this._listeners.length; i++) {
                    try { this._listeners[i].call(this, event); } catch (e) {}
                }
                return true;
            };
            globalThis.AbortSignal = AbortSignal;
        }
        if (typeof AbortController === 'undefined') {
            var AbortController = function() { this.signal = new AbortSignal(); };
            AbortController.prototype.abort = function(reason) {
                if (this.signal.aborted) return;
                this.signal.aborted = true;
                this.signal.reason = reason;
                this.signal.dispatchEvent({ type: 'abort' });
            };
            globalThis.AbortController = AbortController;
        }
    """.trimIndent()

    private fun base64Polyfill() = """
        if (typeof atob === 'undefined') {
            globalThis.atob = function(input) {
                var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
                var str = String(input).replace(/=+$/, '');
                if (str.length % 4 === 1) throw new Error('InvalidCharacterError');
                var output = '';
                var bc = 0, bs, buffer, idx = 0;
                while ((buffer = str.charAt(idx++))) {
                    buffer = chars.indexOf(buffer);
                    if (buffer === -1) continue;
                    bs = bc % 4 ? bs * 64 + buffer : buffer;
                    if (bc++ % 4) output += String.fromCharCode(255 & (bs >> ((-2 * bc) & 6)));
                }
                return output;
            };
        }
        if (typeof btoa === 'undefined') {
            globalThis.btoa = function(input) {
                var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
                var str = String(input);
                var output = '';
                for (var block, charCode, idx = 0, map = chars;
                     str.charAt(idx | 0) || (map = '=', idx % 1);
                     output += map.charAt(63 & (block >> (8 - (idx % 1) * 8)))) {
                    charCode = str.charCodeAt(idx += 3 / 4);
                    if (charCode > 0xFF) throw new Error('InvalidCharacterError');
                    block = (block << 8) | charCode;
                }
                return output;
            };
        }
    """.trimIndent()

    private fun urlPolyfill() = """
        var __native_parse_url = typeof __parse_url !== 'undefined' ? __parse_url :
            function(u) { return JSON.stringify({protocol:'',host:'',hostname:'',port:'',pathname:'/',search:'',hash:''}); };

        var URL = function(urlString, base) {
            var fullUrl = urlString;
            if (base && !/^https?:\/\//i.test(urlString)) {
                var b = typeof base === 'string' ? base : base.href;
                if (urlString.charAt(0) === '/') {
                    var m = b.match(/^(https?:\/\/[^\/]+)/);
                    fullUrl = m ? m[1] + urlString : urlString;
                } else {
                    fullUrl = b.replace(/\/[^\/]*$/, '/') + urlString;
                }
            }
            var parsed = __native_parse_url(fullUrl);
            var data = JSON.parse(parsed);
            this.href = fullUrl;
            this.protocol = data.protocol;
            this.host = data.host;
            this.hostname = data.hostname;
            this.port = data.port;
            this.pathname = data.pathname;
            this.search = data.search;
            this.hash = data.hash;
            this.origin = data.protocol + '//' + data.host;
            this.searchParams = new URLSearchParams(data.search || '');
        };
        URL.prototype.toString = function() { return this.href; };

        var URLSearchParams = function(init) {
            this._params = {};
            var self = this;
            if (init && typeof init === 'object' && !Array.isArray(init)) {
                Object.keys(init).forEach(function(key) { self._params[key] = String(init[key]); });
            } else if (typeof init === 'string') {
                init.replace(/^\?/, '').split('&').forEach(function(pair) {
                    var parts = pair.split('=');
                    if (parts[0]) self._params[decodeURIComponent(parts[0])] = decodeURIComponent(parts[1] || '');
                });
            }
        };
        URLSearchParams.prototype.toString = function() {
            var self = this;
            return Object.keys(this._params).map(function(key) {
                return encodeURIComponent(key) + '=' + encodeURIComponent(self._params[key]);
            }).join('&');
        };
        URLSearchParams.prototype.get = function(key) { return this._params[key] || null; };
        URLSearchParams.prototype.set = function(key, value) { this._params[key] = String(value); };
        URLSearchParams.prototype.has = function(key) { return key in this._params; };
    """.trimIndent()

    private fun consolePolyfill() = """
        if (typeof console === 'undefined') {
            globalThis.console = {
                log: function() { __console_log('log', Array.prototype.slice.call(arguments).join(' ')); },
                error: function() { __console_error('error', Array.prototype.slice.call(arguments).join(' ')); },
                warn: function() { __console_warn('warn', Array.prototype.slice.call(arguments).join(' ')); },
                info: function() { __console_info('info', Array.prototype.slice.call(arguments).join(' ')); },
                debug: function() { __console_debug('debug', Array.prototype.slice.call(arguments).join(' ')); }
            };
        }
    """.trimIndent()

    /**
     * Minimal Promise polyfill — Rhino 1.7.15 supports Promises natively in ES6 mode,
     * but some plugins may run in interpreted mode. This shim handles basic cases.
     */
    private fun promisePolyfill() = """
        if (typeof Promise === 'undefined') {
            globalThis.Promise = function(executor) {
                var self = this;
                self.state = 'pending';
                self.value = undefined;
                self._callbacks = [];
                function resolve(value) {
                    if (self.state !== 'pending') return;
                    self.state = 'resolved';
                    self.value = value;
                    self._callbacks.forEach(function(cb) { cb.onResolved(value); });
                }
                function reject(reason) {
                    if (self.state !== 'pending') return;
                    self.state = 'rejected';
                    self.value = reason;
                    self._callbacks.forEach(function(cb) { cb.onRejected(reason); });
                }
                try { executor(resolve, reject); } catch (e) { reject(e); }
            };
            globalThis.Promise.prototype.then = function(onResolved, onRejected) {
                var self = this;
                return new Promise(function(resolve, reject) {
                    self._callbacks.push({
                        onResolved: function(value) {
                            try { resolve(onResolved ? onResolved(value) : value); }
                            catch (e) { reject(e); }
                        },
                        onRejected: function(reason) {
                            try { resolve(onRejected ? onRejected(reason) : reason); }
                            catch (e) { reject(e); }
                        }
                    });
                });
            };
            globalThis.Promise.prototype.catch = function(onRejected) {
                return this.then(null, onRejected);
            };
            globalThis.Promise.resolve = function(value) { return new Promise(function(r) { r(value); }); };
            globalThis.Promise.reject = function(reason) { return new Promise(function(_, r) { r(reason); }); };
            globalThis.Promise.all = function(promises) {
                return new Promise(function(resolve, reject) {
                    var results = [];
                    var remaining = promises.length;
                    if (remaining === 0) { resolve([]); return; }
                    promises.forEach(function(p, i) {
                        Promise.resolve(p).then(function(v) {
                            results[i] = v;
                            if (--remaining === 0) resolve(results);
                        }, reject);
                    });
                });
            };
        }
    """.trimIndent()

    private fun requirePolyfill() = """
        if (typeof require === 'undefined') {
            var __module_cache = {};
            globalThis.require = function(name) {
                if (name in __module_cache) return __module_cache[name];
                throw new Error("Module not found: " + name);
            };
        }
    """.trimIndent()

    private fun textEncoderPolyfill() = """
        if (typeof TextEncoder === 'undefined') {
            var TextEncoder = function() {};
            TextEncoder.prototype.encode = function(str) {
                var bytes = [];
                for (var i = 0; i < str.length; i++) {
                    var c = str.charCodeAt(i);
                    if (c < 128) bytes.push(c);
                    else if (c < 2048) {
                        bytes.push(192 | (c >> 6));
                        bytes.push(128 | (c & 63));
                    } else {
                        bytes.push(224 | (c >> 12));
                        bytes.push(128 | ((c >> 6) & 63));
                        bytes.push(128 | (c & 63));
                    }
                }
                return bytes;
            };
            globalThis.TextEncoder = TextEncoder;
        }
        if (typeof TextDecoder === 'undefined') {
            var TextDecoder = function() {};
            TextDecoder.prototype.decode = function(bytes) {
                var str = '';
                for (var i = 0; i < bytes.length; ) {
                    var b = bytes[i++];
                    if (b < 128) str += String.fromCharCode(b);
                    else if (b < 224) {
                        var b2 = bytes[i++];
                        str += String.fromCharCode(((b & 31) << 6) | (b2 & 63));
                    } else {
                        var b2 = bytes[i++];
                        var b3 = bytes[i++];
                        str += String.fromCharCode(((b & 15) << 12) | ((b2 & 63) << 6) | (b3 & 63));
                    }
                }
                return str;
            };
            globalThis.TextDecoder = TextDecoder;
        }
    """.trimIndent()
}
