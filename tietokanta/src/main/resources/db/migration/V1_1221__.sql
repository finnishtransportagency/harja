-- Lisää maksuera_alias sarake maksuera tauluun Elinvoimakeskus muutoksen takia
-- Koskee vain 4 urakkaa, tiedot syötetään käsin. 
ALTER TABLE maksuera
ADD COLUMN maksuera_alias varchar(128);
