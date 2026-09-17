-- Päivitetään bonuslajien kanoniset näyttönimet ja kuvaus.
--
-- Urakkakohtainen MHU21-25-konfiguraatio ajetaan erillisenä tuotantokorjauksena,
-- koska kohdeurakat ovat tuotantokohtaista dataa.

WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
UPDATE bonus_laji bl
   SET nimi = 'Bonus alihankintasopimusten maksuehdoista',
       muokkaaja = i.id,
       muokattu = CURRENT_TIMESTAMP
  FROM integraatio i
 WHERE bl.koodi = 'alihankintabonus'
   AND bl.nimi IS DISTINCT FROM 'Bonus alihankintasopimusten maksuehdoista';

-- Liikennevahinkobonus on tässä migraatiossa käytössä MHU21-25-urakoissa.
-- MHU26-urakoiden liitokset lisätään erillisessä migraatiossa.
WITH integraatio AS (
    SELECT id
      FROM kayttaja
     WHERE kayttajanimi = 'Integraatio'
)
UPDATE bonus_laji bl
   SET nimi = 'Bonus liikennevahinkojen aiheuttajien selvittämisestä',
     kuvaus = 'MHU21-25-urakoille urakkakohtaisesti rajattu bonus liikennevahinkojen aiheuttajien selvittämisestä',
       muokkaaja = i.id,
       muokattu = CURRENT_TIMESTAMP
  FROM integraatio i
 WHERE bl.koodi = 'liikennevahinkojen_aiheuttajien_selvitysbonus'
   AND (bl.nimi IS DISTINCT FROM 'Bonus liikennevahinkojen aiheuttajien selvittämisestä'
        OR bl.kuvaus IS DISTINCT FROM 'MHU21-25-urakoille urakkakohtaisesti rajattu bonus liikennevahinkojen aiheuttajien selvittämisestä');
