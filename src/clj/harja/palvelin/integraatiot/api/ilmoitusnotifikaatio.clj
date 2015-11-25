(ns harja.palvelin.integraatiot.api.ilmoitusnotifikaatio
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [thread]]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [taoensso.timbre :as log]))

(defn- kanavan-nimi [urakka-id]
  (str "urakan_" urakka-id "_tapahtumat"))

(defn kuuntele-urakan-ilmoituksia [tapahtumat urakka-id callback]
  (tapahtumat/kuuntele! tapahtumat (kanavan-nimi urakka-id) callback))

(defn lopeta-ilmoitusten-kuuntelu [tapahtumat urakka-id]
  (tapahtumat/kuuroudu! tapahtumat (kanavan-nimi urakka-id)))

(defn notifioi-urakan-ilmoitus [tapahtumat urakka-id ilmoitus-id]
  (tapahtumat/julkaise! tapahtumat (kanavan-nimi urakka-id) ilmoitus-id))
