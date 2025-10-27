CREATE TABLE ajastetut_tehtavat
(
    nimi                    varchar(256) not null,
    suoritusyritys_aika     timestamp,
    onnistunut              boolean,
    viimeisin_onnistunut    timestamp
);

INSERT INTO ajastetut_tehtavat (nimi) VALUES ('siirra_toteumat_analytiikalle');
