(ns harja.geo
  "Yleisiä geometria-apureita")


(defn- laske-pisteiden-extent [pisteet]
  (let [[ensimmainen-x ensimmainen-y] (first pisteet)
        pisteet (rest pisteet)]
    (loop [minx ensimmainen-x
           miny ensimmainen-y
           maxx ensimmainen-x
           maxy ensimmainen-y
           [[x y] & pisteet] pisteet]
    (if-not x
      [minx miny maxx maxy]
      (recur (min x minx)
             (min y miny)
             (max x maxx)
             (max y maxy)
             pisteet)))))
  
       
(defmulti extent (fn [geometry] (:type geometry)))

(defmethod extent :line [{points :points}]
  (laske-pisteiden-extent points))

(defmethod extent :multiline [{lines :lines}]
  (laske-pisteiden-extent (mapcat :points lines)))

;; FIXME: lisää tarvittaessa muita 
