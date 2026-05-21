(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [clojure.string]
            [reagent.core :as r]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.panelit :as panelit]
            [harja.testutils.shared-testutils :as u]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.modaalit :as modaalit]
            [harja.views.hallinta.tyokalut.ui-komponenttien_tarkastelu-nakyma :as nakyma]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.viestit :as viestit]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.valilehdet :as valilehdet])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(defn- uusi-konteksti []
  {:modaalin-tila (r/atom {:nakyvissa? false})
   :valilehtien-tilat (r/atom {})
   :tyylioverridet-tilat (r/atom {})
  :tyylioverride-syotteet-tilat (r/atom {})
  :teemaoverridet-tilat (r/atom {})
  :teemaoverride-syotteet-tilat (r/atom {})})

(defn- css-muuttujan-arvo [selector muuttuja]
  (some-> (u/sel1 selector)
          .-style
          (.getPropertyValue muuttuja)
          clojure.string/trim))

(deftest tabs-override-hyvaksyy-vain-kelvolliset-pituusarvot
  (let [konteksti (uusi-konteksti)
        radius-syote "[data-cy=\"perus-tyylioverride-harja-tabs-radius\"]"
        palauta-nappi "[data-cy=\"perus-palauta-tyylioverridet\"]"
        override-tekstialue "[data-cy=\"perus-kopioitava-override-map\"]"
        esikatselu "[data-cy=\"perus-esikatselu\"]"]
    (komponenttitesti
      [valilehdet/valilehdet-osio konteksti]

      (is (= "{}" (.-value (u/sel1 override-tekstialue))))
      (is (= "" (css-muuttujan-arvo esikatselu "--harja-tabs-radius")))

      (u/change radius-syote "0")
      (u/blur radius-syote)
      (is (= "{:harja-tabs-radius \"0\"}" (.-value (u/sel1 override-tekstialue))))
      (is (= "0" (css-muuttujan-arvo esikatselu "--harja-tabs-radius")))

      (u/change radius-syote "-1rem")
      (u/blur radius-syote)
      (is (= "-1rem" (.-value (u/sel1 radius-syote))))
      (is (= "{:harja-tabs-radius \"0\"}" (.-value (u/sel1 override-tekstialue))))
      (is (= "0" (css-muuttujan-arvo esikatselu "--harja-tabs-radius")))

      (u/change radius-syote "")
      (u/blur radius-syote)
      (is (= "{}" (.-value (u/sel1 override-tekstialue))))
      (is (= "" (css-muuttujan-arvo esikatselu "--harja-tabs-radius")))

      (u/change radius-syote "0")
      (u/blur radius-syote)
      (u/click palauta-nappi)
      (is (= "{}" (.-value (u/sel1 override-tekstialue))))
      (is (= "0.25rem" (.-value (u/sel1 radius-syote))))
      (is (= "" (css-muuttujan-arvo esikatselu "--harja-tabs-radius"))))))

(deftest tabs-override-pysyy-korttikohtaisena
  (let [konteksti (uusi-konteksti)
        radius-syote "[data-cy=\"perus-tyylioverride-harja-tabs-radius\"]"
        perus-esikatselu "[data-cy=\"perus-esikatselu\"]"
        pillerit-esikatselu "[data-cy=\"pillerit-esikatselu\"]"]
    (komponenttitesti
      [valilehdet/valilehdet-osio konteksti]

      (u/change radius-syote "0")
      (u/blur radius-syote)
      (is (= "0" (css-muuttujan-arvo perus-esikatselu "--harja-tabs-radius")))
      (is (= "" (css-muuttujan-arvo pillerit-esikatselu "--harja-tabs-radius"))))))

(deftest viestit-osio-renderoituu-ilman-override-kontekstia
  (komponenttitesti
    [viestit/viestit-osio {:otsikko "Testiviestit"
                           :kuvaus "Renderöityminen ilman tyylioverride-kontekstia"
                           :data-cy "testi-viestit-osio"
                           :esimerkit [{:id :viestit/testi
                                        :otsikko "Onnistunut viesti"
                                        :kuvaus "Yksinkertainen regressiosuoja"
                                        :data-cy "testi-viestikortti"
                                        :luokka :onnistunut
                                        :viesti "Viesti renderöityy"}]}]

    (is (= 1 (count (u/sel "[data-cy=\"testi-viestikortti\"]"))))
    (is (= "Viesti renderöityy" (u/text ".ui-komponenttien-tarkastelu-viesti")))))

(deftest modaalit-osio-renderoituu-ilman-tyylioverride-kontekstia
  (let [modaalin-tila (r/atom {:nakyvissa? false})]
    (komponenttitesti
      [modaalit/modaalit-osio modaalin-tila]

      (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-modaali-kortti\"]"))))
      (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-avaa-modaali\"]")))))))

(deftest panelit-osio-lukitsee-nykyisen-bootstrap-rakenteen
  (komponenttitesti
    [panelit/panelit-osio]

    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-sisalto\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa-sisalto\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"].harja-panel"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa\"].harja-panel"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"] .harja-panel-otsake"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa\"] .harja-panel-otsake"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"] .harja-panel-runko"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa\"] .harja-panel-runko"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"] .panel"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"] .panel-heading"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"] .panel-body"))))
    (is (.includes (u/text "[data-cy=\"ui-komponenttien-tarkastelu-paneli-sisalto\"]")
             "Paneeli käyttää nyt Harjan omaa panel-rakennetta Bootstrap-markupin sijaan."))
    (is (.includes (u/text "[data-cy=\"ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa-sisalto\"]")
             "Portattu wrapper säilyttää tämän optionaalisen otsikkosopimuksen ilman Bootstrapin panel-heading-rakennetta."))))

(deftest tabs-renderoityvat-ilman-valilehtien-tilat-kontekstia
  (komponenttitesti
    [renderointi/renderoi-komponentit
     nil
     [{:id :valilehdet/testi
       :nimi "Testivälilehdet"
       :kuvaus "Renderöityminen ilman ulkoista valilehtiatomia"
       :data-cy "testi-tabs-kortti"
       :bootstrap-tila :portattu
       :tyyppi :valilehdet
       :parametrit {:tyyli :tabs
                    :luokka "tabs-taso1"
                    :data-cy "testi-tabs"
                    :valilehdet [{:otsikko "Perustiedot"
                                  :avain :perustiedot
                                  :tabi-data-cy "testi-tabs-perustiedot"
                                  :sisalto "Ensimmäinen sisältö"}
                                 {:otsikko "Historia"
                                  :avain :historia
                                  :tabi-data-cy "testi-tabs-historia"
                                  :sisalto "Toinen sisältö"}]}}]]

    (is (= 1 (count (u/sel "[data-cy=\"testi-tabs-perustiedot\"]"))))
    (is (= "Ensimmäinen sisältö" (u/text "[data-cy=\"testi-tabs-perustiedot-sisalto\"]")))

    (u/click "[data-cy=\"testi-tabs-historia\"]")
    (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"testi-tabs-historia\"]") "aria-selected")))
    (is (= "Toinen sisältö" (u/text "[data-cy=\"testi-tabs-historia-sisalto\"]")))))

(deftest tabs-kirjoittaa-valinnan-ulkoiseen-tilaan
  (let [konteksti (uusi-konteksti)]
    (komponenttitesti
      [valilehdet/valilehdet-osio konteksti]

      (is (= "Tämä sisältö näyttää aktiivisen välilehden perusrakenteen."
             (u/text "[data-cy=\"ui-komponenttien-tarkastelu-tabs-perustiedot-sisalto\"]")))
      (u/click "[data-cy=\"ui-komponenttien-tarkastelu-tabs-historia\"]")
      (is (= :historia (get @(:valilehtien-tilat konteksti) "ui-komponenttien-tarkastelu-tabs")))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-tabs-historia\"]") "aria-selected")))
      (is (= "Toinen välilehti varmistaa valinnan vaihtumisen ja sisällön renderöinnin."
             (u/text "[data-cy=\"ui-komponenttien-tarkastelu-tabs-historia-sisalto\"]"))))))

(deftest yhteinen-teemapaneeli-hallinnoi-jaettua-mapia
  (let [konteksti (uusi-konteksti)
        korostus-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-korostus\"]"
        radius-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-radius\"]"
        kopioitava-map "[data-cy=\"ui-komponenttien-tarkastelu-teema-kopioitava-map\"]"
        palauta-nappi "[data-cy=\"ui-komponenttien-tarkastelu-teema-palauta\"]"]
    (komponenttitesti
      [renderointi/renderoi-yhteiset-teematokenit
       konteksti
       [{:avain :harja-teema-korostus
         :nimi "Korostusvari"
         :tyyppi :color
         :oletus "#0066cc"}
        {:avain :harja-teema-radius
         :nimi "Kulman sade"
         :tyyppi :text
         :esimerkki "0.25rem"
         :oletus "0.25rem"}]]

      (is (= "{}" (.-value (u/sel1 kopioitava-map))))

      (u/change korostus-syote "#112233")
      (is (= "{:harja-teema-korostus \"#112233\"}" (.-value (u/sel1 kopioitava-map))))

      (u/change radius-syote "0")
      (u/blur radius-syote)
      (is (= "{:harja-teema-korostus \"#112233\", :harja-teema-radius \"0\"}" (.-value (u/sel1 kopioitava-map))))

      (u/click palauta-nappi)
      (is (= "{}" (.-value (u/sel1 kopioitava-map))))
      (is (= "#0066cc" (.-value (u/sel1 korostus-syote))))
      (is (= "0.25rem" (.-value (u/sel1 radius-syote)))))))

(deftest yhteinen-teemapaneeli-kirjoittaa-css-muuttujat-juurielementille
  (let [korostus-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-korostus\"]"
        korostus-hover-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-korostus-hover\"]"
        pinta-hover-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-pinta-hover\"]"
        radius-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-radius\"]"
        juuri "[data-cy=\"ui-komponenttien-tarkastelu-sivu\"]"]
    (komponenttitesti
      [nakyma/ui-komponenttien-tarkastelu]

      (u/change korostus-syote "#112233")
      (u/change korostus-hover-syote "#223344")
      (u/change pinta-hover-syote "#ddeeff")
      (u/change radius-syote "0")
      (u/blur radius-syote)
      (is (= "#112233" (css-muuttujan-arvo juuri "--harja-teema-korostus")))
      (is (= "#223344" (css-muuttujan-arvo juuri "--harja-teema-korostus-hover")))
      (is (= "#ddeeff" (css-muuttujan-arvo juuri "--harja-teema-pinta-hover")))
      (is (= "0" (css-muuttujan-arvo juuri "--harja-teema-radius")))
      (is (= 1 (count (u/sel "[data-cy=\"primitive-viesti-teema\"]")))))))
