-- MPU Kustannukset 
CREATE TABLE IF NOT EXISTS mpu_kustannukset (
  id SERIAL PRIMARY KEY,
  urakka INTEGER REFERENCES urakka(id),
  selite TEXT,
  summa NUMERIC,
  vuosi INTEGER
);
