"use client";

import { useEffect } from "react";

export default function ServiceWorkerRegister() {
  useEffect(() => {
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {
        // silencioso: la PWA funciona igual sin SW si el registro falla
      });
    }
  }, []);
  return null;
}
