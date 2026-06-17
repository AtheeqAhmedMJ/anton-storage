import { useState, useEffect, useRef, useCallback } from "react";

// ─── API Client ──────────────────────────────────────────────────────────────

const API = "http://localhost:8080/api";

const client = {
  token: () => localStorage.getItem("token"),

  headers(extra = {}) {
    const h = { ...extra };
    const t = this.token();
    if (t) h["Authorization"] = `Bearer ${t}`;
    return h;
  },

  async get(path) {
    const r = await fetch(API + path, { headers: this.headers() });
    if (!r.ok) throw new Error((await r.json()).error || r.statusText);
    return r.json();
  },

  async post(path, body) {
    const r = await fetch(API + path, {
      method: "POST",
      headers: this.headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(body),
    });
    if (!r.ok) throw new Error((await r.json()).error || r.statusText);
    return r.json();
  },

  async delete(path) {
    const r = await fetch(API + path, { method: "DELETE", headers: this.headers() });
    if (!r.ok) throw new Error((await r.json()).error || r.statusText);
    return r.status === 204 ? null : r.json();
  },

  async upload(file, path = "") {
    const fd = new FormData();
    fd.append("file", file);
    if (path) fd.append("path", path);
    const r = await fetch(`${API}/files`, {
      method: "POST",
      headers: this.headers(),
      body: fd,
    });
    if (!r.ok) throw new Error((await r.json()).error || r.statusText);
    return r.json();
  },
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

const fmt = (bytes) => {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0, v = bytes;
  while (v >= 1024 && i < 3) { v /= 1024; i++; }
  return `${v.toFixed(1)} ${units[i]}`;
};

const fileIcon = (ct) => {
  if (!ct) return "📄";
  if (ct.startsWith("image/")) return "🖼️";
  if (ct.startsWith("video/")) return "🎥";
  if (ct.startsWith("audio/")) return "🎵";
  if (ct.includes("pdf")) return "📕";
  if (ct.includes("zip") || ct.includes("archive")) return "🗜️";
  if (ct.includes("text")) return "📝";
  if (ct.includes("spreadsheet") || ct.includes("excel")) return "📊";
  return "📄";
};

// ─── Components ──────────────────────────────────────────────────────────────

function Toast({ msg, type, onClose }) {
  useEffect(() => {
    const t = setTimeout(onClose, 3500);
    return () => clearTimeout(t);
  }, [msg]);

  return (
    <div style={{
      position: "fixed", bottom: 24, right: 24, zIndex: 999,
      background: type === "error" ? "#ef4444" : "#10b981",
      color: "#fff", padding: "12px 20px", borderRadius: 10,
      boxShadow: "0 4px 20px rgba(0,0,0,.25)", fontWeight: 500,
      display: "flex", gap: 10, alignItems: "center", maxWidth: 360,
    }}>
      {type === "error" ? "✗" : "✓"} {msg}
      <button onClick={onClose} style={{
        background: "none", border: "none", color: "#fff",
        cursor: "pointer", marginLeft: "auto", fontSize: 16
      }}>×</button>
    </div>
  );
}

function StorageBar({ stats }) {
  if (!stats) return null;
  const pct = Math.min(stats.percentUsed || 0, 100);
  const color = pct > 90 ? "#ef4444" : pct > 70 ? "#f59e0b" : "#6366f1";
  return (
    <div style={{ padding: "16px 24px", background: "#f8f9ff", borderRadius: 12, marginBottom: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8, fontSize: 13, color: "#6b7280" }}>
        <span><b style={{ color: "#1e293b" }}>{stats.usedHuman}</b> used</span>
        <span>{stats.limitHuman} total · {stats.fileCount} files</span>
      </div>
      <div style={{ background: "#e5e7eb", borderRadius: 99, height: 6 }}>
        <div style={{ width: `${pct}%`, background: color, borderRadius: 99, height: 6, transition: "width .5s" }} />
      </div>
    </div>
  );
}

function FileRow({ file, onDownload, onDelete, onShare, onVersions }) {
  const [menu, setMenu] = useState(false);
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 16px", borderRadius: 10,
      background: "#fff", border: "1px solid #f1f5f9",
      marginBottom: 8, transition: "box-shadow .15s",
    }}
      onMouseEnter={e => e.currentTarget.style.boxShadow = "0 2px 12px rgba(99,102,241,.1)"}
      onMouseLeave={e => e.currentTarget.style.boxShadow = "none"}
    >
      <span style={{ fontSize: 24 }}>{fileIcon(file.contentType)}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, color: "#1e293b", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          {file.filename}
        </div>
        <div style={{ fontSize: 12, color: "#94a3b8" }}>
          {file.virtualPath} · {file.sizeHuman} · v{file.currentVersion}
        </div>
      </div>
      <div style={{ display: "flex", gap: 6, flexShrink: 0 }}>
        {[
          { icon: "⬇", label: "Download", fn: () => onDownload(file) },
          { icon: "🔗", label: "Share", fn: () => onShare(file) },
          { icon: "🕑", label: "Versions", fn: () => onVersions(file) },
          { icon: "🗑", label: "Delete", fn: () => onDelete(file) },
        ].map(({ icon, label, fn }) => (
          <button key={label} title={label} onClick={fn} style={{
            background: "#f8f9ff", border: "1px solid #e2e8f0",
            borderRadius: 7, padding: "5px 10px", cursor: "pointer",
            fontSize: 14, color: "#475569", transition: "background .15s"
          }}>{icon}</button>
        ))}
      </div>
    </div>
  );
}

// ─── Modals ───────────────────────────────────────────────────────────────────

function Modal({ title, onClose, children }) {
  return (
    <div style={{
      position: "fixed", inset: 0, background: "rgba(15,23,42,.45)",
      zIndex: 100, display: "flex", alignItems: "center", justifyContent: "center"
    }} onClick={e => e.target === e.currentTarget && onClose()}>
      <div style={{
        background: "#fff", borderRadius: 16, padding: 28,
        width: 480, maxWidth: "95vw", boxShadow: "0 20px 60px rgba(0,0,0,.2)"
      }}>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 20 }}>
          <h3 style={{ margin: 0, color: "#1e293b" }}>{title}</h3>
          <button onClick={onClose} style={{
            background: "none", border: "none", fontSize: 22,
            cursor: "pointer", color: "#94a3b8"
          }}>×</button>
        </div>
        {children}
      </div>
    </div>
  );
}

function ShareModal({ file, onClose, toast }) {
  const [link, setLink] = useState(null);
  const [loading, setLoading] = useState(false);
  const [expiry, setExpiry] = useState("");

  const create = async () => {
    setLoading(true);
    try {
      const res = await client.post("/share", {
        fileId: file.id,
        expiresAt: expiry ? new Date(expiry).toISOString() : null,
        permission: "READ",
      });
      setLink(`${window.location.origin}/share/${res.token}`);
      toast("Share link created!", "success");
    } catch (e) {
      toast(e.message, "error");
    }
    setLoading(false);
  };

  const copy = () => {
    navigator.clipboard.writeText(link);
    toast("Link copied!", "success");
  };

  return (
    <Modal title={`Share "${file.filename}"`} onClose={onClose}>
      <label style={{ display: "block", marginBottom: 8, fontSize: 13, color: "#6b7280" }}>
        Expiry (optional)
      </label>
      <input type="datetime-local" value={expiry} onChange={e => setExpiry(e.target.value)}
        style={inputStyle} />
      <button onClick={create} disabled={loading} style={btnPrimary}>
        {loading ? "Creating…" : "Generate Link"}
      </button>
      {link && (
        <div style={{ marginTop: 16, background: "#f8f9ff", borderRadius: 8, padding: 12, display: "flex", gap: 8 }}>
          <input readOnly value={link} style={{ flex: 1, border: "none", background: "none", fontSize: 13 }} />
          <button onClick={copy} style={{ ...btnPrimary, padding: "6px 12px", marginTop: 0 }}>Copy</button>
        </div>
      )}
    </Modal>
  );
}

function VersionsModal({ file, onClose }) {
  const [versions, setVersions] = useState([]);

  useEffect(() => {
    client.get(`/files/${file.id}/versions`).then(setVersions).catch(() => {});
  }, [file.id]);

  return (
    <Modal title={`Versions — ${file.filename}`} onClose={onClose}>
      {versions.length === 0 ? (
        <p style={{ color: "#94a3b8" }}>No versions found.</p>
      ) : versions.map(v => (
        <div key={v.id} style={{
          display: "flex", justifyContent: "space-between", alignItems: "center",
          padding: "10px 0", borderBottom: "1px solid #f1f5f9"
        }}>
          <div>
            <span style={{ fontWeight: 600 }}>v{v.versionNumber}</span>
            <span style={{ marginLeft: 12, fontSize: 12, color: "#94a3b8" }}>
              {fmt(v.sizeBytes)} · {new Date(v.createdAt).toLocaleDateString()}
            </span>
          </div>
          <a href={`${API}/files/${file.id}/download?version=${v.versionNumber}`}
            style={{ color: "#6366f1", fontSize: 13 }}>Download</a>
        </div>
      ))}
    </Modal>
  );
}

// ─── Upload Drop Zone ─────────────────────────────────────────────────────────

function DropZone({ onUpload, uploading }) {
  const [drag, setDrag] = useState(false);
  const inputRef = useRef();

  const handleDrop = useCallback((e) => {
    e.preventDefault(); setDrag(false);
    const files = [...e.dataTransfer.files];
    files.forEach(f => onUpload(f));
  }, [onUpload]);

  return (
    <div
      onDragOver={e => { e.preventDefault(); setDrag(true); }}
      onDragLeave={() => setDrag(false)}
      onDrop={handleDrop}
      onClick={() => inputRef.current.click()}
      style={{
        border: `2px dashed ${drag ? "#6366f1" : "#cbd5e1"}`,
        borderRadius: 12, padding: "32px 24px", textAlign: "center",
        cursor: "pointer", marginBottom: 24,
        background: drag ? "#f0f0ff" : "#fafbff",
        transition: "all .2s"
      }}>
      <div style={{ fontSize: 36, marginBottom: 8 }}>☁️</div>
      <div style={{ fontWeight: 600, color: "#475569" }}>
        {uploading ? "Uploading…" : "Drop files here or click to browse"}
      </div>
      <div style={{ fontSize: 12, color: "#94a3b8", marginTop: 4 }}>
        Any file type · Up to 500 MB per upload
      </div>
      <input ref={inputRef} type="file" multiple hidden
        onChange={e => [...e.target.files].forEach(f => onUpload(f))} />
    </div>
  );
}

// ─── Auth Screen ──────────────────────────────────────────────────────────────

function AuthScreen({ onLogin }) {
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ username: "", email: "", password: "" });
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (e) => {
    e.preventDefault(); setErr(""); setLoading(true);
    try {
      const res = await client.post(`/auth/${mode}`, form);
      localStorage.setItem("token", res.token);
      onLogin(res);
    } catch (ex) {
      setErr(ex.message);
    }
    setLoading(false);
  };

  return (
    <div style={{
      minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center",
      background: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
    }}>
      <div style={{
        background: "#fff", borderRadius: 20, padding: 40, width: 380,
        boxShadow: "0 25px 60px rgba(0,0,0,.3)"
      }}>
        <div style={{ textAlign: "center", marginBottom: 28 }}>
          <div style={{ fontSize: 40, marginBottom: 8 }}>🗄️</div>
          <h1 style={{ margin: 0, fontSize: 22, color: "#1e293b" }}>Anton Storage</h1>
          <p style={{ margin: "4px 0 0", color: "#94a3b8", fontSize: 13 }}>
            Distributed Object Storage Platform
          </p>
        </div>

        <div style={{ display: "flex", background: "#f1f5f9", borderRadius: 8, marginBottom: 24, padding: 3 }}>
          {["login", "register"].map(m => (
            <button key={m} onClick={() => setMode(m)} style={{
              flex: 1, padding: "8px 0", border: "none", borderRadius: 6, cursor: "pointer",
              background: mode === m ? "#fff" : "transparent",
              fontWeight: mode === m ? 600 : 400,
              color: mode === m ? "#6366f1" : "#64748b",
              boxShadow: mode === m ? "0 1px 4px rgba(0,0,0,.1)" : "none",
              transition: "all .2s", textTransform: "capitalize"
            }}>{m}</button>
          ))}
        </div>

        <form onSubmit={submit}>
          <input placeholder="Username" value={form.username}
            onChange={e => setForm({ ...form, username: e.target.value })}
            style={{ ...inputStyle, marginBottom: 12 }} required />
          {mode === "register" && (
            <input placeholder="Email" type="email" value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
              style={{ ...inputStyle, marginBottom: 12 }} required />
          )}
          <input placeholder="Password" type="password" value={form.password}
            onChange={e => setForm({ ...form, password: e.target.value })}
            style={{ ...inputStyle, marginBottom: 16 }} required />
          {err && <p style={{ color: "#ef4444", fontSize: 13, margin: "0 0 12px" }}>{err}</p>}
          <button type="submit" disabled={loading} style={{ ...btnPrimary, width: "100%" }}>
            {loading ? "Please wait…" : mode === "login" ? "Sign In" : "Create Account"}
          </button>
        </form>
      </div>
    </div>
  );
}

// ─── Main Dashboard ───────────────────────────────────────────────────────────

function Dashboard({ user, onLogout }) {
  const [files, setFiles] = useState([]);
  const [stats, setStats] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [search, setSearch] = useState("");
  const [toast, setToast] = useState(null);
  const [shareFile, setShareFile] = useState(null);
  const [versionsFile, setVersionsFile] = useState(null);

  const showToast = (msg, type = "success") => setToast({ msg, type });

  const refresh = async () => {
    try {
      const [f, s] = await Promise.all([client.get("/files"), client.get("/files/stats")]);
      setFiles(f);
      setStats(s);
    } catch (e) { showToast(e.message, "error"); }
  };

  useEffect(() => { refresh(); }, []);

  const handleUpload = async (file) => {
    setUploading(true);
    try {
      await client.upload(file);
      showToast(`"${file.name}" uploaded`);
      refresh();
    } catch (e) { showToast(e.message, "error"); }
    setUploading(false);
  };

  const handleDownload = (file) => {
    window.open(`${API}/files/${file.id}/download?token=${client.token()}`, "_blank");
    // Note: in real impl, set auth header via fetch + blob URL
  };

  const handleDelete = async (file) => {
    if (!confirm(`Delete "${file.filename}"?`)) return;
    try {
      await client.delete(`/files/${file.id}`);
      showToast(`"${file.filename}" deleted`);
      refresh();
    } catch (e) { showToast(e.message, "error"); }
  };

  const filtered = files.filter(f =>
    f.filename.toLowerCase().includes(search.toLowerCase()) ||
    f.virtualPath.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div style={{ minHeight: "100vh", background: "#f8f9ff" }}>
      {/* Top nav */}
      <div style={{
        background: "#fff", borderBottom: "1px solid #f1f5f9",
        padding: "0 32px", display: "flex", alignItems: "center",
        height: 60, gap: 16
      }}>
        <span style={{ fontSize: 24 }}>🗄️</span>
        <span style={{ fontWeight: 700, fontSize: 18, color: "#1e293b" }}>Anton Storage</span>
        <div style={{ flex: 1 }} />
        <span style={{ fontSize: 13, color: "#94a3b8" }}>
          {user.username}
        </span>
        <button onClick={onLogout} style={{
          background: "none", border: "1px solid #e2e8f0",
          borderRadius: 7, padding: "6px 14px", cursor: "pointer",
          color: "#64748b", fontSize: 13
        }}>Sign out</button>
      </div>

      <div style={{ maxWidth: 860, margin: "0 auto", padding: "32px 24px" }}>
        <StorageBar stats={stats} />

        <DropZone onUpload={handleUpload} uploading={uploading} />

        {/* Search */}
        <div style={{ position: "relative", marginBottom: 20 }}>
          <span style={{ position: "absolute", left: 14, top: "50%", transform: "translateY(-50%)", color: "#94a3b8" }}>🔍</span>
          <input
            placeholder="Search files…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ ...inputStyle, paddingLeft: 38 }}
          />
        </div>

        {/* File list */}
        {filtered.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 0", color: "#cbd5e1" }}>
            <div style={{ fontSize: 48, marginBottom: 12 }}>📭</div>
            <div style={{ fontWeight: 600 }}>{search ? "No files match your search" : "No files yet — upload something!"}</div>
          </div>
        ) : filtered.map(f => (
          <FileRow
            key={f.id} file={f}
            onDownload={handleDownload}
            onDelete={handleDelete}
            onShare={setShareFile}
            onVersions={setVersionsFile}
          />
        ))}
      </div>

      {shareFile && <ShareModal file={shareFile} onClose={() => setShareFile(null)} toast={showToast} />}
      {versionsFile && <VersionsModal file={versionsFile} onClose={() => setVersionsFile(null)} />}
      {toast && <Toast msg={toast.msg} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const inputStyle = {
  width: "100%", padding: "10px 14px", border: "1px solid #e2e8f0",
  borderRadius: 8, fontSize: 14, outline: "none",
  boxSizing: "border-box", color: "#1e293b",
  fontFamily: "inherit"
};

const btnPrimary = {
  width: "100%", padding: "11px 0", marginTop: 8,
  background: "linear-gradient(135deg, #6366f1, #8b5cf6)",
  color: "#fff", border: "none", borderRadius: 9,
  fontWeight: 600, fontSize: 15, cursor: "pointer",
  transition: "opacity .2s", fontFamily: "inherit"
};

// ─── App Root ─────────────────────────────────────────────────────────────────

export default function App() {
  const [user, setUser] = useState(() => {
    const t = localStorage.getItem("token");
    if (!t) return null;
    try {
      const p = JSON.parse(atob(t.split(".")[1]));
      return { username: p.username, userId: p.sub };
    } catch { return null; }
  });

  const login = (data) => setUser(data);
  const logout = () => { localStorage.removeItem("token"); setUser(null); };

  if (!user) return <AuthScreen onLogin={login} />;
  return <Dashboard user={user} onLogout={logout} />;
}
