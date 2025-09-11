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
(defrecord KopioiPysyvaMuutosTulevilleHoitovuosille [hoitovuosi rivit])
(defrecord PaivitaToimenpiteenTehtavamaarat [taulukon-rivit])
(defrecord PaivitaToimenpiteenTavoitehinnanMuutos [rivi tpi hk-alkuvuosi])
;; -- Pysyvät muutokset -- LOPPUU


(extend-protocol tuck/Event
  ;; -- Pysyvät muutokset -- ALKAA
  KopioiPysyvaMuutosTulevilleHoitovuosille
  (process-event [{hoitovuosi :hoitovuosi rivit :rivit} app]
    (log/debug "Tämä on vielä tekemättä")
    ;; TODO: tässä hanskattava muutosten kopiointi tuleville hoitovuosille...
    app)

  PaivitaToimenpiteenTehtavamaarat
  (process-event [{taulukon-rivit :taulukon-rivit} app]
    (log/debug "PaivitaToimenpiteenTehtavamaarat taulukon-rivit: " taulukon-rivit)
    ;; TODO: päivitä oikeaan kohtaan dataa tavoitehinnan tehtävämäärät mahdollista tallennusta varten
    app)

  PaivitaToimenpiteenTavoitehinnanMuutos
  (process-event [{rivi :rivi
                   tpi :tpi
                   hk-alkuvuosi :hk-alkuvuosi} app]
    (log/debug "PaivitaToimenpiteenTavoitehinnanMuutos " rivi " tpi " tpi "hk-alkuvuosi " hk-alkuvuosi)
    ;; TODO: päivitä oikeaan kohtaan dataa tavoitehinnan muutos mahdollista tallennusta varten
    app)
  ;; -- Pysyvät muutokset -- LOPPUU
  )
