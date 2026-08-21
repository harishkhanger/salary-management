// Minimal inline SVG icon set (stroke style, 24px viewBox) — no icon library.

const base = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
} as const

export const HomeIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <path d="M3.5 11.5 12 4l8.5 7.5M6 10v9.5h4.5v-5h3v5H18V10" />
  </svg>
)

export const ChartIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <path d="M4 20V10M10 20V4M16 20v-7M21 20H3" />
  </svg>
)

export const TeamIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <circle cx="9" cy="8" r="3.2" />
    <path d="M3.5 19c.6-3 2.8-4.5 5.5-4.5S13.9 16 14.5 19M16 5.6a3.2 3.2 0 0 1 0 4.8M17.5 14.7c1.7.6 2.6 1.9 3 4.3" />
  </svg>
)

export const RiseIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <path d="M3 17l6-6 4 4 8-8M15 7h6v6" />
  </svg>
)

export const QueueIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <path d="M4 6h16M4 12h16M4 18h10" />
    <circle cx="19" cy="18" r="2.4" />
  </svg>
)

export const CoinsIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <ellipse cx="12" cy="6" rx="7" ry="3" />
    <path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6" />
  </svg>
)

export const AuditIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <path d="M8 3h8l4 4v14H4V3h4zM15 3v5h5" />
    <path d="M8 13h8M8 17h5" />
  </svg>
)

export const GearIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.4-2.3 1a7 7 0 0 0-2-1.2L14.2 3h-4l-.4 2.7a7 7 0 0 0-2 1.2l-2.3-1-2 3.4 2 1.5a7 7 0 0 0 0 2.4l-2 1.5 2 3.4 2.3-1a7 7 0 0 0 2 1.2l.4 2.7h4l.4-2.7a7 7 0 0 0 2-1.2l2.3 1 2-3.4-2-1.5c.06-.4.1-.8.1-1.2z" />
  </svg>
)

export const LogoutIcon = () => (
  <svg viewBox="0 0 24 24" {...base}>
    <path d="M14 4H6v16h8M10 12h11M18 8.5L21.5 12 18 15.5" />
  </svg>
)
