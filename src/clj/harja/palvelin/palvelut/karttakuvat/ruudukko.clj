(ns harja.palvelin.palvelut.karttakuvat.ruudukko
  "Toteuttaa karttaruudukon, jolla voidaan tarkistaa onko ruutuun jo piirretty"
  (:require [taoensso.timbre :as log]))

(defprotocol Ruudukko
  (aseta-kohta! [this kartta-x kartta-y])
  (kohta-asetettu? [this kartta-x kartta-y]))

(defn ruudukko
  "Palauttaa Ruudukko toteutuksen annetulle extent alueelle, pikselikoolle
  ja ruudukon rakeisuudelle."
  [extent px-scale pikselia-per-ruutu]
  (let [[x1 y1 x2 y2] extent
        w (- x2 x1)
        h (- y2 y1)
        x-koko (int (/ w px-scale pikselia-per-ruutu))
        y-koko (int (/ h px-scale pikselia-per-ruutu))

        arr (make-array Integer/TYPE (* x-koko y-koko))
        indeksi (fn [kartta-x kartta-y]
                  (let [x (int (/ (- kartta-x x1) px-scale pikselia-per-ruutu))
                        y (int (/ (- kartta-y y1) px-scale pikselia-per-ruutu))]
                    (if (and (< -1 x x-koko)
                             (< -1 y y-koko))
                      (int (+ (* y x-koko) x))
                      nil)))]
    (reify Ruudukko
      (aseta-kohta! [_ kartta-x kartta-y]
        (when-let [ind (indeksi kartta-x kartta-y)]
          (aset arr ind 1)))
      (kohta-asetettu? [_ kartta-x kartta-y]
        (if-let [ind (indeksi kartta-x kartta-y)]
          (= 1 (aget arr ind))
          true)))))
