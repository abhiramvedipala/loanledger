import { PointerEvent, useState } from "react";
import { money, ScheduleRow } from "./api";

const WIDTH = 720;
const HEIGHT = 240;
const LEFT = 64;
const RIGHT = 24;
const TOP = 12;
const BOTTOM = 28;

/** Scheduled balance over the term. The schedule table below it is the text version of the same data. */
export default function BalanceChart({ rows }: { rows: ScheduleRow[] }) {
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(...rows.map((r) => r.balance));
  const x = (i: number) => LEFT + (i / Math.max(rows.length - 1, 1)) * (WIDTH - LEFT - RIGHT);
  const y = (balance: number) => TOP + (1 - balance / max) * (HEIGHT - TOP - BOTTOM);
  const line = rows.map((r, i) => `${i ? "L" : "M"}${x(i).toFixed(1)} ${y(r.balance).toFixed(1)}`).join(" ");

  function onMove(event: PointerEvent<SVGSVGElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const svgX = ((event.clientX - box.left) / box.width) * WIDTH;
    const i = Math.round(((svgX - LEFT) / (WIDTH - LEFT - RIGHT)) * (rows.length - 1));
    setHover(Math.min(Math.max(i, 0), rows.length - 1));
  }

  const row = hover === null ? null : rows[hover];
  const yearTicks = rows.filter((r) => r.number % 60 === 0);
  return (
    <figure className="chart">
      <figcaption>Scheduled balance</figcaption>
      <div className="chart-plot">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          role="img"
          aria-label={`Scheduled balance falling from ${money(max)} to ${money(rows[rows.length - 1].balance)} over ${rows.length} months`}
          onPointerMove={onMove}
          onPointerLeave={() => setHover(null)}
        >
          {[0, 0.5, 1].map((share) => (
            <g key={share}>
              <line className="grid" x1={LEFT} x2={WIDTH - RIGHT} y1={y(max * share)} y2={y(max * share)} />
              <text className="tick" x={LEFT - 8} y={y(max * share) + 4} textAnchor="end">
                ${Math.round((max * share) / 1000)}k
              </text>
            </g>
          ))}
          {yearTicks.map((r) => (
            <text key={r.number} className="tick" x={x(r.number - 1)} y={HEIGHT - 8} textAnchor="middle">
              {r.dueDate.slice(0, 4)}
            </text>
          ))}
          <path className="series" d={line} />
          {row && hover !== null && (
            <>
              <line className="crosshair" x1={x(hover)} x2={x(hover)} y1={TOP} y2={HEIGHT - BOTTOM} />
              <circle className="marker" cx={x(hover)} cy={y(row.balance)} r={4} />
            </>
          )}
        </svg>
        {row && hover !== null && (
          <div className="tooltip" style={{ left: `${(x(hover) / WIDTH) * 100}%` }}>
            <strong>{money(row.balance)}</strong>
            <br />
            {row.dueDate} · payment {row.number}
          </div>
        )}
      </div>
    </figure>
  );
}
