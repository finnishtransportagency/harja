-- Siirrä toteuma-taulun sekvenssin omistajuus takaisin päätaululle, aikanaan siirtyi RENAME:n myötä ensimmäiselle partitiolle (ks. 832-migraatio)
ALTER SEQUENCE toteuma_id_seq OWNED BY toteuma.id;
