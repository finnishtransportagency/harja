(ns harja.palvelin.ajastetut-tehtavat.sonja-jms-yhteysvarmistus
  "Tekee ajastetun yhteysvarmistuksen API:n"
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [chime :refer [chime-at]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.kyselyt.yhteystarkistukset :as yhteystarkistukset]
            [clj-time.format :as format]
            [clj-time.coerce :as coerce]
            [clj-time.core :as time]))

(def sonja-yhteystarkistus "sonja-ping")

(defn vastaanota-viesti [db jono viesti]
  (let [viesti (.getText viesti)]
    (if (= "ping" viesti)
      (yhteystarkistukset/kirjaa-yhteystarkistus<! db sonja-yhteystarkistus)
      (log/error (format "Yhteyskokeilu Sonjan JMS-jonoon (%s) ei palauttanut oletettua vastausta. Vastaus: %s." jono viesti)))))

(defn laheta-viesti [db sonja jono]
  (lukot/yrita-ajaa-lukon-kanssa
    db
    "sonja-jms-yhteysvarmistus"
    (fn []
      (let [lahteva-viesti "ping"]
        (sonja/laheta sonja jono lahteva-viesti)))))

(defn tarkista-tila [db minuutit]
  ;; todo: tee kanta tasolla: tallenna molemmat lähetys aika ja vastaanotto aika
  (let [viimeisin-tarkistus (coerce/from-sql-time (yhteystarkistukset/hae-viimeisin-ajoaika db sonja-yhteystarkistus))
        edellisesta-kulunut (time/in-minutes (time/interval viimeisin-tarkistus (time/now)))]
    (when (> edellisesta-kulunut (+ minuutit 1))
      )
    ))

(defn tee-jms-yhteysvarmistus-tehtava [{:keys [db sonja]} minuutit jono]
  (when (and minuutit jono)
    (log/debug (format "Varmistetaan Sonjan JMS jonoihin yhteys %s minuutin välein." minuutit))
    (sonja/kuuntele sonja jono #(vastaanota-viesti jono db %))
    (ajastettu-tehtava/ajasta-minuutin-valein
      minuutit
      (fn [_]
        (laheta-viesti db sonja jono)
        (tarkista-tila db minuutit)))))

(defrecord SonjaJmsYhteysvarmistus [ajovali-minuutteina jono]
  component/Lifecycle
  (start [this]
    (assoc this :jms-varmistus (tee-jms-yhteysvarmistus-tehtava this ajovali-minuutteina jono)))
  (stop [this]
    (let [lopeta (get this :jms-varmistus)]
      (when lopeta (lopeta)))
    this))
