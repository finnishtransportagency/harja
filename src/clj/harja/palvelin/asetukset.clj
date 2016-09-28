(ns harja.palvelin.asetukset
  "Yleinen Harja-palvelimen konfigurointi. Esimerkkinä käytetty Antti Virtasen clj-weba."
  (:require [schema.core :as s]
            [taoensso.timbre :as log]
            [gelfino.timbre :as gt]
            [clojure.java.io :as io]
            [harja.palvelin.lokitus.hipchat :as hipchat]
            [taoensso.timbre.appenders.postal :refer [make-postal-appender]]))


(def Tietokanta {:palvelin s/Str
                 :tietokanta s/Str
                 :portti s/Int
                 (s/optional-key :yhteyspoolin-koko) s/Int
                 :kayttaja s/Str
                 :salasana s/Str})
(def Asetukset
  "Harja-palvelinasetuksien skeema"
  {:http-palvelin                         {:portti                         s/Int
                                           :url                            s/Str
                                           (s/optional-key :threads)       s/Int
                                           (s/optional-key :max-body-size) s/Int}
   :kehitysmoodi                          Boolean
   (s/optional-key :testikayttajat)       [{:kayttajanimi s/Str :kuvaus s/Str}]
   :tietokanta                            Tietokanta
   :tietokanta-replica                    Tietokanta
   :fim                                   {:url s/Str}
   :log                                   {(s/optional-key :gelf)    {:palvelin s/Str
                                                                      :taso     s/Keyword}
                                           (s/optional-key :hipchat) {:huone-id s/Int :token s/Str :taso s/Keyword}

                                           (s/optional-key :email)   {:taso          s/Keyword
                                                                      :palvelin      s/Str
                                                                      :vastaanottaja [s/Str]}}
   (s/optional-key :integraatiot)         {:paivittainen-lokin-puhdistusaika [s/Num]}
   (s/optional-key :sonja)                {:url                     s/Str
                                           :kayttaja                s/Str
                                           :salasana                s/Str
                                           (s/optional-key :tyyppi) s/Keyword}
   (s/optional-key :sonja-sahkoposti) {:vastausosoite s/Str
                                       (s/optional-key :suora?) s/Bool
                                       (s/optional-key :palvelin) s/Str
                                       :jonot {(s/optional-key :sahkoposti-sisaan-jono) s/Str
                                               (s/optional-key :sahkoposti-sisaan-kuittausjono) s/Str
                                               (s/optional-key :sahkoposti-ulos-jono) s/Str
                                               (s/optional-key :sahkoposti-ulos-kuittausjono) s/Str}}
   (s/optional-key :sampo)                {:lahetysjono-sisaan       s/Str
                                           :kuittausjono-sisaan      s/Str
                                           :lahetysjono-ulos         s/Str
                                           :kuittausjono-ulos        s/Str
                                           :paivittainen-lahetysaika [s/Num]}
   (s/optional-key :tloik)                {:ilmoitusviestijono     s/Str
                                           :ilmoituskuittausjono   s/Str
                                           :toimenpideviestijono   s/Str
                                           :toimenpidekuittausjono s/Str
                                           :paivittainen-lahetysaika [s/Num]
                                           (s/optional-key :ilmoitukset) {:google-static-maps-key s/Str}}
   (s/optional-key :turi)                 {:url s/Str
                                           :kayttajatunnus s/Str
                                           :salasana s/Str
                                           :paivittainen-lahetysaika [s/Num]}
   (s/optional-key :tierekisteri)         {:url s/Str}

   :ilmatieteenlaitos                     {:lampotilat-url s/Str}

   (s/optional-key :geometriapaivitykset) {(s/optional-key :tuontivali)                                s/Int
                                           (s/optional-key :tieosoiteverkon-shapefile)                 s/Str
                                           (s/optional-key :tieosoiteverkon-alk-osoite)                s/Str
                                           (s/optional-key :tieosoiteverkon-alk-tuontikohde)           s/Str
                                           (s/optional-key :pohjavesialueen-shapefile)                 s/Str
                                           (s/optional-key :pohjavesialueen-alk-osoite)                s/Str
                                           (s/optional-key :pohjavesialueen-alk-tuontikohde)           s/Str
                                           (s/optional-key :talvihoidon-hoitoluokkien-shapefile)       s/Str
                                           (s/optional-key :talvihoidon-hoitoluokkien-alk-osoite)      s/Str
                                           (s/optional-key :talvihoidon-hoitoluokkien-alk-tuontikohde) s/Str
                                           (s/optional-key :soratien-hoitoluokkien-shapefile)          s/Str
                                           (s/optional-key :soratien-hoitoluokkien-alk-osoite)         s/Str
                                           (s/optional-key :soratien-hoitoluokkien-alk-tuontikohde)    s/Str
                                           (s/optional-key :siltojen-shapefile)                        s/Str
                                           (s/optional-key :siltojen-alk-osoite)                       s/Str
                                           (s/optional-key :siltojen-alk-tuontikohde)                  s/Str
                                           (s/optional-key :urakoiden-shapefile)                       s/Str
                                           (s/optional-key :urakoiden-alk-osoite)                      s/Str
                                           (s/optional-key :urakoiden-alk-tuontikohde)                 s/Str
                                           (s/optional-key :ely-alueiden-shapefile)                    s/Str
                                           (s/optional-key :ely-alueiden-alk-osoite)                   s/Str
                                           (s/optional-key :ely-alueiden-alk-tuontikohde)              s/Str
                                           (s/optional-key :valaistusurakoiden-shapefile)              s/Str
                                           (s/optional-key :valaistusurakoiden-alk-osoite)             s/Str
                                           (s/optional-key :valaistusurakoiden-alk-tuontikohde)        s/Str
                                           (s/optional-key :paallystyspalvelusopimusten-shapefile)              s/Str
                                           (s/optional-key :paallystyspalvelusopimusten-alk-osoite)             s/Str
                                           (s/optional-key :paallystyspalvelusopimusten-alk-tuontikohde)         s/Str}

   (s/optional-key :yha)                  {:url            s/Str
                                           :kayttajatunnus s/Str
                                           :salasana       s/Str}

   (s/optional-key :labyrintti)           {:url            s/Str
                                           :kayttajatunnus s/Str
                                           :salasana       s/Str}

   (s/optional-key :virustarkistus)       {:url            s/Str}

   (s/optional-key :paivystystarkistus)   {:paivittainen-aika [s/Num]}

   (s/optional-key :api-yhteysvarmistus)  {(s/optional-key :ajovali-minuutteina)     s/Int
                                           (s/optional-key :url)                     s/Str
                                           (s/optional-key :kayttajatunnus)          s/Str
                                           (s/optional-key :salasana)                s/Str}

   (s/optional-key :sonja-jms-yhteysvarmistus)  {(s/optional-key :ajovali-minuutteina)     s/Int
                                                 (s/optional-key :jono)                    s/Str}
   })

(def oletusasetukset
  "Oletusasetukset paikalliselle dev-serverille"
  {:http-palvelin        {:portti        3000 :url "http://localhost:3000/"
                          :threads       64
                          :max-body-size (* 1024 1024 16)}
   :kehitysmoodi         true
   :tietokanta           {:palvelin          "localhost"
                          :tietokanta        "harja"
                          :portti            5432
                          :yhteyspoolin-koko 64
                          :kayttaja          "harja"
                          :salasana          ""}

   :log                  {:gelf {:palvelin "gl.solitaservices.fi" :taso :info}}
   :geometriapaivitykset {:tuontivali 1}
   })

(defn yhdista-asetukset [oletukset asetukset]
  (merge-with #(if (map? %1)
                (merge %1 %2)
                %2)
              oletukset asetukset))

(defn validoi-asetukset [asetukset]
  (s/validate Asetukset asetukset))

(defn lue-asetukset
  "Lue Harja palvelimen asetukset annetusta tiedostosta ja varmista, että ne ovat oikeat"
  [tiedosto]
  (->> tiedosto
       slurp
       read-string
       (yhdista-asetukset oletusasetukset)))

(defn crlf-filter [msg]
  (assoc msg :args (mapv (fn [s]
                           (if (string? s)
                             (clojure.string/replace s #"[\n\r]" "")
                             s))
                         (:args msg))))

(defn konfiguroi-lokitus [asetukset]
  (log/set-config! [:middleware] [crlf-filter])

  (when-not (:kehitysmoodi asetukset)
    (log/set-config! [:appenders :standard-out :min-level] :info))

  (when-let [gelf (-> asetukset :log :gelf)]
    (log/set-config! [:appenders :gelf] (assoc gt/gelf-appender :min-level (:taso gelf)))
    (log/set-config! [:shared-appender-config :gelf] {:host (:palvelin gelf)}))

  (when-let [hipchat (-> asetukset :log :hipchat)]
    (log/set-config! [:appenders :hipchat]
                     (hipchat/luo-hipchat-appender (:huone-id hipchat) (:token hipchat) (:taso hipchat))))

  (when-let [email (-> asetukset :log :email)]
    (log/set-config! [:appenders :postal]
                     (make-postal-appender
                       {:enabled?   true
                        :rate-limit [1 30000]               ; 1 viesti / 30 sekuntia rajoitus
                        :async?     true
                        :min-level  (:taso email)}
                       {:postal-config
                        ^{:host (:palvelin email)}
                        {:from (str (.getHostName (java.net.InetAddress/getLocalHost)) "@solita.fi")
                         :to   (:vastaanottaja email)}}))))



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
