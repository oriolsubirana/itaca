/**
 * Light/dark appearance. The initial theme is applied before React mounts by the inline script in
 * index.html (no flash); this module is the runtime switch used by the Apariencia setting. Default
 * is dark, persisted in localStorage and reflected on `html.dark` + the theme-color meta.
 */
export type Theme = "light" | "dark";

const KEY = "itaca-theme";
const DARK_BG = "#1b1a18";
const LIGHT_BG = "#faf9f7";

export function getTheme(): Theme {
  try {
    return localStorage.getItem(KEY) === "light" ? "light" : "dark";
  } catch {
    return "dark";
  }
}

export function setTheme(theme: Theme): void {
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    // ignore (private mode): the class still applies for this session
  }
  document.documentElement.classList.toggle("dark", theme === "dark");
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute("content", theme === "dark" ? DARK_BG : LIGHT_BG);
}
