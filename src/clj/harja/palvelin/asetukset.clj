(ns harja.palvelin.asetukset
  "Yleinen Harja-palvelimen konfigurointi. Esimerkkinä käytetty Antti Virtasen clj-weba."
  (:require [schema.core :as s]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io])
  (:import [ch.qos.logback.classic.joran JoranConfigurator]
           [org.slf4j LoggerFactory]))


(def Asetukset
  "Harja-palvelinasetuksien skeema"
  {:http-palvelin {:portti s/Int
                   :url s/Str}
   :kehitysmoodi Boolean
   :tietokanta {:palvelin s/Str
                :tietokanta s/Str
                :portti s/Int
                :kayttaja s/Str
                :salasana s/Str}
   :logback-konfiguraatio s/Str}) 

(def oletusasetukset
  "Oletusasetukset paikalliselle dev-serverille"
  {:http-palvelin {:portti 3000 :url "http://localhost:3000/"}
   :kehitysmoodi true
   :tietokanta {:palvelin "localhost"
                :tietokanta "harja"
                :portti 5432
                :kayttaja "harja"
                :salasana ""}
   :logback-konfiguraatio "logback.properties"})

(defn yhdista-asetukset [oletukset asetukset]
  (merge-with #(if (map? %1)
                 (merge %1 %2)
                 %2)
              oletukset asetukset))

(defn lue-asetukset
  "Lue Harja palvelimen asetukset annetusta tiedostosta ja varmista, että ne ovat oikeat"
  [tiedosto]
  (->> tiedosto
       slurp
       read-string
       (yhdista-asetukset oletusasetukset)
       (s/validate Asetukset)))
      

(defn konfiguroi-lokitus 
  "Konfiguroi logback lokutiksen ulkoisesta .properties tiedostosta."
  [asetukset]
  (let [konfiguroija (JoranConfigurator.)
        konteksti (LoggerFactory/getILoggerFactory)
        konfiguraatio (-> asetukset
                          :logback-konfiguraatio
                          io/file)]
    (println "Lokituksen konfiguraatio: " (.getAbsolutePath konfiguraatio)) ;; käytetään println ennen lokituksen alustusta
    (.setContext konfiguroija konteksti)
    (.reset konteksti)
    (.doConfigure konfiguroija konfiguraatio)))
      
  
