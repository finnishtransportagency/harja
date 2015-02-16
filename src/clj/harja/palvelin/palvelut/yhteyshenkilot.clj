(ns harja.palvelin.palvelut.yhteyshenkilot
  "Yhteyshenkilöiden ja päivystysten hallinnan palvelut"

  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as q]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]))

(declare hae-urakan-yhteyshenkilot hae-yhteyshenkilotyypit)

(defrecord Yhteyshenkilot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu :hae-urakan-yhteyshenkilot
                        (fn [user urakka-id]
                          (hae-urakan-yhteyshenkilot (:db this) user urakka-id)))
      (julkaise-palvelu :hae-yhteyshenkilotyypit
                        (fn [user _]
                          (hae-yhteyshenkilotyypit (:db this) user))))
    this)

  (stop [this]
    (doto (:http-palvelin this)
      (poista-palvelu :hae-urakan-yhteyshenkilot)
      (poista-palvelu :hae-yhteyshenkilotyypit))
    this))

(defn hae-yhteyshenkilotyypit [db user]
  (into #{}
        (map :rooli)
        (q/hae-yhteyshenkilotyypit db)))
                               
(defn hae-urakan-yhteyshenkilot [db user urakka-id]
  (assert (number? urakka-id) "Urakka-id:n pitää olla numero!")
  (let [tulokset (q/hae-urakan-yhteyshenkilot db urakka-id)
        yhteyshenkilot (into []
                             (comp
                              ;; Muodostetaan organisaatiosta parempi
                              (map #(if-let [org-id (:organisaatio_id %)]
                                      (assoc % :organisaatio {:tyyppi (keyword (str (:organisaatio_tyyppi %)))
                                                              :id org-id
                                                              :nimi (:organisaatio_nimi %)})
                                      %))
                              ;; Poistetaan kenttiä, joita emme halua frontille välittää
                              (map #(dissoc % :yu :organisaatio_id :organisaatio_nimi :organisaatio_tyyppi)))
                             tulokset)
        linkit (into #{} (map :yu) tulokset)
        paivystykset (if (empty? linkit) [] (q/hae-paivystykset db linkit))]
    ;; palauta yhteyshenkilöt ja päivystykset erikseen?
    yhteyshenkilot))















