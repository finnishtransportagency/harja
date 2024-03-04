DROP INDEX uniikki_ensisijainen_urakanvastuuhenkilo_roolissa;

CREATE UNIQUE INDEX uniikki_ensisijainen_urakanvastuuhenkilo_roolissa
    ON urakanvastuuhenkilo (urakka, rooli, ensisijainen)
    WHERE ensisijainen = TRUE
