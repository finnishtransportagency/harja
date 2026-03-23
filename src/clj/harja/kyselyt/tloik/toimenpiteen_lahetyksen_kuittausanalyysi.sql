-- name: hae-duplikaattikuittausyhteenvedot
-- Hakee T-LOIKin toimenpiteen-lahetyksen epäillyt duplikaattikuittausryhmät valitulta aikaväliltä.
WITH rikastetut AS (
    SELECT it.id                                                                                AS tapahtumaid,
           it.ulkoinenid                                                                        AS ulkoinenid,
           it.alkanut                                                                           AS alkanut,
           ip.ilmoitusid                                                                        AS ilmoitusid,
           ip.kuittaustyyppi                                                                    AS kuittaustyyppi,
           COALESCE(ip.kanava :: TEXT, 'tuntematon')                                            AS kanava,
           COALESCE(ip.virhe_lkm, 0)                                                            AS virhe_lkm,
           COALESCE(NULLIF(LOWER(BTRIM(ip.kuittaaja_henkilo_sahkoposti)), ''),
                    NULLIF(REGEXP_REPLACE(COALESCE(ip.kuittaaja_henkilo_matkapuhelin,
                                                 ip.kuittaaja_henkilo_tyopuhelin,
                                                 ''),
                                          '\s+',
                                          '',
                                          'g'),
                           ''),
                    NULLIF(LOWER(BTRIM(CONCAT_WS('|',
                                                 ip.kuittaaja_henkilo_etunimi,
                                                 ip.kuittaaja_henkilo_sukunimi,
                                                 ip.kuittaaja_organisaatio_nimi,
                                                 ip.kuittaaja_organisaatio_ytunnus))),
                           ''))                                                                AS kuittaaja_avain
      FROM integraatiotapahtuma it
               JOIN integraatio i
                    ON i.id = it.integraatio
               LEFT JOIN ilmoitustoimenpide ip
                         ON ip.lahetysid = it.ulkoinenid
     WHERE i.jarjestelma = 'tloik'
       AND i.nimi = 'toimenpiteen-lahetys'
       AND ((:alkaen :: TIMESTAMP IS NULL AND it.alkanut >= CURRENT_DATE) OR it.alkanut >= :alkaen)
       AND (:paattyen :: TIMESTAMP IS NULL OR it.alkanut <= :paattyen)
)
SELECT (ilmoitusid :: TEXT || '|' || kuittaustyyppi || '|' || kanava) AS ryhmaavain,
       ilmoitusid                                                      AS ilmoitusid,
       kuittaustyyppi                                                  AS kuittaustyyppi,
       kanava                                                          AS kanava,
       COUNT(*) - 1                                                    AS duplikaatteja,
       SUM(virhe_lkm)                                                  AS kertyneet_lahetysvirheet,
       COUNT(DISTINCT kuittaaja_avain)
         FILTER (WHERE kuittaaja_avain IS NOT NULL)                    AS uniikit_kuittaajat,
       MIN(alkanut)                                                    AS ensimmainen_alkanut,
       MAX(alkanut)                                                    AS viimeisin_alkanut,
       ARRAY_REMOVE(ARRAY_AGG(DISTINCT ulkoinenid), NULL)              AS uniikit_ulkoiset_idt
  FROM rikastetut
 WHERE ilmoitusid IS NOT NULL
   AND kuittaustyyppi IS NOT NULL
 GROUP BY ilmoitusid, kuittaustyyppi, kanava
HAVING COUNT(*) > 1 OR SUM(virhe_lkm) > 0
 ORDER BY kertyneet_lahetysvirheet DESC,
          duplikaatteja DESC,
          viimeisin_alkanut DESC,
          ilmoitusid,
          kuittaustyyppi,
          kanava
 LIMIT :limit;

-- name: hae-duplikaattikuittausten-tilastot
-- single?: true
WITH rikastetut AS (
    SELECT ip.ilmoitusid     AS ilmoitusid,
           ip.kuittaustyyppi AS kuittaustyyppi
      FROM integraatiotapahtuma it
               JOIN integraatio i
                    ON i.id = it.integraatio
               LEFT JOIN ilmoitustoimenpide ip
                         ON ip.lahetysid = it.ulkoinenid
     WHERE i.jarjestelma = 'tloik'
       AND i.nimi = 'toimenpiteen-lahetys'
       AND ((:alkaen :: TIMESTAMP IS NULL AND it.alkanut >= CURRENT_DATE) OR it.alkanut >= :alkaen)
       AND (:paattyen :: TIMESTAMP IS NULL OR it.alkanut <= :paattyen)
)
SELECT COUNT(*)                                                           AS kasitellyt_rivit,
       COUNT(*) FILTER (WHERE ilmoitusid IS NULL OR kuittaustyyppi IS NULL) AS ohitetut_rivit
  FROM rikastetut;

-- name: hae-duplikaattikuittausten-esimerkkitapahtumat
-- Hakee korkeintaan kolme uusinta tapahtumaa per palautettava duplikaattiryhmä.
WITH numeroidut AS (
    SELECT (ip.ilmoitusid :: TEXT || '|' || ip.kuittaustyyppi || '|' || COALESCE(ip.kanava :: TEXT, 'tuntematon')) AS ryhmaavain,
           it.id                                                                                                      AS tapahtumaid,
           it.alkanut                                                                                                 AS alkanut,
           it.ulkoinenid                                                                                              AS ulkoinenid,
           ip.ilmoitusid                                                                                              AS ilmoitusid,
           ip.kuittaustyyppi                                                                                          AS kuittaustyyppi,
           COALESCE(ip.kanava :: TEXT, 'tuntematon')                                                                  AS kanava,
           ROW_NUMBER() OVER (
               PARTITION BY ip.ilmoitusid, ip.kuittaustyyppi, COALESCE(ip.kanava :: TEXT, 'tuntematon')
               ORDER BY it.alkanut DESC, it.id DESC
           )                                                                                                          AS jarjestys
      FROM integraatiotapahtuma it
               JOIN integraatio i
                    ON i.id = it.integraatio
               LEFT JOIN ilmoitustoimenpide ip
                         ON ip.lahetysid = it.ulkoinenid
     WHERE i.jarjestelma = 'tloik'
       AND i.nimi = 'toimenpiteen-lahetys'
       AND ((:alkaen :: TIMESTAMP IS NULL AND it.alkanut >= CURRENT_DATE) OR it.alkanut >= :alkaen)
       AND (:paattyen :: TIMESTAMP IS NULL OR it.alkanut <= :paattyen)
       AND ip.ilmoitusid IS NOT NULL
       AND ip.kuittaustyyppi IS NOT NULL
       AND (ip.ilmoitusid :: TEXT || '|' || ip.kuittaustyyppi || '|' || COALESCE(ip.kanava :: TEXT, 'tuntematon')) = ANY(ARRAY[:ryhmaavaimet] :: TEXT[])
)
SELECT ryhmaavain AS ryhmaavain,
       tapahtumaid AS tapahtumaid,
       alkanut AS alkanut,
       ulkoinenid AS ulkoinenid
  FROM numeroidut
 WHERE jarjestys <= 3
 ORDER BY ryhmaavain,
          alkanut DESC,
          tapahtumaid DESC;
