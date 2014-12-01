(ns harja.palvelin.main
  (:require [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [harja.palvelin.komponentit.todennus :as todennus]
            [com.stuartsierra.component :as component]
            [harja.palvelin.asetukset :refer [lue-asetukset konfiguroi-lokitus]])
  (:gen-class))

(defn luo-jarjestelma [asetukset]
  (let [{:keys [tietokanta http-palvelin kehitysmoodi]} asetukset]
    (component/system-map
     :db (tietokanta/luo-tietokanta (:palvelin tietokanta)
                                    (:portti tietokanta)
                                    (:kayttaja tietokanta)
                                    (:salasana tietokanta))
     :todennus (if kehitysmoodi
                 (todennus/feikki-http-todennus {:nimi "dev" :id "LX123456789"})
                 (todennus/http-todennus))
     :http-palvelin (component/using
                     (http-palvelin/luo-http-palvelin (:portti http-palvelin))
                     [:todennus]))))

(def harja-jarjestelma nil)

(defn dev-start []
  (alter-var-root #'harja-jarjestelma component/start))

(defn dev-stop []
  (alter-var-root #'harja-jarjestelma component/stop))

(defn dev-restart []
  (dev-stop)
  (dev-start))

(defn -main [& argumentit]
  (alter-var-root #'harja-jarjestelma
                  (constantly
                   (-> (lue-asetukset (or (first argumentit) "asetukset.edn"))
                       luo-jarjestelma
                       component/start)))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (component/stop harja-jarjestelma)))))
