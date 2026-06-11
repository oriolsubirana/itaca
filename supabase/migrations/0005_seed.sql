-- Ítaca · Seed inicial con los datos actuales de Oriol
-- Rotación Push/Pull/Leg. Última completada: Push. Próxima: Pull.

-- ── Ejercicios ──────────────────────────────────────────────────────────────
insert into exercises (nombre, grupo_muscular) values
  ('Press Inclinado',       'Pecho'),
  ('Press Plano',           'Pecho'),
  ('Press Militar',         'Hombro'),
  ('Tríceps',               'Tríceps'),
  ('Jalón',                 'Espalda'),
  ('Remo Polea',            'Espalda'),
  ('Facepull',              'Hombro posterior'),
  ('Elev. lateral polea',   'Hombro'),
  ('Bíceps',                'Bíceps'),
  ('Sentadilla barra',      'Pierna')
on conflict (nombre) do nothing;

-- ── Rutinas (ejercicios ordenados) ──────────────────────────────────────────
insert into routines (nombre, exercise_ids) values
  ('Push', array[
     (select id from exercises where nombre = 'Press Inclinado'),
     (select id from exercises where nombre = 'Press Plano'),
     (select id from exercises where nombre = 'Press Militar'),
     (select id from exercises where nombre = 'Tríceps')
   ]),
  ('Pull', array[
     (select id from exercises where nombre = 'Jalón'),
     (select id from exercises where nombre = 'Remo Polea'),
     (select id from exercises where nombre = 'Facepull'),
     (select id from exercises where nombre = 'Elev. lateral polea'),
     (select id from exercises where nombre = 'Bíceps')
   ]),
  ('Leg', array[
     (select id from exercises where nombre = 'Sentadilla barra')
   ])
on conflict (nombre) do nothing;

-- ── Última sesión completada: Push ──────────────────────────────────────────
with w as (
  insert into workouts (fecha, routine_id, completado, notas)
  values (current_date - 2, (select id from routines where nombre = 'Push'), true, 'Sesión de referencia (seed)')
  returning id
)
insert into sets (workout_id, exercise_id, peso, reps, orden)
select w.id, e.id, v.peso, v.reps, v.orden
from w
join (values
  ('Press Inclinado', 50.0, 9, 0),
  ('Press Plano',     50.0, 10, 1),
  ('Press Militar',   14.0, 6, 2),
  ('Tríceps',         12.5, 8, 3)
) as v(nombre, peso, reps, orden) on true
join exercises e on e.nombre = v.nombre;

-- ── Cuentas financieras ─────────────────────────────────────────────────────
insert into accounts (nombre, tipo, moneda) values
  ('Neon',       'corriente', 'CHF'),
  ('Revolut',    'corriente', 'EUR'),
  ('MyInvestor', 'inversión', 'EUR'),
  ('Sabadell',   'corriente', 'EUR')
on conflict (nombre) do nothing;

-- ── Diccionario de analitos EII (normalización con sinónimos ES/CH) ──────────
insert into lab_analytes (canonico, unidad, ref_min, ref_max, sinonimos) values
  ('Calprotectina fecal',        'µg/g',     null, 50,    array['calprotectina','fecal calprotectin','calprotectin','f-calprotectina']),
  ('PCR',                        'mg/L',     null, 5,     array['proteína c reactiva','proteina c reactiva','crp','c-reactive protein']),
  ('VSG',                        'mm/h',     null, 20,    array['velocidad de sedimentación','velocidad de sedimentacion','esr','sedimentación globular']),
  ('Ferritina',                  'ng/mL',    30,   300,   array['ferritin']),
  ('Hierro',                     'µg/dL',    60,   170,   array['fe','iron','sideremia','eisen']),
  ('Transferrina',               'mg/dL',    200,  360,   array['transferrin']),
  ('Saturación de transferrina', '%',        20,   50,    array['ist','índice de saturación','indice de saturacion','transferrin saturation']),
  ('Vitamina B12',               'pg/mL',    200,  900,   array['b12','cobalamina','cobalamin','vitamina b-12']),
  ('Ácido fólico',               'ng/mL',    3,    17,    array['acido folico','folato','folate','vitamina b9']),
  ('Vitamina D',                 'ng/mL',    30,   100,   array['25-oh vitamina d','25-hidroxivitamina d','25-hydroxyvitamin d','calcidiol','vitamina d3']),
  ('Hemoglobina',                'g/dL',     13,   17,    array['hb','hemoglobin','hämoglobin','haemoglobin']),
  ('Hematocrito',                '%',        40,   50,    array['hct','hematocrit','hämatokrit']),
  ('Leucocitos',                 '10³/µL',   4,    11,    array['white blood cells','wbc','glóbulos blancos','globulos blancos','leukozyten']),
  ('Plaquetas',                  '10³/µL',   150,  400,   array['platelets','trombocitos','thrombozyten']),
  ('Neutrófilos',                '%',        40,   70,    array['neutrophils','neutrofilos','segmentados']),
  ('Linfocitos',                 '%',        20,   45,    array['lymphocytes','linfocitos']),
  ('Albúmina',                   'g/dL',     3.5,  5.2,   array['albumin','albumina']),
  ('Proteínas totales',          'g/dL',     6.4,  8.3,   array['proteinas totales','total protein','gesamteiweiß']),
  ('ALT (GPT)',                  'U/L',      null, 41,    array['alt','gpt','alanina aminotransferasa','alanine aminotransferase','alat']),
  ('AST (GOT)',                  'U/L',      null, 40,    array['ast','got','aspartato aminotransferasa','aspartate aminotransferase','asat']),
  ('GGT',                        'U/L',      null, 60,    array['gamma-glutamil transferasa','gamma-gt','γ-gt']),
  ('Fosfatasa alcalina',         'U/L',      40,   130,   array['alp','alkaline phosphatase','fal']),
  ('Bilirrubina total',          'mg/dL',    null, 1.2,   array['bilirubin','bilirrubina']),
  ('Creatinina',                 'mg/dL',    0.7,  1.3,   array['creatinine','kreatinin']),
  ('Urea',                       'mg/dL',    17,   49,    array['bun','blood urea nitrogen','harnstoff']),
  ('Filtrado glomerular',        'mL/min',   90,   null,  array['egfr','tfg','gfr','filtrado glomerular estimado']),
  ('Glucosa',                    'mg/dL',    70,   100,   array['glucose','glucemia','glukose','glykämie']),
  ('Sodio',                      'mmol/L',   135,  145,   array['na','sodium','natrium']),
  ('Potasio',                    'mmol/L',   3.5,  5.1,   array['k','potassium','kalium']),
  ('Magnesio',                   'mg/dL',    1.7,  2.4,   array['mg','magnesium']),
  ('TSH',                        'µU/mL',    0.4,  4.0,   array['tirotropina','thyroid stimulating hormone','thyreotropin'])
on conflict (canonico) do nothing;
