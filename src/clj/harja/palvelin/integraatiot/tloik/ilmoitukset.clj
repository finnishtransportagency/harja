(ns harja.palvelin.integraatiot.tloik.ilmoitukset
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as time]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitus-sanoma]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.kuittaus-sanoma :as kuittaus]
            [harja.palvelin.komponentit.sonja :as sonja]))

(defn laheta-kuittaus [sonja integraatioloki kuittausjono kuittaus tapahtuma-id onnistunut lisatietoja]
  (integraatioloki/kirjaa-lahteva-jms-kuittaus integraatioloki kuittaus tapahtuma-id onnistunut lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus))

(defn vastaanota-ilmoitus [sonja integraatioloki db kuittausjono viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        tloik-viesti-id (:viesti-id viestin-sisalto)
        tapahtuma-id  (integraatioloki/kirjaa-saapunut-jms-viesti integraatioloki "tloik" "ilmoituksen-kirjaus" viesti-id viestin-sisalto)]
    (try
      (jdbc/with-db-transaction [transaktio db]
        (let [ilmoitus (ilmoitus-sanoma/lue-viesti viestin-sisalto)
              kuittaus (ilmoitus/kasittele-ilmoitus transaktio ilmoitus)]
          (println "------- SONJA:" sonja)
          (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id true nil)))
      (catch Exception e
        (log/error e "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta.")
        (let [virhe (str "Poikkeus: " e)
              kuittaus (kuittaus/muodosta tloik-viesti-id (.toString (time/now)) "virhe" nil virhe)]
          (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id false virhe))))))