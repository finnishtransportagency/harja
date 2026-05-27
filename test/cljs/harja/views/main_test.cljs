(ns harja.views.main-test
  (:require [cljs.test :as t :refer-macros [deftest is]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.testutils.shared-testutils :as u]
            [harja.ui.listings :as listings]
            [harja.ui.palaute :as palaute]
            [harja.ui.yleiset :as yleiset]
            [harja.views.haku :as haku]
            [harja.views.main :as sut])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(deftest haku-renderoi-header-version-ilman-bootstrapin-navbar-luokkia
  (with-redefs [listings/suodatettu-lista (fn [_ _]
                                            [:div {:data-cy "testi-haku-lista"}])]
    (komponenttitesti
      [haku/haku]
      (is (= 1 (count (u/sel "[data-cy=\"harja-ylanavigaatio-haku\"].harja-ylin-header-haku"))))
      (is (= 1 (count (u/sel "[data-cy=\"testi-haku-lista\"]"))))
      (is (= 0 (count (u/sel ".navbar-form"))))
      (is (= 0 (count (u/sel ".navbar-left"))))
      (is (= 0 (count (u/sel ".form-group")))))))

(deftest header-renderoi-oman-sivulistan-ilman-bootstrapin-nav-pills-luokkia
  (with-redefs [k/kehitysymparistossa? (constantly false)
                haku/haku (fn []
                            [:form {:data-cy "testi-header-haku"}])
                sut/harja-info (fn [_]
                                 [:a {:data-cy "testi-header-info"} "INFO"])
                palaute/palaute-linkki (fn []
                                         [:a {:data-cy "testi-header-palaute"} "Palaute"])
                sut/kayttajatiedot (fn [_]
                                     [:a {:data-cy "testi-header-kayttaja"} "Käyttäjä"])]
    (komponenttitesti
      [sut/header :hallinta]
      (is (= 1 (count (u/sel "[data-cy=\"harja-ylanavigaatio\"].harja-navbar"))))
      (is (= 1 (count (u/sel "[data-cy=\"harja-ylanavigaatio-vasen\"] > li .harja-navbar-item-haku [data-cy=\"testi-header-haku\"]"))))
      (is (= 1 (count (u/sel "[data-cy=\"harja-ylanavigaatio-sivut\"].harja-ylin-header-sivut"))))
      (is (= 0 (count (u/sel ".nav"))))
      (is (= 0 (count (u/sel ".nav-pills"))))
      (is (= 0 (count (u/sel "#sivut > li.active")))))))

(deftest muodosta-header-sivulista-merkitsee-valitun-linkin-harjan-omalla-luokalla
  (with-redefs [yleiset/linkki (fn [teksti _]
                                 [:a {:href "#"} teksti])]
    (komponenttitesti
      [sut/muodosta-header-sivulista
       :hallinta
       [{:id :urakat :teksti "Urakat" :toiminto (constantly nil)}
        {:id :hallinta :teksti "Hallinta" :toiminto (constantly nil)}]]
      (is (= 2 (count (u/sel "#sivut > li"))))
      (is (= 1 (count (u/sel "#sivut > li.harja-ylin-header-linkki-aktiivinen"))))
      (is (= "Hallinta" (u/text "#sivut > li.harja-ylin-header-linkki-aktiivinen > a"))))))