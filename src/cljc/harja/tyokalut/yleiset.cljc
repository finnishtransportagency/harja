(ns harja.tyokalut.yleiset
  (:require [clojure.pprint :as pp]))

(defn pyorista-kahteen [numero]
  (/ 100 (Math/round (* 100 numero))))
(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn printdenty
  "Auttaa tulostamaan ja palauttamaan arvoja thredingissa:
    (-> dataa
      (map jotain)
      printdenty
      (jotain-muuta nonnoo)
      (printdenty \"debuggia: \"))

  tai
    (->> dataa
        (jotain boo)
        printdenty
        (jotain-muuta nonnoo)
        (printdenty \"debuggia: \"))"
  ([x]
   (printdenty x ""))
  ([x y]
   (let [f (fn [prefix object]
             (println prefix object)
             object)]
     (if (string? x)
       (f x y)
       (f y x)))))

(defn prettydenty
  "Auttaa tulostamaan ja palauttamaan arvoja thredingissa.

  (-> dataa
      (map jotain)
      prettydentity
      (jotain-muuta noo)
      (prettydentity \"debuggia: \"))

  tai:
  (->> dataa
       (jotain blaa)
       prettydentity
       (jotain-muuta boo)
       (prettydentity \"debuggia: \"))"
  ([x]
   (prettydenty x ""))
  ([x y]
   (let [f (fn [prefix object]
             (print prefix)
             (pp/pprint object)
             object)]
     (if (string? x)
       (f x y)
       (f y x)))))
