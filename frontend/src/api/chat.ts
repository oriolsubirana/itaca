import { api } from "./client";

export type ChatMode = "general" | "workout";

export interface Session {
  id: number;
  mode: ChatMode;
  createdAt: string;
}

export interface ChatMessage {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
}

const API_TOKEN = import.meta.env.VITE_API_TOKEN as string | undefined;

export function createSession(mode: ChatMode): Promise<Session> {
  return api<Session>("/chat/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mode }),
  });
}

export function getMessages(sessionId: number): Promise<ChatMessage[]> {
  return api<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`);
}

interface StreamHandlers {
  onChunk: (text: string) => void;
  onError: (message: string) => void;
}

/**
 * Sends a message and consumes the SSE stream of the reply.
 * EventSource only supports GET, so we parse the stream by hand.
 */
export async function streamMessage(
  sessionId: number,
  content: string,
  handlers: StreamHandlers,
): Promise<void> {
  const headers = new Headers({
    "Content-Type": "application/json",
    Accept: "text/event-stream",
  });
  if (API_TOKEN) headers.set("Authorization", `Bearer ${API_TOKEN}`);

  const response = await fetch(`/api/chat/sessions/${sessionId}/messages`, {
    method: "POST",
    headers,
    body: JSON.stringify({ content }),
  });
  // A non-OK status (e.g. the session no longer exists after a server/DB reset)
  // throws so the caller can recover (recreate the session and retry).
  if (!response.ok || !response.body) {
    throw new Error(`chat ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let separator = buffer.indexOf("\n\n");
    while (separator >= 0) {
      const block = buffer.slice(0, separator);
      buffer = buffer.slice(separator + 2);
      handleEvent(block, handlers);
      separator = buffer.indexOf("\n\n");
    }
  }
}

function handleEvent(block: string, handlers: StreamHandlers): void {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }
  const data = dataLines.join("\n");
  if (event === "chunk" && data) {
    const payload = JSON.parse(data) as { text: string };
    handlers.onChunk(payload.text);
  } else if (event === "error") {
    const payload = data ? (JSON.parse(data) as { text: string }) : { text: "Error" };
    handlers.onError(payload.text);
  }
}
