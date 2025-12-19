-- Tallennetaan elinvoimakeskukset omantyyppisenä organisaationa organisaatiotauluun
ALTER TABLE urakka
    DROP CONSTRAINT urakka_elinvoimakeskus_id_fkey,
    ADD CONSTRAINT urakka_elinvoimakeskus_id_fkey FOREIGN KEY (elinvoimakeskus_id) REFERENCES organisaatio (id);

ALTER TABLE tielupa
    DROP CONSTRAINT tielupa_elinvoimakeskus_id_fkey,
    ADD CONSTRAINT tielupa_elinvoimakeskus_id_fkey FOREIGN KEY (elinvoimakeskus_id) REFERENCES organisaatio (id);

DROP TABLE elinvoimakeskus;
