(ns harja.palvelin.palvelut.siltatarkastukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.siltatarkastukset :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.domain.roolit :as roolit]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn hae-urakan-sillat
  "Hakee annetun urakan alueen sillat sekä niiden viimeisimmän tarkastuspäivän ja tarkastajan."
  [db user urakka-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (q/hae-urakan-sillat db urakka-id)))

(defn hae-sillan-tarkastukset
  "Hakee annetun sillan siltatarkastukset"
  [db user silta-id]
  (into []
        (q/hae-sillan-tarkastukset db silta-id)))

(defn hae-siltatarkastusten-kohteet
  "Hakee siltatarkastusten kohteet ID:iden perusteella"
  [db user siltatarkastus-idt]
  (log/debug  "kutsun kohta kohteita, " (pr-str siltatarkastus-idt))
  (into []
        (q/hae-siltatarkastusten-kohteet db siltatarkastus-idt)))

(defn paivita-siltatarkastuksen-kohteet!
  "Päivittää siltatarkastuksen kohteet"
  [db user {:keys [urakka-id siltatarkastus-id kohderivit]}]
  (oik/vaadi-rooli-urakassa user oik/rooli-urakanvalvoja urakka-id)
  (jdbc/with-db-transaction [c db]
                            (doseq [rivi kohderivit]
                              (do
                                  (log/info "  päivittyi: " (q/paivita-siltatarkastuksen-kohteet! c (:tulos rivi) (:lisatieto rivi) siltatarkastus-id
                                                                                                  (:kohdenro rivi))))
                              )
                            (hae-siltatarkastusten-kohteet c user [siltatarkastus-id])))


(defrecord Siltatarkastukset []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :hae-urakan-sillat
                        (fn [user urakka-id]
                          (hae-urakan-sillat db user urakka-id)))
      (julkaise-palvelu http :hae-sillan-tarkastukset
                        (fn [user silta-id]
                          (hae-sillan-tarkastukset db user silta-id)))
      (julkaise-palvelu http :hae-siltatarkastusten-kohteet
                        (fn [user siltatarkastus-idt]
                          (hae-siltatarkastusten-kohteet db user siltatarkastus-idt)))
      (julkaise-palvelu http :paivita-siltatarkastuksen-kohteet
                        (fn [user tiedot]
                          (paivita-siltatarkastuksen-kohteet! db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae-urakan-sillat)
    (poista-palvelut (:http-palvelin this) :hae-sillan-tarkastukset)
    (poista-palvelut (:http-palvelin this) :hae-siltatarkastusten-kohteet)
    (poista-palvelut (:http-palvelin this) :paivita-siltatarkastuksen-kohteet)))
