(ns harja.ui.kartta.apurit
  #?(:cljs
     (:require [ol.geom.Point])))

(def kulmaraja-nuolelle (/ Math/PI 2)) ;; pi / 2 = 90 astetta

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
          verrokki-kulma rotaatio
          ensimmainen? true]
     (if-not sijainti
       ;; Kaikki käsitelty
       pisteet-ja-rotaatiot

       (let [[x1 y1] viimeisin-sijanti
             [x2 y2] sijainti
             dx (- x1 x2)
             dy (- y1 y2)
             dist (Math/sqrt (+ (* dx dx) (* dy dy)))
             kulman-erotus (- verrokki-kulma rotaatio)]
         (cond
           (or (> dist valimatka)
               (> (max kulman-erotus (- kulman-erotus)) kulmaraja-nuolelle)
               ensimmainen?)
           (recur (conj pisteet-ja-rotaatiot
                        [(-> sijainti second luo-piste) rotaatio])
                  sijainti taitokset rotaatio false)

           :else
           (recur pisteet-ja-rotaatiot
                  viimeisin-sijanti taitokset verrokki-kulma false)))))))
