(ns harja.palvelin.ajastetut-tehtavat.analytiikan-toteumat
  "Ajastettu tehtävä toteumien siirtämiseksi analytiikan tarpeeksi analytiikan-toteumat tauluun"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.toteumat :as toteuma-kyselyt]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konversio]
            [harja.pvm :as pvm]))

(defn siirra-toteumat
  "Toteumat siirretään aina myöhään yöllä, jotta edellisen päivän kaikki toteumat ehtivät muodostua.
  Funktio olettaa, että sitä ajetaan ajastetusti yöllä, joten käytetän - nyt - hetkeä defaulttina."
  [db & args]
  (let [;; Testeissä ja lokaalisti voidaan ajatukset aloittaa milloin vain
        annettu-nyt (first args)
        nyt (or annettu-nyt (pvm/nyt))]
    (toteuma-kyselyt/siirra-toteumat-analytiikalle db {:nyt (konversio/sql-timestamp nyt)})))

(defn- ajasta [db]
  (log/info "Ajastetaan toteumien siirto analytiikan_toteumat tauluun joka päivä.")
  (ajastettu-tehtava/ajasta-paivittain [5 15 0]
    (do
      (fn [_]
        (lukot/yrita-ajaa-lukon-kanssa
          db
          "analytiikan_toteumat_siirto"
          #(do
             (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Alkaa " (pvm/nyt))
             (siirra-toteumat db)
             (log/info "ajasta-paivittain :: siirra-analyytikan-toteumat :: Loppuu " (pvm/nyt))))))))

(defrecord AnalytiikanToteumat []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :analytiikan-toteumien-ajastus
                (ajasta db)))
  (stop [{poista :analytiikan-toteumien-ajastus :as this}]
    (poista)
    (dissoc this :analytiikan-toteumien-ajastus)))
