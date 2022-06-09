(ns harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat
  "Ajastettu tehtävä toteumien siirtämiseksi analytiikan tarpeeksi analytiikan-toteumat tauluun"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.toteumat :as toteuma-kyselyt]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.core :as t]))

(defn siirra-toteumat
  "Toteumat siirretään aina myöhään yöllä, jotta edellisen päivän kaikki toteumat ehtivät muodostua."
  [db & args]
  (let [annettu-nyt (first args) ;; Testeissä ja lokaalisti voidaan ajatukset aloittaa milloin vain
        nyt (or annettu-nyt (pvm/nyt))]
    (log/debug "aika"nyt)
    (toteuma-kyselyt/siirra-toteumat-analytiikalle db {:nyt nyt})
    (log/debug "Siirto tehty!")))

(defn- ajasta [db]
  (log/info "Ajastetaan toteumien siirto analytiikan_toteumat tauluun joka päivä.")
  (ajastettu-tehtava/ajasta-paivittain [5 15 0]
    (do
      (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Alkaa " (pvm/nyt))
      (fn [_]
        (lukot/yrita-ajaa-lukon-kanssa
          db
          "analytiikan_toteumat_siirto"
          #(siirra-toteumat db)))
      (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Loppuu " (pvm/nyt)))))

(defrecord AnalytiikanToteumat []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :analytiikan-toteumien-ajastus
                (ajasta db)))
  (stop [{poista :analytiikan-toteumien-ajastus :as this}]
    (poista)
    (dissoc this :analytiikan-toteumien-ajastus)))
