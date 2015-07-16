(ns harja.palvelin.integraatiot.tloik.ilmoitukset
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitus-sanoma]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]))

(defn laheta-kuittaus [sonja integraatioloki kuittausjono kuittaus tapahtuma-id virheet]
  ;; todo: toteuta
  )

(defn vastaanota-ilmoitus [sonja integraatioloki db kuittausjono viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        tloik-viesti-id (:viesti-id viestin-sisalto)
        tapahtuma-id (integraatioloki/kirjaa-jms-viesti integraatioloki "tloik" "ilmoituksen-kirjaus" viesti-id "sisään" viestin-sisalto)]
    (try
      (jdbc/with-db-transaction [transaktio db]
        (let [ilmoitus (ilmoitus-sanoma/lue-viesti viestin-sisalto)
              kuittaus (ilmoitus/kasittele-ilmoitus transaktio ilmoitus)]
          (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id nil)))
      (catch Exception e
        ;; todo: tee kuittaus
        (log/error e "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta.")))))



