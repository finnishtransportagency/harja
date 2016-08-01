(ns harja.ui.kentat-test
  "Lomakekenttien komponenttitestejä"
  (:require [harja.ui.kentat :as kentat]
            [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils :as u]
            [cljs.core.async :as async]
            [reagent.core :as r]
            [cljs-react-test.simulate :as sim])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(deftest numero
  (let [data (r/atom nil)
        val! #(u/change :input %)
        val #(some-> :input u/sel1 .-value)]
    (komponenttitesti
     [kentat/tee-kentta {:desimaalien-maara 2
                         :nimi :foo :tyyppi :numero}
      data]

     "aluksi arvo on tyhjä"
     (is (= "" (val)))

     "Normaali kokonaisluku päivittyy oikein"
     (val! "80")
     --
     (is (= "80" (val)))
     (is (= 80 @data))

     "Keskeneräinen numero ei päivitä dataa"
     (val! "-")
     --
     (is (= "-" (val)))
     (is (nil? @data))

     "Negatiivinen luku"
     (val! "-42")
     --
     (is (= "-42" (val)))
     (is (= -42 @data))

     "Keskeneräinen desimaaliluku"
     (val! "0.")
     --
     (is (= "0." (val)))
     (is (zero? @data))

     "Desimaaliluku"
     (val! "0.42")
     --
     (is (= "0.42" (val)))
     (is (= 0.42 @data))

     "Kentän blur poistaa tekstin"
     (sim/blur (u/sel1 :input) nil)
     --
     (is (= "0,42" (val)))

     "Datasta tuleva arvo päivittää tekstin"
     (reset! data 0.66)
     --
     (is (= "0,66" (val)))
     )))
