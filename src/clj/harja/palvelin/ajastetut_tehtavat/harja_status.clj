(ns harja.palvelin.ajastetut-tehtavat.harja-status
  "Tekee ajastetun status tarkistuksen kaikille komponenteille, joita tarvitsee vahtia."
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.palvelin.integraatiot.jms :as jms-util]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.status :as status-kyselyt]
            [harja.kyselyt.jarjestelman-tila :as jarjestelman-tila-kyselyt])
  (:import (java.net InetAddress)))

(def palvelimen-nimi
  (fmt/leikkaa-merkkijono 512
    (.toString (InetAddress/getLocalHost))))

(defn tarkista-tietokanta [db]
  (let [tulos (status-kyselyt/hae-tietokannan-tila db)
        status (if tulos "ok" "nok")
        _ (status-kyselyt/aseta-komponentin-tila<! db {:palvelin palvelimen-nimi
                                                       :komponentti "db"
                                                       :status status
                                                       :lisatiedot nil})]))

(defn tarkista-replica [db kehitysmoodi?]
  (let [viive (status-kyselyt/hae-replikoinnin-viive db)
        status (cond
                 (not (ominaisuus-kaytossa? :replica-db)) "ei-kaytossa"
                 (and (ominaisuus-kaytossa? :replica-db) (not (nil? viive)) (not kehitysmoodi?) (> 5 viive)) "ok"
                 (and (ominaisuus-kaytossa? :replica-db) (not (nil? viive)) (not kehitysmoodi?) (> 10 viive)) "hidas"
                 (and (ominaisuus-kaytossa? :replica-db) (not kehitysmoodi?)) "nok"
                 kehitysmoodi? "ei-kaytossa")
        replica {:palvelin palvelimen-nimi
                 :komponentti "replica"
                 :status status
                 :lisatiedot nil}
        _ (status-kyselyt/aseta-komponentin-tila<! db replica)]))

(defn tarkista-tloik [db itmf tloik-asetukset]
  (let [lahetysjonot (when (and tloik-asetukset itmf)
                       (vals (select-keys tloik-asetukset [:ilmoitusviestijono :toimenpidekuittausjono])))
        kuuntelijajonot (when (and tloik-asetukset itmf)
                          (vals (select-keys tloik-asetukset [:ilmoituskuittausjono :toimenpideviestijono])))
        lahetysjonot-status (reduce (fn [viesti j]
                                      (let [olemassa? (jms-util/jms-jono-olemassa? itmf j)
                                            ok? (jms-util/jms-jono-ok? itmf j)
                                            ;; Onnistumisesta ei oteta tarkempia tietoja
                                            jono-status (when (or (not olemassa?) (not ok?))
                                                          (str j ": - olemassa: " olemassa? " - status: " ok? "\n"))]
                                        (str viesti jono-status)))
                              "" lahetysjonot)
        kuuntelijajonot-status (reduce (fn [viesti j]
                                         (let [ok? (jms-util/jms-jono-ok? itmf j)
                                               jono-status (when (not ok?) (str j " - status: " ok? "\n"))]
                                           (str viesti jono-status)))
                                 "" kuuntelijajonot)
        ilmoitusviestijonon-kuuntelija-status (let [ok? (or (nil? (:ilmoitusviestijono tloik-asetukset))
                                                          (jms-util/jms-jonolla-kuuntelija? itmf
                                                            (:ilmoitusviestijono tloik-asetukset)
                                                            :tloik-ilmoitusviesti))
                                                    jono-status (when (not ok?)
                                                                  (str (:ilmoitusviestijono tloik-asetukset) " - status: " ok? "\n"))]
                                                jono-status)
        toimenpidekuittausjonon-kuuntelija-status (let [ok? (or (nil? (:toimenpidekuittausjono tloik-asetukset))
                                                              (jms-util/jms-jonolla-kuuntelija? itmf
                                                                (:toimenpidekuittausjono tloik-asetukset)
                                                                :tloik-toimenpidekuittaus))
                                                        jono-status (when (not ok?)
                                                                      (str (:toimenpidekuittausjono tloik-asetukset) " - status: " ok? "\n"))]
                                                    jono-status)
        status (cond (and (not (nil? lahetysjonot)) (= "" lahetysjonot-status) (= "" kuuntelijajonot-status)) "ok"
                     (and (nil? lahetysjonot) (nil? kuuntelijajonot)) "ei-kaytossa"
                     :else "nok")
        lisatiedot (when (not= "ok" status)
                     (str lahetysjonot-status kuuntelijajonot-status
                       ilmoitusviestijonon-kuuntelija-status
                       toimenpidekuittausjonon-kuuntelija-status))
        _ (status-kyselyt/aseta-komponentin-tila<! db {:palvelin palvelimen-nimi
                                                       :komponentti "tloik"
                                                       :status status
                                                       :lisatiedot lisatiedot})]))


(defn tarkista-harja-status [db itmf tloik-asetukset kehitysmoodi?]
  (try
    (let [_ (tarkista-tietokanta db)
          _ (tarkista-replica db kehitysmoodi?)
          _ (tarkista-tloik db itmf tloik-asetukset)])
    (catch Exception e
      (log/error e (format "Harjan statusta ei voitu tarkistaa: %s" e)))))

(defn tarkista-status [db itmf tloik-asetukset kehitysmoodi?]
  (log/debug (format "Tarkistetaan harjan status."))
  ;; Tätä ei tarkoituksella ajeta lukon kanssa, koska halutaan, että kaikilta appista pyörittäviltä palvelimilta, tehdään sama toimenpide
  (ajastettu-tehtava/ajasta-minuutin-valein
    1 1                                                     ;; alkaa pyöriä 1 min 1 sekunnin kuluttua käynnistyksestä - ja sen jälkeen minuutin välein
    (fn [_] (tarkista-harja-status db itmf tloik-asetukset kehitysmoodi?))))

(defn- poista-statusviestit [db]
  (status-kyselyt/poista-statusviestit db))

(defn- ajastus-poista-statusviestit [db]
  (log/info "Ajastetaan statusviestien siivous - ajetaan kerran vuorokaudessa - poistetaan viikon kaikki yli kaksi päivää vanhat")
  (ajastettu-tehtava/ajasta-paivittain [0 55 0]
    (fn [_]
      (lukot/yrita-ajaa-lukon-kanssa
        db
        "status_viestit"
        #(do
           (log/info "ajasta-paivittain :: status_viestit :: Alkaa " (pvm/nyt))
           (poista-statusviestit db)
           (log/info "ajasta-paivittain :: status_viestit :: Loppuu " (pvm/nyt)))))))

(defrecord HarjaStatus [tloik kehitysmoodi]
  component/Lifecycle
  (start [{db :db itmf :itmf :as this}]
    (assoc this :harja-status (tarkista-status db itmf tloik kehitysmoodi)
                :poista-turhat-statusviestit (ajastus-poista-statusviestit db)))
  (stop [{harja-status :harja-status
          poista-turhat-statusviestit :poista-turhat-statusviestit :as this}]
    (do
      (harja-status)
      (poista-turhat-statusviestit))
    (dissoc this
      :harja-status
      :poista-turhat-statusviestit)))
