import { useEffect, useRef, useState } from "react";

/**
 * AmbientPlayer — synthesized ambient tones (no external assets, Worker-safe).
 * Rain, forest, lofi, and silence. Uses Web Audio API only.
 */

type Track = "silence" | "rain" | "forest" | "lofi";

const TRACKS: { id: Track; label: string }[] = [
  { id: "silence", label: "Silence" },
  { id: "rain", label: "Rain" },
  { id: "forest", label: "Forest" },
  { id: "lofi", label: "Lo-fi Hum" },
];

function makeNoiseBuffer(ctx: AudioContext, seconds = 2): AudioBuffer {
  const buffer = ctx.createBuffer(1, ctx.sampleRate * seconds, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
  return buffer;
}

export function AmbientPlayer() {
  const [active, setActive] = useState<Track>("silence");
  const [vol, setVol] = useState(0.4);
  const ctxRef = useRef<AudioContext | null>(null);
  const nodesRef = useRef<Array<AudioNode>>([]);
  const gainRef = useRef<GainNode | null>(null);

  useEffect(() => {
    return () => stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (gainRef.current && ctxRef.current) {
      gainRef.current.gain.setTargetAtTime(vol, ctxRef.current.currentTime, 0.2);
    }
  }, [vol]);

  const stop = () => {
    nodesRef.current.forEach((n) => {
      try {
        (n as OscillatorNode | AudioBufferSourceNode).stop?.();
      } catch {
        /* noop */
      }
      try {
        n.disconnect();
      } catch {
        /* noop */
      }
    });
    nodesRef.current = [];
    gainRef.current?.disconnect();
    gainRef.current = null;
    ctxRef.current?.close().catch(() => undefined);
    ctxRef.current = null;
  };

  const start = (track: Track) => {
    stop();
    if (track === "silence") return;
    const AC = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    const ctx = new AC();
    ctxRef.current = ctx;
    const master = ctx.createGain();
    master.gain.value = vol;
    master.connect(ctx.destination);
    gainRef.current = master;

    if (track === "rain") {
      const noise = ctx.createBufferSource();
      noise.buffer = makeNoiseBuffer(ctx, 3);
      noise.loop = true;
      const hp = ctx.createBiquadFilter();
      hp.type = "highpass";
      hp.frequency.value = 300;
      const lp = ctx.createBiquadFilter();
      lp.type = "lowpass";
      lp.frequency.value = 4000;
      noise.connect(hp).connect(lp).connect(master);
      noise.start();
      nodesRef.current = [noise, hp, lp];
    } else if (track === "forest") {
      const noise = ctx.createBufferSource();
      noise.buffer = makeNoiseBuffer(ctx, 3);
      noise.loop = true;
      const lp = ctx.createBiquadFilter();
      lp.type = "lowpass";
      lp.frequency.value = 1200;
      noise.connect(lp).connect(master);
      noise.start();
      // Occasional bird-like chirps
      const chirp = () => {
        if (!ctxRef.current) return;
        const o = ctx.createOscillator();
        const g = ctx.createGain();
        o.type = "triangle";
        o.frequency.value = 1600 + Math.random() * 1200;
        g.gain.value = 0;
        g.gain.linearRampToValueAtTime(0.06, ctx.currentTime + 0.02);
        g.gain.linearRampToValueAtTime(0, ctx.currentTime + 0.25);
        o.connect(g).connect(master);
        o.start();
        o.stop(ctx.currentTime + 0.3);
      };
      const iv = setInterval(() => Math.random() > 0.6 && chirp(), 2000);
      nodesRef.current = [noise, lp, { disconnect: () => clearInterval(iv) } as unknown as AudioNode];
    } else if (track === "lofi") {
      // Warm sine pad + subtle noise
      const freqs = [130.81, 164.81, 196.0]; // C, E, G
      const oscs = freqs.map((f) => {
        const o = ctx.createOscillator();
        o.type = "sine";
        o.frequency.value = f;
        const g = ctx.createGain();
        g.gain.value = 0.12;
        o.connect(g).connect(master);
        o.start();
        return o;
      });
      const noise = ctx.createBufferSource();
      noise.buffer = makeNoiseBuffer(ctx, 4);
      noise.loop = true;
      const nGain = ctx.createGain();
      nGain.gain.value = 0.03;
      noise.connect(nGain).connect(master);
      noise.start();
      nodesRef.current = [...oscs, noise, nGain];
    }
  };

  const choose = (t: Track) => {
    setActive(t);
    start(t);
  };

  return (
    <div className="border border-white/10 rounded-md p-4 space-y-3 bg-white/[0.02]">
      <div className="flex items-center justify-between">
        <p className="font-mono text-[10px] tracking-[0.3em] uppercase text-silver-dim">Ambient</p>
        {active !== "silence" && (
          <span className="font-mono text-[9px] tracking-[0.2em] uppercase text-ember">Live</span>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        {TRACKS.map((t) => (
          <button
            key={t.id}
            onClick={() => choose(t.id)}
            className={`font-mono text-[10px] tracking-[0.2em] uppercase px-3 py-1.5 rounded-full border transition-colors ${
              active === t.id
                ? "border-ember/60 text-ember bg-ember/10"
                : "border-white/10 text-silver-dim hover:text-silver hover:border-white/30"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {active !== "silence" && (
        <label className="flex items-center gap-3 text-[10px] font-mono uppercase tracking-widest text-silver-dim">
          Vol
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={vol}
            onChange={(e) => setVol(Number(e.target.value))}
            className="flex-1 accent-ember"
          />
        </label>
      )}
    </div>
  );
}
