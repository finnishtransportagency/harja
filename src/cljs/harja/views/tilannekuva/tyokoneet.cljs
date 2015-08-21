(ns harja.views.tilannekuva.tyokoneet
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.views.kartta :as kartta]))

(def tyokonetta-klikattu
  (tapahtumat/kuuntele! :tyokone-klikattu
                        (fn [tapahtuma] 
                          (kartta/nayta-popup! (:sijainti tapahtuma)
                                               [:div.kartta-tyokone-popup
                                                [:p [:b "Työkone"]]
                                                [:div "Tyyppi: " (:tyokonetyyppi tapahtuma)]
                                                [:div "Organisaatio: " (:organisaationimi tapahtuma)]
                                                [:div "Urakka: " (:urakkanimi tapahtuma)]
                                                [:div "Tehtävät: "
                                                 (let [tehtavat (str/join "," (:tehtavat tapahtuma))]
                                                   [:span tehtavat])]]))))


