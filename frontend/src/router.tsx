import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from "@tanstack/react-router";
import { TabBar } from "./components/TabBar";
import { Home } from "./pages/Home";
import { Chat } from "./pages/Chat";
import { Salud } from "./pages/Salud";
import { Gym } from "./pages/Gym";
import { Finanzas } from "./pages/Finanzas";

function Layout() {
  return (
    <div className="min-h-dvh bg-paper text-ink">
      <main className="mx-auto max-w-2xl px-5 pt-6 pb-28">
        <Outlet />
      </main>
      <TabBar />
    </div>
  );
}

const rootRoute = createRootRoute({ component: Layout });

const routes = [
  createRoute({ getParentRoute: () => rootRoute, path: "/", component: Home }),
  createRoute({ getParentRoute: () => rootRoute, path: "/chat", component: Chat }),
  createRoute({ getParentRoute: () => rootRoute, path: "/salud", component: Salud }),
  createRoute({ getParentRoute: () => rootRoute, path: "/gym", component: Gym }),
  createRoute({ getParentRoute: () => rootRoute, path: "/finanzas", component: Finanzas }),
];

export const router = createRouter({
  routeTree: rootRoute.addChildren(routes),
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
