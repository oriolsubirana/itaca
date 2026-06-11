import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  createSession,
  getMessages,
  streamMessage,
  type ChatMode,
} from "../api/chat";

const QUICK_REPLIES = [
  "Empiezo entreno",
  "Mismo peso",
  "+2,5 kg",
  "Siguiente ejercicio",
  "Termino",
];

function sessionKey(mode: ChatMode) {
  return `itaca.chat.session.${mode}`;
}

interface Pending {
  user: string;
  assistant: string;
  failed?: boolean;
}

export function Chat() {
  const [mode, setMode] = useState<ChatMode>(
    () => (localStorage.getItem("itaca.chat.mode") as ChatMode) ?? "general",
  );
  const [sessionId, setSessionId] = useState<number | null>(() => {
    const stored = localStorage.getItem(sessionKey(mode));
    return stored ? Number(stored) : null;
  });
  const [input, setInput] = useState("");
  const [pending, setPending] = useState<Pending | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const history = useQuery({
    queryKey: ["chat", sessionId],
    queryFn: () => getMessages(sessionId!),
    enabled: sessionId !== null,
  });

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [history.data, pending]);

  function switchMode(next: ChatMode) {
    setMode(next);
    localStorage.setItem("itaca.chat.mode", next);
    const stored = localStorage.getItem(sessionKey(next));
    setSessionId(stored ? Number(stored) : null);
    setPending(null);
  }

  function resetSession() {
    localStorage.removeItem(sessionKey(mode));
    setSessionId(null);
    setPending(null);
  }

  async function send(text: string) {
    const content = text.trim();
    if (!content || pending) return;
    setInput("");
    setPending({ user: content, assistant: "" });
    try {
      let id = sessionId;
      if (id === null) {
        const session = await createSession(mode);
        id = session.id;
        localStorage.setItem(sessionKey(mode), String(id));
        setSessionId(id);
      }
      await streamMessage(id, content, {
        onChunk: (chunk) =>
          setPending((p) => p && { ...p, assistant: p.assistant + chunk }),
        onError: (message) =>
          setPending((p) => p && { ...p, assistant: message, failed: true }),
      });
      await queryClient.invalidateQueries({ queryKey: ["chat", id] });
    } catch {
      setPending((p) => p && { ...p, assistant: "No he podido conectar.", failed: true });
      return;
    }
    setPending(null);
  }

  const messages = history.data ?? [];

  return (
    <div className="flex min-h-[calc(100dvh-9.5rem)] flex-col">
      <header className="sticky top-0 z-10 -mx-5 mb-4 flex items-center gap-2 bg-paper/95 px-5 pt-[max(0.75rem,env(safe-area-inset-top))] pb-3 backdrop-blur">
        {(["general", "workout"] as const).map((m) => (
          <button
            key={m}
            onClick={() => switchMode(m)}
            className={`min-h-11 rounded-full border px-4 text-sm ${
              mode === m
                ? "border-ink bg-ink text-paper"
                : "border-line text-ink-soft"
            }`}
          >
            {m === "general" ? "General" : "Entreno"}
          </button>
        ))}
        <button
          onClick={resetSession}
          className="ml-auto min-h-11 px-2 text-xs uppercase tracking-wide text-ink-soft"
        >
          Nueva sesión
        </button>
      </header>

      <div
        className={`flex flex-1 flex-col gap-4 pb-44 ${
          messages.length || pending ? "justify-end" : "justify-center"
        }`}
      >
        {messages.length === 0 && !pending && (
          <p className="text-center text-sm leading-relaxed text-ink-soft">
            {mode === "workout"
              ? "Dile «empiezo» y Claude te guiará la sesión con los pesos de la última vez."
              : "Habla con Claude: consulta y registra entrenos, salud y finanzas."}
          </p>
        )}
        {messages.map((message) => (
          <Bubble key={message.id} role={message.role} text={message.content} />
        ))}
        {pending && (
          <>
            <Bubble role="USER" text={pending.user} />
            {pending.assistant ? (
              <Bubble
                role="ASSISTANT"
                text={pending.assistant}
                error={pending.failed}
              />
            ) : (
              <TypingDots />
            )}
          </>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="fixed inset-x-0 bottom-14 bg-paper/95 pb-[env(safe-area-inset-bottom)] backdrop-blur">
        <div className="mx-auto max-w-2xl px-5 pb-3">
          {mode === "workout" && (
            <div className="no-scrollbar -mx-1 mb-2 flex gap-2 overflow-x-auto px-1 pt-2">
              {QUICK_REPLIES.map((reply) => (
                <button
                  key={reply}
                  onClick={() => void send(reply)}
                  disabled={!!pending}
                  className="min-h-11 shrink-0 rounded-full border border-line px-4 text-sm text-ink disabled:opacity-40"
                >
                  {reply}
                </button>
              ))}
            </div>
          )}
          <form
            className="flex items-end gap-2 border-t border-line pt-3"
            onSubmit={(e) => {
              e.preventDefault();
              void send(input);
            }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={mode === "workout" ? "jalón 45 por 12…" : "Escribe…"}
              enterKeyHint="send"
              className="min-h-11 flex-1 rounded-lg border border-line bg-paper px-4 text-base outline-none focus:border-ink-soft"
            />
            <button
              type="submit"
              disabled={!input.trim() || !!pending}
              className="flex size-11 items-center justify-center rounded-lg bg-ink text-paper disabled:opacity-40"
              aria-label="Enviar"
            >
              ↑
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

function TypingDots() {
  return (
    <div className="typing flex gap-1 py-1" role="status" aria-label="Pensando">
      <span />
      <span />
      <span />
    </div>
  );
}

function Bubble({
  role,
  text,
  error,
}: {
  role: "USER" | "ASSISTANT";
  text: string;
  error?: boolean;
}) {
  if (role === "USER") {
    return (
      <div className="flex justify-end">
        <p className="max-w-[85%] rounded-2xl rounded-br-md bg-ink px-4 py-2.5 text-sm leading-relaxed text-paper">
          {text}
        </p>
      </div>
    );
  }
  return (
    <div
      className={`markdown max-w-[92%] text-sm leading-relaxed ${
        error ? "text-red-800" : "text-ink"
      }`}
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown>
    </div>
  );
}
