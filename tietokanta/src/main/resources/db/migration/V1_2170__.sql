CREATE TYPE MHU_MUUTOSTYYPPI AS ENUM (
    'pysyva',
    'rahavaraus',
    'johto-ja-hallintokorvaus',
    'erillisrahoitettu',

    -- toteutuneisiin määriin perustuva tavoitehinnan muutos, esim. suolaa meni suunniteltua enemmän, koskee määrämitattavia tehtäviä (ns. lihavoidut ja kursivoidut)
    'toteutuneet-maarat',

    -- poikkeaminen tehtävä- ja määräluettelon määrästä yksittäisen hoitovuoden osalta, (=ei pysyvä muutos)
    -- esim. tehdään jotain tehtävä- ja määräluettelon tehtävää toisen sijaan (käytännössä siis siirretään rahaa tehtäviltä toisille).
    'maarapoikkeama'
);

CREATE TABLE mhu_muutos (
    id SERIAL PRIMARY KEY,
    versio INTEGER DEFAULT 1,  -- jokainen tallennus tallentaa täyden version muutoksesta, ml. alitaulut joissa on tietoa
    -- tähän tieto, millä aika välillä ko. rivi oli validi, olennaista varsinkin kun siirtyy historiaan
    validi_aikana TSTZRANGE DEFAULT TSTZRANGE(CURRENT_TIMESTAMP, NULL),
    urakka INTEGER REFERENCES urakka(id) NOT NULL,
    voimassa_alkaen DATE,
    tyyppi MHU_MUUTOSTYYPPI,
    nimi TEXT,
    syy TEXT,
    luoja INTEGER REFERENCES kayttaja(id) NOT NULL,
    luotu TIMESTAMP DEFAULT NOW() NOT NULL,
    muokkaaja INTEGER REFERENCES kayttaja(id),
    muokattu TIMESTAMP DEFAULT NOW(),
    poistettu BOOLEAN DEFAULT FALSE, -- vain historiarivit voivat olla poistettuja

    kulu_kohdistus INTEGER REFERENCES kulu_kohdistus(id) DEFAULT NULL,
    luonnos BOOLEAN DEFAULT FALSE
);

CREATE TABLE mhu_muutos_kustannusvaikutus (
    versio INTEGER DEFAULT 1, -- jokainen tallennus tallentaa täyden version muutoksesta, ml. alitaulut joissa on tietoa
    muutos INTEGER REFERENCES mhu_muutos(id) NOT NULL,
    kustannuslaji SUUNNITTELU_OSIO,
    toimenpide INTEGER REFERENCES toimenpide(id),
    hoitokauden_alkuvuosi INTEGER NOT NULL, -- kustannusvaikutuksen suuruus ko. hoitovuodelle
    summa NUMERIC -- euroa
);


-- Tehtävä- ja määräluettelon muutoksia voi olla yhtä mhu_muutosta kohta 0...n
CREATE TABLE mhu_muutos_tehtava_ja_maaraluettelo (
    versio INTEGER DEFAULT 1, -- jokainen tallennus tallentaa täyden version muutoksesta, ml. alitaulut joissa on tietoa
    muutos INTEGER REFERENCES mhu_muutos(id) NOT NULL,
    tehtava INTEGER REFERENCES tehtava(id) NOT NULL,
    hoitokauden_alkuvuosi INTEGER,
    edellinen_maara NUMERIC,
    maaramuutos NUMERIC,
    uusi_maara NUMERIC
);


CREATE TABLE mhu_muutos_liite (
    versio INTEGER DEFAULT 1, -- jokainen tallennus tallentaa täyden version muutoksesta, ml. alitaulut joissa on tietoa
    muutos INTEGER REFERENCES mhu_muutos(id),
    liite INTEGER REFERENCES liite(id)
);

CREATE INDEX mhu_muutos_id_versio_idx ON mhu_muutos (id, versio);
CREATE INDEX mhu_muutos_kustannusvaikutus_idx ON mhu_muutos_kustannusvaikutus (muutos, versio, hoitokauden_alkuvuosi);
CREATE INDEX mhu_muutos_tehtava_ja_maaraluettelo_idx ON mhu_muutos_tehtava_ja_maaraluettelo (muutos, versio, hoitokauden_alkuvuosi);
CREATE INDEX mhu_muutos_liite_idx ON mhu_muutos_liite (muutos, versio);

-- historiataulujen luonti, hyödyntää INHERITS-toimintoa. INHERIT ei peri rajoitteita, joten historiataulussa
-- voi olla mhu_muutoksia monta riviä samalla id:lla kuten halutaankin
CREATE TABLE mhu_muutos_historia () INHERITS (mhu_muutos);
-- varmistetaan että samaa (id, versio)-yhdistelmää on vain yksi
CREATE UNIQUE INDEX mhu_muutos_historia_id_versio ON mhu_muutos_historia (id, versio);
CREATE TABLE mhu_muutos_liite_historia () INHERITS (mhu_muutos_liite);
CREATE TABLE mhu_muutos_kustannusvaikutus_historia () INHERITS (mhu_muutos_kustannusvaikutus);
CREATE TABLE mhu_muutos_tehtava_ja_maaraluettelo_historia () INHERITS (mhu_muutos_tehtava_ja_maaraluettelo);


-- historiatauluja päivittävät triggerit ja niiden funktiot
CREATE FUNCTION paivita_mhu_muutos_historia()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO mhu_muutos_historia
    VALUES (OLD.*);
    NEW.validi_aikana := TSTZRANGE(LOWER(OLD.validi_aikana), CURRENT_TIMESTAMP);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER mhu_muutos_historia_trigger
    BEFORE UPDATE OR DELETE ON mhu_muutos
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_historia();


CREATE FUNCTION paivita_mhu_muutos_kustannusvaikutus_historia()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO mhu_muutos_kustannusvaikutus_historia
    VALUES (OLD.*);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER paivita_mhu_muutos_kustannusvaikutus_historia_trigger
    BEFORE UPDATE OR DELETE ON mhu_muutos_kustannusvaikutus
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_kustannusvaikutus_historia();


CREATE FUNCTION paivita_mhu_muutos_tehtava_ja_maaraluettelo_historia()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo_historia
    VALUES (OLD.*);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER mhu_muutos_tehtava_ja_maaraluettelo_historia_trigger
    BEFORE UPDATE OR DELETE ON mhu_muutos_tehtava_ja_maaraluettelo
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_tehtava_ja_maaraluettelo_historia();

CREATE FUNCTION paivita_mhu_muutos_liite_historia()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO mhu_muutos_liite_historia
    VALUES (OLD.*);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER mhu_muutos_historia_trigger
    BEFORE UPDATE OR DELETE ON mhu_muutos_liite
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_liite_historia();

-- mhu_muutos tietomalliin liittyvät kommentit
COMMENT ON column mhu_muutos_kustannusvaikutus.summa IS 'Muutoksen kustannusvaikutus euroina.';
COMMENT ON type MHU_MUUTOSTYYPPI IS E'MHU-urakoiden tavoitehintamuutoksien mahdolliset tyypit.';
COMMENT ON table mhu_muutos IS E'Pitää kirjaa tavoitehintaan vaikuttavista muutoksista MHU-urakoissa.';
