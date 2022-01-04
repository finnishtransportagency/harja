/*
 Ottaa vastaan vuoden, kuukauden ja urakan ID:n. Palauttaa urakan hoitokauden numeron kyseiselle kuukaudelle ja vuodelle.
 Ei välitä, onko haettu hoitokausi todella urakan ajan sisällä.
 */
CREATE OR REPLACE FUNCTION urakan_hoitokauden_numero(vuosi INT, kuukausi INT, urakka_id INT) RETURNS INT AS
$$
DECLARE
    urakan_aloitusvuosi INT;
BEGIN
    SELECT EXTRACT(YEAR FROM alkupvm) FROM urakka WHERE id = urakka_id INTO urakan_aloitusvuosi;

    RETURN CASE
               WHEN kuukausi < 10 THEN vuosi - urakan_aloitusvuosi
               WHEN (kuukausi >= 10) THEN vuosi - urakan_aloitusvuosi + 1
        END;
END;
$$ LANGUAGE plpgsql;

UPDATE kustannusarvioitu_tyo kt
SET indeksikorjaus_vahvistettu = NULL,
    vahvistaja = NULL
WHERE kt.id IN (
    SELECT kt.id
    FROM kustannusarvioitu_tyo kt
             LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
             LEFT JOIN urakka u ON tpi.urakka = u.id
             LEFT JOIN suunnittelu_kustannussuunnitelman_tila skt
                       ON (skt.urakka = tpi.urakka AND
                           skt.hoitovuosi = urakan_hoitokauden_numero(kt.vuosi, kt.kuukausi, u.id) AND
                           skt.osio = kt.osio)
    WHERE (skt.id IS NULL OR skt.vahvistettu IS FALSE)
      AND indeksikorjaus_vahvistettu IS NOT NULL);

UPDATE kiinteahintainen_tyo kt
SET indeksikorjaus_vahvistettu = NULL,
    vahvistaja = NULL
WHERE kt.id IN (
    SELECT kt.id
    FROM kiinteahintainen_tyo kt
             LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
             LEFT JOIN urakka u ON tpi.urakka = u.id
             LEFT JOIN suunnittelu_kustannussuunnitelman_tila skt
                       ON (skt.urakka = tpi.urakka AND
                           skt.hoitovuosi = urakan_hoitokauden_numero(kt.vuosi, kt.kuukausi, u.id) AND
                           skt.osio = 'hankintakustannukset')
    WHERE (skt.id IS NULL OR skt.vahvistettu IS FALSE)
      AND indeksikorjaus_vahvistettu IS NOT NULL);
