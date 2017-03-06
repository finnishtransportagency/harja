(ns harja.tiedot.tierekisteri.varusteet-test
  (:require [cljs.test :refer-macros [deftest is async]]
            [tuck.core :as tuck]
            [harja.tiedot.tierekisteri.varusteet :as tiedot]))

(deftest kytke-haku-paalle
  (binding [tuck/*current-send-function* :D]
    (let [alku-app {:tierekisterin-varusteet
                    {:hakuehdot {:haku-kaynnissa? false
                                 :tunniste "testi0001"
                                 :tietolaji "tl506"}}}
          {{{haku-kaynnissa? :haku-kaynnissa?} :hakuehdot} :tierekisterin-varusteet}
          (tuck/process-event (tiedot/->HaeVarusteita) alku-app)]
      (is haku-kaynnissa? "Haku on kytketty päälle"))))

(deftest tarkista-varustehaun-tulosten-kasittely
  (let [alku-app {:tierekisterin-varusteet
                  {:hakuehdot {:haku-kaynnissa? false
                               :tunniste "testi0001"
                               :tietolaji "tl506"}}}
        tietolaji {:tunniste "tl523", :ominaisuudet []}
        hakutulokset [{:varuste {:tunniste "Livi153413" :tietue {:sijainti {:tie {:numero 20 :aosa 4 :aet 4080 :losa nil :let nil :ajr 2 :puoli 2 :kaista nil}} :alkupvm nil :loppupvm nil :karttapvm nil :kuntoluokitus "" :ely 12 :tietolaji {:tunniste "tl523" :arvot {"teknro" "2195" "z" "" "urakka" "" "x" "" "tunniste" "Livi153413" "teket" "" "teknimi" "Jäälintie E" "tektyyppi" "63" "y" "" "kuntoluokitus" ""}}}}}
                      {:varuste {:tunniste "Livi153431" :tietue {:sijainti {:tie {:numero 20 :aosa 4 :aet 2900 :losa nil :let nil :ajr 2 :puoli 2 :kaista nil}} :alkupvm nil :loppupvm nil :karttapvm nil :kuntoluokitus "" :ely 12 :tietolaji {:tunniste "tl523" :arvot {"teknro" "2193" "z" "" "urakka" "" "x" "" "tunniste" "Livi153431" "teket" "" "teknimi" "Laivakankaantie E" "tektyyppi" "63" "y" "" "kuntoluokitus" ""}}}}}]
        uusi-app (tuck/process-event (tiedot/->VarusteHakuTulos tietolaji hakutulokset) alku-app)]
    (is (not (get-in uusi-app [:tierekisterin-varusteet :hakuehdot :haku-kaynnissa?])) "Haku on kytketty pois päältä")
    (is (= (count hakutulokset) (count (get-in uusi-app [:tierekisterin-varusteet :varusteet]))) "Varusteita merkittiin oikea määrä")))




