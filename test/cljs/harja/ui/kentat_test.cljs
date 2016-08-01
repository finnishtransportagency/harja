(ns harja.ui.kentat-test
  "Lomakekenttien komponenttitestejä"
  (:require [harja.ui.kentat :as kentat]
            [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils :as u]
            [cljs.core.async :as async]
            [reagent.core :as r]
            [cljs-react-test.simulate :as sim]
            [harja.pvm :as pvm])
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
     (is (= "0,66" (val))))))

(deftest pvm
  (let [data (r/atom nil)
        val! #(u/change :input %)
        val #(some-> :input u/sel1 .-value)]
    (komponenttitesti
     [kentat/tee-kentta {:tyyppi :pvm :placeholder "anna pvm"} data]

     "Alkutilanteessa arvo on tyhjä ja placeholder on asetettu"
     (is (= "" (val)))
     (is (= "anna pvm" (.getAttribute (u/sel1 :input) "placeholder")))

     "Virheellistä tekstiä ei voi syöttää"
     (val! "66...")
     --
     (is (= "" (val)))
     (is (nil? @data))

     "Keskeneräinen pvm on ok"
     (val! "12.")
     --
     (is (= "12." (val)))
     (is (nil? @data))

     "Täytetty pvm asettaa arvon"
     (val! "7.7.2010")
     --
     (is (= "7.7.2010" (val)))
     (is (nil? @data)) ;; arvoa ei aseteta ennen blur tai selectiä
     (sim/blur (u/sel1 :input) nil)
     --
     (is (= (pvm/->pvm "7.7.2010") @data))

     "Picker ei ole näkyvissä"
     (is (nil? (u/sel1 :table.pvm-valinta)))

     "Klikkauksesta picker tulee näkyviin"
     (u/click :input)
     --
     (is (u/sel1 :table.pvm-valinta))

     "Seuraava kk napin klikkaaminen elokuun 2010"
     (u/click :.pvm-seuraava-kuukausi)
     --
     (is (= "Elo 2010" (u/text ".pvm-kontrollit tr td:nth-child(2)")))

     "Viidestoista päivä klikkaus (su, 3. rivi)"
     (u/click ".pvm-paivat tr:nth-child(3) td:nth-child(7)")
     --
     (is (= "15.08.2010" (val)))
     (is (pvm/sama-pvm? (pvm/->pvm "15.8.2010") @data)))))
