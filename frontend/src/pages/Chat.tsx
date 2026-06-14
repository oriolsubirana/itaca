import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useSearch } from "@tanstack/react-router";
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
  // Home can deep-link here with a seeded prompt and/or workout mode (?seed=&workout=).
  const search = useSearch({ from: "/chat" });
  const navigate = useNavigate();
  const [mode, setMode] = useState<ChatMode>(() =>
    search.workout ? "workout" : (localStorage.getItem("itaca.chat.mode") as ChatMode) ?? "general",
  );
  const [sessionId, setSessionId] = useState<number | null>(() => {
    const stored = localStorage.getItem(sessionKey(mode));
    return stored ? Number(stored) : null;
  });
  const [input, setInput] = useState("");
  const [pending, setPending] = useState<Pending | null>(null);
  const stick = useRef(true);
  const queryClient = useQueryClient();

  const history = useQuery({
    queryKey: ["chat", sessionId],
    queryFn: () => getMessages(sessionId!),
    enabled: sessionId !== null,
  });

  // Stick to the bottom while streaming, but only if the user hasn't scrolled up
  // to read — instant scrollTop (no smooth) so it follows without jerky jumps.
  useEffect(() => {
    const onScroll = () => {
      const el = document.scrollingElement;
      if (el) stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

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

  const send = useCallback(
    async (text: string) => {
      const content = text.trim();
      if (!content || pending) return;
      setInput("");
      setPending({ user: content, assistant: "" });
      const openSession = async () => {
        const session = await createSession(mode);
        localStorage.setItem(sessionKey(mode), String(session.id));
        setSessionId(session.id);
        return session.id;
      };
      const handlers = {
        onChunk: (chunk: string) => setPending((p) => p && { ...p, assistant: p.assistant + chunk }),
        onError: (message: string) => setPending((p) => p && { ...p, assistant: message, failed: true }),
      };
      try {
        let id = sessionId ?? (await openSession());
        try {
          await streamMessage(id, content, handlers);
        } catch {
          // The cached session may be gone (server/DB reset): recreate and retry once.
          localStorage.removeItem(sessionKey(mode));
          id = await openSession();
          await streamMessage(id, content, handlers);
        }
        await queryClient.invalidateQueries({ queryKey: ["chat", id] });
      } catch {
        setPending((p) => p && { ...p, assistant: "No he podido conectar.", failed: true });
        return;
      }
      setPending(null);
    },
    [pending, sessionId, mode, queryClient],
  );

  // A seeded prompt from Home (?seed=) is sent automatically, so it lands as a
  // real message in the thread instead of just prefilling the input.
  const sentSeed = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (!search.seed || sentSeed.current === search.seed) return;
    sentSeed.current = search.seed;
    void send(search.seed);
    // Drop the seed from the URL so a reload/re-navigation doesn't resend it.
    void navigate({ to: "/chat", search: {}, replace: true });
  }, [search.seed, send, navigate]);

  const messages = history.data ?? [];

  useEffect(() => {
    if (!stick.current) return;
    const el = document.scrollingElement as HTMLElement | null;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages.length, pending?.user, pending?.assistant]);

  return (
    <div className="flex min-h-[calc(100dvh-9.5rem)] flex-col">
      <header className="sticky top-0 z-10 -mx-5 mb-4 flex items-center justify-between gap-2 border-b border-line bg-paper/95 px-5 pt-[max(0.75rem,env(safe-area-inset-top))] pb-3 backdrop-blur">
        <div className="flex items-center gap-2.5">
          <span className="size-2 rounded-full bg-ink" />
          <span className="text-[17px] font-medium tracking-tight text-ink">Ítaca</span>
        </div>
        <div className="flex items-center gap-1.5">
          {(["general", "workout"] as const).map((m) => (
            <button
              key={m}
              onClick={() => switchMode(m)}
              className={`min-h-9 rounded-full border px-3 text-[13px] ${
                mode === m ? "border-ink bg-ink text-paper" : "border-line text-ink-soft"
              }`}
            >
              {m === "general" ? "General" : "Entreno"}
            </button>
          ))}
          <button
            onClick={resetSession}
            aria-label="Nueva sesión"
            className="flex size-9 items-center justify-center text-ink-soft"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} className="size-[18px]">
              <path d="M4 12a8 8 0 1 1 2.3 5.6" strokeLinecap="round" />
              <path d="M4 17v-4h4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </div>
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
            className="flex items-center gap-2.5 border-t border-line pt-3"
            onSubmit={(e) => {
              e.preventDefault();
              void send(input);
            }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={mode === "workout" ? "jalón 45 por 12…" : "Escribe a Ítaca…"}
              enterKeyHint="send"
              className="h-12 flex-1 rounded-full border border-line bg-paper px-4 text-[15px] outline-none focus:border-ink/40"
            />
            <button
              type="submit"
              disabled={!input.trim() || !!pending}
              className="flex size-12 shrink-0 items-center justify-center rounded-full bg-ink text-paper disabled:opacity-40"
              aria-label="Enviar"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} className="size-5">
                <path d="M12 19V5M5 12l7-7 7 7" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
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
        <p className="max-w-[80%] rounded-2xl rounded-br-md bg-ink px-3.5 py-2.5 text-[14.5px] leading-snug text-paper">
          {text}
        </p>
      </div>
    );
  }
  return (
    <div className="flex justify-start">
      <div
        className={`markdown max-w-[88%] text-pretty rounded-2xl rounded-bl-md bg-[#f1efea] px-3.5 py-2.5 text-[14.5px] leading-relaxed ${
          error ? "text-red-800" : "text-ink"
        }`}
      >
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown>
      </div>
    </div>
  );
}
