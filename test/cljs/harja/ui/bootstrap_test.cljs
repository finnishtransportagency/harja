(ns harja.ui.bootstrap-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [reagent.core :as r]
            [harja.testutils.shared-testutils :as u]
            [harja.ui.bootstrap :as bootstrap])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

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
      --
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
      --
      (is (= :vaihtoehto @aktiivinen))
      (is (= 1 (count (u/sel ".harja-tabs-valilehti.harja-tabs-valittu"))))
      (is (= "true" (.getAttribute (u/sel1 "[data-cy=\"Vaihtoehto\"]") "aria-selected"))))))

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
