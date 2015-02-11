(ns harja.palvelin.palvelut.yhteyshenkilot
  "Yhteyshenkilöiden ja päivystysten hallinnan palvelut"

  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as q]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]))

(declare hae-urakan-yhteyshenkilot)

(defrecord Yhteyshenkilot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae-urakan-yhteyshenkilot
                        (fn [user urakka-id]
                          (hae-urakan-yhteyshenkilot (:db this) user urakka-id))))
    this)

  (stop [this]
    (doto (:http-palvelin this)
      (poista-palvelu :hae-urakan-yhteyshenkilot))
    this))

(defn hae-urakan-yhteyshenkilot [db user urakka-id]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (let [tulokset (q/hae-urakan-yhteyshenkilot db urakka-id)
        yhteyshenkilot (into []
                             (comp
                              ;; Muodostetaan organisaatiosta parempi
                              (map #(if-let [urk-id (:urakoitsija_id %)]
                                      (assoc % :organisaatio {:tyyppi :urakoitsija
                                                              :id urk-id
                                                              :nimi (:urakoitsija_nimi %)})
                                      %))
                              ;; Poistetaan kenttiä, joita emme halua frontille välittää
                              (map #(dissoc % :yu :urakoitsija_id :urakoitsija_nimi)))
                             tulokset)
        linkit (into #{} (map :yu) tulokset)
        paivystykset (if (empty? linkit) [] (q/hae-paivystykset db linkit))]
    ;; palauta yhteyshenkilöt ja päivystykset erikseen?
    yhteyshenkilot))













