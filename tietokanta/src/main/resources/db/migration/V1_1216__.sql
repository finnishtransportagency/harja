CREATE TYPE ajastetuntehtavan_tyyppi AS ENUM ('siirra_toteumat_analytiikalle');

CREATE TABLE ajastetut_tehtavat
(
    tyyppi                  ajastetuntehtavan_tyyppi not null,
    suoritusyritys_aika     timestamp,
    onnistunut              boolean,
    viimeisin_onnistunut    timestamp
);

INSERT INTO ajastetut_tehtavat (tyyppi) VALUES ('siirra_toteumat_analytiikalle');
