(ns harja.ui.kentat-test
  "Lomakekenttien komponenttitestejä"
  (:require [harja.ui.kentat :as kentat]
            [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu]]
            [cljs.core.async :as async]
            [reagent.core :as r]
            [cljs-react-test.simulate :as sim]
            [harja.pvm :as pvm]
            [clojure.string :as str])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each
  u/komponentti-fixture
  fake-palvelut-fixture)

(deftest valinta
 (let [data (r/atom nil)]
  (komponenttitesti
   [kentat/tee-kentta {:nimi :foo :tyyppi :valinta
                       :valinta-nayta #(if (nil? %) "Valitse" %)
                       :valinnat ["abc" "kissa kävelee" "tikapuita pitkin taivaseen"]}
    data]

   "Aluksi arvo on Valitse ja data nil"
   (is (= "Valitse" (u/text :.valittu)))
   (is (nil? @data))
   --

   "Ennen klikkaamistakin kolme vaikkei näkyvissä"
   (is (= 3 (count (u/sel :li.harja-alasvetolistaitemi))))

   "Ennen klikkaamista valinnat eivät ole näkyvissä"
   (is (nil? (u/sel1 :div.dropdown.open)))

   "Klikkaaminen avaa pulldownin"
   (u/click :button.nappi-alasveto)
   --
   (is (= 3 (count (u/sel :li.harja-alasvetolistaitemi))))
   (is (some? (u/sel1 :div.dropdown.open)))

   "Valitaan kissa kävelee"
   (u/click ".harja-alasvetolistaitemi:nth-child(2) > a")
   --
   (is (= "kissa kävelee" (u/text :.valittu)))
   (is (= @data "kissa kävelee"))

   "Valinnan jälkeen lista piiloon"
   (is (nil? (u/sel1 :div.dropdown.open))))))


(deftest numero
  (let [data (r/atom nil)
        val! #(u/change :input %)
        val #(some-> :input u/sel1 (.getAttribute "value"))]
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

(deftest positiivinen-numero
  (let [data (r/atom nil)
        val! #(u/change :input %)
        val #(some-> :input u/sel1 (.getAttribute "value"))]
    (komponenttitesti
     [kentat/tee-kentta {:nimi :foo :tyyppi :positiivinen-numero}
      data]

     "aluksi arvo on tyhjä"
     (is (= "" (val)))

     "Normaali kokonaisluku päivittyy oikein"
     (val! "80")
     --
     (is (= "80" (val)))
     (is (= 80 @data))

     "Miinusmerkkiä ei hyväksytä"
     (val! "-12")
     --
     (is (= "12" (val)))
     (is (= 12 @data)))))

(deftest pvm
  (let [data (r/atom nil)
        val! #(u/change :input %)
        val #(some-> :input u/sel1 (.getAttribute "value"))]
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
     (sim/blur (u/sel1 :input) {:target {:value "7.7.2010"}})
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


(deftest pvm-aika
  (let [data (r/atom nil)
        pvm-kentta "tr td:nth-child(1) input"
        aika-kentta "tr td:nth-child(2) input"
        pvm! #(do (u/change pvm-kentta %)
                  (u/blur pvm-kentta))
        pvm #(.-value (u/sel1 pvm-kentta))
        aika #(.-value (u/sel1 aika-kentta))
        aika! #(do (u/change aika-kentta %)
                   (u/blur aika-kentta))
        p (fn [pp kk vvvv tt mm]
            (pvm/aikana (pvm/luo-pvm vvvv (dec kk) pp)
                        tt mm 0 0))]
    (komponenttitesti
     [kentat/tee-kentta {:tyyppi :pvm-aika} data]

     "Alkutilanteessa arvo on tyhjä ja placeholderit oikein"
     (is (nil? @data))
     (is (= "" (pvm)))
     (is (= "" (aika)))
     (is (= "pp.kk.vvvv" (.getAttribute (u/sel1 pvm-kentta) "placeholder")))
     (is (= "tt:mm" (.getAttribute (u/sel1 aika-kentta) "placeholder")))
     --

     "Pelkän päivämäärän asettaminen ei aseta arvoa"
     (pvm! "8.4.1981")
     --
     (is (nil? @data))

     "Ajan täyttäminen asettaa arvon"
     (aika! "05:40")
     --
     (is (= (p 8 4 1981 5 40) @data))

     "Ajan vaihtaminen ohjelmallisesti reflektoituu input kentissä"
     (reset! data (p 29 8 1997 2 14))
     --
     (is (= "29.08.1997" (pvm)))
     (is (= "02:14" (aika)))

     "Ohjelmallinen tyhjentäminen reflektoituu input kentissä"
     (reset! data nil)
     --
     (is (= "" (pvm)))
     (is (= "" (aika)))

     "Asetetaan aika tekstinä ja valitaan pvm pickerilla"
     (aika! "12:34")
     (u/click pvm-kentta)
     --
     (u/click ".pvm-tanaan")
     --
     (is (= @data (pvm/aikana (pvm/nyt) 12 34 0 0))))))


(def +tie20-osa1-alkupiste+ {:type :point, :coordinates [426938.1807000004 7212765.558800001]})
(def +tr-vastaukset+
  {{:alkuosa 1, :numero 20, :alkuetaisyys 0}
   [+tie20-osa1-alkupiste+]

   {:alkuosa 1 :numero 20 :alkuetaisyys 0 :loppuosa 1 :loppuetaisyys 100}
   [{:type :multiline,
     :lines [{:type :line,
              :points [[426938.1807000004 7212765.558800001]
                       [426961.68209999986 7212765.378899999]
                       [426978.40299999993 7212763.941300001]
                       [426991.6160000004 7212762.211199999]
                       [427003.70409999974 7212760.276799999]
                       [427016.42399999965 7212757.082199998]
                       [427036.6384315559 7212751.272574459]]}]}]})

(deftest tierekisteriosoite
  (let [data (r/atom nil)
        sijainti (r/atom nil)
        tr-sel {:tr-numero :input.tr-numero
                :tr-alkuosa :input.tr-alkuosa
                :tr-alkuetaisyys :input.tr-alkuetaisyys
                :tr-loppuosa :input.tr-loppuosa
                :tr-loppuetaisyys :input.tr-loppuetaisyys}
        tr-kentat [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys]
        arvo (fn [kentta]
               (.-value (u/sel1 (tr-sel kentta))))
        aseta! (fn [kentta arvo]
                 (u/change (tr-sel kentta) arvo))
        hae-tr-viivaksi (fake-palvelukutsu
                         :hae-tr-viivaksi
                         (fn [payload]
                           (.log js/console ":hae-tr-viivaksi => " payload)
                           (get +tr-vastaukset+ payload)))]
    (komponenttitesti
     [kentat/tee-kentta {:tyyppi :tierekisteriosoite :sijainti sijainti} data]

     "Alkutilassa kaikki kentät ovat tyhjiä"
     (is (every? str/blank? (map arvo tr-kentat)))
     (aseta! :tr-numero "20")
     --
     (is (= "20" (arvo :tr-numero)))

     "Tien sekä alkuosan ja -etäisyyden asettaminen hakee osoitteen"
     (aseta! :tr-alkuosa "1")
     (aseta! :tr-alkuetaisyys "0")
     --
     (u/blur (tr-sel :tr-alkuetaisyys))
     --
     (<! hae-tr-viivaksi)
     --
     (is (= @sijainti +tie20-osa1-alkupiste+) "Sijainti on päivittynyt oikein")

     "Loppuosan ja -etäisyyden täyttäminen hakee koko osoitteen"
     (aseta! :tr-loppuosa "1")
     (aseta! :tr-loppuetaisyys "100")
     --
     (u/blur (tr-sel :tr-loppuetaisyys))
     --
     (<! hae-tr-viivaksi)
     --
     (is (= (:type @sijainti) :multiline) "Sijainti haettu uudestaan"))))

(deftest checkbox-group
  (let [data (r/atom #{})
        vaihtoehdot ["Foo" "Bar" "Baz" "Quux"]
        dump #(println "------\n" (.-innerHTML (u/sel1 :div.boolean-group)))
        valitut (fn []
                  (filter #(.-checked %) (u/sel "input[type='checkbox']")))]
    (komponenttitesti
     [kentat/tee-kentta {:tyyppi :checkbox-group
                         :vaihtoehdot vaihtoehdot
                         :vaihtoehto-nayta str} data]

     "Alkutilassa ei ole yhtään valittua checkboxia"
     (is (zero? (count (valitut))))
     --
     "Klikataan ensimmäistä"
     (u/change :input true)
     --
     (is (= 1 (count (valitut))))
     (is (= #{"Foo"} @data))
     --
     "Poistetaan valinta"
     (u/change :input false)
     --
     (is (= #{} @data))
     --
     "Ohjelmallisesti asetettu valitut"
     (reset! data #{"Bar" "Quux"})
     --
     (is (= 2 (count (valitut)))))))

(deftest checkbox-group-muu
  (let [data (r/atom {"Foo" true
                      "Bar" false
                      "Muu" false
                      :muu "MUUTA"})
        valitut (fn []
                  (filter #(.-checked %) (u/sel "input[type='checkbox']")))]
    (komponenttitesti
     [kentat/tee-kentta {:tyyppi :checkbox-group
                         :vaihtoehdot ["Foo" "Bar" "Muu"]
                         :vaihtoehto-nayta str
                         :valittu-fn get
                         :valitse-fn assoc
                         :muu-vaihtoehto "Muu"
                         :muu-kentta {:tyyppi :string
                                      :nimi :muu}} data]
     "Alkutilanteessa Foo valittu ja muu kenttä ei näy"
     (is (= 1 (count (valitut))))
     (is (nil? (u/sel1 ".muu input")))

     "Valitaan muu niin kenttä ilmestyy näkyviin"
     (u/change (nth (u/sel :input) 2) true)
     --
     (is (= 2 (count (valitut))))
     (is (= "MUUTA" (some-> ".muu input" u/sel1 .-value)))

     "Muutetaan muu valintaa"
     (u/change ".muu input" "JOTAIN")
     --
     (is (= {"Foo" true "Bar" false "Muu" true :muu "JOTAIN"} @data)))))
