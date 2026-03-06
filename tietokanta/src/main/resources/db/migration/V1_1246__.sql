-- Tallennetaan elinvoimakeskukset yhatietoihin samaan tapaan kuin elytkin tallennettiin
ALTER TABLE yhatiedot
    ADD COLUMN elinvoimakeskukset varchar(2048)[];
