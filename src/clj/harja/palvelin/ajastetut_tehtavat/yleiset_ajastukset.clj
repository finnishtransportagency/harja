(ns harja.palvelin.ajastetut-tehtavat.yleiset-ajastukset
  "Kokoelma pienempiä yleisiä ajastuksia, jotka eivät sovi yhteen isommaksi tarkoitettuun palveluun"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.tapahtumat :as tapahtumat-kyselyt]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]))

(defn- siivoa-tapahtuman-tiedot [db]
  (let [poistetut (first (tapahtumat-kyselyt/poista-viimeisimmat-tapahtumat db))]
    (log/info (format "tapahtuma-tiedot taulusta poistettiin %s riviä" (:maara poistetut)))))

(defn- ajasta-siivoa-tapahtuman-tiedot [db]
  (log/info "Ajastetaan siivoa tapahtuman tiedot - ajetaan joka tunti.")
  (ajastettu-tehtava/ajasta-minuutin-valein 60 30
    (fn [_]
      (do
        (log/info "ajasta-minuutin-valein :: siivoa-tapahtuman-tiedot :: Alkaa " (pvm/nyt))
        ;; Aseta 30 sekunnin vanhenemisaika lukolle
        (lukot/yrita-ajaa-lukon-kanssa db "siivoa_tapahtuman_tiedot" #(siivoa-tapahtuman-tiedot db) 30)
        (log/info "ajasta-minuutin-valein :: siivoa-tapahtuman-tiedot :: Loppuu " (pvm/nyt))))))

(defrecord YleisetAjastuket []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :siivoa-tapahtuman-tiedot-ajastus
                (ajasta-siivoa-tapahtuman-tiedot db)))
  (stop [{poista :siivoa-tapahtuman-tiedot-ajastus :as this}]
    (poista)
    (dissoc this :siivoa-tapahtuman-tiedot-ajastus)))
