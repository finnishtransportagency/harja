-- Muuta hoitourakoille 'muu-bonus'-tyyppiseksi virheellisesti muunnetut 'muu'-tyyppiset erilliskustannukset takaisin 'muu'-tyyppisiksi.
-- Jätetään 'muu-bonus'-tyyppisiksi vain sellaiset rivit, joissa mainitaan sana 'bonus'

  WITH paivitettavat AS
           (SELECT ek.id AS ek_id
              FROM erilliskustannus ek
                       JOIN urakka u ON ek.urakka = u.id
             WHERE ek.tyyppi = 'muu-bonus'
               AND (u.tyyppi = 'hoito' AND
                 -- lisatieto-kolumnissa voi olla NULL-arvoja, joten varmistetaan stringiin vertaaminen, jotta "NOT ILIKE" osuu.
                    COALESCE(ek.lisatieto, '') NOT ILIKE '%bonus%'))

UPDATE erilliskustannus ek2
   SET tyyppi    = 'muu',
       muokattu  = CURRENT_TIMESTAMP,
       muokkaaja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM paivitettavat p
 WHERE ek2.id = p.ek_id;
