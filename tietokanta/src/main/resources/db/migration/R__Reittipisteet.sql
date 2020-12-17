-- Reittipisteiden käsittely

-- Hakee klikkauspisteelle interpoloidun kohdan toteuman reittipisteiden perusteella.
-- Palauttaa arvioidun ajan ja osoitteen interpoloidulle pisteelle.
CREATE OR REPLACE FUNCTION aika_ja_osoite_pisteessa(toteumaid INTEGER, piste GEOMETRY)
   RETURNS RECORD AS $$
DECLARE
  lahin INTEGER; -- lähimmän pisteen indeksi reittipisteissä
  i INTEGER;
  pisteita INTEGER; -- montako pistettä toteumassa on
  min_etaisyys NUMERIC; -- etäisyys lähimpään
  etaisyys NUMERIC;
  rpt reittipistedata[]; -- toteuman reittipisteet
  rp reittipistedata;
  alku reittipistedata;  -- välin, johon klikkaus osuu, alkupiste
  loppu reittipistedata; -- välin, johon klikkaus osuu, loppupiste
  edellinen reittipistedata;
  seuraava reittipistedata;
  edellinen_et NUMERIC;
  seuraava_et NUMERIC;
  viiva GEOMETRY;
  suhteellinen_paikka NUMERIC; --- 0-1 suhteellinen paikka
  aika TIMESTAMP;
BEGIN
  -- Etsitään lähin reittipiste
  min_etaisyys := NULL;
  SELECT INTO rpt reittipisteet
    FROM toteuman_reittipisteet totrp
   WHERE totrp.toteuma = toteumaid;
  pisteita := array_length(rpt, 1);
  FOR i IN 1..pisteita LOOP
    rp := rpt[i];
    etaisyys := ST_Distance84(piste, rp.sijainti::geometry);
    IF min_etaisyys IS NULL OR etaisyys < min_etaisyys THEN
      lahin := i;
      min_etaisyys := etaisyys;
    END IF;
  END LOOP;
  RAISE NOTICE 'lähin piste % etäisyydellä %', rpt[lahin], min_etaisyys;
  -- Haetaan lähintä pistettä edeltävä/seuraava piste sekä niiden etäisyydet
  -- annettuun pisteeseen.
  edellinen := NULL;
  seuraava := NULL;
  edellinen_et := NULL;
  seuraava_et := NULL;
  IF lahin > 1 THEN
    edellinen := rpt[lahin-1];
    edellinen_et := ST_Distance84(piste, edellinen.sijainti::GEOMETRY);
  END IF;
  IF lahin < pisteita THEN
    seuraava := rpt[lahin+1];
    seuraava_et := ST_Distance84(piste, seuraava.sijainti::GEOMETRY);
  END IF;
  -- Valitaan alku ja loppu edellisen ja seuraavan pisteen etäisyyden perusteella
  IF edellinen_et IS NULL THEN
    alku := rpt[lahin];
    loppu := seuraava;
  ELSEIF seuraava_et IS NULL THEN
    alku := edellinen;
    loppu := rpt[lahin];
  ELSEIF edellinen_et < seuraava_et THEN
    alku := edellinen;
    loppu := rpt[lahin];
  ELSE
    alku := rpt[lahin];
    loppu := seuraava;
  END IF;
  RAISE NOTICE 'alku: % -- loppu: %', alku, loppu;
  -- Interpoloidaan aika
  viiva := ST_MakeLine(alku.sijainti::geometry, loppu.sijainti::geometry);
  suhteellinen_paikka  := ST_LineLocatePoint(viiva, ST_ClosestPoint(viiva, piste::geometry));
  aika := alku.aika + (suhteellinen_paikka * (loppu.aika - alku.aika));
  RAISE NOTICE 'aika %', aika;
  RETURN ROW(aika, yrita_tierekisteriosoite_pisteelle2(rpt[lahin].sijainti::geometry,50));
END
$$ LANGUAGE plpgsql;
