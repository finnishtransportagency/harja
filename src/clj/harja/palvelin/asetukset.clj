(ns harja.palvelin.asetukset
  "Yleinen Harja-palvelimen konfigurointi. Esimerkkinä käytetty Antti Virtasen clj-weba."
  (:require [schema.core :as s]
            [taoensso.timbre :as log]
            [gelfino.timbre :as gt]
            [clojure.java.io :as io]
            [harja.palvelin.lokitus.hipchat :as hipchat]
            [taoensso.timbre.appenders.postal :refer [make-postal-appender]]))


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
   :fim {:url s/Str}
   :log {(s/optional-key :gelf) {:palvelin s/Str
                                 :taso s/Keyword}
         (s/optional-key :hipchat) {:huone-id s/Int :token s/Str :taso s/Keyword}

         (s/optional-key :email) {:taso s/Keyword
                                  :palvelin s/Str
                                  :vastaanottaja [s/Str]}}
   
   }) 

(def oletusasetukset
  "Oletusasetukset paikalliselle dev-serverille"
  {:http-palvelin {:portti 3000 :url "http://localhost:3000/"}
   :kehitysmoodi true
   :tietokanta {:palvelin "localhost"
                :tietokanta "harja"
                :portti 5432
                :kayttaja "harja"
                :salasana ""}
   
   :log {:gelf {:palvelin "gl.solitaservices.fi" :taso :info}}
   })

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
      

(defn konfiguroi-lokitus [asetukset]
  (when-let [gelf (-> asetukset :log :gelf)]
    (log/set-config! [:appenders :gelf] (assoc gt/gelf-appender :min-level (:taso gelf)))
    (log/set-config! [:shared-appender-config :gelf] {:host (:palvelin gelf)}))

  (when-let [hipchat (-> asetukset :log :hipchat)]
    (log/set-config! [:appenders :hipchat]
                     (hipchat/luo-hipchat-appender (:huone-id hipchat) (:token hipchat) (:taso hipchat))))

  (when-let [email (-> asetukset :log :email)]
    (log/set-config! [:appenders :postal]
                     (make-postal-appender
                      {:enabled? true
                       :rate-limit [1 30000] ; 1 viesti / 30 sekuntia rajoitus
                       :async? true
                       :min-level (:taso email)}
                      {:postal-config
                       ^{:host (:palvelin email)}
                       {:from (str (.getHostName (java.net.InetAddress/getLocalHost)) "@solita.fi")
                        :to (:vastaanottaja email)}}))))

 

(comment (defn konfiguroi-lokitus 
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
    (.doConfigure konfiguroija konfiguraatio))))
      
  
