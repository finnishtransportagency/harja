(ns harja.palvelin.ajastetut-tehtavat.kustannusarvioiden-toteumat
  "Ajastettu tehtävä toteutuneiden kustannusten muodostamiseksi valmiiksi toteutuneet_kustannukset tauluun"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.toteutuneet_kustannukset :as q]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.core :as t]))

(defn- onko-toteumat-jo-siirretty?
  "Tarkistetaan onko annetulle kuukaudelle tehty yhtään siirtoa. Jos on, niin ei siirretä uudestaan.
  Oletus on lähtökohtaisesti niin, että jos yksikin siirto on tehty, kaikki siirrot on tehty.
  Joka aiheuttaa sen ongelman, että jos on mahdollista muokata menneisyyden budjetteja, niin niitä ei ikinä
  siirretä toteutuneiksi."
  [db]
  (let [siirtamattomat (q/hae-siirtamattomat-kustannukset db)
        _ (log/debug "Näin monta riviä on vielä siirtämättä: " (pr-str siirtamattomat))]
    (if (> siirtamattomat 0)
      false
      true)))

(defn siirra-kustannukset
  "Kustannukset siirretään aina kuukauden ensimmäisenä päivänä, jotta saadaan edellisen kuukauden kaikki
  budjetoidut kustannukset matkaan."
  [db & args]
  (let [annettu-nyt (first args)
        nyt (or annettu-nyt (pvm/nyt))
        nyt-vuosi (pvm/vuosi nyt)
        nyt-kuukausi (pvm/kuukausi nyt)
        nyt-paiva (pvm/paiva nyt)
        onko-kuukauden-ensimmainen-paiva? (and (= nyt-paiva 1) )
        ;; Edellinen kuukausi voi olla nolla, jos vähennetään tammikuussa 1
        edellinen-kuukausi (if (= 0 (dec nyt-kuukausi))
                             12
                             (dec nyt-kuukausi))
        ;; Jos tammikuussa katsotaan edellistä kuukautta, pitää myös vuodesta vähentää yksi.
        vuosi (if (= 0 (dec nyt-kuukausi))
                    (dec nyt-vuosi)
                    nyt-vuosi)

        onko-siirto-tehty? (if onko-kuukauden-ensimmainen-paiva?
                             ;; Kuukauden ensimmäisenä päivänä tehdään aina siirto
                             false
                             ;; Tarkista onko siirto tehty
                             (onko-toteumat-jo-siirretty? db))]
    (log/info "Siirretään kustannusarvoitu_tyo taulusta toteutueet_kustannukset tauluun, jos on kuukauden ensimmäinen päivä tai jos siirtoa ei ole vielä tehty.")
    (if (not onko-siirto-tehty?)
      (do
        ;; Siirrä rivit
        (q/siirra-budjetoidut-tyot-toteutumiin db {:pvm nyt})

        (println "Siirto valamis!"))
      (log/info "Ei tehdä toista kertaa."))))

(defn- ajasta [db]
  (log/info "Ajastetaan kustannusarvoidun_tyon siirto toteutuneet_kustannukset tauluun joka päivä.")
  (ajastettu-tehtava/ajasta-paivittain [1 40 0]
    (do
      (log/info "ajasta-paivittain :: siirra-kustannukset :: Alkaa " (pvm/nyt))
      (fn [_]
          (lukot/yrita-ajaa-lukon-kanssa
            db
            "kustannusarvoidun_tyon_siirto"
            #(siirra-kustannukset db))))))

(defrecord KustannusarvioidenToteumat []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :kustannusarvioiduntyontoteumien-ajastus
                (ajasta db)))
  (stop [{poista :kustannusarvioiduntyontoteumien-ajastus :as this}]
    (poista)
    (dissoc this :kustannusarvioiduntyontoteumien-ajastus)))
