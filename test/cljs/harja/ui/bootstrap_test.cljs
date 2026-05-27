(ns harja.ui.bootstrap-test
  (:require [clojure.string :as str]
            [cljs.test :as t :refer-macros [deftest is]]
            [reagent.core :as r]
            [harja.testutils.shared-testutils :as u]
            [harja.ui.bootstrap :as bootstrap])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(defn- laskettu-tyyliarvo [selector ominaisuus]
  (some-> (u/sel1 selector)
          js/getComputedStyle
          (.getPropertyValue ominaisuus)
          str/trim))

(deftest tabs-kunnioittaa-eksplisiittista-data-cy-arvoa
  (let [aktiivinen (r/atom :perustiedot)]
    (komponenttitesti
      [bootstrap/tabs {:active aktiivinen :classes "tabs-taso1"}
       {:teksti "Perustiedot" :data-cy "ui-komponenttien-tarkastelu-tabs-perustiedot"} :perustiedot [:div "Perustiedot sisältö"]
       {:teksti "Historia" :data-cy "ui-komponenttien-tarkastelu-tabs-historia"} :historia [:div "Historia sisältö"]]

      (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-tabs-perustiedot\"]"))))
      (is (= 1 (count (u/sel "[data-cy=\"ui-komponenttien-tarkastelu-tabs-historia\"]"))))
      (is (= 1 (count (u/sel ".harja-tabs"))))
      (is (= 2 (count (u/sel ".harja-tabs-valilehti"))))
      (is (= 0 (count (u/sel ".nav"))))
      (is (= 0 (count (u/sel ".nav-tabs"))))
      (is (= 0 (count (u/sel ".nav-pills"))))
      (is (= "Perustiedot sisältö" (u/text :.valilehti)))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-tabs-perustiedot\"]") "aria-selected")))

      (u/click "[data-cy=\"ui-komponenttien-tarkastelu-tabs-historia\"]")
      (is (= :historia @aktiivinen))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"ui-komponenttien-tarkastelu-tabs-historia\"]") "aria-selected")))
      (is (= "Historia sisältö" (u/text :.valilehti))))))

(deftest tabs-muodostaa-oletus-data-cy-arvon-luokasta-ja-otsikosta
  (let [aktiivinen (r/atom :paallystysilmoitukset)]
    (komponenttitesti
      [bootstrap/tabs {:active aktiivinen :classes "tabs-taso2"}
       "Päällystysilmoitukset" :paallystysilmoitukset [:div "Sisältö"]
       "Historia" :historia [:div "Historia"]]

      (is (= 1 (count (u/sel "[data-cy=\"tabs-taso2-Paallystysilmoitukset\"]"))))
      (is (= 1 (count (u/sel "[data-cy=\"tabs-taso2-Historia\"]")))))))

(deftest pills-variantti-merkitsee-valitun-valilehden
  (let [aktiivinen (r/atom :aktiivinen)]
    (komponenttitesti
      [bootstrap/tabs {:active aktiivinen :style :pills}
       "Aktiivinen" :aktiivinen [:div "Aktiivinen sisältö"]
       "Vaihtoehto" :vaihtoehto [:div "Vaihtoehdon sisältö"]]

      (is (= 1 (count (u/sel ".harja-tabs-tyyli-pills"))))
      (is (= 1 (count (u/sel ".harja-tabs-valilehti.harja-tabs-valittu"))))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"Aktiivinen\"]") "aria-selected")))

      (u/click "[data-cy=\"Vaihtoehto\"]")
      (is (= :vaihtoehto @aktiivinen))
      (is (= 1 (count (u/sel ".harja-tabs-valilehti.harja-tabs-valittu"))))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"Vaihtoehto\"]") "aria-selected"))))))

(deftest tabs-komponenttikohtainen-token-voittaa-yhteisen-teematokenin
  (let [aktiivinen (r/atom :perustiedot)
        aktiivinen-tabi "[data-cy=\"tabs-perustiedot\"]"
        passiivinen-tabi "[data-cy=\"tabs-historia\"]"]
    (komponenttitesti
      [:div {:style {"--harja-teema-radius" "0"
                     "--harja-teema-reuna" "rgb(17, 18, 19)"
                     "--harja-teema-korostus" "rgb(21, 22, 23)"
                     "--harja-teema-pinta" "rgb(24, 25, 26)"
                     "--harja-teema-teksti" "rgb(27, 28, 29)"
                     "--harja-tabs-border-color" "rgb(30, 31, 32)"
                     "--harja-tabs-radius" "12px"
                     "--harja-tabs-padding-pysty" "0.75rem"
                     "--harja-tabs-padding-vaaka" "2rem"
                     "--harja-tabs-gap" "1rem"
                     "--harja-tabs-taso1-passiivinen-tausta" "rgb(101, 102, 103)"
                     "--harja-tabs-taso1-passiivinen-teksti" "rgb(104, 105, 106)"
                     "--harja-tabs-taso1-aktiivinen-tausta" "rgb(107, 108, 109)"
                     "--harja-tabs-taso1-aktiivinen-teksti" "rgb(110, 111, 112)"}}
       [bootstrap/tabs {:active aktiivinen :classes "tabs-taso1"}
        {:teksti "Perustiedot" :data-cy "tabs-perustiedot"} :perustiedot [:div "Perustiedot sisältö"]
                  {:teksti "Historia" :data-cy "tabs-historia"} :historia [:div "Historia sisältö"]]]

      (is (= "rgb(107, 108, 109)" (laskettu-tyyliarvo aktiivinen-tabi "background-color")))
      (is (= "rgb(110, 111, 112)" (laskettu-tyyliarvo aktiivinen-tabi "color")))
      (is (= "rgb(30, 31, 32)" (laskettu-tyyliarvo aktiivinen-tabi "border-top-color")))
      (is (= "12px" (laskettu-tyyliarvo aktiivinen-tabi "border-top-left-radius")))
      (is (= "32px" (laskettu-tyyliarvo aktiivinen-tabi "padding-right")))
      (is (= "16px" (laskettu-tyyliarvo aktiivinen-tabi "margin-right")))
      (is (= "rgb(101, 102, 103)" (laskettu-tyyliarvo passiivinen-tabi "background-color")))
      (is (= "rgb(104, 105, 106)" (laskettu-tyyliarvo passiivinen-tabi "color"))))))

(deftest tabs-renderoi-otsikon-sisallon-kun-se-annetaan-mapissa
  (let [aktiivinen (r/atom :perustiedot)]
    (komponenttitesti
      [bootstrap/tabs {:active aktiivinen :classes "tabs-taso1"}
       {:teksti "Perustiedot"
        :sisalto [:span {:data-cy "tabs-otsikko-perustiedot"} "Mukautettu otsikko"]
        :data-cy "tabs-perustiedot"} :perustiedot [:div "Perustiedot sisältö"]
       {:teksti "Historia"
        :data-cy "tabs-historia"} :historia [:div "Historia sisältö"]]

      (is (= 1 (count (u/sel "[data-cy=\"tabs-otsikko-perustiedot\"]"))))
      (is (= "Mukautettu otsikko" (u/text "[data-cy=\"tabs-otsikko-perustiedot\"]"))))))

(deftest navbar-renderoi-harjan-omat-luokat-ja-vaihtaa-mobiilivalikon-tilaa
  (komponenttitesti
    [bootstrap/navbar {:luokka "testi-navbar"
                       :data-cy "testi-navbar"}
     [:span {:data-cy "testi-navbar-otsikko"} "Harja"]
     [:a {:href "#"} "Urakat"]
     :right
     [:a {:href "#"} "Kirjaudu ulos"]]

    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar\"].harja-navbar.testi-navbar"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar-otsikko\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar-vaihtaja\"].harja-navbar-vaihtaja"))))
    (is (= "false" (.getAttribute (u/sel1 "[data-cy=\"testi-navbar-vaihtaja\"]") "aria-expanded")))
    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar-sisalto\"].harja-navbar-sisalto"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar-vasen\"].harja-navbar-lista"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar-oikea\"].harja-navbar-lista.harja-navbar-lista-oikea"))))
    (is (= 0 (count (u/sel ".navbar"))))
    (is (= 0 (count (u/sel ".navbar-default"))))
    (is (= 0 (count (u/sel ".navbar-header"))))
    (is (= 0 (count (u/sel ".navbar-toggle"))))
    (is (= 0 (count (u/sel ".navbar-collapse"))))
    (is (= 0 (count (u/sel ".navbar-nav"))))

    (u/click "[data-cy=\"testi-navbar-vaihtaja\"]")
    (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"testi-navbar-vaihtaja\"]") "aria-expanded")))
    (is (= 1 (count (u/sel "[data-cy=\"testi-navbar-sisalto\"].harja-navbar-sisalto-avoin"))))))

(deftest panel-kunnioittaa-eksplisiittista-data-cy-arvoa
  (komponenttitesti
    [bootstrap/panel {:data-cy "testi-paneli"
                      :class "oma-paneliluokka"}
     "Paneelin otsikko"
     [:div "Paneelin sisältö"]]

    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli\"].harja-panel.oma-paneliluokka"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli\"] .harja-panel-otsake"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli\"] .harja-panel-runko"))))
    (is (= 0 (count (u/sel "[data-cy=\"testi-paneli\"] .panel"))))
    (is (= 0 (count (u/sel "[data-cy=\"testi-paneli\"] .panel-heading"))))
    (is (= 0 (count (u/sel "[data-cy=\"testi-paneli\"] .panel-body"))))
    (is (= "Paneelin sisältö" (u/text "[data-cy=\"testi-paneli\"] .harja-panel-runko")))))

(deftest panel-ilman-otsikkoa-jattaa-headingin-pois
  (komponenttitesti
    [bootstrap/panel {:data-cy "testi-paneli-ilman-otsikkoa"} [:div "Paneelin sisältö"]]

    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli-ilman-otsikkoa\"]"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli-ilman-otsikkoa\"].harja-panel"))))
    (is (= 0 (count (u/sel "[data-cy=\"testi-paneli-ilman-otsikkoa\"] .harja-panel-otsake"))))
    (is (= 1 (count (u/sel "[data-cy=\"testi-paneli-ilman-otsikkoa\"] .harja-panel-runko"))))
    (is (= 0 (count (u/sel "[data-cy=\"testi-paneli-ilman-otsikkoa\"] .panel-heading"))))
    (is (= 0 (count (u/sel "[data-cy=\"testi-paneli-ilman-otsikkoa\"] .panel-body"))))))

(deftest dropdown-panel-renderoi-harjan-omat-luokat-ja-vaihtaa-tilaa
  (let [auki? (r/atom false)]
    (komponenttitesti
      [bootstrap/dropdown-panel {:open auki?
                                 :style :primary
                                 :data-cy "testi-dropdown-panel"}
       "Asetukset"
       [:div "Dropdown-panelin sisältö"]]

      (is (= 1 (count (u/sel "[data-cy=\"testi-dropdown-panel\"].harja-panel.harja-dropdown-panel.harja-dropdown-panel-tyyli-primary"))))
      (is (= 1 (count (u/sel "[data-cy=\"testi-dropdown-panel-vaihtaja\"].harja-panel-otsake.harja-dropdown-panel-vaihtaja"))))
      (is (= "false" (.getAttribute (u/sel1 "[data-cy=\"testi-dropdown-panel-vaihtaja\"]") "aria-expanded")))
      (is (= "rgb(51, 122, 183)" (laskettu-tyyliarvo "[data-cy=\"testi-dropdown-panel-vaihtaja\"]" "background-color")))
      (is (= "rgb(255, 255, 255)" (laskettu-tyyliarvo "[data-cy=\"testi-dropdown-panel-vaihtaja\"]" "color")))
      (is (= 0 (count (u/sel "[data-cy=\"testi-dropdown-panel-sisalto\"]"))))
      (is (= 0 (count (u/sel ".panel"))))
      (is (= 0 (count (u/sel ".panel-heading"))))
      (is (= 0 (count (u/sel ".panel-body"))))
      (is (= 0 (count (u/sel ".glyphicon-plus"))))
      (is (= 0 (count (u/sel ".glyphicon-minus"))))

      (u/click "[data-cy=\"testi-dropdown-panel-vaihtaja\"]")
      (is (= true @auki?))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"testi-dropdown-panel-vaihtaja\"]") "aria-expanded")))
      (is (= 1 (count (u/sel "[data-cy=\"testi-dropdown-panel-sisalto\"].harja-panel-runko"))))
      (is (= "Dropdown-panelin sisältö" (u/text "[data-cy=\"testi-dropdown-panel-sisalto\"]"))))))

(deftest dropdown-panel-sallii-hiccup-otsikon
  (let [auki? (r/atom true)]
    (komponenttitesti
      [bootstrap/dropdown-panel {:open auki?
                                 :data-cy "testi-dropdown-panel-hiccup"}
       [:span {:data-cy "testi-dropdown-panel-otsikko"} "Mukautettu otsikko"]
       [:div "Sisältö"]]

      (is (= 1 (count (u/sel "[data-cy=\"testi-dropdown-panel-otsikko\"]"))))
      (is (= "Mukautettu otsikko" (u/text "[data-cy=\"testi-dropdown-panel-otsikko\"]")))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"testi-dropdown-panel-hiccup-vaihtaja\"]") "aria-expanded"))))))

(deftest dropdown-renderoi-harjan-omat-luokat-ja-vaihtaa-aukiolon
  (komponenttitesti
    [:div {:data-cy "testi-dropdown-juuri"}
     [bootstrap/dropdown "Työkalut"
      [[:a {:href "#"
            :data-cy "testi-dropdown-raportit"}
        "Raportit"]
       [:a {:href "#"
            :data-cy "testi-dropdown-asetukset"}
        "Asetukset"]]]]

    (is (= 1 (count (u/sel ".harja-dropdown"))))
    (is (= 1 (count (u/sel ".harja-dropdown-vaihtaja"))))
    (is (= "false" (.getAttribute (u/sel1 ".harja-dropdown-vaihtaja") "aria-expanded")))
    (is (= 0 (count (u/sel ".harja-dropdown-valikko"))))
    (is (= 0 (count (u/sel ".dropdown-toggle"))))
    (is (= 0 (count (u/sel ".dropdown-menu"))))
    (is (= 0 (count (u/sel ".caret"))))

    (u/click ".harja-dropdown-vaihtaja")
    (is (= "true" (.getAttribute (u/sel1 ".harja-dropdown-vaihtaja") "aria-expanded")))
    (is (= 1 (count (u/sel ".harja-dropdown-avoin"))))
    (is (= 1 (count (u/sel ".harja-dropdown-valikko"))))
    (is (= 2 (count (u/sel ".harja-dropdown-kohta"))))
    (is (= "Raportit" (u/text "[data-cy=\"testi-dropdown-raportit\"]")))

    (u/click "[data-cy=\"testi-dropdown-raportit\"]")
    (is (= "false" (.getAttribute (u/sel1 ".harja-dropdown-vaihtaja") "aria-expanded")))
    (is (= 0 (count (u/sel ".harja-dropdown-valikko"))))))
