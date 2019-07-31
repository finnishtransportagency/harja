(ns harja.palvelin.palvelut.kiinteahintaiset-tyot
  (:require [clojure.set :as set]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt
             [kiinteahintaiset-tyot :as q]
             [toimenpideinstanssit :as tpi-q]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [harja.domain.roolit :as roolit]))


(defn hae-urakan-kiinteahintaiset-tyot
  "Funktio palauttaa urakan kiinteahintaiset työt. Käytetään teiden hoidon urakoissa (MHU)."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (into []
        (comp
          (map #(assoc %
                  :summa (if (:summa %) (double (:summa %)))))
          (map #(if (:osuus-hoitokauden-summasta %)
                  (update % :osuus-hoitokauden-summasta big/->big)
                  %)))
        (q/hae-kiinteahintaiset-tyot db urakka-id)))

(defn tallenna-kiinteahintaiset-tyot
  "Funktio tallentaa urakan kiinteahintaiset tyot. Käytetään teiden hoidon urakoissa (MHU)."
  [db user {:keys [urakka-id sopimusnumero tyot]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")
  (let [nykyiset-arvot (hae-urakan-kiinteahintaiset-tyot db user urakka-id)
        valitut-vuosi-ja-kk (into #{} (map (juxt :vuosi :kuukausi) tyot))
        tyo-avain (fn [rivi]
                    [(:tyyppi rivi) (:tehtava rivi) (:toimenpideinstanssi rivi) (:vuosi rivi) (:kuukausi rivi)])
        tyot-kannassa (into #{} (map tyo-avain
                                     (filter #(valitut-vuosi-ja-kk [(:vuosi %) (:kuukausi %)])
                                             nykyiset-arvot)))
        urakan-toimenpideinstanssit (into #{}
                                          (map :id)
                                          (tpi-q/urakan-toimenpideinstanssi-idt db urakka-id))
        tallennettavat-toimenpideinstanssit (into #{} (map #(:toimenpideinstanssi %) tyot))]

    ;; Varmistetaan ettei päivitystä voi tehdä toimenpideinstanssille, joka ei kuulu
    ;; tähän urakkaan.
    (when-not (empty? (set/difference tallennettavat-toimenpideinstanssit
                                      urakan-toimenpideinstanssit))
      (throw (roolit/->EiOikeutta "virheellinen toimenpideinstanssi")))

    (doseq [tyo tyot]
      (as-> tyo t
            (update t :summa big/unwrap)
            (assoc t :sopimus sopimusnumero)
            (assoc t :kayttaja (:id user))
            (if (not (tyot-kannassa (tyo-avain t)))
              (q/lisaa-kiinteahintainen-tyo<! db t)
              (q/paivita-kiinteahintainen-tyo! db t))))

    (hae-urakan-kiinteahintaiset-tyot db user urakka-id)))
