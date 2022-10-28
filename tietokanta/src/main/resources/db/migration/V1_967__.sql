ALTER TYPE erilliskustannustyyppi ADD VALUE 'muu-bonus';

UPDATE
    erilliskustannus
SET tyyppi = 'muu-bonus'
FROM erilliskustannus ek
         LEFT JOIN toimenpideinstanssi tpi on ek.toimenpideinstanssi = tpi.id
         LEFT JOIN toimenpidekoodi tpk3 ON tpi.toimenpide = tpk3.id
         LEFT JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id
        LEFT JOIN urakka u ON ek.urakka = u.id
WHERE ek.tyyppi = 'muu' AND
      u.loppupvm > current_date AND
      (u.tyyppi = 'hoito' AND
       ek.lisatieto ILIKE '%bonus%');