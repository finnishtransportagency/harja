-- Välitavoitteelle valtakunnallisuus ja linkitys toiseen (valtakunnalliseen) välitavoitteeseen
CREATE TYPE valitavoite_tyyppi AS ENUM ('kertaluontoinen','toistuva');
ALTER TABLE valitavoite ADD COLUMN tyyppi valitavoite_tyyppi;
ALTER TABLE valitavoite ADD COLUMN toistopvm DATE;
ALTER TABLE valitavoite ADD COLUMN urakkatyyppi urakkatyyppi; -- Urakkatyyppi jota valtakunnallinen tavoite koskee
ALTER TABLE valitavoite ADD COLUMN valtakunnallinen_valitavoite integer REFERENCES valitavoite (id); -- Viittaus välitavoitteeseen ilman urakka-id:tä