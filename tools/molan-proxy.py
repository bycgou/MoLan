#!/usr/bin/env python3
"""MoLan 演示转发器（Windows / Linux 均可跑，仅用标准库）

学校网关要求 /<用户名>/ 下的每个请求都带 Authorization 头，而浏览器地址栏导航
无法携带自定义请求头，所以直接打开 https://51wxr.com/xingpengbo/ 一定是 401。

本脚本在本机监听一个端口，把收到的请求加上 Authorization 头后转发到网关，
再把响应原样返回。于是浏览器访问 http://127.0.0.1:21060/ 就能正常用演示系统。

用法：
    python molan-proxy.py            # 监听 127.0.0.1:21060
    python molan-proxy.py 8080       # 指定端口

只读转发，不落盘、不改数据。
"""
import http.client
import os
import socketserver
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UPSTREAM_HOST = os.environ.get("MOLAN_PROXY_HOST", "51wxr.com")
UPSTREAM_PREFIX = os.environ.get("MOLAN_PROXY_PREFIX", "/xingpengbo")
AUTH_HEADER = os.environ.get("MOLAN_PROXY_AUTH", "Bearer demo")
LOCAL_HOST = os.environ.get("MOLAN_PROXY_BIND", "127.0.0.1")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _forward(self, method):
        path = self.path
        if not path.startswith("/"):
            path = "/" + path
        upstream_path = UPSTREAM_PREFIX + path

        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None

        headers = {}
        for key in ("Content-Type", "Accept", "Accept-Language", "Cookie", "User-Agent"):
            value = self.headers.get(key)
            if value:
                headers[key] = value
        headers["Authorization"] = AUTH_HEADER
        headers["Accept-Encoding"] = "identity"
        headers["Host"] = UPSTREAM_HOST

        conn = http.client.HTTPSConnection(UPSTREAM_HOST, 443, timeout=300)
        try:
            conn.request(method, upstream_path, body=body, headers=headers)
            resp = conn.getresponse()

            self.send_response(resp.status)
            for key in ("Content-Type", "Cache-Control", "Content-Encoding"):
                value = resp.getheader(key)
                if value:
                    self.send_header(key, value)
            location = resp.getheader("Location")
            if location:
                # 上游的跳转地址要改写成本地地址，否则浏览器会跳到网关去（没有鉴权头 → 401）
                location = location.replace(
                    "https://%s%s" % (UPSTREAM_HOST, UPSTREAM_PREFIX), "")
                location = location.replace("https://%s" % UPSTREAM_HOST, "")
                self.send_header("Location", location)
            if resp.chunked:
                self.send_header("Transfer-Encoding", "chunked")
                self.end_headers()
                while True:
                    chunk = resp.read(8192)
                    if not chunk:
                        break
                    self.wfile.write(b"%x\r\n" % len(chunk) + chunk + b"\r\n")
                self.wfile.write(b"0\r\n\r\n")
            else:
                payload = resp.read()
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
        except Exception as exc:  # 网络异常直接回 502，避免脚本静默退出
            self.send_response(502)
            self.send_header("Content-Type", "application/json;charset=UTF-8")
            self.end_headers()
            self.wfile.write(('{"code":502,"message":"转发失败: %s"}' % exc).encode("utf-8"))
        finally:
            conn.close()

    def do_GET(self):
        self._forward("GET")

    def do_POST(self):
        self._forward("POST")

    def do_PUT(self):
        self._forward("PUT")

    def do_DELETE(self):
        self._forward("DELETE")

    def do_OPTIONS(self):
        self._forward("OPTIONS")

    def log_message(self, fmt, *args):
        sys.stderr.write("[proxy] %s\n" % (fmt % args))


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 21060
    server = Server((LOCAL_HOST, port), Handler)
    print("MoLan 演示转发器已启动：http://%s:%d/  ->  https://%s%s/" % (
        LOCAL_HOST, port, UPSTREAM_HOST, UPSTREAM_PREFIX))
    print("按 Ctrl+C 退出")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")


if __name__ == "__main__":
    main()
