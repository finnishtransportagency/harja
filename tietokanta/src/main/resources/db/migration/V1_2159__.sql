-- Tiedosto uudelleennimetään ennen mergeä 

-- Poista turhaksi jääneitä sarakkeita kanava aineistosta, mitä Harjassa ei käytetä 
ALTER TABLE kan_sulku DROP COLUMN kanavatyyppi;
ALTER TABLE kan_sulku DROP COLUMN kanavatyyppi;

-- Korjaa linkitys 
