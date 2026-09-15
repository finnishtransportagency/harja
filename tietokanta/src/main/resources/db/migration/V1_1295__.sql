-- Lisätään MHU2026-bonusten tallennuksessa käytettävät erilliskustannustyypit.
ALTER TYPE erilliskustannustyyppi ADD VALUE IF NOT EXISTS 'alihankkijatyytyvaisyyskyselybonus';
ALTER TYPE erilliskustannustyyppi ADD VALUE IF NOT EXISTS 'maaraaikaan_tehtavien_toiden_aiempi_toteutusbonus';
ALTER TYPE erilliskustannustyyppi ADD VALUE IF NOT EXISTS 'liikennevahinkojen_aiheuttajien_selvitysbonus';
