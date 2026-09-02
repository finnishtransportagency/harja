-- Repeatable: Urakka tehtävämäärä yhteenvetonäkymä
-- 
-- Yhdistää urakka_tehtavamaara taulun tarjousmäärät ja 
-- mhu_muutos_tehtava_ja_maaraluettelo taulun mutaatiosummat yhdeksi yhteenvetonäkymäksi.
--
-- Ratkaisu keskittää tehtävämäärien laskennan yhteen paikkaan ja estää 
-- laskentalogiikan duplikoinnin eri kyselyihin.
--
-- VIEW palauttaa:
-- - tarjous_maara: alkuperäinen tarjousmäärä (urakka_tehtavamaara.maara)
-- - muutossumma: kaikkien mutaatioiden summa ko. tehtävälle ja hoitokaudelle
-- - laskettu_maara: tarjous_maara + muutossumma (todellinen suunniteltu määrä)

CREATE OR REPLACE VIEW urakka_tehtavamaara_yhteenveto AS
SELECT 
    -- Perusrivitiedot urakka_tehtavamaara taulusta
    ut.id,
    ut.urakka,
    ut."hoitokauden-alkuvuosi" as hoitokauden_alkuvuosi,
    ut.tehtava,
    
    -- Määrätiedot
    ut.maara AS tarjous_maara,
    COALESCE(muutokset.muutossumma, 0) AS muutossumma,
    ut.maara + COALESCE(muutokset.muutossumma, 0) AS laskettu_maara,
    
    -- Metatiedot
    ut.poistettu,
    ut.luotu,
    ut.luoja,
    ut.muokattu,
    ut.muokkaaja
FROM 
    urakka_tehtavamaara ut
    LEFT JOIN (
        -- Aggregoidaan kaikki tehtävän mutaatiot hoitokausittain
        -- Huom: mhu_muutos_tehtava_ja_maaraluettelo päätalussa on aina vain uusin versio
        -- per (muutos, tehtava, hoitokauden_alkuvuosi) yhdistelmä, koska ON CONFLICT
        -- päivittää rivin in-place ja trigger siirtää vanhan version historiaan.
        SELECT 
            m.urakka,
            mmtml.tehtava,
            mmtml.hoitokauden_alkuvuosi,
            SUM(mmtml.maaramuutos) AS muutossumma
        FROM 
            mhu_muutos_tehtava_ja_maaraluettelo mmtml
            JOIN mhu_muutos m ON mmtml.muutos = m.id
        WHERE 
            m.poistettu = FALSE
            -- Varmistetaan että muutos on voimassa ko. hoitokaudella:
            -- Muutos vaikuttaa hoitokauteen jos voimassa_alkaen on hoitokauden loppuun mennessä.
            -- Hoitokausi on 1.10.XXXX - 30.9.XXXX+1, joten tarkistetaan että muutos on alkanut
            -- ennen hoitokauden loppua. Tämä tukee:
            -- 1) Pysyviä muutoksia jotka jatkuvat useampaan hoitokauteen
            -- 2) Kesken hoitokauden luotuja muutoksia
            AND m.voimassa_alkaen <= make_date(mmtml.hoitokauden_alkuvuosi + 1, 9, 30)
        GROUP BY 
            m.urakka, 
            mmtml.tehtava, 
            mmtml.hoitokauden_alkuvuosi
    ) muutokset 
        ON ut.urakka = muutokset.urakka 
        AND ut.tehtava = muutokset.tehtava
        AND ut."hoitokauden-alkuvuosi" = muutokset.hoitokauden_alkuvuosi
WHERE 
    ut.poistettu = FALSE;

-- Indeksit VIEW:n taustalla oleville tauluille (jos puuttuu)
-- Nämä parantavat VIEW:n suorituskykyä merkittävästi

-- Indeksi urakka_tehtavamaara tauluun (todennäköisesti jo olemassa)
CREATE INDEX IF NOT EXISTS urakka_tehtavamaara_urakka_hoitokausi_tehtava_idx 
    ON urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava) 
    WHERE poistettu = FALSE;

-- Indeksi mhu_muutos tauluun poistettu-filtteröintiin
CREATE INDEX IF NOT EXISTS mhu_muutos_poistettu_idx 
    ON mhu_muutos (id) 
    WHERE poistettu = FALSE;

-- Indeksi mhu_muutos_tehtava_ja_maaraluettelo tauluun aggregointiin
CREATE INDEX IF NOT EXISTS mhu_muutos_tehtava_ja_maaraluettelo_aggregointi_idx 
    ON mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi);

-- Kommentit VIEW:lle ja sen sarakkeille
COMMENT ON VIEW urakka_tehtavamaara_yhteenveto IS 
    E'Yhteenvetonäkymä joka yhdistää urakan tehtävämäärät (tarjous) ja niiden mutaatiot (muutokset) yhdeksi laskettavaksi määräksi.
    
    Käyttötarkoitus:
    - Korvaa manuaaliset mutaatiosummien laskennat eri kyselyissä
    - Tarjoaa yhtenäisen tavan hakea todellinen suunniteltu määrä (tarjous + muutokset)
    - Käytetään raporteissa, UI:ssa ja analytiikassa
    
    Sarakkeet:
    - tarjous_maara: Alkuperäinen tarjousmäärä sopimuksesta
    - muutossumma: Kaikkien hyväksyttyjen mutaatioiden summa
    - laskettu_maara: Todellinen suunniteltu määrä (tarjous_maara + muutossumma)';

COMMENT ON COLUMN urakka_tehtavamaara_yhteenveto.tarjous_maara IS 
    'Alkuperäinen tarjousmäärä urakka_tehtavamaara taulusta. Tämä on sopimuksen mukainen lähtömäärä.';

COMMENT ON COLUMN urakka_tehtavamaara_yhteenveto.muutossumma IS 
    'Kaikkien hyväksyttyjen (poistettu = FALSE) mutaatioiden summa tälle tehtävälle ja hoitokaudelle. Päätalussa on aina vain uusin versio per muutos.';

COMMENT ON COLUMN urakka_tehtavamaara_yhteenveto.laskettu_maara IS 
    'Todellinen suunniteltu määrä: tarjous_maara + muutossumma. Tätä tulisi käyttää kun viitataan "suunniteltuun määrään" tai "tavoitemäärään".';
