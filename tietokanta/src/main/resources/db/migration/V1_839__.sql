-- Vain osa toimenpidekoodeista on sellaisia tehtäviä, että niitä voidaan lisätä
-- käyttöliittymän kautta käsin suunnitelluille tehtäville.
-- Määritellään kaikki toimenpidekoodit aluksi käsin lisättävien joukosta.
-- Ja koska ympäristöt ovat erilaisia (tehtävien nimet ja id;t vaihtelevat ympäristön mukaan)
-- , niin etukäteen ei voida määritellä, että mitkä laitetaan käsin lisättävien listalle ja mitä ei.
-- Niinpä ne on tehtävä jokaiseen ympäristöön erikseen suorilla tietokanta-ajoilla.
ALTER TABLE toimenpidekoodi
    ADD kasin_lisattava_maara BOOLEAN DEFAULT FALSE;

-- Lisätään uuid tehtäväryhmän muutamalle lisätyöhön liittyvälle ryhmälle, jotta nimen vaihto
-- ei sotke tehtäväryhmän käyttöä hakulausekkeissa.
UPDATE tehtavaryhma SET yksiloiva_tunniste = '91896f23-1d4a-4385-8e1f-28a8f0c8ba80'
WHERE nimi = 'Lisätyöt' AND otsikko = '7.0 LISÄTYÖT';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '0b65b36d-e84e-40b6-a0ad-e4f539f05227'
WHERE nimi = 'Välitaso Lisätyöt' AND otsikko = '7.0 LISÄTYÖT';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '6a2f1000-bb92-48d7-af80-7510c532115a'
WHERE nimi = 'Alataso Lisätyöt' AND otsikko = '7.0 LISÄTYÖT';


-- Tee uusi 9kk partitio toteuma taululle
SELECT * FROM luo_toteumataulun_partitio( '2019-10-01'::DATE, '2020-07-01'::DATE);
-- Muokataan aiemmin kaikki ensimmäiset toteumat sisältänyt partitio uuden nimiseksi, koska se sisältää
-- nyt eri tiedot
-- Poistetaan ensin constraint
ALTER TABLE toteuma_010101_200701 DROP CONSTRAINT toteuma_010101_200701_alkanut_check;
-- Muutetaan partition nimi vastaamaan uutta tulevaa sisältöä
ALTER TABLE toteuma_010101_200701 rename to toteuma_010101_191001;

-- Uusi insert trigger toteuma partitioille
CREATE OR REPLACE FUNCTION toteuma_insert() RETURNS trigger AS $$
DECLARE
    alkanut date;
BEGIN
    alkanut := NEW.alkanut;
    IF alkanut < '2019-10-01'::date THEN
        INSERT INTO toteuma_010101_191001 VALUES (NEW.*);
    ELSIF alkanut >= '2019-10-01'::date AND alkanut < '2020-07-01'::date THEN
        INSERT INTO toteuma_191001_200701 VALUES (NEW.*);
    ELSIF alkanut >= '2020-07-01'::date AND alkanut < '2021-01-01'::date THEN
        INSERT INTO toteuma_200701_210101 VALUES (NEW.*);
    ELSIF alkanut >= '2021-01-01'::date AND alkanut < '2021-07-01'::date THEN
        INSERT INTO toteuma_210101_210701 VALUES (NEW.*);
    ELSIF alkanut >= '2021-07-01'::date AND alkanut < '2022-01-01'::date THEN
        INSERT INTO toteuma_210701_220101 VALUES (NEW.*);
    ELSIF alkanut >= '2022-01-01'::date AND alkanut < '2022-07-01'::date THEN
        INSERT INTO toteuma_220101_220701 VALUES (NEW.*);
    ELSIF alkanut >= '2022-07-01'::date AND alkanut < '2023-01-01'::date THEN
        INSERT INTO toteuma_220701_230101 VALUES (NEW.*);
    ELSIF alkanut >= '2023-01-01'::date AND alkanut < '2023-07-01'::date THEN
        INSERT INTO toteuma_230101_230701 VALUES (NEW.*);
    ELSIF alkanut >= '2023-07-01'::date AND alkanut < '2024-01-01'::date THEN
        INSERT INTO toteuma_230701_240101 VALUES (NEW.*);
    ELSIF alkanut >= '2024-01-01'::date AND alkanut < '2024-07-01'::date THEN
        INSERT INTO toteuma_240101_240701 VALUES (NEW.*);
    ELSIF alkanut >= '2024-07-01'::date AND alkanut < '2025-01-01'::date THEN
        INSERT INTO toteuma_240701_250101 VALUES (NEW.*);
        -- kaatissäkki kaikelle liian uudelle, typotetulle jne. Jos ja kun Harja elää 1.1.2025 pitempään, muuta
        -- tätä funktiota ja luo tarvittava määrä hoitokausipartitioita lisää jotta saadaan partitioinnin
        -- hyödyt myös silloin käyttöön
    ELSIF alkanut >= '2025-01-01'::date THEN
        INSERT INTO toteuma_250101_991231 VALUES (NEW.*);  ELSE
        RAISE EXCEPTION 'Taululle toteuma ei löydy insert ehtoa, korjaa toteuma_insert() sproc!';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Päivitetään aikavälin siirtoon tehty funktio
CREATE OR REPLACE FUNCTION siirra_aikavalin_toteumat(alkupvm DATE, loppupvm DATE)
    RETURNS VOID AS
$$
BEGIN
    RAISE NOTICE 'Siirretään toteumat aikaväliltä % - %', alkupvm, loppupvm;
    WITH x AS (
        DELETE FROM toteuma_010101_191001 WHERE alkanut BETWEEN alkupvm AND loppupvm returning *
    )
    INSERT INTO toteuma SELECT * FROM x;
END
$$
    LANGUAGE plpgsql;

-- Siirretään toteumat vanhasta isosta partitiosta uuteen 9kk pitkään
SELECT * FROM siirra_aikavalin_toteumat( '2019-10-01'::DATE, '2020-07-01'::DATE);

-- Päivitetään check constraint vanhalle partitiolle
ALTER TABLE toteuma_010101_191001
    ADD CONSTRAINT toteuma_010101_191001_alkanut_check
        CHECK (alkanut >= '0001-01-01' AND alkanut < '2019-10-01');

-- Muokkaa toteuma taulun poistettu kenttä pelkästään boolean arvoksi
-- Ensin muutetaan null arvot falseksi
UPDATE toteuma set poistettu = false WHERE poistettu is null;
UPDATE toteuma_010101_191001 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_191001_200701 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_200701_210101 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_210101_210701 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_210701_220101 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_220101_220701 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_220701_230101 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_230101_230701 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_230701_240101 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_240101_240701 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_240701_250101 set poistettu = false WHERE poistettu is null;
UPDATE toteuma_250101_991231 set poistettu = false WHERE poistettu is null;
-- Sitten muokataan itse kenttä, jotta sinne ei voi null arvoja enää lisätä
ALTER TABLE toteuma ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_010101_191001 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_191001_200701 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_200701_210101 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_210101_210701 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_210701_220101 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_220101_220701 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_220701_230101 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_230101_230701 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_230701_240101 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_240101_240701 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_240701_250101 ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_250101_991231 ALTER COLUMN poistettu SET NOT NULL;
-- set default false
ALTER TABLE toteuma ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_010101_191001 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_191001_200701 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_200701_210101 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_210101_210701 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_210701_220101 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_220101_220701 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_220701_230101 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_230101_230701 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_230701_240101 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_240101_240701 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_240701_250101 ALTER COLUMN poistettu SET DEFAULT FALSE;
ALTER TABLE toteuma_250101_991231 ALTER COLUMN poistettu SET DEFAULT FALSE;



-- Muokataan toteuma_tehtava taulu suorituskyvyltään paremmaksi
-- Lisätään urakkaindeksiä varten uusi columni
ALTER TABLE toteuma_tehtava ADD COLUMN urakka_id INTEGER;
-- Muokataan poistettu kenttä sisältämään pelkästään true/false arvoja, koska se parantaa indeksin toimintaa
UPDATE toteuma_tehtava set poistettu = false where poistettu is null;
ALTER TABLE toteuma_tehtava ALTER COLUMN poistettu SET NOT NULL;
ALTER TABLE toteuma_tehtava ALTER COLUMN poistettu SET DEFAULT FALSE;


