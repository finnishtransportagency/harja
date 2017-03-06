(ns harja.ui.kartta.apurit
  (:require [harja.geo :as geo]
            #?@(:cljs
                [[ol.geom.Point]])))

(def kulmaraja-nuolelle (/ Math/PI 4)) ;; pi / 4 = 45 astetta
(defn abs [i] (max i (- i)))

(def +koko-suomi-extent+ [60000 6613000 736400 7780300])

(defn kulma [[x1 y1] [x2 y2]]
  (Math/atan2
   (- y2 y1)
   (- x2 x1)))

(defn taitokset-valimatkoin
  ([min-etaisyys max-etaisyys taitokset]
   (taitokset-valimatkoin min-etaisyys max-etaisyys taitokset
                          #?(:clj identity
                             :cljs (fn [p]
                                     (ol.geom.Point. (clj->js p))))))
  ([min-etaisyys max-etaisyys taitokset luo-piste]

   (let [alkupiste (:sijainti (first taitokset))]
     (loop [pisteet-ja-rotaatiot []
            viimeisin-sijanti nil
            edellisen-taitoksen-kulma nil
            [{:keys [sijainti rotaatio]} & taitokset] taitokset]
      (if-not sijainti
        ;; Kaikki käsitelty
        pisteet-ja-rotaatiot

        (let [[x1 y1 :as nuoli] (or viimeisin-sijanti (first alkupiste))
              [x2 y2 :as taitoksen-alku] (first sijainti)
              [x3 y3 :as taitoksen-loppu] (second sijainti)
              dist (geo/etaisyys nuoli taitoksen-loppu)]
          (cond
            (when edellisen-taitoksen-kulma
              (and (or (nil? viimeisin-sijanti) (> dist min-etaisyys))
                   (> (abs (- (abs edellisen-taitoksen-kulma) (abs rotaatio))) kulmaraja-nuolelle)))
            ;; Datassa on paikoin suoralla tiellä todella lyhyitä pätkiä, kun tehdään muka tiukka käännös
            ;;  |
            ;;  |
            ;;  |
            ;;  -
            ;;  |
            ;;  |
            ;; Käytännössä käyttäjä ei tuota "-" pätkää kartalla näe, mutta datassa se on. Jos nuoli
            ;; laitettaisiin tuon pätkän loppuun, ja pätkän omalla rotaatiolla, niin silloin se
            ;; osoittaisi käyttäjän näkökulmasta osoittaisi minne sattuu. Laitetaan nuoli "-" taitoksen
            ;; ALKUUN, ja käytetään edellisen taitoksen kulmaa, niin käyttäjästä näyttää, että nuoli
            ;; osoittaa oikein suoran tien suuntaan.
            (recur (conj pisteet-ja-rotaatiot
                         [(luo-piste taitoksen-alku) edellisen-taitoksen-kulma])
                   taitoksen-alku
                   rotaatio
                   taitokset)

            (> dist max-etaisyys)
            (recur (conj pisteet-ja-rotaatiot
                         [(luo-piste taitoksen-loppu) rotaatio])
                   taitoksen-loppu
                   rotaatio
                   taitokset)

            :else
            (recur pisteet-ja-rotaatiot
                   viimeisin-sijanti
                   rotaatio
                   taitokset))))))))



(defn pisteiden-taitokset
  ([pisteet] (pisteiden-taitokset pisteet true))
  ([pisteet kiertosuunta-ylos?]
   (reduce (fn [taitokset [p1 p2]]
             (conj taitokset
                   {:sijainti [p1 p2]
                    :rotaatio (let [kulma (kulma p1 p2)]
                                (if kiertosuunta-ylos? kulma (- kulma)))}))
           []
           (partition 2 1 pisteet))))



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

(def ^:private suurin-resoluutio 1024)
(def ^:private pienin-resoluutio 8)
(def ^:private resoluutio-range (- suurin-resoluutio pienin-resoluutio))

(defn ikonin-skaala-resoluutiolle
  "Palauttaa ikonille uuden skaalan näkyvän alueen koon perusteella.
Suurempi näkyvä alue skaalaa ikonia pienemmäksi."
  [resoluutio scale]
  (double
    (* scale
       (cond
         (<= resoluutio pienin-resoluutio) suurin-skaala
         (>= resoluutio suurin-resoluutio) pienin-skaala
         :default
         (+ pienin-skaala (* skaala-range
                             (/ (- suurin-resoluutio resoluutio) resoluutio-range)))))))
