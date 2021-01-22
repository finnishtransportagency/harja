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
  [db vuosi kuukausi]
  (let [toteutuneet-siirrot (q/hae-toteutuneiden-siirtojen-maara db {:kuukausi kuukausi :vuosi vuosi})]
    (if (> toteutuneet-siirrot 0)
      true
      false)))

(defn- siirra-kustannukset [db]
  (let [nyt (pvm/nyt)
        nyt-vuosi (pvm/vuosi nyt)
        nyt-kuukausi (pvm/kuukausi nyt)
        nyt-paiva (pvm/paiva nyt)
        viimeinen-paiva (t/day (t/last-day-of-the-month nyt-vuosi nyt-kuukausi))
        onko-kuukauden-viimeinen-paiva? (and (= nyt-paiva viimeinen-paiva) )
        ;; Edellinen kuukausi voi olla nolla, jos vähennetään tammikuussa 1
        edellinen-kuukausi (if (= 0 (dec nyt-kuukausi))
                             12
                             (dec nyt-kuukausi))
        ;; Jos tammikuussa katsotaan edellistä kuukautta, pitää myös vuodesta vähentää yksi.
        nyt-vuosi (if (= 0 (dec nyt-kuukausi))
                    (dec nyt-vuosi)
                    nyt-vuosi)

        onko-siirto-tehty? (if onko-kuukauden-viimeinen-paiva?
                             ;; Kuukauden viimeisenä päivänä tehdään aina siirto
                             false
                             ;; Tarkista onko siirto tehty
                             (onko-toteumat-jo-siirretty? db nyt-vuosi edellinen-kuukausi))
        ;; Jos ei ole kuukauden viimeinen päivä ja siirtoa ei ole tehty,
        ;; niin päivitetaan kuukausi, jotta käsitellään edellisen kuukauden arvioidut työt
        nyt-kuukausi (if (and (not onko-siirto-tehty?)
                              (not onko-kuukauden-viimeinen-paiva?))
                       edellinen-kuukausi
                       nyt-kuukausi)]
    (log/info "Siirretään kustannusarvoitu_tyo taulusta toteutueet_kustannukset tauluun, jos on viimeinen päivä ja jos siirtoa ei ole vielä tehty.")
    (if (not onko-siirto-tehty?)
      (do
        ;; Siirrä kustannusarvioidut työt
        (q/siirra-kustannusarvoidut-tyot-toteutumiin! db {:kuukausi nyt-kuukausi
                                                          :vuosi nyt-vuosi})
        ;; Siirrä johto ja hallintokorvaukset
        (q/siirra-johto-ja-hallintokorvaukset-toteutumiin! db {:kuukausi nyt-kuukausi
                                                               :vuosi nyt-vuosi})
        (println "Siirto valamis!"))
      (log/info "Ei tehdä toista kertaa."))))

(defn- ajasta [db]
  (log/info "Ajastetaan kustannusarvoidun_tyon siirto toteutuneet_kustannukset tauluun joka päivä.")
  (ajastettu-tehtava/ajasta-paivittain [1 40 0]
                                       (fn [_]
                                         (lukot/yrita-ajaa-lukon-kanssa
                                           db
                                           "kustannusarvoidun_tyon_siirto"
                                           #(siirra-kustannukset db)))))

(defrecord KustannusarvoidenToteumat []
  component/Lifecycle
  (start [{db :db :as this}]
    (assoc this :kustannusarvoiduntyontoteumien-ajastus
                (ajasta db)))
  (stop [{poista :kustannusarvoiduntyontoteumien-ajastus :as this}]
    (poista)
    (dissoc this :kustannusarvoiduntyontoteumien-ajastus)))
