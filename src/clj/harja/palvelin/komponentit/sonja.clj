(ns harja.palvelin.komponentit.sonja
  (:require [clojure.string :as clj-str]
            [harja.palvelin.integraatiot.jms-clientit.apache-classic :as activemq]
            [harja.palvelin.integraatiot.jms-clientit.apache-artemis :as artemis]
            [harja.palvelin.integraatiot.jms :as jms]))

(defn luo-oikea-sonja [asetukset]
  (case (:tyyppi asetukset)
    :activemq (activemq/->ApacheClassic "sonja" asetukset)
    :artemis (artemis/->ApacheArtemis "sonja" asetukset)))

(defn luo-sonja [asetukset]
  (if (and asetukset (not (clj-str/blank? (:url asetukset))))
    (luo-oikea-sonja asetukset)
    (jms/luo-feikki-jms)))
