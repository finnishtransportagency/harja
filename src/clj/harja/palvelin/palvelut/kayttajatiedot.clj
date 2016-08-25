(ns harja.palvelin.palvelut.kayttajatiedot
  "Palvelu, jolla voi hakea perustietoja nykyisestä käyttäjästä"
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.kayttajat :as q]
            [harja.domain.roolit :as roolit]
            [harja.domain.oikeudet :as oikeudet]))

(defn oletusurakkatyyppi
  [db user]
  (let [kayttajan-urakat (oikeudet/kayttajan-urakat user)]
    (if (empty? kayttajan-urakat)
      :hoito
      (keyword (q/hae-kayttajan-yleisin-urakkatyyppi db kayttajan-urakat)))))

(defrecord Kayttajatiedot []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :kayttajatiedot
                      (fn [user alku]
                        (assoc user :urakkatyyppi
                                    (oletusurakkatyyppi (:db this) user))))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :kayttajatiedot)
    this))
