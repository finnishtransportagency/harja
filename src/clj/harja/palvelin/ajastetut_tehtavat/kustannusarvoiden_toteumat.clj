(ns harja.palvelin.ajastetut-tehtavat.kustannusarvoiden-toteumat
  "Ajastettu tehtävä toteutuneiden kustannusten muodostamiseksi valmiiksi toteutunut_tyo tauluun"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.toteutunut-tyo :as q]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.periodic :as time-periodic]
            [chime :as chime]))

(defn- tee-siirto [db kuukausi vuosi]
  (let [urakat (q/hae-kustannusarvioituun-tyohon-liittyvat-urakat db)
        _ (println "urakat ja heidän sopimukset" (pr-str urakat))
        kustannusarvoidut-tyot (q/siirra-kustannusarvoidut-tyot-toteutumiin db {:kuukausi kuukausi
                                                                              :vuosi vuosi})
        _ (println "kustannusarvoidut-tyot" (pr-str kustannusarvoidut-tyot))
        ]))

(defn- onko-toteumat-jo-siirretty?
  "Tarkistetaan onko annetulle kuukaudelle tehty yhtään siirtoa. Jos on, niin ei siirretä uudestaan.
  Oletus on lähtökohtaisesti niin, että jos yksikin siirto on tehty, kaikki siirrot on tehty."
  [db vuosi kuukausi]
  (let [toteutuneet-siirrot (q/hae-toteutuneiden-siirtojen-maara db {:kuukausi kuukausi :vuosi vuosi})
        _ (println "toteutuneet-siirrot" (pr-str toteutuneet-siirrot))]
    (if (> toteutuneet-siirrot 0)
      true
      false)))

(defn- siirra-kustannukset [db]
  (let [nyt (pvm/nyt)
        _ (println "nyt " (pr-str nyt))
        nyt-vuosi (pvm/vuosi nyt)
        _ (println "nyt-vuosi " (pr-str nyt-vuosi))
        nyt-kuukausi (pvm/kuukausi nyt)
        _ (println "nyt-kuukausi " (pr-str nyt-kuukausi))
        viimeinen-paiva (t/day (t/last-day-of-the-month nyt-vuosi nyt-kuukausi))
        _ (println "viimeinen-paiva " (pr-str viimeinen-paiva))
        kuukauden-loppupaiva (pvm/luo-pvm nyt-vuosi nyt-kuukausi viimeinen-paiva)
        _ (println "kuukauden-loppupaiva " (pr-str kuukauden-loppupaiva))
        onko-kuukauden-viimeinen-paiva? (or (= nyt kuukauden-loppupaiva) false)
        _ (println "onko-kuukauden-viimeinen-paiva? " (pr-str onko-kuukauden-viimeinen-paiva?))
        onko-siirto-tehty? (if onko-kuukauden-viimeinen-paiva?
                             false                          ;; Tämä ajetaan vain kerran päivässä, joten ajoa ei ole vielä tehty
                             (onko-toteumat-jo-siirretty? db nyt-vuosi nyt-kuukausi)             ;; Tarkista onko siirto tehty
                             )
        _ (println "onko-siirto-tehty? " (pr-str onko-siirto-tehty?))
        ;; Jos ei ole enää kuukauden viimeinen päivä ja siirtoa ei ole tehty,
        ;; niin päivitetaan kuukausi, jotta käsitellään edellisen kuukauden arvioidut työt
        nyt-kuukausi (if (and (not onko-siirto-tehty?)
                              (not onko-kuukauden-viimeinen-paiva?))
                       (dec nyt-kuukausi)
                       nyt-kuukausi)
        _ (println "nyt-kuukausi " (pr-str nyt-kuukausi))]
    (log/info "Siirretään kustannusarvoitu_tyo taulusta toteutunut_tyo tauluun, jos on viimeinen päivä
     ja jos siirtoa ei ole vielä tehty.")
    (if (not onko-siirto-tehty?)
      (do
        (println "onko tehty: " (pr-str onko-siirto-tehty?))
        (tee-siirto db nyt-kuukausi nyt-vuosi)
        (println "siirto valamis!!!!")
        )
      (log/info "Siirto on jo tehty. Ei tehdä toista kertaa."))))

(defn- ajasta [db]
  (log/info "Ajastetaan kustannusarvoidun_tyon siirto toteutunut_tyo tauluun joka päivä.")
  (ajastettu-tehtava/ajasta-paivittain [2 31 0]
                                       (fn [_]
                                         (lukot/yrita-ajaa-lukon-kanssa
                                           db
                                           "kustannusarvoidun_tyon_siirto"
                                           #(siirra-kustannukset db)))))

(defrecord KustannusarvoidenToteumat []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this ::kustannusarvoiduntyontoteumien-ajastus
                (ajasta db)))
  (stop [{poista ::kustannusarvoiduntyontoteumien-ajastus :as this}]
    (poista)
    (dissoc this ::kustannusarvoiduntyontoteumien-ajastus)))
