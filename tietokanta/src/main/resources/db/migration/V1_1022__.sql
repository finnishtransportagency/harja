-- Lisää urakkakohtaisen kesäkauden määrittelyä varten uudet sarakkeet
ALTER TABLE urakka
    -- Tallennetaan kesäkauden alku ja loppu DATE-tietotyyppiin, jotta saadaan ilmainen päivämäärän validointi
    -- ja mahdollistetaan mm. date operaatiot
    ADD COLUMN kesakausi_alkupvm  DATE DEFAULT TO_DATE('2000-05-01', 'YYYY-MM-DD'),
    ADD COLUMN kesakausi_loppupvm DATE DEFAULT TO_DATE('2000-09-30', 'YYYY-MM-DD');
