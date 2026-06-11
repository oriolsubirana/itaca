"use client";

import { useEffect, useRef, useState } from "react";

interface Msg {
  role: "user" | "assistant";
  text: string;
}

const QUICK = ["mismo peso", "+2.5 kg", "siguiente ejercicio", "termino"];

export default function ChatPage() {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Carga la sesión activa + historial
  useEffect(() => {
    fetch("/api/chat/session")
      .then((r) => r.json())
      .then((d) => {
        setSessionId(d.session.id);
        setMessages(d.messages);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, sending]);

  async function send(text: string) {
    const t = text.trim();
    if (!t || !sessionId || sending) return;
    setInput("");
    setMessages((m) => [...m, { role: "user", text: t }]); // optimista
    setSending(true);
    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId, text: t }),
      });
      const d = await res.json();
      setMessages((m) => [...m, { role: "assistant", text: d.reply || "…" }]);
    } catch {
      setMessages((m) => [...m, { role: "assistant", text: "⚠️ No he podido responder. Reintenta." }]);
    } finally {
      setSending(false);
    }
  }

  async function nuevaSesion() {
    const res = await fetch("/api/chat/session", { method: "POST" });
    const d = await res.json();
    setSessionId(d.session.id);
    setMessages([]);
  }

  return (
    <div className="flex h-[calc(100dvh-4rem)] flex-col">
      <header
        className="flex items-center justify-between border-b border-line px-4 pb-3 pt-4"
        style={{ paddingTop: "max(1rem, env(safe-area-inset-top))" }}
      >
        <div>
          <h1 className="text-xl font-medium tracking-tight">Chat</h1>
          <p className="text-sm text-muted">Tu interfaz con Ítaca</p>
        </div>
        <button onClick={nuevaSesion} className="tap rounded-full border border-line px-3 text-sm text-muted">
          Nueva
        </button>
      </header>

      {/* Mensajes */}
      <div ref={scrollRef} className="no-scrollbar flex-1 space-y-3 overflow-y-auto px-4 py-4">
        {messages.length === 0 && (
          <div className="mt-10 text-center text-sm text-muted">
            <p className="mb-2 text-base text-foreground">Hola Oriol 👋</p>
            <p>Dime &ldquo;empiezo pull&rdquo; para entrenar, o pregúntame por tu salud o finanzas.</p>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={m.role === "user" ? "flex justify-end" : "flex justify-start"}>
            <div
              className={`max-w-[82%] whitespace-pre-wrap rounded-2xl px-3.5 py-2.5 text-[15px] leading-relaxed ${
                m.role === "user"
                  ? "bg-accent text-accent-fg"
                  : "border border-line bg-surface text-foreground"
              }`}
            >
              {m.text}
            </div>
          </div>
        ))}
        {sending && (
          <div className="flex justify-start">
            <div className="rounded-2xl border border-line bg-surface px-3.5 py-2.5 text-muted">…</div>
          </div>
        )}
      </div>

      {/* Respuestas rápidas */}
      <div className="no-scrollbar flex gap-2 overflow-x-auto px-4 pb-1">
        {QUICK.map((q) => (
          <button
            key={q}
            onClick={() => send(q)}
            disabled={sending}
            className="tap shrink-0 rounded-full border border-line bg-surface px-3 text-sm text-foreground disabled:opacity-50"
          >
            {q}
          </button>
        ))}
      </div>

      {/* Input */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
        className="flex items-center gap-2 border-t border-line bg-background px-3 py-2"
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Escribe… (p. ej. jalón 45x12)"
          enterKeyHint="send"
          autoComplete="off"
          className="tap flex-1 rounded-full border border-line bg-surface px-4 text-[16px] outline-none focus:border-accent"
        />
        <button
          type="submit"
          disabled={sending || !input.trim()}
          className="tap grid size-11 place-items-center rounded-full bg-accent text-accent-fg disabled:opacity-40"
          aria-label="Enviar"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        </button>
      </form>
    </div>
  );
}
