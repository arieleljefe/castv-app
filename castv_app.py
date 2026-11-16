import os
import socket
import urllib.parse
import urllib.request
import xml.sax.saxutils
from http.server import HTTPServer, SimpleHTTPRequestHandler
from socketserver import ThreadingMixIn
import json
import random
import threading
import time

MOVIES_DIR = "/storage/emulated/0/Movies"
MUSIC_DIR = "/storage/emulated/0/Music"
SERVER_DIR = "/storage/emulated/0"
TV_IP = "192.168.100.14"
TV_PORT = "38400"
HTTP_PORT = 8000

def get_local_ip():
    """Detecta automáticamente la IP local del móvil en la red."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect((TV_IP, 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip

PHONE_IP = get_local_ip()

state_lock = threading.Lock()

active_playlist = []
active_index = -1
active_type = 'movies'
playing_filename = "Nada reproduciéndose"
auto_next_enabled = False
is_manual_stop = False
last_change_time = 0

browse_type = 'movies'
browse_playlist = []

HTML_PAGE = """<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CASTV 2.0 - Reproductor</title>
    <style>
        :root {
            --bg-color: #0f172a;
            --card-bg: #1e293b;
            --accent: #38bdf8;
            --accent-hover: #0ea5e9;
            --text: #f8fafc;
            --text-muted: #94a3b8;
            --danger: #ef4444;
            --warning: #f59e0b;
            --success: #22c55e;
            --off-btn: #334155;
            --whatsapp: #25d366;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background-color: var(--bg-color);
            color: var(--text);
            margin: 0;
            padding: 16px;
            display: flex;
            flex-direction: column;
            align-items: center;
        }
        .container {
            width: 100%;
            max-width: 600px;
        }
        h1 {
            text-align: center;
            font-size: 1.5rem;
            margin-bottom: 4px;
            color: var(--accent);
        }
        .subtitle {
            text-align: center;
            font-size: 0.85rem;
            color: var(--text-muted);
            margin-bottom: 16px;
        }
        .card {
            background-color: var(--card-bg);
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 16px;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.3);
        }
        .now-playing {
            background: linear-gradient(135deg, #1e293b, #334155);
            border-left: 4px solid var(--accent);
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 16px;
        }
        .now-playing-title {
            font-size: 0.75rem;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        .now-playing-file {
            font-size: 0.95rem;
            font-weight: bold;
            color: var(--accent);
            margin-top: 4px;
            word-break: break-all;
        }
        .controls-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 8px;
            margin-bottom: 8px;
        }
        .controls-grid.secondary {
            grid-template-columns: repeat(3, 1fr);
        }
        button {
            background-color: var(--accent);
            color: #0f172a;
            border: none;
            padding: 10px;
            font-size: 0.85rem;
            font-weight: bold;
            border-radius: 8px;
            cursor: pointer;
            transition: background 0.2s;
        }
        button:hover {
            background-color: var(--accent-hover);
        }
        button.danger {
            background-color: var(--danger);
            color: white;
        }
        button.warning {
            background-color: var(--warning);
            color: white;
        }
        button.success {
            background-color: var(--success);
            color: white;
        }
        button.toggle-off {
            background-color: var(--off-btn);
            color: var(--text-muted);
        }
        button.toggle-on {
            background-color: var(--success);
            color: white;
        }
        .tabs {
            display: flex;
            gap: 10px;
            margin-bottom: 12px;
        }
        .tabs button {
            flex: 1;
            background-color: #334155;
            color: var(--text);
            font-size: 0.95rem;
            padding: 12px;
        }
        .tabs button.active {
            background-color: var(--accent);
            color: #0f172a;
        }
        .file-list {
            max-height: 300px;
            overflow-y: auto;
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .file-item {
            background-color: #334155;
            padding: 12px;
            border-radius: 8px;
            cursor: pointer;
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 0.9rem;
            transition: background 0.2s;
        }
        .file-item:hover {
            background-color: #475569;
        }
        .file-item.playing {
            border: 1px solid var(--accent);
            background-color: #26334d;
        }
        #status {
            text-align: center;
            font-size: 0.85rem;
            color: var(--warning);
            margin-top: 8px;
            min-height: 20px;
        }
        .footer {
            text-align: center;
            font-size: 0.8rem;
            color: var(--text-muted);
            margin-top: 24px;
            padding-top: 12px;
            border-top: 1px solid #1e293b;
        }
        .footer span {
            color: var(--accent);
            font-weight: bold;
        }
        .whatsapp-link {
            display: inline-block;
            margin-top: 6px;
            color: var(--whatsapp);
            text-decoration: none;
            font-weight: bold;
            font-size: 0.85rem;
            transition: opacity 0.2s;
        }
        .whatsapp-link:hover {
            opacity: 0.8;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📺 CASTV 2.0</h1>
        <div class="subtitle">Reproductor inteligente para JVC VIDAA</div>

        <div class="now-playing">
            <div class="now-playing-title">Reproduciendo ahora</div>
            <div id="current-file-display" class="now-playing-file">Ningún archivo seleccionado</div>
        </div>

        <div class="card">
            <h3 style="margin-top:0; font-size:0.95rem; margin-bottom:10px;">Controles del Reproductor</h3>
            <div class="controls-grid">
                <button class="success" onclick="sendAction('prev')">⏮️ Ant</button>
                <button class="warning" onclick="sendControl('Pause')">⏸️ Pausa</button>
                <button onclick="sendControl('Play')">▶️ Play</button>
                <button class="success" onclick="sendAction('next')">⏭️ Sig</button>
            </div>
            <div class="controls-grid secondary">
                <button class="danger" onclick="sendControl('Stop')">⏹️ Stop</button>
                <button style="background-color:#6366f1; color:white;" onclick="sendAction('shuffle')">🔀 Aleatorio</button>
                <button id="btn-autonext" class="toggle-off" onclick="toggleAutoNext()">🔁 Auto: OFF</button>
            </div>
        </div>

        <div class="card">
            <div class="tabs">
                <button id="btn-movies" class="active" onclick="switchTab('movies')">🎬 Películas</button>
                <button id="btn-music" onclick="switchTab('music')">🎵 Música</button>
            </div>
            <div id="file-list" class="file-list">
                <div style="text-align:center; color: var(--text-muted);">Cargando archivos...</div>
            </div>
        </div>

        <div id="status"></div>

        <div class="footer">
            Creado por <span>Ariel De Jesus Bueno</span><br>
            <a class="whatsapp-link" href="https://wa.me/18094480441?text=Hola%20Ariel,%20vi%20tu%20app%20CASTV%202.0%20y%20está%20excelente!" target="_blank">
                💬 WhatsApp: +1 (809) 448-0441
            </a>
        </div>
    </div>

    <script>
        let currentTab = 'movies';

        function showStatus(msg) {
            const st = document.getElementById('status');
            st.innerText = msg;
            setTimeout(() => { if(st.innerText === msg) st.innerText = ''; }, 4000);
        }

        function switchTab(tab) {
            currentTab = tab;
            document.getElementById('btn-movies').classList.toggle('active', tab === 'movies');
            document.getElementById('btn-music').classList.toggle('active', tab === 'music');
            loadFiles();
        }

        function loadFiles() {
            fetch('/files?type=' + currentTab)
                .then(res => res.json())
                .then(data => {
                    renderFiles(data.files, data.current_playing);
                    if(data.playing_name) {
                        document.getElementById('current-file-display').innerText = data.playing_name;
                    }
                    const btnAuto = document.getElementById('btn-autonext');
                    if (data.auto_next) {
                        btnAuto.className = "toggle-on";
                        btnAuto.innerText = "🔁 Auto: ON";
                    } else {
                        btnAuto.className = "toggle-off";
                        btnAuto.innerText = "🔁 Auto: OFF";
                    }
                });
        }

        function renderFiles(files, currentPlayingPath) {
            const list = document.getElementById('file-list');
            list.innerHTML = '';
            if(files.length === 0) {
                list.innerHTML = '<div style="text-align:center; color: var(--text-muted);">No hay archivos encontrados.</div>';
                return;
            }
            files.forEach(f => {
                const div = document.createElement('div');
                div.className = 'file-item' + (f.path === currentPlayingPath ? ' playing' : '');
                div.innerHTML = `<span>${f.name}</span> <span style="font-size:0.8rem; color:var(--accent);">${f.path === currentPlayingPath ? '▶️ Reproduciendo' : 'Reproducir ➔'}</span>`;
                div.onclick = () => playFile(f.path);
                list.appendChild(div);
            });
        }

        function playFile(path) {
            showStatus('🚀 Enviando al TV...');
            fetch('/play?path=' + encodeURIComponent(path))
                .then(res => res.json())
                .then(data => {
                    showStatus('✅ Reproduciendo en el TV');
                    document.getElementById('current-file-display').innerText = data.playing_name;
                    loadFiles();
                });
        }

        function sendControl(action) {
            fetch('/control?action=' + action)
                .then(res => res.text())
                .then(msg => showStatus('📡 Comando: ' + action));
        }

        function sendAction(action) {
            fetch('/action?type=' + action)
                .then(res => res.json())
                .then(data => {
                    if(data.playing_name) {
                        showStatus('▶️ ' + data.playing_name);
                        document.getElementById('current-file-display').innerText = data.playing_name;
                        loadFiles();
                    } else {
                        showStatus('⚠️ ' + (data.msg || 'Acción no disponible'));
                    }
                });
        }

        function toggleAutoNext() {
            fetch('/toggle_auto')
                .then(res => res.json())
                .then(data => {
                    const btn = document.getElementById('btn-autonext');
                    if (data.auto_next) {
                        btn.className = "toggle-on";
                        btn.innerText = "🔁 Auto: ON";
                        showStatus('🔁 Auto-avance activado');
                    } else {
                        btn.className = "toggle-off";
                        btn.innerText = "🔁 Auto: OFF";
                        showStatus('🔁 Auto-avance desactivado');
                    }
                });
        }

        setInterval(loadFiles, 4000);
        loadFiles();
    </script>
</body>
</html>
"""

def get_file_list(ftype):
    target_dir = MUSIC_DIR if ftype == 'music' else MOVIES_DIR
    extensions = ('.mp3', '.m4a') if ftype == 'music' else ('.mp4',)
    files_list = []
    if os.path.exists(target_dir):
        for f in sorted(os.listdir(target_dir)):
            if f.lower().endswith(extensions):
                fullpath = os.path.join(target_dir, f)
                files_list.append({"name": f, "path": fullpath})
    return files_list

def send_soap(action, inner_xml):
    url = f"http://{TV_IP}:{TV_PORT}/upnp/control/mingusavtr"
    envelope = f"""<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:{action} xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
{inner_xml}
</u:{action}>
</s:Body>
</s:Envelope>"""
    req = urllib.request.Request(
        url,
        data=envelope.encode('utf-8'),
        headers={
            'Content-Type': 'text/xml; charset="utf-8"',
            'SOAPAction': f'"urn:schemas-upnp-org:service:AVTransport:1#{action}"'
        },
        method='POST'
    )
    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            return response.status, response.read().decode('utf-8')
    except Exception:
        return 500, ""

def get_transport_state():
    status, body = send_soap("GetTransportInfo", "<InstanceID>0</InstanceID>")
    if status == 200:
        if "<CurrentTransportState>PLAYING</CurrentTransportState>" in body:
            return "PLAYING"
        elif "<CurrentTransportState>STOPPED</CurrentTransportState>" in body:
            return "STOPPED"
        elif "<CurrentTransportState>PAUSED_PLAYBACK</CurrentTransportState>" in body:
            return "PAUSED"
    return "UNKNOWN"

def get_mime_and_class(filename):
    fn = filename.lower()
    if fn.endswith('.mp3'):
        return "audio/mpeg", "object.item.audioItem.musicTrack"
    elif fn.endswith('.m4a'):
        return "audio/mp4", "object.item.audioItem.musicTrack"
    else:
        return "video/mp4", "object.item.videoItem"

def play_media(filepath):
    global playing_filename, is_manual_stop, last_change_time

    if not os.path.exists(filepath):
        return False

    filename = os.path.basename(filepath)
    relpath = os.path.relpath(filepath, SERVER_DIR)
    file_size = os.path.getsize(filepath)

    mime, upnp_class = get_mime_and_class(filename)

    encoded_relpath = urllib.parse.quote(relpath, safe='/')
    media_url = f"http://{PHONE_IP}:{HTTP_PORT}/{encoded_relpath}"

    title = xml.sax.saxutils.escape(filename)
    escaped_url = xml.sax.saxutils.escape(media_url)

    protocol_info = f"http-get:*:{mime}:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    inner_body = f"""<InstanceID>0</InstanceID>
<CurrentURI>{escaped_url}</CurrentURI>
<CurrentURIMetaData>&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;&lt;item id="1" parentID="0" restricted="1"&gt;&lt;dc:title&gt;{title}&lt;/dc:title&gt;&lt;upnp:class&gt;{upnp_class}&lt;/upnp:class&gt;&lt;res size="{file_size}" protocolInfo="{protocol_info}"&gt;{escaped_url}&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>"""

    with state_lock:
        is_manual_stop = False
        playing_filename = filename
        last_change_time = time.time()

    send_soap("SetAVTransportURI", inner_body)
    send_soap("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    return True

def tv_monitor_worker():
    global active_index, active_playlist, auto_next_enabled, is_manual_stop, playing_filename
    was_playing = False

    while True:
        time.sleep(3)

        with state_lock:
            auto_on = auto_next_enabled
            manual_stop = is_manual_stop
            playlist_snapshot = list(active_playlist)
            idx_snapshot = active_index
            since_change = time.time() - last_change_time

        if not auto_on or not playlist_snapshot or manual_stop:
            was_playing = False
            continue

        if playing_filename == "Nada reproduciéndose":
            continue

        if since_change < 5:
            continue

        state = get_transport_state()

        if state == "PLAYING":
            was_playing = True
        elif state == "STOPPED" and was_playing:
            was_playing = False
            with state_lock:
                if idx_snapshot < len(playlist_snapshot) - 1:
                    active_index = idx_snapshot + 1
                else:
                    active_index = 0
                next_file = active_playlist[active_index]['path']
            play_media(next_file)

monitor_thread = threading.Thread(target=tv_monitor_worker, daemon=True)
monitor_thread.start()

class CastvHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=SERVER_DIR, **kwargs)

    def send_head(self):
        """Soporte HTTP 206 y cabeceras DLNA requeridas por JVC VIDAA."""
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            return super().send_head()
        
        try:
            f = open(path, 'rb')
        except OSError:
            self.send_error(404, "File not found")
            return None
        
        fs = os.fstat(f.fileno())
        file_size = fs[6]
        
        filename = os.path.basename(path)
        mime, _ = get_mime_and_class(filename)
        dlna_features = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

        range_header = self.headers.get('Range')
        if range_header:
            try:
                range_match = range_header.replace('bytes=', '').split('-')
                start = int(range_match[0]) if range_match[0] else 0
                end = int(range_match[1]) if range_match[1] else file_size - 1
                if end >= file_size:
                    end = file_size - 1
                length = end - start + 1
                
                f.seek(start)
                self.send_response(206)
                self.send_header("Content-Type", mime)
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
                self.send_header("Content-Length", str(length))
                self.send_header("transferMode.dlna.org", "Interactive")
                self.send_header("contentFeatures.dlna.org", dlna_features)
                self.end_headers()
                return f
            except Exception:
                pass
        
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(file_size))
        self.send_header("transferMode.dlna.org", "Interactive")
        self.send_header("contentFeatures.dlna.org", dlna_features)
        self.end_headers()
        return f

    def do_GET(self):
        global active_playlist, active_index, active_type
        global browse_playlist, browse_type
        global playing_filename, auto_next_enabled, is_manual_stop

        parsed_path = urllib.parse.urlparse(self.path)
        path = parsed_path.path
        query = urllib.parse.parse_qs(parsed_path.query)

        if path == '/':
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(HTML_PAGE.encode('utf-8'))

        elif path == '/files':
            ftype = query.get('type', ['movies'])[0]
            with state_lock:
                if browse_type != ftype or not browse_playlist:
                    browse_type = ftype
                    browse_playlist = get_file_list(ftype)

                current_path = (active_playlist[active_index]['path']
                                if (active_type == ftype and 0 <= active_index < len(active_playlist))
                                else "")
                data = {
                    "files": browse_playlist,
                    "current_playing": current_path,
                    "playing_name": playing_filename,
                    "auto_next": auto_next_enabled
                }

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps(data).encode('utf-8'))

        elif path == '/toggle_auto':
            with state_lock:
                auto_next_enabled = not auto_next_enabled
                resp = {"auto_next": auto_next_enabled}
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps(resp).encode('utf-8'))

        elif path == '/play':
            filepath = query.get('path', [''])[0]
            if filepath:
                ftype = 'music' if filepath.startswith(MUSIC_DIR) else 'movies'
                with state_lock:
                    active_type = ftype
                    active_playlist = get_file_list(ftype)
                    active_index = -1
                    for idx, item in enumerate(active_playlist):
                        if item['path'] == filepath:
                            active_index = idx
                            break

                play_media(filepath)

                with state_lock:
                    response_data = {"status": "OK", "playing_name": playing_filename}

                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.end_headers()
                self.wfile.write(json.dumps(response_data).encode('utf-8'))
            else:
                self.send_response(404)
                self.end_headers()

        elif path == '/action':
            action_type = query.get('type', [''])[0]

            with state_lock:
                if not active_playlist:
                    active_playlist[:] = get_file_list(active_type)

                if active_playlist:
                    if action_type == 'next':
                        active_index = (active_index + 1) % len(active_playlist)
                    elif action_type == 'prev':
                        active_index = (active_index - 1) % len(active_playlist)
                    elif action_type == 'shuffle':
                        random.shuffle(active_playlist)
                        active_index = 0
                    target_file = active_playlist[active_index]['path'] if active_playlist else None
                else:
                    target_file = None

            if target_file:
                play_media(target_file)
                with state_lock:
                    resp = {"status": "OK", "playing_name": playing_filename}
            else:
                resp = {"status": "ERROR", "msg": "Playlist vacía"}

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps(resp).encode('utf-8'))

        elif path == '/control':
            action = query.get('action', [''])[0]
            if action in ['Pause', 'Play', 'Stop']:
                if action == 'Stop':
                    with state_lock:
                        is_manual_stop = True
                inner = "<InstanceID>0</InstanceID><Speed>1</Speed>" if action == "Play" else "<InstanceID>0</InstanceID>"
                send_soap(action, inner)
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"OK")
            else:
                self.send_response(400)
                self.end_headers()

        else:
            super().do_GET()

    def log_message(self, format, *args):
        return

class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

if __name__ == '__main__':
    server = ThreadingHTTPServer(('0.0.0.0', HTTP_PORT), CastvHandler)
    print(f"🚀 CASTV 2.0 corriendo en http://{PHONE_IP}:{HTTP_PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")
