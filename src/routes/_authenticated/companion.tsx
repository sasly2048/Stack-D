import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { Nav } from "@/components/nav";
import { askCompanion, type CompanionMessage } from "@/lib/companion.functions";
import { haptic } from "@/lib/haptics";

export const Route = createFileRoute("/_authenticated/companion")({
  head: () => ({
    meta: [
      { title: "Study Companion — Stack'd" },
      { name: "description", content: "A private, always-on focus coach trained on your own protocol." },
    ],
  }),
  component: CompanionPage,
});

const OPENERS = [
  "Why did I break last session?",
  "Schedule my week for a 40-hour goal.",
  "Am I heading toward burnout?",
  "What tier am I closest to unlocking?",
];

function CompanionPage() {
  const ask = useServerFn(askCompanion);
  const [messages, setMessages] = useState<CompanionMessage[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, busy]);

  useEffect(() => { inputRef.current?.focus(); }, []);

  const send = async (raw?: string) => {
    const text = (raw ?? input).trim();
    if (!text || busy) return;
    const next: CompanionMessage[] = [...messages, { role: "user", content: text }];
    setMessages(next);
    setInput("");
    setBusy(true);
    haptic("tap");
    try {
      const { reply } = await ask({ data: { history: messages, message: text } });
      setMessages([...next, { role: "assistant", content: reply }]);
      haptic("success");
    } catch (err) {
      setMessages([...next, { role: "assistant", content: `⚠ ${(err as Error).message}` }]);
      haptic("error");
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  };

  return (
    <div className="min-h-screen bg-obsidian text-silver flex flex-col">
      <Nav />
      <div className="pt-24 flex-1 max-w-3xl w-full mx-auto px-6 pb-6 flex flex-col">
        <div className="mb-4">
          <h1 className="text-3xl font-serif">Study Companion</h1>
          <p className="text-xs text-muted-foreground font-mono mt-1">
            Private coach · reads your protocol · never leaves your account
          </p>
        </div>

        <div ref={scrollRef} className="flex-1 overflow-y-auto glass rounded-xl p-5 space-y-4 min-h-[50vh]">
          {messages.length === 0 && (
            <div className="text-center py-10 space-y-4">
              <div className="text-sm text-muted-foreground">Ask anything about your focus.</div>
              <div className="flex flex-wrap gap-2 justify-center">
                {OPENERS.map((o) => (
                  <button
                    key={o}
                    onClick={() => send(o)}
                    className="text-xs font-mono px-3 py-1.5 rounded-full border border-white/10 hover:border-ember/60 hover:text-ember transition-colors"
                  >
                    {o}
                  </button>
                ))}
              </div>
            </div>
          )}
          {messages.map((m, i) => (
            <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
              <div
                className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap ${
                  m.role === "user"
                    ? "bg-ember/15 border border-ember/30 text-silver"
                    : "text-silver/90"
                }`}
              >
                {m.content}
              </div>
            </div>
          ))}
          {busy && (
            <div className="flex justify-start">
              <div className="text-xs font-mono text-muted-foreground animate-pulse">Companion is thinking…</div>
            </div>
          )}
        </div>

        <form
          onSubmit={(e) => { e.preventDefault(); send(); }}
          className="mt-4 glass rounded-xl p-3 flex gap-3 items-end"
        >
          <textarea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
            }}
            rows={1}
            placeholder="Ask the companion…"
            className="flex-1 bg-transparent resize-none outline-none text-sm max-h-40"
          />
          <button
            type="submit"
            disabled={busy || !input.trim()}
            className="btn-ember px-4 py-2 border border-silver/20 rounded-full text-silver text-xs font-mono uppercase tracking-widest disabled:opacity-50"
          >
            {busy ? "…" : "Send"}
          </button>
        </form>
      </div>
    </div>
  );
}
