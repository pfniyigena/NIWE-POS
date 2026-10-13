import { Injectable, effect, signal } from '@angular/core';
import { AuthService } from './auth.service';

export interface ThemeOption {
  id: string;
  label: string;
  dark: boolean;
  /** Preview swatches: page background, card surface, raised surface. */
  swatch: [string, string, string];
}

/** The ready-made themes. 'navy' is the original look and the default; the rest are defined in styles.scss. */
export const THEMES: ThemeOption[] = [
  { id: 'navy', label: 'Navy (default)', dark: true, swatch: ['#12151f', '#1b2030', '#232a3d'] },
  { id: 'charcoal', label: 'Charcoal', dark: true, swatch: ['#121212', '#1c1c1e', '#262628'] },
  { id: 'black', label: 'True black', dark: true, swatch: ['#000000', '#0d0d0f', '#17171a'] },
  { id: 'slate', label: 'Slate', dark: true, swatch: ['#0f172a', '#1e293b', '#273449'] },
  { id: 'forest', label: 'Forest', dark: true, swatch: ['#0f1a15', '#16251e', '#1e3228'] },
  { id: 'plum', label: 'Plum', dark: true, swatch: ['#17121f', '#211a2c', '#2b2238'] },
  { id: 'warm', label: 'Warm brown', dark: true, swatch: ['#1a1512', '#251e19', '#30271f'] },
  { id: 'white', label: 'Clean white', dark: false, swatch: ['#f5f6f8', '#ffffff', '#eef0f4'] },
  { id: 'paper', label: 'Warm paper', dark: false, swatch: ['#faf7f2', '#ffffff', '#f1ece3'] },
  { id: 'grey', label: 'Soft grey', dark: false, swatch: ['#e9ecf1', '#f7f8fa', '#dde1e8'] },
  { id: 'sky', label: 'Sky blue', dark: false, swatch: ['#e3f2fc', '#ffffff', '#d3e9f8'] },
];

/** The user's own colours for the "Custom" theme: a page background and an accent (buttons, links, highlights). */
export interface CustomColours {
  bg: string;
  accent: string;
}

/** Starting point for Custom: a sky-blue page with a blue accent. */
export const DEFAULT_CUSTOM: CustomColours = { bg: '#dbeafe', accent: '#2563eb' };

export const CUSTOM_ID = 'custom';
const DEFAULT_THEME = 'navy';
/** Last theme used in this browser — shown on the login screen and before anyone has signed in. */
const LAST_KEY = 'pos.theme.last';
const LAST_CUSTOM_KEY = 'pos.theme.last-custom';
const userKey = (userId: number) => `pos.theme.user.${userId}`;
const userCustomKey = (userId: number) => `pos.theme.custom.${userId}`;

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage blocked (private mode...) — the theme still applies for this session.
  }
}

// ---- colour maths for the Custom theme -------------------------------------------------------------

const HEX = /^#[0-9a-fA-F]{6}$/;

export function isHex(value: string): boolean {
  return HEX.test(value);
}

function rgb(hex: string): [number, number, number] {
  return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
}

function toHex(c: [number, number, number]): string {
  return '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
}

/** Blend {@code a} towards {@code b}: amount 0 = a, 1 = b. */
function mix(a: string, b: string, amount: number): string {
  const x = rgb(a);
  const y = rgb(b);
  return toHex([x[0] + (y[0] - x[0]) * amount, x[1] + (y[1] - x[1]) * amount, x[2] + (y[2] - x[2]) * amount]);
}

/** WCAG relative luminance, 0 (black) to 1 (white). */
function luminance(hex: string): number {
  const [r, g, b] = rgb(hex).map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** WCAG contrast ratio between two colours (1 to 21). */
export function contrastRatio(a: string, b: string): number {
  const l1 = luminance(a);
  const l2 = luminance(b);
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
}

/** Every CSS variable the Custom theme sets, so they can all be cleared when the user switches away. */
const CUSTOM_VARS = [
  '--bg', '--surface', '--surface-alt', '--border', '--text', '--muted', '--accent', '--accent-hover', '--accent-contrast',
  '--tab-inactive', '--tab-text', '--tab-active-bg', '--tab-active-text', '--tab-hover-bg', '--tab-hover-text',
];

/** Derives the full set of theme variables from two colours. Light/dark is chosen from the background's brightness. */
export function customVariables(colours: CustomColours): { light: boolean; vars: Record<string, string> } {
  const light = luminance(colours.bg) > 0.4;
  const { bg, accent } = colours;
  const accentText = luminance(accent) > 0.45 ? '#0b1220' : '#ffffff';
  const vars: Record<string, string> = light
    ? {
        '--bg': bg,
        '--surface': mix(bg, '#ffffff', 0.7),
        '--surface-alt': mix(bg, '#000000', 0.06),
        '--border': 'rgba(0, 0, 0, 0.12)',
        '--text': '#1f2430',
        '--muted': '#5b6475',
        '--tab-inactive': mix(bg, '#000000', 0.1),
        '--tab-text': '#2b3345',
        '--tab-active-bg': '#ffffff',
        '--tab-active-text': '#1f2430',
        '--tab-hover-bg': mix(bg, '#000000', 0.18),
        '--tab-hover-text': '#111827',
      }
    : {
        '--bg': bg,
        '--surface': mix(bg, '#ffffff', 0.06),
        '--surface-alt': mix(bg, '#ffffff', 0.12),
        '--border': 'rgba(255, 255, 255, 0.1)',
        '--text': '#e7e9f0',
        '--muted': '#9aa3b6',
        '--tab-inactive': mix(bg, '#ffffff', 0.2),
        '--tab-text': '#e5e3f1',
        '--tab-active-bg': '#ffffff',
        '--tab-active-text': mix(bg, '#000000', 0.2),
        '--tab-hover-bg': mix(bg, '#ffffff', 0.3),
        '--tab-hover-text': '#ffffff',
      };
  vars['--accent'] = accent;
  vars['--accent-hover'] = mix(accent, light ? '#000000' : '#ffffff', 0.18);
  vars['--accent-contrast'] = accentText;
  return { light, vars };
}

/**
 * Per-user colour theme. The choice is remembered in this browser under the signed-in user's id, so two people
 * sharing a till each get their own look, and it follows the user on login/logout. It is applied as
 * {@code <html data-theme="...">}; the matching variables live in styles.scss. The "Custom" theme instead sets the
 * variables inline from the user's two chosen colours (see {@link customVariables}). Stored per browser, not on the
 * server, so a user who signs in on another device picks the theme again there.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly current = signal<string>(DEFAULT_THEME);
  readonly custom = signal<CustomColours>({ ...DEFAULT_CUSTOM });

  constructor(private auth: AuthService) {
    this.load(null);
    effect(
      () => {
        const user = this.auth.currentUser();
        this.load(user ? user.userId : null);
      },
      { allowSignalWrites: true }
    );
  }

  /** Switch theme for the signed-in user (and remember it as this browser's last theme). */
  select(id: string): void {
    const theme = id === CUSTOM_ID || THEMES.some((t) => t.id === id) ? id : DEFAULT_THEME;
    const user = this.auth.currentUser();
    if (user) write(userKey(user.userId), theme);
    write(LAST_KEY, theme);
    this.current.set(theme);
    this.apply();
  }

  /** Save and apply the user's own colours (also switches to the Custom theme). Ignores invalid hex codes. */
  setCustom(colours: CustomColours): void {
    if (!isHex(colours.bg) || !isHex(colours.accent)) return;
    const clean = { bg: colours.bg.toLowerCase(), accent: colours.accent.toLowerCase() };
    const json = JSON.stringify(clean);
    const user = this.auth.currentUser();
    if (user) write(userCustomKey(user.userId), json);
    write(LAST_CUSTOM_KEY, json);
    this.custom.set(clean);
    this.select(CUSTOM_ID);
  }

  private load(userId: number | null): void {
    const storedTheme = userId === null ? read(LAST_KEY) : read(userKey(userId));
    const storedCustom = userId === null ? read(LAST_CUSTOM_KEY) : read(userCustomKey(userId));
    this.custom.set(this.parseCustom(storedCustom));
    this.current.set(storedTheme === CUSTOM_ID || THEMES.some((t) => t.id === storedTheme) ? (storedTheme as string) : DEFAULT_THEME);
    this.apply();
  }

  private parseCustom(json: string | null): CustomColours {
    try {
      const value = json ? (JSON.parse(json) as Partial<CustomColours>) : null;
      if (value && typeof value.bg === 'string' && typeof value.accent === 'string' && isHex(value.bg) && isHex(value.accent)) {
        return { bg: value.bg, accent: value.accent };
      }
    } catch {
      // fall through to the default
    }
    return { ...DEFAULT_CUSTOM };
  }

  private apply(): void {
    const root = document.documentElement;
    for (const name of CUSTOM_VARS) root.style.removeProperty(name);
    const id = this.current();
    if (id === CUSTOM_ID) {
      const { light, vars } = customVariables(this.custom());
      // A light custom theme borrows the light status colours (alerts, badges) from the 'white' theme block.
      if (light) root.setAttribute('data-theme', 'white');
      else root.removeAttribute('data-theme');
      for (const [name, value] of Object.entries(vars)) root.style.setProperty(name, value);
    } else if (id === DEFAULT_THEME) {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', id);
    }
  }
}
