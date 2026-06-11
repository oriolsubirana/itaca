--liquibase formatted sql

--changeset oriol:training-seed-001
--comment Exercises and Push/Pull/Leg routines (exercise names are UI text, kept in Spanish)
INSERT INTO exercises (name, muscle_group) VALUES
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

INSERT INTO routines (name) VALUES ('Push'), ('Pull'), ('Leg');

INSERT INTO routine_exercises (routine_id, exercise_id, position)
SELECT r.id, e.id, v.position
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
) AS v(routine, exercise, position)
JOIN routines r ON r.name = v.routine
JOIN exercises e ON e.name = v.exercise;

--changeset oriol:training-seed-002
--comment Latest sessions of the rotation: previous Pull and Leg, Push as last completed (next: Pull)
INSERT INTO workouts (date, routine_id, completed)
SELECT v.date::date, r.id, TRUE
FROM (VALUES
    ('2026-06-04', 'Pull'),
    ('2026-06-06', 'Leg'),
    ('2026-06-09', 'Push')
) AS v(date, routine)
JOIN routines r ON r.name = v.routine;

-- 3 working sets per exercise with the last recorded weight/reps
INSERT INTO sets (workout_id, exercise_id, weight_kg, reps, position)
SELECT w.id, e.id, v.weight_kg, v.reps, (v.exercise_position - 1) * 3 + s.n
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
) AS v(routine, exercise, weight_kg, reps, exercise_position)
JOIN routines r ON r.name = v.routine
JOIN workouts w ON w.routine_id = r.id
JOIN exercises e ON e.name = v.exercise
CROSS JOIN (VALUES (1), (2), (3)) AS s(n);
