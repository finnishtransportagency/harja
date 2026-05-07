SELECT bp.id                    AS profiili_id,
       bp.nimi                  AS profiili_nimi,
      bplet.nimi               AS laji_esitystiedot_nimi,
       bplet.kuvaus             AS laji_esitystiedot_kuvaus
  FROM bonus_profiili bp;