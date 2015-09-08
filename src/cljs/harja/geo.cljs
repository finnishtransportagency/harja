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

;; Kuinka paljon yksittäisen pisteen extentiä laajennetaan joka suuntaan
(def pisteen-extent-laajennus 35)

(defmethod extent :point [{c :coordinates}]
  (let [d pisteen-extent-laajennus
        [x y] c]
    [(- x d) (- y d) (+ x d) (+ y d)]))

;; FIXME: lisää tarvittaessa muita 
