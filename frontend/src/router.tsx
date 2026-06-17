import {
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";
import { Layout } from "./components/Layout";
import { Home } from "./pages/Home";
import { Chat } from "./pages/Chat";
import { Salud } from "./pages/Salud";
import { Gym } from "./pages/Gym";
import { Finanzas } from "./pages/Finanzas";
import { Entradas } from "./pages/Entradas";

const rootRoute = createRootRoute({ component: Layout });

const routes = [
  createRoute({ getParentRoute: () => rootRoute, path: "/", component: Home }),
  createRoute({
    getParentRoute: () => rootRoute,
    path: "/chat",
    component: Chat,
    validateSearch: (s: Record<string, unknown>): { seed?: string; workout?: boolean } => ({
      seed: typeof s.seed === "string" ? s.seed : undefined,
      workout: s.workout === true || s.workout === "true",
    }),
  }),
  createRoute({ getParentRoute: () => rootRoute, path: "/salud", component: Salud }),
  createRoute({ getParentRoute: () => rootRoute, path: "/gym", component: Gym }),
  createRoute({ getParentRoute: () => rootRoute, path: "/finanzas", component: Finanzas }),
  createRoute({ getParentRoute: () => rootRoute, path: "/entradas", component: Entradas }),
];

export const router = createRouter({
  routeTree: rootRoute.addChildren(routes),
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
