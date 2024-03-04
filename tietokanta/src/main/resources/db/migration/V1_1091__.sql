-- Kanavien laadunseurannan häiriötilanteen kirjauksen muutokset 
ALTER TABLE kan_hairio ADD COLUMN tieodotusaika_h NUMERIC; -- Tieliikenteen odotusaika 
ALTER TABLE kan_hairio ADD COLUMN ajoneuvo_lkm INTEGER; -- Odottavan tieliikenteen ajoneuvomäärä 
ALTER TABLE kan_hairio ADD COLUMN korjaajan_nimi VARCHAR(128); 
ALTER TABLE kan_hairio ADD COLUMN korjauksen_aloitus TIMESTAMP;
ALTER TABLE kan_hairio ADD COLUMN korjauksen_lopetus TIMESTAMP;
ALTER TABLE kan_hairio RENAME COLUMN odotusaika_h TO vesiodotusaika_h; -- Tarkennetaan sarakkeen nimi, eli meillä on nyt vesiliikenne ja tieliikenne 
