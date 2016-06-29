-- Välitavoitteelle linkitys toiseen (valtakunnalliseen) välitavoitteeseen
ALTER TABLE valitavoite ADD COLUMN valtakunnallinen_valitavoite integer REFERENCES valitavoite (id);