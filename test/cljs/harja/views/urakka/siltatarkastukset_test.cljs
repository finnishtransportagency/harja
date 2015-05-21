(ns harja.views.urakka.siltatarkastukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]

    [harja.views.urakka.siltatarkastukset :as testattava]
    [harja.pvm :refer [->pvm] :as pvm]
    [harja.loki :refer [log]]))

(def +siltatarkastuksen-kohteet+
  {1 ["A" ""], 2 ["A" ""], 3 ["A" ""], 4 ["A" ""], 5 ["A" ""], 6 ["A" ""], 7 ["A" ""], 8 ["B" ""],
   9 ["A" ""], 10 ["A" ""], 11 ["A" ""], 12 ["A" ""], 13 ["A" ""], 14 ["A" ""], 15 ["A" ""], 16 ["A" ""],
   17 ["A" ""], 18 ["D" ""], 19 ["A" ""], 20 ["C" ""], 21 ["B" ""], 22 ["A" ""], 23 ["A" ""], 24 ["D" "Lisätieto"]})

(defn siltatarkastus [n]
  (let [vuosi (+ 2010 n)
        tarkastus-pvm  (->pvm (str "10.10." vuosi))]
    {:kohteet       +siltatarkastuksen-kohteet+, :silta-id (+ 1 n), :urakka-id (+ 2 n), :id (+ 3 n),
    :tarkastusaika tarkastus-pvm, :tarkastaja "Teppo Teräväinen"}))

(def +siltatarkastukset+
 [(siltatarkastus 0) (siltatarkastus 1) (siltatarkastus 2) (siltatarkastus 3)])

(deftest siltatarkastuksen-rivit-ja-sarakkeet
  (let [valittu-tarkastus (siltatarkastus 4)
        muut-tarkastukset +siltatarkastukset+
        sarakkeet (testattava/siltatarkastuksen-sarakkeet muut-tarkastukset)
        rivit (testattava/siltatarkastusten-rivit valittu-tarkastus muut-tarkastukset)
        kohde1-rivi (second rivit)
        kohde24-rivi (last rivit)
        viesti "siltatarkastuksen-rivit-ja-sarakkeet"]
    ;; 24 kohdetta 4 alaotsikon alla
    (is (= 28 (count rivit)) viesti)
    ;; on kiva, jos sarakkeita on yhtä monta kuin rivillä itemeitä
    (is (= (count sarakkeet) (count kohde1-rivi)) viesti)
    (is (= "A" (:tulos kohde1-rivi)) viesti)
    (is (= "" (:lisatieto kohde1-rivi)) viesti)
    (is (= "D" (:tulos kohde24-rivi)) viesti)
    (is (= "Lisätieto" (:lisatieto kohde24-rivi)) viesti)
    (is (= 24 (count (:kohteet valittu-tarkastus))) viesti)))