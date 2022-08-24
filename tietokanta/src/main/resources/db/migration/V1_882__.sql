CREATE TYPE litteyslukuluokka as ENUM
    ('FI10', 'FI15', 'FI20', 'FI35');

ALTER TABLE pot2_mk_urakan_massa DROP COLUMN litteyslukuluokka;
ALTER TABLE pot2_mk_urakan_massa
    ADD COLUMN litteyslukuluokka litteyslukuluokka NOT NULL DEFAULT 'FI15';