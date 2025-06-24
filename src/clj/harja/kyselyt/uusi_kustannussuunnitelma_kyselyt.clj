(ns harja.kyselyt.uusi-kustannussuunnitelma-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja.tyokalut.yleiset :as yleiset]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :as tpi-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as tehtava-kyselyt]))

(defqueries "harja/kyselyt/uusi_kustannussuunnitelma_kyselyt.sql"
  {:positional? true})

(declare hae-urakan-toimenpiteet hae-kiintea-kustannus-toimenpiteelle-kuukaudelta
  hae-kiintea-kustannus-kuukausittain poista-kiinteat-kustannukset-kuukausittain!
  tallenna-kiinteat-kustannukset-kuukaudelta<! paivita-kiinteat-kustannukset-kuukausittain<!
  hae-erillishankinta-kuukausittain hae-kuukauden-erillishankinta
  paivita-kuukauden-erillishankinta<! tallenna-kuukauden-erillishankinta<!
  hae-hoidonjohtopalkkiot-kuukausittain hae-kuukauden-hoidonjohtopalkkio
  hae-rahavaraus-vuodelta
  paivita-kuukauden-hoidonjohtopalkkio<! tallenna-kuukauden-hoidonjohtopalkkio<!
  vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille!
  vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille!
  vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille! indeksikorjaukset-vahvistettu?)

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
  ;; Lisätään transktiot, jottei yhden epäonnistuminen päästä muita läpi
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

(defn tallenna-erillishankinnat
  [db kayttaja urakka-id hoitovuoden-alkuvuosi erillishankinnat]
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtavaryhma (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db
                              {:yksiloiva_tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c"}))
        ; Tallenna kuukausittaiset summat
        _ (doseq [rivi erillishankinnat]
            (let [dbrivi (first (hae-kuukauden-erillishankinta db {:id (:id rivi)}))
                  t (if (:id dbrivi)
                      (paivita-kuukauden-erillishankinta<! db
                        {:id (:id dbrivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :muokkaaja (:id kayttaja)})
                      ;; Lisää uusi
                      (tallenna-kuukauden-erillishankinta<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id hoidonjohto-tpi-id
                         :vuosi (:vuosi rivi)
                         :kuukausi (:kuukausi rivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :tehtavaryhma-id (:id tehtavaryhma)
                         :luoja (:id kayttaja)}))]))]))

(defn tallenna-hoidonjohtopalkkiot
  [db kayttaja urakka-id hoitovuoden-alkuvuosi hoidonjohtopalkkiot]
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ;; Hae hoidonjohto toimenpideinstannssi
        ;; Hoindonjohto toimenpide.koodi = 23151
        hoidonjohto-tpi-id (:id (first (tpi-kyselyt/hae-urakan-toimenpideinstanssi-toimenpidekoodilla db
                                         {:urakka urakka-id
                                          :koodi "23151"})))
        tehtava (first (tehtava-kyselyt/hae-tehtava-tunnisteella db {:tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8"}))
        ; Tallenna kuukausittaiset summat
        _ (doseq [rivi hoidonjohtopalkkiot]
            (let [dbrivi (first (hae-kuukauden-hoidonjohtopalkkio db {:id (:id rivi)}))
                  t (if (:id dbrivi)
                      (paivita-kuukauden-hoidonjohtopalkkio<! db
                        {:id (:id dbrivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :muokkaaja (:id kayttaja)})
                      ;; Lisää uusi
                      (tallenna-kuukauden-hoidonjohtopalkkio<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id hoidonjohto-tpi-id
                         :vuosi (:vuosi rivi)
                         :kuukausi (:kuukausi rivi)
                         :summa (:summa rivi)
                         :summa_indeksikorjattu nil
                         :tehtava-id (:id tehtava)
                         :luoja (:id kayttaja)}))]))]))
