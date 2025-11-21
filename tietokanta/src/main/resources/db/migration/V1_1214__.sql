-- Erillisrahoitetut muutokset kulu tauluun
ALTER TYPE kohdistustyyppi ADD VALUE 'erillisrahoitettu-muutos';
ALTER TABLE kulu_kohdistus ADD COLUMN muutos INTEGER REFERENCES mhu_muutos(id) DEFAULT NULL;


-- Muutostöillä tpi:tä ei ole, koska kuluja ei kirjata suoraan 
ALTER TABLE mhu_muutos_kustannusvaikutus DROP CONSTRAINT IF EXISTS kustannusvaikutus_tpi_null;
CREATE UNIQUE INDEX kustannusvaikutus_tpi_null ON mhu_muutos_kustannusvaikutus (
    muutos, kustannuslaji, hoitokauden_alkuvuosi,
    COALESCE(toimenpideinstanssi, -1)
);
