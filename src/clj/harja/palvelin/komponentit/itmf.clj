(ns harja.palvelin.komponentit.itmf
  (:require [clojure.string :as clj-str]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.integraatiot.jms :as jms]))

(defn luo-oikea-itmf [asetukset]
  (if (ominaisuus-kaytossa? :itmf)
    (jms/->JMSClient "itmf" asetukset)
    (jms/luo-feikki-jms)))

(defn luo-itmf [asetukset]
  (if (and asetukset (not (clj-str/blank? (:url asetukset))))
    (luo-oikea-itmf asetukset)
    (jms/luo-feikki-jms)))
