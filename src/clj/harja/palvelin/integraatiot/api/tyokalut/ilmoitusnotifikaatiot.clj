(ns harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot
  (:require [clojure.core.async :refer [thread]]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [taoensso.timbre :as log]))

(defn- kanavan-nimi [urakka-id]
  (str "urakan_" urakka-id "_tapahtumat"))

(defn kuuntele-urakan-ilmoituksia [urakka-id callback]
  (log/debug (format "Kuunnellaan urakan id: %s ilmoituksia." urakka-id))
  (tapahtuma-apurit/tapahtuman-kuuntelija! (kanavan-nimi urakka-id) callback))

(defn kuuntele-kaikkia-ilmoituksia [callback]
  (tapahtuma-apurit/tapahtuman-kuuntelija! "kaikki-ilmoitus-tapahtumat" callback))

(defn ilmoita-saapuneesta-ilmoituksesta
  ([ilmoitus-id]
   (log/debug (format "Ilmoitetaan ilmoituksesta id: %s." ilmoitus-id)
     (tapahtuma-apurit/julkaise-tapahtuma "kaikki-ilmoitus-tapahtumat" ilmoitus-id)))
  ([urakka-id ilmoitus-id]
   (log/debug (format "Ilmoitetaan urakan id: %s uudesta ilmoituksesta id: %s." urakka-id ilmoitus-id))
   (tapahtuma-apurit/julkaise-tapahtuma (kanavan-nimi urakka-id) ilmoitus-id)))

(defn- valityskanavan-nimi [ilmoitus-id]
  (str "ilmoitus_" ilmoitus-id "_valitetty"))

(defn ilmoita-lahetetysta-ilmoituksesta
  "Ilmoita tietyn ilmoituksen onnistuneesta välittämisestä. Kanava on keyword, joka kertoo millä mekanismilla
välitys tehtiin: :api, :sms tai :email."
  [ilmoitus-id valitystapa]
  (log/debug (str "ilmoita-lahetetysta-ilmoituksesta " ilmoitus-id))
  (tapahtuma-apurit/julkaise-tapahtuma (valityskanavan-nimi ilmoitus-id) (name valitystapa)))
