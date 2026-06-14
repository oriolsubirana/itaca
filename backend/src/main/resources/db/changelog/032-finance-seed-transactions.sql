--liquibase formatted sql

--changeset oriol:finance-seed-002
--comment Dev sample data so the Finanzas dashboard renders: finpension account, balances and 3 months of categorized transactions (CHF + EUR). Replaced once real CSV imports land.
INSERT INTO accounts (name, type, currency) VALUES ('finpension', 'investment', 'CHF');

INSERT INTO balance_snapshots (account_id, date, balance)
SELECT a.id, v.date, v.balance
FROM accounts a, (VALUES
    ('Neon',       '2026-06-14'::date,  3240.50),
    ('Revolut',    '2026-06-14'::date,   580.00),
    ('finpension', '2024-05-28'::date,  6800.00),
    ('Sabadell',   '2026-06-14'::date,  2180.00),
    ('MyInvestor', '2026-06-14'::date,  9650.00)
) AS v(name, date, balance)
WHERE a.name = v.name;

INSERT INTO transactions (account_id, date, amount, description, category)
SELECT a.id, v.date, v.amount, v.description, v.category
FROM accounts a, (VALUES
    -- Neon (CHF, corriente)
    ('Neon', '2026-04-25'::date,  5400.00, 'Nómina',                'income'),
    ('Neon', '2026-04-01',       -1450.00, 'Alquiler',              'housing'),
    ('Neon', '2026-04-03',         -88.20, 'Coop',                  'groceries'),
    ('Neon', '2026-04-09',         -42.50, 'Restaurante Luigi',     'restaurants'),
    ('Neon', '2026-04-05',         -45.00, 'SBB',                   'transport'),
    ('Neon', '2026-04-05',        -500.00, 'Aportación finpension', 'investment'),
    ('Neon', '2026-04-04',         -12.95, 'Spotify',               'subscriptions'),
    ('Neon', '2026-04-15',         -75.00, 'Decathlon',             'shopping'),
    ('Neon', '2026-05-25',        5400.00, 'Nómina',                'income'),
    ('Neon', '2026-05-01',       -1450.00, 'Alquiler',              'housing'),
    ('Neon', '2026-05-06',         -94.30, 'Migros',                'groceries'),
    ('Neon', '2026-05-11',         -36.00, 'Restaurante Sushi',     'restaurants'),
    ('Neon', '2026-05-02',         -59.00, 'Swisscom',              'utilities'),
    ('Neon', '2026-05-05',        -500.00, 'Aportación finpension', 'investment'),
    ('Neon', '2026-05-04',         -12.95, 'Spotify',               'subscriptions'),
    ('Neon', '2026-05-18',         -28.40, 'Farmacia',              'health'),
    ('Neon', '2026-06-25',        5400.00, 'Nómina',                'income'),
    ('Neon', '2026-06-01',       -1450.00, 'Alquiler',              'housing'),
    ('Neon', '2026-06-03',         -92.30, 'Coop',                  'groceries'),
    ('Neon', '2026-06-12',         -64.10, 'Migros',                'groceries'),
    ('Neon', '2026-06-08',         -38.00, 'Restaurante Luigi',     'restaurants'),
    ('Neon', '2026-06-05',         -45.00, 'SBB',                   'transport'),
    ('Neon', '2026-06-05',        -500.00, 'Aportación finpension', 'investment'),
    ('Neon', '2026-06-02',         -59.00, 'Swisscom',              'utilities'),
    ('Neon', '2026-06-04',         -12.95, 'Spotify',               'subscriptions'),
    ('Neon', '2026-06-10',         -23.50, 'Farmacia',              'health'),
    ('Neon', '2026-06-15',         -89.90, 'Zara',                  'shopping'),
    -- Revolut (CHF, corriente)
    ('Revolut', '2026-04-14',      -22.00, 'Uber',                  'transport'),
    ('Revolut', '2026-04-20',      -54.00, 'Cine + cena',           'leisure'),
    ('Revolut', '2026-05-16',      -31.50, 'Uber Eats',             'restaurants'),
    ('Revolut', '2026-05-22',      -48.00, 'Concierto',             'leisure'),
    ('Revolut', '2026-06-13',      -27.80, 'Uber',                  'transport'),
    ('Revolut', '2026-06-18',      -65.00, 'Escapada finde',        'leisure'),
    -- Sabadell (EUR, corriente)
    ('Sabadell', '2026-04-26',    1200.00, 'Ingreso freelance',     'income'),
    ('Sabadell', '2026-04-02',     -78.00, 'Mercadona',             'groceries'),
    ('Sabadell', '2026-04-10',     -45.00, 'Restaurante',           'restaurants'),
    ('Sabadell', '2026-04-07',     -39.99, 'Endesa',                'utilities'),
    ('Sabadell', '2026-04-12',    -300.00, 'Aportación MyInvestor', 'investment'),
    ('Sabadell', '2026-05-26',    1200.00, 'Ingreso freelance',     'income'),
    ('Sabadell', '2026-05-03',     -82.50, 'Mercadona',             'groceries'),
    ('Sabadell', '2026-05-09',     -52.00, 'Cena amigos',           'restaurants'),
    ('Sabadell', '2026-05-15',     -60.00, 'Gasolina',              'fuel'),
    ('Sabadell', '2026-05-12',    -300.00, 'Aportación MyInvestor', 'investment'),
    ('Sabadell', '2026-06-26',    1200.00, 'Ingreso freelance',     'income'),
    ('Sabadell', '2026-06-04',     -88.30, 'Mercadona',             'groceries'),
    ('Sabadell', '2026-06-09',     -41.00, 'Restaurante',           'restaurants'),
    ('Sabadell', '2026-06-07',     -39.99, 'Endesa',                'utilities'),
    ('Sabadell', '2026-06-14',     -55.00, 'Gasolina',              'fuel'),
    ('Sabadell', '2026-06-12',    -300.00, 'Aportación MyInvestor', 'investment')
) AS v(name, date, amount, description, category)
WHERE a.name = v.name;
--rollback DELETE FROM transactions; DELETE FROM balance_snapshots; DELETE FROM accounts WHERE name = 'finpension';
