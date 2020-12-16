(ns harja.palvelin.komponentit.itmf
  (:require [clojure.string :as clj-str]
            [harja.palvelin.integraatiot.jms :as jms]))

(defn luo-oikea-itmf [asetukset]
  (jms/->JMSClient "itmf" asetukset))

(defn luo-itmf [asetukset]
  (if (and asetukset (not (clj-str/blank? (:url asetukset))))
    (luo-oikea-itmf asetukset)
    (jms/luo-feikki-jms)))
