CREATE TABLE ajastetut_tehtavat
(
    nimi               varchar(256) not null,
    suoritusyritys     timestamp DEFAULT NOW(),
    onnistutunut       boolean,
    virhe              varchar(256)
);
