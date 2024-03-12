ALTER TABLE urakanvastuuhenkilo ADD COLUMN "toissijainen-varahenkilo" BOOLEAN DEFAULT FALSE;

DROP INDEX uniikki_ensisijainen_urakanvastuuhenkilo_roolissa;

CREATE UNIQUE INDEX uniikki_ensisijainen_urakanvastuuhenkilo_roolissa
    ON urakanvastuuhenkilo (urakka, rooli, ensisijainen, "toissijainen-varahenkilo")
    WHERE ensisijainen = TRUE OR "toissijainen-varahenkilo" = FALSE;
