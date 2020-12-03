(ns harja.palvelin.raportointi.raportit.vemtr
  "Valtakunnallinen ja ELY-kohtainen määrätoteumaraportti"
  (:require [harja.kyselyt
             [vemtr :as vemtr-q]]
            [harja.palvelin.raportointi.raportit.tehtavamaarat :as tm-r]
            [harja.pvm :as pvm])
  (:import (java.math RoundingMode)))


(defn suorita
  [db user params]
  (let [{:keys [otsikot rivit debug]} (tm-r/muodosta-taulukko db user vemtr-q/hae-tehtavamaarat-ja-toteumat-aikavalilla params)]
    [:raportti
     {:nimi "Tehtävämäärät"}
     [:taulukko
      {:otsikko    "Määrätoteumat ajalta "
       :sheet-nimi "Määrätoteumat"}
      otsikot
      rivit]]))

;; tarvittavat sarakkeet:
;;   - 1 nimi (esim sorateiden pölynsidonta) [x]
;;   - 2 jarjestys (tpi:n tiedoista generoitu järjestysindeksi, käytetään sorttaukseen mutta ei näy taulukossa)
;;   - 3 suunniteltu (suunniteltu määrä, esim 100)
;;   - 4 hoitokauden-alkuvuosi (esim 2020)
;;   - 5 suunnitteluyksikko (esim km)
;;   - 6 yksikko
;;   - 7 toimenpidekoodi (tpk-taulun tason 4 id eli tehtävä, jossa suunnitteluyksikkö, hinnoittelutyyppi, muokkaus- ja voimassaolotietoja ym)
;;    - 8 urakka (urakka-id)
;;    - 9 toimenpide, tpk-taulun tason 3 rivi, esim Oulu MHU Soratien hoito TP (toimenpideinstanssi-taulusta nimi, siellä urakkakohtaisia toimenpiteiden laskutustietoja )
;;    - 10 toteuma - tulee toteuma_tehtava tai toteuma_materiaali -taulukon linkityksen kautta
;; (11 kpl)
;;    - 11 toteutunut-materiaalimaara

;; harja=# select tpk4.emo, tpk4.nimi, tpi.nimi, tyo.id, tyo.urakka, tyo.maara, tyo.yksikko from yksikkohintainen_tyo tyo  , toteuma_tehtava tt JOIN toteuma t ON (tt.toteuma = t.id AND
;;                      -- t.tyyppi::TEXT IN (:tyotyypit) AND
;;                      t.poistettu IS NOT TRUE)
;;   JOIN toimenpidekoodi tpk4 ON tpk4.id = tt.toimenpidekoodi
;;   JOIN toimenpideinstanssi tpi
;;     ON (tpi.toimenpide = tpk4.emo AND tpi.urakka = t.urakka) where t.tyyppi = 'yksikkohintainen';
;; harja=# select tpk4.emo, tpk4.nimi, tpi.nimi, tyo.id, tyo.urakka, tyo.maara, tyo.yksikko from yksikkohintainen_tyo tyo  , toteuma_tehtava tt JOIN toteuma t ON (tt.toteuma = t.id AND
;;                      -- t.tyyppi::TEXT IN (:tyotyypit) AND
;;                      t.poistettu IS NOT TRUE)
;;   JOIN toimenpidekoodi tpk4 ON tpk4.id = tt.toimenpidekoodi
;;   JOIN toimenpideinstanssi tpi
;;     ON (tpi.toimenpide = tpk4.emo AND tpi.urakka = t.urakka) where t.tyyppi = 'yksikkohintainen';



;; \set alkupvm '2020-01-01'
;; \set loppupvm '2020-12-31'
;; \set hoitokausi (2020)

;; with urakat as (select u.id
;;                 from urakka u
;;                 where (:'alkupvm' between u.alkupvm and u.loppupvm
;;                   or :'loppupvm' between u.alkupvm and u.loppupvm)
;;   ),
;;      toteumat as (select tt.maara,
;;                          tt.toimenpidekoodi,
;;                          tt.poistettu,
;;                          tt.urakka_id
;;                   from toteuma t
;;                          join toteuma_tehtava tt on tt.toteuma = t.id and tt.poistettu = false
;;                               and tt.urakka_id in (select id from urakat)
;;                   where --t.lahde = 'harja-ui'
;;                       :'alkupvm' <= t.paattynyt
;;                     and :'loppupvm' >= t.alkanut
;;                     and t.poistettu is not true)
;; select tpk.nimi            as "nimi",
;;        tpk.jarjestys       as "jarjestys",
;;        sum(ut.maara)       as "suunniteltu",
;;        ut."hoitokauden-alkuvuosi",
;;        tpk.suunnitteluyksikko as "suunnitteluyksikko",
;;        tpk.yksikko         as "yksikko",
;;        tpk.id              as "toimenpidekoodi",
;;        ut.urakka as "urakka",
;;        tpi.nimi             as "toimenpide",
;;        sum(toteumat.maara) as "toteuma"
;; from urakka_tehtavamaara ut
;;        join toimenpidekoodi tpk on ut.tehtava = tpk.id
;;        join toimenpideinstanssi tpi on tpi.toimenpide = tpk.emo and tpi.urakka = ut.urakka
;;        left outer join toteumat on toteumat.toimenpidekoodi = ut.tehtava and toteumat.urakka_id = ut.urakka
;;        join tehtavaryhma tr on tpk.tehtavaryhma = tr.id
;; where ut.poistettu is not true
;;   and ut."hoitokauden-alkuvuosi" in (:hoitokausi)
;;   and ut.urakka in (select id from urakat)
;; group by tpk.id, tpk.nimi, tpk.yksikko, ut."hoitokauden-alkuvuosi", tpk.jarjestys, tpi.nimi, tpk.suunnitteluyksikko, ut.urakka
;;  union
;; select 'kek' as nimi, 4242 as jarjestys, 555 as suunniteltu, 2020 as "hoitokauden-alkuvuosi", 'parsec' as suunnitteluyksikko, 'parsec' as yksikko, 666 as toimenpidekoodi, 35 as urakka, 'kahvittelu' as toimenpide, null as toteuma
;; order by toimenpidekoodi
