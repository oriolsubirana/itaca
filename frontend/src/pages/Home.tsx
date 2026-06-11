import { Link } from "@tanstack/react-router";
import { PageTitle } from "../components/PageTitle";

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="border-t border-line py-5">
      <h2 className="mb-1 text-xs uppercase tracking-[0.15em] text-ink-soft">
        {title}
      </h2>
      <div className="text-sm leading-relaxed">{children}</div>
    </section>
  );
}

export function Home() {
  return (
    <>
      <PageTitle>Ítaca</PageTitle>

      <Link
        to="/chat"
        className="mb-8 flex min-h-14 items-center justify-between rounded-lg bg-ink px-5 text-paper"
      >
        <span className="text-sm">Hablar con Claude</span>
        <span aria-hidden>→</span>
      </Link>

      <Card title="Gym">
        Próxima rutina: Pull · Última sesión: Push (9 jun)
      </Card>
      <Card title="Salud">Estado EII del último mes — pendiente de datos</Card>
      <Card title="Finanzas">Patrimonio neto — pendiente de datos</Card>
      <Card title="Semana">Días de gym y bici — pendiente de datos</Card>
    </>
  );
}
