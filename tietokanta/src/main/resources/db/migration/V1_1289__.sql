-- Lisätään MHU2026-bonusten tallennuksessa käytettävät erilliskustannustyypit.
ALTER TYPE erilliskustannustyyppi ADD VALUE IF NOT EXISTS 'alihankkijatyytyvaisyyskyselybonus';
ALTER TYPE erilliskustannustyyppi ADD VALUE IF NOT EXISTS 'maaraaikaan_tehtavien_toiden_aiempi_toteutusbonus';
ALTER TYPE erilliskustannustyyppi ADD VALUE IF NOT EXISTS 'liikennevahinkojen_aiheuttajien_selvitysbonus';

-- Nimeä raportti uudelleen
UPDATE raportti
   SET koodi = '#''harja.palvelin.raportointi.raportit.sanktio/suorita'
 WHERE nimi = 'sanktioraportti-yllapito'
   AND koodi = '#''harja.palvelin.raportointi.raportit.sanktioraportti-yllapito/suorita';
