(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [clojure.string]
            [reagent.core :as r]
            [harja.ui.bootstrap :as bootstrap]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.alasvedot :as alasvedot]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.navigaatio :as navigaatio]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.panelit :as panelit]
            [harja.testutils.shared-testutils :as u]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.modaalit :as modaalit]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-nakyma :as nakyma]
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

(defn- laskettu-tyyliarvo [selector ominaisuus]
  (some-> (u/sel1 selector)
          js/getComputedStyle
          (.getPropertyValue ominaisuus)
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

(deftest navigaatio-osio-nayttaa-dropdownin-ja-navbarin-kaytoksen
  (komponenttitesti
    [navigaatio/navigaatio-osio]

    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .harja-dropdown"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .harja-dropdown-vaihtaja"))))
    (is (= "false" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .harja-dropdown-vaihtaja") "aria-expanded")))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .harja-dropdown-valikko"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .dropdown-toggle"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .dropdown-menu"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .caret"))))

    (u/click "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .harja-dropdown-vaihtaja")
    (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .harja-dropdown-vaihtaja") "aria-expanded")))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"] .harja-dropdown-valikko"))))
    (is (= "Raportit" (u/text "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-raportit\"]")))

    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar\"].harja-navbar"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-otsikko\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar\"] .harja-navbar-brandi"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-vaihtaja\"]"))))
    (is (= "false" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-navbar-vaihtaja\"]") "aria-expanded")))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-sisalto\"].harja-navbar-sisalto"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-vasen\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-oikea\"]"))))
    (is (= "Harja" (u/text "[data-cy=\"ui-komponenttien-tarkastelu-navbar\"] .harja-navbar-brandi")))
    (is (= "Urakat" (u/text "[data-cy=\"ui-komponenttien-tarkastelu-navbar-vasen\"]")))
    (is (= "Kirjaudu ulos" (u/text "[data-cy=\"ui-komponenttien-tarkastelu-navbar-oikea\"]")))
    (is (= 0 (count (u/sel ".navbar"))))
    (is (= 0 (count (u/sel ".navbar-default"))))
    (is (= 0 (count (u/sel ".navbar-toggle"))))
    (is (= 0 (count (u/sel ".navbar-collapse"))))
    (is (= 0 (count (u/sel ".navbar-nav"))))

    (u/click "[data-cy=\"ui-komponenttien-tarkastelu-navbar-vaihtaja\"]")
    (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-navbar-vaihtaja\"]") "aria-expanded")))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-sisalto\"].harja-navbar-sisalto-avoin"))))))

(deftest alasvedot-osio-nayttaa-tuotantopolun-shared-alasvedot
  (komponenttitesti
    [alasvedot/alasvedot-osio]

    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-alasvedot-osio\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-vayla-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-ryhmitelty-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-alasveto-toiminnolla-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-valikko\"].dropdown.livi-alasveto"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-valikko\"] .dropdown-menu.livi-alasvetolista"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-vayla-valikko\"].select-default"))))

    (u/click "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-valikko\"] button")
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-valikko\"].open"))))

    (u/click "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-ryhmitelty-valikko\"] button")
    (is (= 2 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-ryhmitelty-valikko\"] .haku-lista-ryhman-otsikko"))))

    (u/click "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-valikko\"] .harja-alasvetolistaitemi")
    (is (.includes (u/text "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-valinta\"]") "Valittu:"))))

(deftest tarkastelusivu-sisaltaa-navigaatio-osion-ja-navbar-kortin
  (komponenttitesti
    [nakyma/ui-komponenttien-tarkastelu]

    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navigaatio-osio\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-navbar\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-alasvedot-osio\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-vayla-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-livi-pudotusvalikko-ryhmitelty-kortti\"]"))))))

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

(deftest panelit-osio-nayttaa-dropdown-panelin-ja-sen-vaihtajakaytoksen
  (komponenttitesti
    [panelit/panelit-osio]

    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-kortti\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel\"].harja-panel.harja-dropdown-panel.harja-dropdown-panel-tyyli-primary"))))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-vaihtaja\"]"))))
    (is (= "false" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-vaihtaja\"]") "aria-expanded")))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-sisalto\"]"))))

    (u/click "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-vaihtaja\"]")
    (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-vaihtaja\"]") "aria-expanded")))
    (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-sisalto\"]"))))
    (is (.includes (u/text "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel-sisalto\"]")
             "Dropdown-panel on nyt portattu pois Bootstrapin panel- ja glyphicon-markupista."))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel\"] .panel"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel\"] .panel-heading"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel\"] .panel-body"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel\"] .glyphicon-plus"))))
    (is (= 0 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-dropdown-panel\"] .glyphicon-minus"))))))

(deftest panelin-komponenttikohtainen-token-voittaa-yhteisen-teematokenin
  (komponenttitesti
    [:div {:style (renderointi/muunna-tyylioverridet-inline-tyyliksi
                    {:harja-teema-lohko-marginaali-ala "2rem"
                     :harja-teema-varjo "0 0 1rem rgba(0, 0, 0, 0.4)"
                     :harja-teema-sisalto-padding-pysty "0.5rem"
                     :harja-teema-sisalto-padding-vaaka "1.5rem"
                     :harja-teema-sisalto-padding-ala "2rem"
                     :harja-panel-marginaali-ala "3rem"
                     :harja-panel-varjo "none"
                     :harja-panel-padding-pysty "0.75rem"
                     :harja-panel-padding-vaaka "2rem"
                     :harja-panel-padding-ala "2.5rem"})}
     [bootstrap/panel {:data-cy "testi-paneli-precedence"}
      "Precedence"
      [:div "Sisalto"]]]

    (is (= "none" (laskettu-tyyliarvo "[data-cy=\"testi-paneli-precedence\"]" "box-shadow")))
    (is (= "48px" (laskettu-tyyliarvo "[data-cy=\"testi-paneli-precedence\"]" "margin-bottom")))
    (is (= "12px" (laskettu-tyyliarvo "[data-cy=\"testi-paneli-precedence\"] .harja-panel-otsake" "padding-top")))
    (is (= "32px" (laskettu-tyyliarvo "[data-cy=\"testi-paneli-precedence\"] .harja-panel-otsake" "padding-right")))
    (is (= "12px" (laskettu-tyyliarvo "[data-cy=\"testi-paneli-precedence\"] .harja-panel-runko" "padding-top")))
    (is (= "32px" (laskettu-tyyliarvo "[data-cy=\"testi-paneli-precedence\"] .harja-panel-runko" "padding-right")))
    (is (= "40px" (laskettu-tyyliarvo "[data-cy=\"testi-paneli-precedence\"] .harja-panel-runko" "padding-bottom")))))

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
        varjo-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-varjo\"]"
        radius-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-radius\"]"
        lohko-marginaali-ala-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-lohko-marginaali-ala\"]"
        sisalto-padding-pysty-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-sisalto-padding-pysty\"]"
        sisalto-padding-vaaka-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-sisalto-padding-vaaka\"]"
        sisalto-padding-ala-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-sisalto-padding-ala\"]"
        juuri "[data-cy=\"ui-komponenttien-tarkastelu-sivu\"]"
        paneli "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"]"
        panelin-otsake "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"] .harja-panel-otsake"
        panelin-runko "[data-cy=\"ui-komponenttien-tarkastelu-paneli\"] .harja-panel-runko"]
    (komponenttitesti
      [nakyma/ui-komponenttien-tarkastelu]

      (u/change korostus-syote "#112233")
      (u/change korostus-hover-syote "#223344")
      (u/change pinta-hover-syote "#ddeeff")
      (u/change varjo-syote "none")
      (u/change radius-syote "0")
      (u/change lohko-marginaali-ala-syote "2rem")
      (u/change sisalto-padding-pysty-syote "0.5rem")
      (u/change sisalto-padding-vaaka-syote "1.5rem")
      (u/change sisalto-padding-ala-syote "2rem")
      (u/blur varjo-syote)
      (u/blur radius-syote)
      (u/blur lohko-marginaali-ala-syote)
      (u/blur sisalto-padding-pysty-syote)
      (u/blur sisalto-padding-vaaka-syote)
      (u/blur sisalto-padding-ala-syote)
      (is (= "#112233" (css-muuttujan-arvo juuri "--harja-teema-korostus")))
      (is (= "#223344" (css-muuttujan-arvo juuri "--harja-teema-korostus-hover")))
      (is (= "#ddeeff" (css-muuttujan-arvo juuri "--harja-teema-pinta-hover")))
      (is (= "none" (css-muuttujan-arvo juuri "--harja-teema-varjo")))
      (is (= "0" (css-muuttujan-arvo juuri "--harja-teema-radius")))
      (is (= "2rem" (css-muuttujan-arvo juuri "--harja-teema-lohko-marginaali-ala")))
      (is (= "0.5rem" (css-muuttujan-arvo juuri "--harja-teema-sisalto-padding-pysty")))
      (is (= "1.5rem" (css-muuttujan-arvo juuri "--harja-teema-sisalto-padding-vaaka")))
      (is (= "2rem" (css-muuttujan-arvo juuri "--harja-teema-sisalto-padding-ala")))
      (is (= "none" (laskettu-tyyliarvo paneli "box-shadow")))
      (is (= "32px" (laskettu-tyyliarvo paneli "margin-bottom")))
      (is (= "8px" (laskettu-tyyliarvo panelin-otsake "padding-top")))
      (is (= "24px" (laskettu-tyyliarvo panelin-otsake "padding-right")))
      (is (= "8px" (laskettu-tyyliarvo panelin-runko "padding-top")))
      (is (= "24px" (laskettu-tyyliarvo panelin-runko "padding-right")))
      (is (= "32px" (laskettu-tyyliarvo panelin-runko "padding-bottom")))
      (is (= 1 (count (u/sel "[data-cy=\"primitive-viesti-info-teema\"]")))))))

(deftest yhteinen-teemapaneeli-vaikuttaa-modaaliin
  (let [pinta-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-pinta\"]"
        reuna-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-reuna\"]"
        teksti-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-teksti\"]"
        radius-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-radius\"]"
        varjo-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-varjo\"]"]
    (komponenttitesti
      [nakyma/ui-komponenttien-tarkastelu]

      (u/change pinta-syote "#ddeeff")
      (u/change reuna-syote "#112233")
      (u/change teksti-syote "#123456")
      (u/change radius-syote "0")
      (u/change varjo-syote "none")
      (u/blur radius-syote)
      (u/blur varjo-syote)
      (u/click "[data-cy=\"ui-komponenttien-tarkastelu-avaa-modaali\"]")
      (is (= "rgb(221, 238, 255)" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "background-color")))
      (is (= "rgb(17, 34, 51)" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "border-top-color")))
      (is (= "rgb(18, 52, 86)" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "color")))
      (is (= "0px" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "border-top-left-radius")))
      (is (= "none" (laskettu-tyyliarvo "[data-cy=\"harja-modal-sisalto\"]" "box-shadow"))))))

(deftest yhteinen-teemapaneeli-vaikuttaa-tabseihin
  (let [korostus-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-korostus\"]"
        pinta-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-pinta\"]"
        teksti-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-teksti\"]"
        reuna-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-reuna\"]"
        radius-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-radius\"]"
        aktiivinen-tabi "[data-cy=\"ui-komponenttien-tarkastelu-tabs-perustiedot\"]"
        passiivinen-tabi "[data-cy=\"ui-komponenttien-tarkastelu-tabs-historia\"]"]
    (komponenttitesti
      [nakyma/ui-komponenttien-tarkastelu]

      (u/change korostus-syote "#112233")
      (u/change pinta-syote "#ddeeff")
      (u/change teksti-syote "#123456")
      (u/change reuna-syote "#223344")
      (u/change radius-syote "0")
      (u/blur radius-syote)
      (is (= "rgb(221, 238, 255)" (laskettu-tyyliarvo aktiivinen-tabi "background-color")))
      (is (= "rgb(18, 52, 86)" (laskettu-tyyliarvo aktiivinen-tabi "color")))
      (is (= "rgb(34, 51, 68)" (laskettu-tyyliarvo aktiivinen-tabi "border-top-color")))
      (is (= "0px" (laskettu-tyyliarvo aktiivinen-tabi "border-top-left-radius")))
      (is (= "rgb(17, 34, 51)" (laskettu-tyyliarvo passiivinen-tabi "background-color")))
      (is (= "rgb(221, 238, 255)" (laskettu-tyyliarvo passiivinen-tabi "color"))))))

(deftest yhteinen-teemapaneeli-vaikuttaa-viestiin
  (let [pinta-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-pinta\"]"
        reuna-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-reuna\"]"
        teksti-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-teksti\"]"
        radius-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-radius\"]"
        varjo-syote "[data-cy=\"ui-komponenttien-tarkastelu-teema-token-harja-teema-varjo\"]"
        viesti "[data-cy=\"primitive-viesti-info-teema\"]"]
    (komponenttitesti
      [nakyma/ui-komponenttien-tarkastelu]

      (u/change pinta-syote "#ddeeff")
      (u/change reuna-syote "#112233")
      (u/change teksti-syote "#123456")
      (u/change radius-syote "0")
      (u/change varjo-syote "none")
      (u/blur radius-syote)
      (u/blur varjo-syote)
      (is (= "rgb(221, 238, 255)" (laskettu-tyyliarvo viesti "background-color")))
      (is (= "rgb(17, 34, 51)" (laskettu-tyyliarvo viesti "border-top-color")))
      (is (= "rgb(18, 52, 86)" (laskettu-tyyliarvo viesti "color")))
      (is (= "0px" (laskettu-tyyliarvo viesti "border-top-left-radius")))
      (is (= "none" (laskettu-tyyliarvo viesti "box-shadow"))))))
