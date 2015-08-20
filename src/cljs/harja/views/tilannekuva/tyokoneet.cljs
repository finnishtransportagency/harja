(ns harja.views.tilannekuva.tyokoneet
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.views.kartta :as kartta]))

(defn kaanna-sijainti [[x y]]
  [y x])

(def tyokonetta-klikattu
  (tapahtumat/kuuntele! :tyokone-klikattu
                        (fn [tapahtuma]
                          (log "tyokonetta klikattu" (clj->js tapahtuma))
                          (kartta/nayta-popup! (kaanna-sijainti (:sijainti tapahtuma))
                                               [:div
                                                [:p [:b "Työkone"]]
                                                [:div "Tyyppi: " (:tyokonetyyppi tapahtuma)]
                                                [:div "Organisaatio: " (:organisaatio tapahtuma)]
                                                [:div "Tehtävät: "
                                                 (let [tehtavat (str/join "," (:tehtavat tapahtuma))]
                                                   [:span tehtavat])]]))))
