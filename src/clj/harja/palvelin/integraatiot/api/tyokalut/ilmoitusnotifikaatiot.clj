(ns harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot
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

(defn ilmoita-saapuneesta-ilmoituksesta [tapahtumat urakka-id ilmoitus-id]
  (log/debug (format "Ilmoitetaan urakan id: %s uudesta ilmoituksesta id: %s." urakka-id ilmoitus-id))
  (tapahtumat/julkaise! tapahtumat (kanavan-nimi urakka-id) ilmoitus-id))

(defn- valityskanavan-nimi [ilmoitus-id]
  (str "ilmoitus_" ilmoitus-id "_valitetty"))

(defn kun-ilmoitus-lahetetty [tapahtumat ilmoitus-id callback]
  (let [kanava (valityskanavan-nimi ilmoitus-id)]
    (tapahtumat/kuuntele! tapahtumat kanava
                          (fn [tapa]
                            (try
                              (callback tapa)
                              (finally
                                (tapahtumat/kuuroudu! tapahtumat kanava)))))))


(defn lopeta-ilmoituksen-lahetyksen-kuuntelu [tapahtumat ilmoitus-id]
  (tapahtumat/kuuroudu! tapahtumat (valityskanavan-nimi ilmoitus-id)))

(defn ilmoita-lahetetysta-ilmoituksesta
  "Ilmoita tietyn ilmoituksen onnistuneesta välittämisestä. Kanava on keyword, joka kertoo millä mekanismilla
välitys tehtiin: :api, :sms tai :email."
  [tapahtumat ilmoitus-id valitystapa]
  (tapahtumat/julkaise! tapahtumat (valityskanavan-nimi ilmoitus-id) (name valitystapa)))
