--liquibase formatted sql

--changeset oriol:training-seed-001
--comment Ejercicios y rutinas Push/Pull/Leg
INSERT INTO exercises (nombre, grupo_muscular) VALUES
    ('Press Inclinado',             'pecho'),
    ('Press Plano',                 'pecho'),
    ('Press Militar',               'hombro'),
    ('Tríceps en polea',            'tríceps'),
    ('Jalón al pecho',              'espalda'),
    ('Remo en polea',               'espalda'),
    ('Facepull',                    'hombro posterior'),
    ('Elevación lateral en polea',  'hombro'),
    ('Curl de bíceps',              'bíceps'),
    ('Sentadilla con barra',        'pierna');

INSERT INTO routines (nombre) VALUES ('Push'), ('Pull'), ('Leg');

INSERT INTO routine_exercises (routine_id, exercise_id, posicion)
SELECT r.id, e.id, v.posicion
FROM (VALUES
    ('Push', 'Press Inclinado',            1),
    ('Push', 'Press Plano',                2),
    ('Push', 'Press Militar',              3),
    ('Push', 'Tríceps en polea',           4),
    ('Pull', 'Jalón al pecho',             1),
    ('Pull', 'Remo en polea',              2),
    ('Pull', 'Facepull',                   3),
    ('Pull', 'Elevación lateral en polea', 4),
    ('Pull', 'Curl de bíceps',             5),
    ('Leg',  'Sentadilla con barra',       1)
) AS v(rutina, ejercicio, posicion)
JOIN routines r ON r.nombre = v.rutina
JOIN exercises e ON e.nombre = v.ejercicio;

--changeset oriol:training-seed-002
--comment Últimas sesiones de la rotación: Pull y Leg previas, Push la última completada (próxima: Pull)
INSERT INTO workouts (fecha, routine_id, completado)
SELECT v.fecha::date, r.id, TRUE
FROM (VALUES
    ('2026-06-04', 'Pull'),
    ('2026-06-06', 'Leg'),
    ('2026-06-09', 'Push')
) AS v(fecha, rutina)
JOIN routines r ON r.nombre = v.rutina;

-- 3 series de trabajo por ejercicio con el último peso/reps registrado
INSERT INTO sets (workout_id, exercise_id, peso, reps, orden)
SELECT w.id, e.id, v.peso, v.reps, (v.posicion - 1) * 3 + s.n
FROM (VALUES
    ('Pull', 'Jalón al pecho',             45.0, 12, 1),
    ('Pull', 'Remo en polea',              45.0, 10, 2),
    ('Pull', 'Facepull',                   17.5, 12, 3),
    ('Pull', 'Elevación lateral en polea',  6.0,  6, 4),
    ('Pull', 'Curl de bíceps',             15.0, 12, 5),
    ('Leg',  'Sentadilla con barra',       50.0,  5, 1),
    ('Push', 'Press Inclinado',            50.0,  9, 1),
    ('Push', 'Press Plano',                50.0, 10, 2),
    ('Push', 'Press Militar',              14.0,  6, 3),
    ('Push', 'Tríceps en polea',           12.5,  8, 4)
) AS v(rutina, ejercicio, peso, reps, posicion)
JOIN routines r ON r.nombre = v.rutina
JOIN workouts w ON w.routine_id = r.id
JOIN exercises e ON e.nombre = v.ejercicio
CROSS JOIN (VALUES (1), (2), (3)) AS s(n);
