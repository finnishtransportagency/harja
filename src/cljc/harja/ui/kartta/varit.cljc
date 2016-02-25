(ns harja.ui.kartta.varit)

(defn rgb [r g b]
  #?(:clj
     (java.awt.Color. (int r) (int g) (int b))
     :cljs
     (str "rgb(" r ", " g ", " b ")")))

(defn rgba [r g b a]
  #?(:clj
     (java.awt.Color. (int r) (int g) (int b) (int (* 255 a)))
     :cljs
     (str "rgba(" r ", " g ", " b ", " a)))
