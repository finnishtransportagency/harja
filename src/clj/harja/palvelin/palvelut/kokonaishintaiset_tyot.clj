(ns harja.palvelin.palvelut.kokonaishintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.palvelin.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kokonaishintaiset-tyot :as q]))

(declare hae-urakan-kokonaishintaiset-tyot tallenna-kokonaishintaiset-tyot)

(defrecord Kokonaishintaiset-tyot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :kokonaishintaiset-tyot (fn [user urakka-id]
                                  (hae-urakan-kokonaishintaiset-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-kokonaishintaiset-tyot (fn [user tiedot]
                                           (tallenna-kokonaishintaiset-tyot (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :kokonaishintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-kokonaishintaiset-tyot)
    this))


(defn hae-urakan-kokonaishintaiset-tyot
  "Palvelu, joka palauttaa urakan kokonaishintaiset työt."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (map #(assoc %
               :summa (if (:summa %) (double (:summa %)))))
        (q/listaa-kokonaishintaiset-tyot db urakka-id)))

(defn tallenna-kokonaishintaiset-tyot
  "Palvelu joka tallentaa urakan kokonaishintaiset tyot."
  [db user {:keys [urakka-id sopimusnumero tyot]}]
  (oikeudet/vaadi-rooli-urakassa user oikeudet/rooli-urakanvalvoja urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")
  (jdbc/with-db-transaction [c db]
                            (let [nykyiset-arvot (hae-urakan-kokonaishintaiset-tyot c user urakka-id)
                                  valitut-vuosi-ja-kk (into #{} (map (juxt :vuosi :kuukausi) tyot))
                                  tyo-avain (fn [rivi]
                                              [(:toimenpideinstanssi rivi) (:vuosi rivi) (:kuukausi rivi)])
                                  tyot-kannassa (into #{} (map tyo-avain
                                                               (filter #(and
                                                                         (= (:sopimus %) sopimusnumero)
                                                                         (valitut-vuosi-ja-kk [(:vuosi %) (:kuukausi %)]))
                                                                       nykyiset-arvot)))
                                  uniikit-toimenpideninstanssit (into #{} (map #(:toimenpideinstanssi %) tyot))]
                              (doseq [tyo tyot]
                                (let [params [(:summa tyo) (:maksupvm tyo) (:toimenpideinstanssi tyo)
                                              sopimusnumero (:vuosi tyo) (:kuukausi tyo)]]
                                  (if (not (tyot-kannassa (tyo-avain tyo)))
                                    ;; insert
                                    (q/lisaa-kokonaishintainen-tyo<! c (:summa tyo)
                                                                     (if (:maksupvm tyo) (konv/sql-date (:maksupvm tyo)) nil)
                                                                     (:toimenpideinstanssi tyo)
                                                                     sopimusnumero (:vuosi tyo) (:kuukausi tyo))
                                    ;;update
                                    (q/paivita-kokonaishintainen-tyo! c (:summa tyo)
                                                                      (if (:maksupvm tyo) (konv/sql-date (:maksupvm tyo)) nil)
                                                                      (:toimenpideinstanssi tyo)
                                                                      sopimusnumero (:vuosi tyo) (:kuukausi tyo)))))
                              (log/info "Merkitään kustannussuunnitelmat likaiseksi toimenpideinstansseille: " uniikit-toimenpideninstanssit)
                              (q/merkitse-kustannussuunnitelmat-likaisiksi! c uniikit-toimenpideninstanssit))
                            (hae-urakan-kokonaishintaiset-tyot c user urakka-id)))
