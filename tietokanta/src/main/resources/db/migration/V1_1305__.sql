-- Erillisrahoitetut muutokset kulu tauluun
ALTER TYPE kohdistustyyppi ADD VALUE 'erillisrahoitettu-muutos';
ALTER TABLE kulu_kohdistus ADD COLUMN muutos INTEGER REFERENCES mhu_muutos(id) DEFAULT NULL;


-- Muutostöillä tpi:tä ei ole, koska kuluja ei kirjata suoraan 
ALTER TABLE mhu_muutos_kustannusvaikutus DROP CONSTRAINT IF EXISTS uniikki_muutos_kustannusvaikutus;
CREATE UNIQUE INDEX uniikki_muutos_kustannusvaikutus ON mhu_muutos_kustannusvaikutus (
    muutos, kustannuslaji, hoitokauden_alkuvuosi,
    COALESCE(toimenpideinstanssi, -1)
);
