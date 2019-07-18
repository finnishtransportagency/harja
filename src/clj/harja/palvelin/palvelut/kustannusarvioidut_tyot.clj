(ns harja.palvelin.palvelut.kustannusarvioidut-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.kustannusarvioidut-tyot :as q]
            [harja.kyselyt.kokonaishintaiset-tyot :as kok-q]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [clojure.set :as set]
            [harja.domain.roolit :as roolit]))

(defn hae-urakan-kustannusarvioidut-tyot
  "Funktio palauttaa urakan kustannusarvioidut työt. Käytetään teiden hoidon urakoissa (MHU). Kutsu funktiota budjettisuunnittelu-palvelun kautta. Oikeustarkastus tehdään siellä."
  [db user urakka-id]
  (into []
        (comp
          (map #(assoc %
                  :summa (if (:summa %) (double (:summa %)))))
          (map #(if (:osuus-hoitokauden-summasta %)
                  (update % :osuus-hoitokauden-summasta big/->big)
                  %)))
        (q/hae-kustannusarvioidut-tyot db {:urakka urakka-id})))

(defn tallenna-kustannusarvioidut-tyot
  "Funktio tallentaa ja palautaa urakan kustannusarvioidut tyot. Käytetään teiden hoidon urakoissa (MHU). Kutsu funktiota budjettisuunnittelu-palvelun kautta. Oikeustarkastus tehdään siellä."
  [db user {:keys [urakka-id sopimusnumero tyot]}]
  (assert (vector? tyot) "tyot tulee olla vektori")
  (let [nykyiset-arvot (hae-urakan-kustannusarvioidut-tyot db user urakka-id)
        valitut-vuosi-ja-kk (into #{} (map (juxt :vuosi :kuukausi) tyot))
        tyo-avain (fn [rivi]
                    [(:tyyppi rivi) (:toimenpideinstanssi rivi) (:vuosi rivi) (:kuukausi rivi)])
        tyot-kannassa (into #{} (map tyo-avain
                                     (filter #(and
                                                (= (:sopimus %) sopimusnumero)
                                                (valitut-vuosi-ja-kk [(:vuosi %) (:kuukausi %)]))
                                             nykyiset-arvot)))
        urakan-toimenpideinstanssit (into #{}
                                          (map :id)
                                          (tpi-q/urakan-toimenpideinstanssi-idt db urakka-id))
        tallennettavat-toimenpideinstanssit (into #{} (map #(:toimenpideinstanssi %) tyot))]

    ;; Varmistetaan ettei päivitystä voi tehdä toimenpideinstanssille, joka ei kuulu tähän urakkaan.
    (when-not (empty? (set/difference tallennettavat-toimenpideinstanssit
                                      urakan-toimenpideinstanssit))
      (throw (roolit/->EiOikeutta "virheellinen toimenpideinstanssi")))

    (doseq [tyo tyot]
      (as-> tyo t
            (update t :summa big/unwrap)
            (assoc t :sopimus sopimusnumero)
            (assoc t :kayttaja (:id user))
            (if (not (tyot-kannassa (tyo-avain t)))
              (q/lisaa-kustannusarvioitu-tyo<! db t)
              (q/paivita-kustannusarvioitu-tyo! db t)))))

  (hae-urakan-kustannusarvioidut-tyot db user urakka-id))