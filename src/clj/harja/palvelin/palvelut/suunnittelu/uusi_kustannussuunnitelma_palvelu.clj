(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu
  (:require [certifiable.log :as log]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :as yleiset]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as suunnitelma-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-kustannussuunnitelman-tiedot [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "hae-kustannussuunnitelman-tiedot :: tiedot: " tiedot)
  (let [;; Urakan sopimus id
        sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ;; Haetaan urakan toimenpiteet
        toimenpiteet (suunnitelma-q/hae-urakan-toimenpiteet db {:urakkaid urakka-id})
        ;; Kiinteähintaiset kustannukset
        kiinteat (reduce (fn [acc {:keys [nimi toimenpideinstanssi-id] :as toimenpide}]
                           (let [kiinteat-alkukausi (suunnitelma-q/hae-kiinteat-kustannukset-kuukausittain
                                                      db {:sopimus-id sopimus-id
                                                          :kuukaudet [10, 11, 12]
                                                          :vuosi hoitovuoden-alkuvuosi
                                                          :toimenpideinstanssi-id toimenpideinstanssi-id})
                                 kiinteat-loppukausi (suunnitelma-q/hae-kiinteat-kustannukset-kuukausittain
                                                       db {:sopimus-id sopimus-id
                                                           :kuukaudet [1, 2, 3, 4, 5, 6, 7, 8, 9]
                                                           :vuosi (inc hoitovuoden-alkuvuosi)
                                                           :toimenpideinstanssi-id toimenpideinstanssi-id})
                                 alkukausi (if (seq kiinteat-alkukausi) (apply + (map :summa kiinteat-alkukausi)) 0)
                                 alkukausi-indeksikorjattu (if (seq kiinteat-alkukausi)
                                                             (apply + (map (fn [rivi]
                                                                             (if (:summa_indeksikorjattu rivi)
                                                                               (:summa_indeksikorjattu rivi)
                                                                               0))
                                                                        kiinteat-loppukausi))
                                                             0)
                                 loppukausi (if (seq kiinteat-loppukausi) (apply + (map :summa kiinteat-loppukausi)) 0)
                                 loppukausi-indeksikorjattu (if (seq kiinteat-loppukausi)
                                                              (apply + (map (fn [rivi]
                                                                              (if (:summa_indeksikorjattu rivi)
                                                                                (:summa_indeksikorjattu rivi)
                                                                                0))
                                                                         kiinteat-loppukausi))
                                                              0)]
                             (conj acc {:nimi nimi
                                        :toimenpideinstanssi-id toimenpideinstanssi-id
                                        :alkukausi alkukausi
                                        :alkukausi-indeksikorjattu alkukausi-indeksikorjattu
                                        :loppukausi loppukausi
                                        :loppukausi-indeksikorjattu loppukausi-indeksikorjattu
                                        :yhteensa (+ alkukausi loppukausi)
                                        :yhteensa-indeksikorjattu (+ alkukausi-indeksikorjattu loppukausi-indeksikorjattu)
                                        :pysyvat-muutokset "Ei muutoksia"})))
                   []
                   toimenpiteet)
        ;; Yhteenvetorivi
        yhteenveto {:nimi "Yhteensä"
                    :alkukausi (apply + (map :alkukausi kiinteat))
                    :alkukausi-indeksikorjattu (apply + (map :alkukausi-indeksikorjattu kiinteat))
                    :loppukausi (apply + (map :loppukausi kiinteat))
                    :loppukausi-indeksikorjattu (apply + (map :loppukausi-indeksikorjattu kiinteat))
                    :yhteensa (+ (apply + (map :alkukausi kiinteat)) (apply + (map :loppukausi kiinteat)))
                    :yhteensa-indeksikorjattu (+ (apply + (map :alkukausi-indeksikorjattu kiinteat)) (apply + (map :loppukausi-indeksikorjattu kiinteat)))
                    :pysyvat-muutokset "Ei muutoksia"}
        kiinteat (conj kiinteat yhteenveto)

        ;; Hae tarjouksen tiedot
        tarjous (tarjous-kyselyt/hae-tarjous db urakka-id)

        k {:urakka-id urakka-id
           :tarjous tarjous
           :kustannussuunnitelma {:kilpailutettavat-hankinnat {:toimenpiteet kiinteat}}}]
    k))

(defn tallenna-kuukausittainen-summa [db kk-jakso alkujakso? nimi viimeinen-summa summa hoitovuoden-alkuvuosi sopimus-id
                                      toimenpideinstanssi-id kayttaja-id]
  (let [_ (doseq [kk kk-jakso]
            (let [summa (cond
                          (and alkujakso? (= kk 12)) viimeinen-summa
                          (and (not alkujakso?) (= kk 9)) viimeinen-summa
                          :else summa)
                  dbrivi (first (suunnitelma-q/hae-kuukausittainen-kiintea-kustannus db
                                  {:vuosi hoitovuoden-alkuvuosi
                                   :kuukausi kk
                                   :sopimus-id sopimus-id
                                   :toimenpideinstanssi-id toimenpideinstanssi-id}))
                  t (if (:id dbrivi)
                      (suunnitelma-q/paivita-kiinteat-kustannukset-kuukausittain<! db
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
                      (suunnitelma-q/tallenna-kiinteat-kustannukset-kuukausittain<! db
                        {:sopimus-id sopimus-id
                         :toimenpideinstanssi-id toimenpideinstanssi-id
                         :vuosi hoitovuoden-alkuvuosi
                         :kuukausi kk
                         :summa summa
                         :summa_indeksikorjattu nil
                         :tehtavaryhma nil
                         :tehtava nil
                         :luoja kayttaja-id}))]))]))

(defn tallenna-kilpailutettavat-hankinnat [db kayttaja {:keys [urakka-id hoitovuoden-alkuvuosi] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (log/info "tallenna-kilpailutettavat-hankinnat :: tiedot: " tiedot)
  (let [sopimus-id (urakat-q/urakan-paasopimus-id db urakka-id)
        ; Splittaa alkukauden summat kuukausittain
        _ (doseq [{:keys [nimi alkukausi loppukausi toimenpideinstanssi-id] :as toimenpide} (butlast (:toimenpiteet tiedot))]
            (let [alkukausi (bigdec alkukausi)
                  alkukausi-kuukaudet (yleiset/round2 2 (with-precision 4 (/ alkukausi 3)))
                  alkukausi-viimeinen-kuukausi (- alkukausi (* 2 alkukausi-kuukaudet))
                  loppukausi (bigdec loppukausi)
                  loppukausi-kuukaudet (yleiset/round2 2 (with-precision 4 (/ loppukausi 9)))
                  loppukausi-viimeinen-kuukausi (- loppukausi (* 8 loppukausi-kuukaudet))

                  ;; Tallenna alkujakso
                  _ (tallenna-kuukausittainen-summa db (range 10 13) true nimi alkukausi-viimeinen-kuukausi alkukausi-kuukaudet
                      hoitovuoden-alkuvuosi sopimus-id toimenpideinstanssi-id (:id kayttaja))
                  ;; Tallenna loppujakso
                  _ (tallenna-kuukausittainen-summa db (range 1 10) false nimi loppukausi-viimeinen-kuukausi loppukausi-kuukaudet
                      (inc hoitovuoden-alkuvuosi) sopimus-id toimenpideinstanssi-id (:id kayttaja))]))]
    (hae-kustannussuunnitelman-tiedot db kayttaja {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})))


(defrecord UusiKustannussuunnitelmaPalvelu []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-kustannussuunnitelman-tiedot
      (fn [user tiedot]
        (hae-kustannussuunnitelman-tiedot (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
      :tallenna-kilpailutettavat-hankinnat
      (fn [user tiedot]
        (tallenna-kilpailutettavat-hankinnat (:db this) user tiedot)))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-kustannussuunnitelman-tiedot
      :tallenna-kilpailutettavat-hankinnat)
    this))
