(ns harja.palvelin.palvelut.tietyoilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelut async]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clj-time.coerce :refer [from-sql-time]]
            [harja.kyselyt.ilmoitukset :as q]
            [harja.domain.ilmoitukset :as ilmoitukset-domain]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot])
  (:import (java.util Date)))


(defn hae-tietyoilmoitukset [db user tiedot param4]
  )

(defrecord Tietyoilmoitukset []
  component/Lifecycle
  (start [{db :db
           tloik :tloik
           http :http-palvelin
           :as this}]
    (julkaise-palvelu http :hae-tietyoilmoitukset
                      (fn [user tiedot]
                        (hae-tietyoilmoitukset db user tiedot 501)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tietyoilmoitukset)
    this))
