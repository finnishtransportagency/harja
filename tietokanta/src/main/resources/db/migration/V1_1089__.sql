-- Siirrä maksueränumeron sekvenssin omistajuus ko. sarakkeelle
ALTER SEQUENCE maksueranumero OWNED BY maksuera.numero;

-- Poista käyttämätön sekvenssi livitunnisteet
DROP SEQUENCE IF EXISTS livitunnisteet;
