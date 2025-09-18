(ns harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot
  "Urakan muutosten tiedot - kirjatut muutokset."
  (:require [taoensso.timbre :as log]
            [tuck.core :as tuck]))

;; Muutostyypit:
;; - Pysyvät muutokset
;; - Muutostyö
;;   - Erillisrahoitettu
;;   - Poikkeama tehtävä- ja määräluettelon määrästä
;; - Johto- ja hallintokorvaus


;; --- Tuck-eventit ja käsittelijät ---

;; -- Pysyvät muutokset -- ALKAA
(defrecord PaivitaToimenpiteenTehtavamaarat [taulukon-rivit])
(defrecord PaivitaToimenpiteenTavoitehinnanMuutos [rivi tpi hk-alkuvuosi])
(defrecord KopioiPysyvaMuutosTulevilleHoitovuosille [hoitovuosi rivit])
;; -- Pysyvät muutokset -- LOPPUU


(extend-protocol tuck/Event
  ;; -- Pysyvät muutokset -- ALKAA

  PaivitaToimenpiteenTehtavamaarat
  (process-event [{taulukon-rivit :taulukon-rivit} app]
    (log/debug "PaivitaToimenpiteenTehtavamaarat taulukon-rivit: " taulukon-rivit)
    (-> app
      (update-in [:muokattava-muutos :tehtavat_ja_maarat]
        (fn [vanhat-arvot]
          ;; Uudet rivit tulevat aina yksittäisen vetolaatikon tiedoista
          ;; -> [{:tehtava 1 :maara 10...} {:tehtava 2 :maara 20...} ...]
          ;; Kaikkien vetolaatikkojen tiedot yhdistetään ja kootaan samaan app-tilaan yhdeksi vektoriksi
          ;; Uuden rivin tieto korvaa vanhan saman tehtävän tiedon, mikäli app-tilasta löytyy jo rivi samalla
          ;; tehtävä-id:llä
          (let [uudet-tehtavat (into {} (map (fn [rivi] [(:tehtava rivi) rivi]) taulukon-rivit))
                vanhat-tehtavat (into {} (map (fn [rivi] [(:tehtava rivi) rivi]) vanhat-arvot))
                yhdistetyt-tehtavat (merge vanhat-tehtavat uudet-tehtavat)]
            (vec (vals yhdistetyt-tehtavat)))))))

  PaivitaToimenpiteenTavoitehinnanMuutos
  (process-event [{rivi :rivi
                   tpi :tpi
                   hk-alkuvuosi :hk-alkuvuosi} app]
    (log/debug "PaivitaToimenpiteenTavoitehinnanMuutos " rivi " tpi " tpi "hk-alkuvuosi " hk-alkuvuosi)
    ;; TODO: päivitä oikeaan kohtaan dataa tavoitehinnan muutos mahdollista tallennusta varten
    app)

  ;; TODO: Jätetään myöhemmäksi, aluksi tallennus muokkaus ja muut tärkeämmät ominaisuudet.
  KopioiPysyvaMuutosTulevilleHoitovuosille
  (process-event [{hoitovuosi :hoitovuosi rivit :rivit} app]
    (log/debug "Tämä on vielä tekemättä")
    ;; TODO: tässä hanskattava muutosten kopiointi tuleville hoitovuosille...
    app)

  ;; -- Pysyvät muutokset -- LOPPUU
  )
