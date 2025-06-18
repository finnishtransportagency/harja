(ns harja.kyselyt.uusi-kustannussuunnitelma-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.tyokalut.yleiset :as yleiset]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/kyselyt/uusi_kustannussuunnitelma_kyselyt.sql"
  {:positional? true})

(declare hae-urakan-toimenpiteet hae-kiintea-kustannus-toimenpiteelle-kuukaudelta
  hae-kiintea-kustannus-kuukausittain poista-kiinteat-kustannukset-kuukausittain!
  tallenna-kiinteat-kustannukset-kuukaudelta<! paivita-kiinteat-kustannukset-kuukausittain<!)

(defn tallenna-hankintojen-kuukausittainen-summa [db kk-jakso alkujakso? nimi viimeinen-summa summa hoitovuoden-alkuvuosi sopimus-id
                                                  toimenpideinstanssi-id kayttaja-id]
  (let [_ (doseq [kk kk-jakso]
            (let [summa (cond
                          (and alkujakso? (= kk 12)) viimeinen-summa
                          (and (not alkujakso?) (= kk 9)) viimeinen-summa
                          :else summa)
                  dbrivi (first (hae-kiintea-kustannus-toimenpiteelle-kuukaudelta db
                                  {:vuosi hoitovuoden-alkuvuosi
                                   :kuukausi kk
                                   :sopimus-id sopimus-id
                                   :toimenpideinstanssi-id toimenpideinstanssi-id}))
                  t (if (:id dbrivi)
                      (paivita-kiinteat-kustannukset-kuukausittain<! db
                        {:id (:id dbrivi)
                         :vuosi hoitovuoden-alkuvuosi
                         :kuukausi kk
                         :summa summa
                         :summa_indeksikorjattu nil
                         :toimenpideinstanssi-id toimenpideinstanssi-id
                         :tehtavaryhma nil
                         :tehtava nil
                         :muokkaaja kayttaja-id})
                      ;; Lisää uusi
                      (tallenna-kiinteat-kustannukset-kuukaudelta<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id toimenpideinstanssi-id
                         :vuosi hoitovuoden-alkuvuosi
                         :kuukausi kk
                         :summa summa
                         :summa_indeksikorjattu nil
                         :tehtavaryhma nil
                         :tehtava nil
                         :luoja kayttaja-id}))]))]))

(defn tallenna-kilpailutettavat-hankinnat
  [db kayttaja urakka-id hoitovuoden-alkuvuosi kilpailutettavat-hankinnat]
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ; Splittaa alkukauden summat kuukausittain
        _ (doseq [{:keys [nimi alkukausi loppukausi toimenpideinstanssi-id] :as toimenpide} kilpailutettavat-hankinnat]
            (let [alkukausi (bigdec alkukausi)

                  alkukausi-kuukaudet (yleiset/round2 2 (with-precision 4 (/ alkukausi 3)))
                  alkukausi-viimeinen-kuukausi (- alkukausi (* 2 alkukausi-kuukaudet))
                  loppukausi (bigdec loppukausi)
                  loppukausi-kuukaudet (yleiset/round2 2 (with-precision 4 (/ loppukausi 9)))
                  loppukausi-viimeinen-kuukausi (- loppukausi (* 8 loppukausi-kuukaudet))
                  ;; Tallenna alkujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 10 13) true nimi alkukausi-viimeinen-kuukausi alkukausi-kuukaudet
                      hoitovuoden-alkuvuosi sopimus-id toimenpideinstanssi-id (:id kayttaja))
                  ;; Tallenna loppujakso
                  _ (tallenna-hankintojen-kuukausittainen-summa db (range 1 10) false nimi loppukausi-viimeinen-kuukausi loppukausi-kuukaudet
                      (inc hoitovuoden-alkuvuosi) sopimus-id toimenpideinstanssi-id (:id kayttaja))]))]))
