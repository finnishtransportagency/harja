(ns harja.palvelin.palvelut.kayttajatiedot
  "Palvelu, jolla voi hakea perustietoja nykyisestä käyttäjästä"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.kayttajat :as k]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]))

(declare hae-kayttajatiedot)

(defrecord Kayttajatiedot [testikayttajat]
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :kayttajatiedot
                      (fn [user alku]
                        (let [kt (hae-kayttajatiedot (:db this) user alku)
                              oikea-kayttaja (:oikea-kayttaja user)]
                          (log/info "KAYTTAJA: " user)
                          (log/info "jvh? " (roolit/jvh? user) ", oikea jvh? " (and oikea-kayttaja (roolit/jvh? oikea-kayttaja)))
                          (if (and testikayttajat
                                   (or (roolit/jvh? user)
                                       (and oikea-kayttaja
                                            (roolit/jvh? oikea-kayttaja))))
                            (assoc kt
                                   :testikayttajat testikayttajat)
                            kt))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :kayttajatiedot)
    this))


(defn hae-kayttajatiedot
  "Hae tämänhetkisen käyttäjän tiedot frontille varten"
  [db user alku]
  (if (nil? user)
    ;; Jos käyttäjää ei ole löytynyt, palautetaan frontille tieto
    {:poistettu true}
    (let [user (merge user
                      (k/hae-kayttajan-tiedot db user (:id user)))]
      (log/info "USER: " user)
      user)))
  
  
