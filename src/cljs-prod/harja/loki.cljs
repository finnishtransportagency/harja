(ns harja.loki
  "Tuotantomoodin lokitus: NOP")

(def +lokitetaan+ false)

(defn log [& things]
  (when +lokitetaan+
    (.apply js/console.log js/console (apply array things))))

(defn logt [data])

(defn ^:export lokitus-paalle []
  (set! +lokitetaan+ true))

(defn ^:export lokitus-pois []
  (set! +lokitetaan+ false))


(defn tarkkaile!
  [nimi atomi]
  )
