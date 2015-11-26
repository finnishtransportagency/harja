(ns harja.palvelin.integraatiot.api.ilmoitusnotifikaatio
  (:require [clojure.core.async :refer [thread]]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [taoensso.timbre :as log]))

(defn- kanavan-nimi [urakka-id]
  (str "urakan_" urakka-id "_tapahtumat"))

(defn kuuntele-urakan-ilmoituksia [tapahtumat urakka-id callback]
  (log/debug (format "Kuunnellaan urakan id: %s ilmoituksia." urakka-id))
  (tapahtumat/kuuntele! tapahtumat (kanavan-nimi urakka-id) callback))

(defn lopeta-ilmoitusten-kuuntelu [tapahtumat urakka-id]
  (log/debug (format "Lopetetaan urakan id: %s ilmoitusten kuuntelu." urakka-id))
  (tapahtumat/kuuroudu! tapahtumat (kanavan-nimi urakka-id)))

(defn notifioi-urakan-ilmoitus [tapahtumat urakka-id ilmoitus-id]
  (log/debug (format "Ilmoitetaan urakan id: %s uudesta ilmoituksesta id: %s." urakka-id ilmoitus-id))
  (tapahtumat/julkaise! tapahtumat (kanavan-nimi urakka-id) ilmoitus-id))
