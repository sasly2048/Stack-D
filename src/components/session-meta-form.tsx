import { useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { toast } from "sonner";
import { updateSessionMeta } from "@/lib/challenges.functions";

export function SessionMetaForm({ historyId }: { historyId: string }) {
  const save = useServerFn(updateSessionMeta);
  const [notes, setNotes] = useState("");
  const [tagsRaw, setTagsRaw] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const tags = tagsRaw
        .split(",")
        .map((t) => t.trim().toLowerCase())
        .filter(Boolean)
        .slice(0, 8);
      await save({ data: { historyId, notes, tags } });
      setSaved(true);
      toast.success("Session marked");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not save");
    } finally {
      setBusy(false);
    }
  };

  if (saved) {
    return (
      <div className="border border-ember/30 bg-ember/[0.04] rounded-md p-4 font-mono text-[10px] tracking-[0.3em] uppercase text-ember">
        Marked ·  {tagsRaw && `#${tagsRaw.split(",")[0].trim()}`}
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="border border-white/10 rounded-md p-5 space-y-4">
      <div>
        <label className="block font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim mb-2">
          Notes
        </label>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          maxLength={2000}
          rows={3}
          placeholder="What did you hold?"
          className="w-full bg-black/30 border border-white/10 rounded px-3 py-2 text-sm text-silver focus:border-ember/60 focus:outline-none resize-none"
        />
      </div>
      <div>
        <label className="block font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim mb-2">
          Tags · comma separated
        </label>
        <input
          value={tagsRaw}
          onChange={(e) => setTagsRaw(e.target.value)}
          placeholder="deep-work, writing, study"
          className="w-full bg-black/30 border border-white/10 rounded px-3 py-2 text-sm text-silver focus:border-ember/60 focus:outline-none"
        />
      </div>
      <button
        type="submit"
        disabled={busy}
        className="w-full py-3 border border-ember/40 text-ember rounded font-mono text-[10px] tracking-[0.3em] uppercase hover:bg-ember/10 transition-colors disabled:opacity-40"
      >
        {busy ? "Marking…" : "Mark session"}
      </button>
    </form>
  );
}
