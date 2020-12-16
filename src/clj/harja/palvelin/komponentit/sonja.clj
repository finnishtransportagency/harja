(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylän JMS-jonoihin liittymiseksi."
  (:require [clojure.string :as clj-str]
            [harja.palvelin.integraatiot.jms :as jms]))

(defn luo-oikea-sonja [asetukset]
  (jms/->JMSClient "sonja" asetukset))

(defn luo-sonja [asetukset]
  (if (and asetukset (not (clj-str/blank? (:url asetukset))))
    (luo-oikea-sonja asetukset)
    (jms/luo-feikki-jms)))
