(ns harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat
  "Ajastettu tehtävä toteumien siirtämiseksi analytiikan tarpeeksi analytiikan-toteumat tauluun"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.toteumat :as toteuma-kyselyt]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.ajastetut-tehtavat-kyselyt :as ajastetut-tehtavat-kyselyt]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konversio]
            [harja.pvm :as pvm]))

(defn siirra-toteumat
  "Toteumat siirretään aina myöhään yöllä, jotta edellisen päivän kaikki toteumat ehtivät muodostua.
  Funktio olettaa, että sitä ajetaan ajastetusti yöllä, joten käytetän - nyt - hetkeä defaulttina.
  Siirretään kaikki ne toteumat, jotka on muodostuneet (lisätty) tai päivitetty edellisen ajokerran jälkeen.
  Edellinen ajokerta saadaan ajastetut_tehtavat taulusta."
  [db & args]
  (let [;; Testeissä ja lokaalisti voidaan ajatukset aloittaa milloin vain
        viimeisin-ajokerta (ajastetut-tehtavat-kyselyt/hae-viimeisin-onnistunut-ajokerta db "siirra_toteumat_analytiikalle")
        _ (log/info "Viimeisin onnistunut ajokerta analytiikan_toteumat siirrossa:" viimeisin-ajokerta)
        annettu-alkuaika (first args)                       ;; Annetaan testeissä
        annettu-loppuaika (second args)                     ;; Annetaan testeissä
        alkuaika (or annettu-alkuaika                       ;; Käytetään testeissä
                   viimeisin-ajokerta                       ;; Saatiin tietokanansta
                   (pvm/ajan-muokkaus pvm/nyt true 1 :paiva) ;; Ensimmäisellä kerralla siirretään viimeisen päivän toteumat
                   )
        alkuaika-sql (if (= "java.sql.Timestamp" (type alkuaika))
                       alkuaika
                       (konversio/sql-timestamp alkuaika))
        loppuaika (or
                    annettu-loppuaika                     ;; Käytetään testeissä
                    pvm/nyt                                 ;; Nykyhetki muulloin
                    )
        loppuaika-sql (if (= "java.sql.Timestamp" (type loppuaika))
                        loppuaika
                        (konversio/sql-timestamp loppuaika))]
    (try
      (toteuma-kyselyt/siirra-toteumat-analytiikalle db {:alkuaika alkuaika-sql :loppuaika loppuaika-sql})
      (ajastetut-tehtavat-kyselyt/lisaa_ajastettu_tehtava! db {:tyyppi "siirra_toteumat_analytiikalle",
                                                               :ajankohta loppuaika-sql,
                                                               :onnistunut true,
                                                               :virhe nil})
      (log/info "Toteumien siirto analytiikan_toteumat tauluun onnistui aikaväliltä: " alkuaika-sql " - " loppuaika-sql)
      (catch Exception e
        (log/error e "Toteumien siirto analytiikan_toteumat tauluun epäonnistui. ERROR:" e)
        (ajastetut-tehtavat-kyselyt/lisaa_ajastettu_tehtava! db {:tyyppi "siirra_toteumat_analytiikalle",
                                                                 :ajankohta loppuaika-sql,
                                                                 :onnistunut false,
                                                                 :virhe e})))))

(defn- ajasta [db]
  (log/info "Ajastetaan toteumien siirto analytiikan_toteumat tauluun joka päivä.")
  (ajastettu-tehtava/ajasta-paivittain [5 15 0]
    (fn [_]
      (lukot/yrita-ajaa-lukon-kanssa
        db
        "analytiikan_toteumat_siirto"
        #(do
           (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Alkaa " (pvm/nyt))
           (siirra-toteumat db)
           (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Loppuu " (pvm/nyt)))))))

(defrecord AnalytiikanToteumat []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :analytiikan-toteumien-ajastus
      (ajasta db)))
  (stop [{poista :analytiikan-toteumien-ajastus :as this}]
    (poista)
    (dissoc this :analytiikan-toteumien-ajastus)))
