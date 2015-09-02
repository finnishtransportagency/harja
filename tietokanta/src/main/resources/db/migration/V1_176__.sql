-- Nimi: Urakoiden hoitokausien laskenta
-- Kuvaus: Lisää urakoiden hoitokausien laskennan stored proceduren. Parametrinä annetaan urakan id. Paluuarvona
-- saadaan lista hoitokausien alkamis ja päättymis päivämääristä. Lisää myös päivämäärävälille oman tyypin.

CREATE TYPE paivamaaravali AS
(alkupvm DATE, loppupvm DATE);

CREATE OR REPLACE FUNCTION urakan_hoitokaudet(urakka_id INTEGER)
  RETURNS SETOF paivamaaravali LANGUAGE plpgsql AS $$
DECLARE
  urakan_alkupvm                DATE;
  urakan_loppupvm               DATE;
  urakan_tyyppi                 TEXT;
  nyt                           DATE;
  nykyinen_hoitokauden_alkupvm  DATE;
  nykyinen_hoitokauden_loppupvm DATE;
  hoitokauden_alkupvm           DATE;
  hoitokauden_loppupvm          DATE;
BEGIN
  SELECT
    alkupvm,
    loppupvm,
    tyyppi
  INTO urakan_alkupvm, urakan_loppupvm, urakan_tyyppi
  FROM urakka
  WHERE id = urakka_id;

  nyt := urakan_alkupvm;

  -- Hoidon urakat
  IF urakan_tyyppi = 'hoito'
  THEN
    LOOP
      IF nyt > urakan_loppupvm
      THEN
        EXIT;
      ELSE
        RETURN NEXT (nyt, ((EXTRACT(YEAR FROM nyt) + 1) || '-09-30') :: DATE);
        -- Inkrementoi vuodella
        nyt := nyt + INTERVAL '1 year';
      END IF;
    END LOOP;

  -- Muut urakat
  ELSE
    -- Urakka loppuu samana vuonna
    IF EXTRACT(YEAR FROM urakan_alkupvm) = EXTRACT(YEAR FROM urakan_loppupvm)
    THEN
      RETURN NEXT (urakan_alkupvm, urakan_loppupvm);

    -- Urakka on monivuotinen
    ELSE
      nykyinen_hoitokauden_alkupvm := urakan_alkupvm;

      LOOP
        IF nyt > urakan_loppupvm
        THEN
          EXIT;
        ELSE

          IF EXTRACT(YEAR FROM nykyinen_hoitokauden_alkupvm) != EXTRACT(YEAR FROM nyt)
          THEN
            hoitokauden_alkupvm := nykyinen_hoitokauden_alkupvm;
            hoitokauden_loppupvm := nyt - INTERVAL '1 day';

            nykyinen_hoitokauden_alkupvm = nyt;

            RETURN NEXT (hoitokauden_alkupvm, hoitokauden_loppupvm);

          ELSEIF urakan_loppupvm = nyt
            THEN
              hoitokauden_alkupvm := nykyinen_hoitokauden_alkupvm;
              hoitokauden_loppupvm := nyt;

              nykyinen_hoitokauden_alkupvm = nyt;

              RETURN NEXT (hoitokauden_alkupvm, hoitokauden_loppupvm);

          END IF;

          -- Inkrementoi päivällä
          nyt := nyt + INTERVAL '1 day';
        END IF;
      END LOOP;
    END IF;
  END IF;
END
$$;