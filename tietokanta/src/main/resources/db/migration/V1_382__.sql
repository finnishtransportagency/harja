-- Välitavoitteelle valtakunnallisuus ja linkitys toiseen (valtakunnalliseen) välitavoitteeseen
CREATE TYPE valitavoite_tyyppi AS ENUM ('kertaluontoinen','toistuva');
ALTER TABLE valitavoite ADD COLUMN tyyppi valitavoite_tyyppi;
ALTER TABLE valitavoite ADD COLUMN toistopaiva INT CHECK (toistopaiva > 0 AND toistopaiva <= 31);
ALTER TABLE valitavoite ADD COLUMN toistokuukausi INT CHECK (toistokuukausi > 0 AND toistokuukausi <= 12);
ALTER TABLE valitavoite ADD COLUMN urakkatyyppi urakkatyyppi; -- Urakkatyyppi jota valtakunnallinen tavoite koskee
ALTER TABLE valitavoite ADD COLUMN valtakunnallinen_valitavoite integer REFERENCES valitavoite (id); -- Viittaus välitavoitteeseen ilman urakka-id:tä