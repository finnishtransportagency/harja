(ns harja.geo
  "Yleiskäyttöisiä paikkatietoon ja koordinaatteihin liittyviä apureita."
  (:import (org.postgresql.geometric PGpoint PGpolygon)))


(defprotocol MuunnaGeometria
  "Geometriatyyppien muunnos PostgreSQL muodosta Clojure dataksi"
  (pg->clj [this]))

(extend-protocol MuunnaGeometria

  ;; Piste muunnetaan muotoon [x y]
  PGpoint
  (pg->clj [^PGpoint p]
    [(.x p) (.y p)])

  ;; Polygoni muunnetaan muotoon [[x1 y1] ... [xN yN]]
  PGpolygon
  (pg->clj [^PGpolygon poly]
    (mapv pg->clj (seq (.points poly))))

  ;; NULL geometriaoli on myös nil Clojure puolella
  nil
  (pg->clj [_] nil))

(defmacro muunna-pg-tulokset
  "Ottaa sisään SQL haun tulokset ja muuntaa annetut sarakkeet PG geometriatyypeistä Clojure dataksi."
  [tulokset & sarakkeet]
  (let [tulosrivi (gensym)]
    `(map (fn [~tulosrivi]
            (assoc ~tulosrivi
              ~@(mapcat (fn [sarake]
                          [sarake `(pg->clj (get ~tulosrivi ~sarake))])
                        sarakkeet)))
          ~tulokset)))

         
  
