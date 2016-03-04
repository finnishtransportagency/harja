(ns harja.ui.kartta.varit)

(defn- tarkista-vari [n]
  (assert (<= 0 n 255) "Värikomponentin tulee olla 0 - 255 välillä"))

(defn rgb [r g b]
  (tarkista-vari r)
  (tarkista-vari g)
  (tarkista-vari b)
  #?(:clj
     (java.awt.Color. (int r) (int g) (int b))
     :cljs
     (str "rgb(" r ", " g ", " b ")")))

(defn rgba [r g b a]
  (tarkista-vari r)
  (tarkista-vari g)
  (tarkista-vari b)
  (assert (<= 0 a 1.0) "Alphan tulee olla 0 ja 1 välillä")
  #?(:clj
     (java.awt.Color. (int r) (int g) (int b) (int (* 255 a)))
     :cljs
     (str "rgba(" r ", " g ", " b ", " a)))
