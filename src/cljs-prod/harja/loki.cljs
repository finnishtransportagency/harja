(ns harja.loki
  "Tuotantomoodin lokitus. Lokita, jos erikseen laitettu päälle."
  (:require
    [taoensso.timbre :as log]))

;; -- Konfiguroi Timbre-lokitus tuotantoon --
;; Timbre käyttää js/console appenderia CLJS:ssa, konfiguroidaan min-level sopivalle tasolle, jotta selaimen
;; konsoli ei täyty turhista lokituksista

(log/merge-config!
  {:appenders
   {:console
    {:min-level :info}}})

;; ---

;; TODO: Näitä apufunktioita ei juurikaan käytetä, poista jos ei tarvetta

(def +lokitetaan+ false)

(defn log [& things]
  (when +lokitetaan+
    (.apply js/console.log js/console (apply array things))))

(defn warn [& things]
  (.apply js/console.warn js/console (apply array things)))

(defn logt [data])

(defn error [& things]
  (.apply js/console.error js/console (apply array things)))

(defn ^:export lokitus-paalle []
  (set! +lokitetaan+ true))

(defn ^:export lokitus-pois []
  (set! +lokitetaan+ false))


(defn tarkkaile!
  [nimi atomi]
  )
