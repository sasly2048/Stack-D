import { useEffect, useMemo, useState } from "react";

/**
 * DottedMap — a geographically accurate dotted world map.
 *
 * The land mask is a pre-rasterised equirectangular grid (180 x 71 cells,
 * lon -180..180, lat -58..84) derived from Natural Earth 110m land data and
 * bit-packed into the base64 string below. Cells are square in degrees, so the
 * SVG viewBox aspect (180/71) equals the true geographic aspect — the map is
 * never stretched, and it fills its slot edge to edge.
 */

const LAND_MASK_B64 =
  "AAAAAAAB/+A//gAAAAAAAAAAAAAAAAAAAAAAA///////AAHgBwAADgAAAAAAAAAAAGf//////4AB/gAAAAD+AAAAAAAAAABz" +
  "G/4////4AAZAAAOAAPwAAAAAAAAAAfm/wA///wAAAAADwAf/4AP4AAAAAAH/3/8AP//wAAAAAODj///+HgAAgH4AD/73/wP/" +
  "/gAAA8AMH///////wBg/////7934H//AAAP/gL//////////+f//////3+H/wAAA//7///////////8///////n+H8A+AB+/" +
  "////////////Cf/////508D8AAAH//////////////Af/////gHgB4AAAP7////////////4AHoP///wH+AAAAGP4///////" +
  "////cAADwB///8H+AAAAGD7/////////8A8AAcAA////v/wAAAfDv/////////4B4AAAABf///v/wAAAbv///////////AwA" +
  "AAAAP/////4AAAH////////////AgAAAAAP////+8AAAH///////////9AAAAAAAD/////MAAAD///////////9AAAAAAAD/" +
  "////AAAAB//7/f//////5AAAAAAAD////gAAAAf9vwPf//////zgAAAAAAD////gAAAAfi///v/////+CAAAAAAAD///+AAA" +
  "AAfCLf/v/////cCAAAAAAAD///8AAAAAf/zf///////mOAAAAAAAB///8AAAAAP/Ai///////H+AAAAAAAAf//4AAAAAf/zB" +
  "///////hgAAAAAAAAf//wAAAAAf//////////hAAAAAAAAAP/swAAAAA/////f/////gAAAAAAAAAP/A4AAAAB///+/v////" +
  "/gAAAAAAAAAD/AQAAAAD/////3/////gAAAAAAAAAA+A4AAAAD////f/D///+gAAAAAAAAAAfGOAAAAH//////B/z/wAAAAA" +
  "AAgAAAf+B4AAAD////v+A/h+wAAAAAAAAAAAH8AAAAAH////38A/B/AgAAAAAAAAAAAfgAAAAH/////wAcAfggAAAAAAAAAA" +
  "AHgAAAAH/////AAcAfg4AAAAAAAAAAABj7AAAD/////wAMAXhYAAAAAAAAAAAA//AAAB/////wAOASBYAAAAAAAAAAAAX/gA" +
  "AB/////gACAYCcAAAAAAAAAAAAH/8AAAfP///gAAAsHAAAAAAAAAAAAAP/+AAAAB///AAAA8eAAAAAAAAAAAAAP/+AAAAD//" +
  "+AAAAc+yAAAAAAAAAAAAf//wAAAD//4AAAAM/hgAAAAAAAAAAAf//+AAAB//4AAAAOfn/AAAAAAAAAAAf///gAAB//wAAAAG" +
  "AQfsAAAAAAAAAAf///gAAA//wAAAAB4AXxwAAAAAAAAAP///gAAA//wAAAAAH8DYIAAAAAAAAAH///AAAA//wAAAAAAAhEAA" +
  "AAAAAAAAH//+AAAA//4gAAAAAB5AAAAAAAAAAAD//+AAAA//5gAAAAAPxgBAAAAAAAAAB//+AAAA//zgAAAAAf/gBBAAAAAA" +
  "AAAf/8AAAA//DgAAAAA//wAAAAAAAAAAAf/8AAAA//HgAAAAH//4CAAAAAAAAAAf/4AAAAf/HAAAAAH//8AAAAAAAAAAA//g" +
  "AAAAf/DAAAAAP//+AAAAAAAAAAA//AAAAAf+AAAAAAP//+AAAAAAAAAAA//AAAAAP8AAAAAAH//+AAAAAAAAAAA/+AAAAAP8" +
  "AAAAAAH//+AAAAAAAAAAA/8AAAAAH4AAAAAAH4f8AAAAAAAAAAB/4AAAAAGAAAAAAADAf8AIAAAAAAAAB/wAAAAAAAAAAAAA" +
  "AAD4AEAAAAAAAAB/gAAAAAAAAAAAAAAABwAGAAAAAAAAB+AAAAAAAAAAAAAAAAAwAOAAAAAAAAB+AAAAAAAAAAAAAAAAAwAY" +
  "AAAAAAAAD8AAAAAAAAAAAAAAAAAABwAAAAAAAAD4AAAAAAAAAAAAAAAAAAAgAAAAAAAAD4AAAAAAAAAACAAAAAAAAAAAAAAA" +
  "AADxgAAAAAAAAAAAAAAAAAAAAAAAAAAABwAAAAAAAAAAAAAAAAAAAAAAAAAAAAA4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
  "AAAAAAAAAAAAAAAAAAA=";

const COLS = 180;
const ROWS = 71;
const LON_MIN = -180;
const LON_MAX = 180;
const LAT_MAX = 84;
const LAT_MIN = -58;

/** Aspect ratio of the rendered map — use for the containing slot. */
export const DOTTED_MAP_ASPECT = COLS / ROWS;

function decodeMask(): Uint8Array {
  const bin =
    typeof atob === "function"
      ? atob(LAND_MASK_B64)
      : Buffer.from(LAND_MASK_B64, "base64").toString("binary");
  const cells = new Uint8Array(COLS * ROWS);
  for (let i = 0; i < cells.length; i++) {
    const byte = bin.charCodeAt(i >> 3);
    cells[i] = (byte >> (7 - (i & 7))) & 1;
  }
  return cells;
}

const LAND = decodeMask();

function isLandCell(col: number, row: number) {
  if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return false;
  return LAND[row * COLS + col] === 1;
}

function lonLatToCell(lon: number, lat: number) {
  return {
    x: ((lon - LON_MIN) / (LON_MAX - LON_MIN)) * COLS,
    y: ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN)) * ROWS,
  };
}

type CityPulse = { name?: string; lon: number; lat: number };

const DEFAULT_CITIES: CityPulse[] = [
  { name: "Los Angeles", lon: -118.2, lat: 34.05 },
  { name: "Toronto", lon: -79.4, lat: 43.7 },
  { name: "New York", lon: -74.0, lat: 40.7 },
  { name: "Mexico City", lon: -99.1, lat: 19.4 },
  { name: "S\u00e3o Paulo", lon: -46.6, lat: -23.5 },
  { name: "London", lon: -0.13, lat: 51.5 },
  { name: "Berlin", lon: 13.4, lat: 52.5 },
  { name: "Lagos", lon: 3.4, lat: 6.5 },
  { name: "Nairobi", lon: 36.8, lat: -1.3 },
  { name: "Cape Town", lon: 18.4, lat: -33.9 },
  { name: "Dubai", lon: 55.3, lat: 25.2 },
  { name: "Delhi", lon: 77.2, lat: 28.6 },
  { name: "Singapore", lon: 103.8, lat: 1.35 },
  { name: "Seoul", lon: 127.0, lat: 37.55 },
  { name: "Tokyo", lon: 139.7, lat: 35.7 },
  { name: "Sydney", lon: 151.2, lat: -33.9 },
];

/**
 * Density per breakpoint: mobile samples every other cell (and draws fatter
 * dots) so the silhouette stays legible at ~360px wide; desktop uses the full
 * grid. SSR renders the desktop grid, then the effect narrows it if needed.
 */
function useDensity() {
  const [stride, setStride] = useState(1);
  useEffect(() => {
    const mobile = window.matchMedia("(max-width: 639px)");
    const apply = () => setStride(mobile.matches ? 2 : 1);
    apply();
    mobile.addEventListener("change", apply);
    return () => mobile.removeEventListener("change", apply);
  }, []);
  return stride;
}

export function DottedMap({
  className = "",
  dotColor = "rgba(226,226,226,0.32)",
  pulseColor = "#F0A968",
  cities = DEFAULT_CITIES,
  step = 10,
}: {
  className?: string;
  dotColor?: string;
  pulseColor?: string;
  cities?: CityPulse[];
  step?: number;
}) {
  const stride = useDensity();

  const dots = useMemo(() => {
    const out: Array<{ x: number; y: number }> = [];
    for (let y = 0; y < ROWS; y += stride) {
      for (let x = 0; x < COLS; x += stride) {
        // With a stride, treat the block as land if any cell in it is land,
        // so thin features (Japan, the UK, Indonesia) survive downsampling.
        let land = false;
        for (let dy = 0; dy < stride && !land; dy++)
          for (let dx = 0; dx < stride && !land; dx++)
            if (isLandCell(x + dx, y + dy)) land = true;
        if (land) out.push({ x, y });
      }
    }
    return out;
  }, [stride]);

  const width = COLS * step;
  const height = ROWS * step;
  const r = Math.max(1.2, step * (stride > 1 ? 0.3 : 0.2) * stride);

  const pulses = cities.map((c) => {
    const { x, y } = lonLatToCell(c.lon, c.lat);
    return { cx: x * step, cy: y * step, name: c.name };
  });

  const equatorY = ((LAT_MAX - 0) / (LAT_MAX - LAT_MIN)) * height;

  return (
    <svg
      aria-hidden="true"
      viewBox={`0 0 ${width} ${height}`}
      className={`w-full h-auto ${className}`}
      preserveAspectRatio="xMidYMid meet"
    >
      <line
        x1="0"
        x2={width}
        y1={equatorY}
        y2={equatorY}
        stroke="rgba(226,226,226,0.05)"
        strokeDasharray="2 6"
      />
      {dots.map((d, i) => (
        <circle
          key={i}
          cx={d.x * step + (step * stride) / 2}
          cy={d.y * step + (step * stride) / 2}
          r={r}
          fill={dotColor}
        />
      ))}
      {pulses.map((p, i) => (
        <g key={`p-${i}`}>
          <circle
            cx={p.cx}
            cy={p.cy}
            r={r * 2.6}
            fill={pulseColor}
            opacity={0.95}
            style={{ filter: `drop-shadow(0 0 10px ${pulseColor})` }}
          />
          <circle
            cx={p.cx}
            cy={p.cy}
            r={r * 2.6}
            fill="none"
            stroke={pulseColor}
            strokeOpacity={0.6}
            style={{
              transformOrigin: `${p.cx}px ${p.cy}px`,
              animation: `map-pulse 3.4s ease-out ${i * 0.35}s infinite`,
            }}
          />
        </g>
      ))}
    </svg>
  );
}
