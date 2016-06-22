(ns harja.ui.kartta.apurit
  #?(:cljs
     (:require [ol.geom.Point])))

(def kulmaraja-nuolelle (/ Math/PI 2)) ;; pi / 2 = 90 astetta

(defn kulma [[x1 y1] [x2 y2]]
  (Math/atan2
   (- y2 y1)
   (- x2 x1)))

(defn taitokset-valimatkoin
  ([valimatka taitokset]
   (taitokset-valimatkoin valimatka taitokset
                          #?(:clj identity
                             :cljs (fn [p]
                                     (ol.geom.Point. (clj->js p))))))
  ([valimatka taitokset luo-piste]
   (loop [pisteet-ja-rotaatiot []
          viimeisin-sijanti [0 0]
          [{:keys [sijainti rotaatio]} & taitokset] taitokset
          ensimmainen? true]
     (if-not sijainti
       ;; Kaikki käsitelty
       pisteet-ja-rotaatiot

       (let [[x1 y1] viimeisin-sijanti
             [x2 y2] (second sijainti)
             dx (- x1 x2)
             dy (- y1 y2)
             dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
         (cond
           (or (> dist valimatka)
               ensimmainen?)
           (recur (conj pisteet-ja-rotaatiot
                        [(-> sijainti second luo-piste)
                         (kulma viimeisin-sijanti (second sijainti))])
                  (second sijainti) taitokset false)

           :else
           (recur pisteet-ja-rotaatiot
                  viimeisin-sijanti taitokset false)))))))



(defn pisteiden-taitokset
  ([pisteet] (pisteiden-taitokset pisteet true))
  ([pisteet kiertosuunta-ylos?]
   (reduce (fn [taitokset [p1 p2 p3]]
             (conj taitokset
                   {:sijainti [p1 p2]
                    :rotaatio (let [kulma (/ (+ (kulma p1 p2)
                                                (kulma p2 p3))
                                             2.0)]
                                (if kiertosuunta-ylos? kulma (- kulma)))}))
           []
           (partition 3 1 pisteet))))



(def ^:private suurin-extent 1500000)
(def ^:private pienin-extent 25000)
(def ^:private extent-range (- suurin-extent pienin-extent))
(def ^:private suurin-skaala 1.0)
(def ^:private pienin-skaala 0.4)
(def ^:private skaala-range (- suurin-skaala pienin-skaala))

(defn ikonin-skaala
  "Palauttaa ikonille uuden skaalan näkyvän alueen koon perusteella.
Suurempi näkyvä alue skaalaa ikonia pienemmäksi."
  [hypotenuusa scale]
  (double
   (* scale
      (cond
        (<= hypotenuusa pienin-extent) suurin-skaala
        (>= hypotenuusa suurin-extent) pienin-skaala
        :default
        (+ pienin-skaala (* skaala-range
                            (/ (- suurin-extent hypotenuusa) extent-range)))))))
