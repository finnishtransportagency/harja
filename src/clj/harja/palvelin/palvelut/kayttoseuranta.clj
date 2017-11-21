(ns harja.palvelin.palvelut.kayttoseuranta
  (:require [clojure.java.jdbc :as jdbc]
            [jeesql.core :refer [defqueries]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kanavat.kanavat :as q]

            [harja.domain.kanavat.kanava :as kan]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.urakka :as ur]
            [clojure.spec.alpha :as s]))

(defqueries "harja/kyselyt/kayttoseuranta.sql")

(s/def ::lokita-kaytto-kysely (s/keys :req []))

(defn lokita-kaytto! [db user tiedot]
  (log/debug "Kirjaa käyttö")
  (kirjaa-kaytto<! db {:kayttaja (:id user)
                       :tila (:tila tiedot)
                       :sivu (:sivu tiedot)
                       :lisatieto (:lisatieto tiedot)}))

(defrecord Kayttoseuranta []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :lokita-kaytto
      (fn [user tiedot]
        (lokita-kaytto! db user tiedot))
      {:kysely-spec ::lokita-kaytto-kysely})

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :lokita-kaytto)
    this))
