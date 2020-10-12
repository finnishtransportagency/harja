(ns harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot
  (:require [clojure.core.async :refer [thread]]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [taoensso.timbre :as log]))

(defn- kanavan-nimi [urakka-id]
  (str "urakan_" urakka-id "_tapahtumat"))

(defn kuuntele-urakan-ilmoituksia [urakka-id callback]
  (log/debug (format "Kuunnellaan urakan id: %s ilmoituksia." urakka-id))
  (tapahtuma-apurit/tapahtuman-kuuntelija! (kanavan-nimi urakka-id) callback))

(defn ilmoita-saapuneesta-ilmoituksesta [urakka-id ilmoitus-id]
  (log/debug (format "Ilmoitetaan urakan id: %s uudesta ilmoituksesta id: %s." urakka-id ilmoitus-id))
  (tapahtuma-apurit/julkaise-tapahtuma (kanavan-nimi urakka-id) ilmoitus-id))

(defn- valityskanavan-nimi [ilmoitus-id]
  (str "ilmoitus_" ilmoitus-id "_valitetty"))

(defn ilmoita-lahetetysta-ilmoituksesta
  "Ilmoita tietyn ilmoituksen onnistuneesta välittämisestä. Kanava on keyword, joka kertoo millä mekanismilla
välitys tehtiin: :api, :sms tai :email."
  [ilmoitus-id valitystapa]
  (tapahtuma-apurit/julkaise-tapahtuma (valityskanavan-nimi ilmoitus-id) (name valitystapa)))
