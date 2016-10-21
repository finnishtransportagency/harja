(ns harja-laadunseuranta.tiedot.kalman)

(def state {:lat 0
            :lon 0
            :speed 0
            :heading 0
            :accuracy 0})

(defn- deg->rad [deg]
  (* Math/PI (/ deg 180)))

(defn predicted-measurement
  "Laskee mittausestimaatin mitä sijainnin pitäisi olla dt sekunnin kuluttua, perustuen nykyiseen estimaattiin"
  [{:keys [lon lat accuracy] :as state} speed heading dt]
  (let [matka (* dt speed)
        direction (deg->rad heading)]
    {:lon (+ lon (* matka (Math/sin direction)))
     :lat (+ lat (* matka (Math/cos direction)))
     :accuracy (* 1.5 accuracy)}))

(defn measurement-error
  "Laskee mittausvirheen mittausestimaatista ja mittauksesta"
  [measurement prediction]
  {:lon (- (:lon measurement) (:lon prediction))
   :lat (- (:lat measurement) (:lat prediction))})

(defn gain [arvio mitattu]
  (/ (:accuracy arvio) (+ (:accuracy arvio) (:accuracy mitattu))))

(defn correct-estimate [arvio gain error]
  (assoc arvio
         :lat (+ (:lat arvio) (* gain (:lat error)))
         :lon (+ (:lon arvio) (* gain (:lon error)))
         :accuracy (* (- 1.0 gain) (:accuracy arvio))))

(defn kalman [arvio mitattu dt]
  (if (nil? arvio)
    mitattu
    (let [prediction (predicted-measurement arvio
                                            (:speed mitattu)
                                            (:heading mitattu)
                                            dt)
          error (measurement-error mitattu prediction)
          gain (gain prediction mitattu)]
      (correct-estimate prediction gain error))))
