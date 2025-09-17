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
(defrecord PaivitaPysyvanMuutoksenLomakkeenVetolaatikot [vetolaatikot-auki])
;; -- Pysyvät muutokset -- LOPPUU


(extend-protocol tuck/Event
  ;; -- Pysyvät muutokset -- ALKAA

  PaivitaToimenpiteenTehtavamaarat
  (process-event [{taulukon-rivit :taulukon-rivit} app]
    (log/debug "PaivitaToimenpiteenTehtavamaarat taulukon-rivit: " taulukon-rivit)
    (-> app
      (assoc-in [:muokattava-muutos :tehtavat_ja_maarat] taulukon-rivit)))

  PaivitaToimenpiteenTavoitehinnanMuutos
  (process-event [{rivi :rivi
                   tpi :tpi
                   hk-alkuvuosi :hk-alkuvuosi} app]
    (log/debug "PaivitaToimenpiteenTavoitehinnanMuutos " rivi " tpi " tpi "hk-alkuvuosi " hk-alkuvuosi)
    ;; TODO: päivitä oikeaan kohtaan dataa tavoitehinnan muutos mahdollista tallennusta varten
    app)

  PaivitaPysyvanMuutoksenLomakkeenVetolaatikot
  (process-event [{vetolaatikot-auki :vetolaatikot-auki} app]
    (log/debug "PaivitaPysyvanMuutoksenLomakkeenVetolaatikot taulukon-rivit: " vetolaatikot-auki)
    (-> app
      (assoc-in [:muokattava-muutos :vetolaatikot-auki] vetolaatikot-auki)))

  ;; TODO: Jätetään myöhemmäksi, aluksi tallennus muokkaus ja muut tärkeämmät ominaisuudet.
  KopioiPysyvaMuutosTulevilleHoitovuosille
  (process-event [{hoitovuosi :hoitovuosi rivit :rivit} app]
    (log/debug "Tämä on vielä tekemättä")
    ;; TODO: tässä hanskattava muutosten kopiointi tuleville hoitovuosille...
    app)

  ;; -- Pysyvät muutokset -- LOPPUU
  )
