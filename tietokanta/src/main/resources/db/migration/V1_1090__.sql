-- Siirrä toteuma-taulun sekvenssin omistajuus takaisin päätaululle, kun se partitioinnin yhteydessä karkasi ensimmäiselle peritylle taululle
ALTER SEQUENCE toteuma_id_seq OWNED BY toteuma.id;
