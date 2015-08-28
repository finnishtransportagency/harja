(ns harja.views.tilannekuva.tyokoneet
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.views.kartta :as kartta]))

(def klikattu-tyokone (atom nil))

(defn poista-tyokoneen-valinta []
  (when @klikattu-tyokone
    (reset! klikattu-tyokone nil)
    (kartta/poista-popup!)))

(defonce karttaa-vedetty
  (tapahtumat/kuuntele! :karttaa-vedetty
                        (fn [tapahtuma]
                          (poista-tyokoneen-valinta))))

(defonce tyhjaa-klikattu
  (tapahtumat/kuuntele! :tyhja-click
                        (fn [tapahtuma]
                          (poista-tyokoneen-valinta))))

(defn tee-popup [tapahtuma]
  (kartta/nayta-popup! (:sijainti tapahtuma)
                       [:div.kartta-tyokone-popup
                        [:p [:b "Työkone"]]
                        [:div "Tyyppi: " (:tyokonetyyppi tapahtuma)]
                        [:div "Organisaatio: " (:organisaationimi tapahtuma)]
                        [:div "Urakka: " (:urakkanimi tapahtuma)]
                        [:div "Tehtävät: "
                         (let [tehtavat (str/join ", " (:tehtavat tapahtuma))]
                           [:span tehtavat])]]))

(defonce tyokonedatan-muuttuminen
  (tapahtumat/kuuntele! :uusi-tyokonedata
                        (fn [data]
                          (when-let [tk @klikattu-tyokone]
                            (when-let [haettu (first (filter #(= tk (:tyokoneid %))
                                                             (:tyokoneet data)))]
                              (kartta/keskita-kartta-pisteeseen (:sijainti haettu))
                              (kartta/poista-popup!)
                              (tee-popup haettu))))))

(defonce tyokonetta-klikattu
  (tapahtumat/kuuntele! :tyokone-klikattu
                        (fn [tapahtuma]
                          (reset! klikattu-tyokone (:tyokoneid tapahtuma))
                          (kartta/keskita-kartta-pisteeseen (:sijainti tapahtuma))
                          (tee-popup tapahtuma))))


