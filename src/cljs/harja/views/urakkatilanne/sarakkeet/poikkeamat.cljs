(ns harja.views.urakkatilanne.sarakkeet.poikkeamat
  (:require [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]))


(defn avoimet-poikkeamat-sarake
  [rivi]
  (let [{:keys [avoimet_laatupoikkeamat avoimet_turvallisuuspoikkeamat]} rivi]
    [:span.avoimet-poikkeamat
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Siirry laatupoikkeamiin"]
      [:a.klikattava.alleviivaa {:href (str "/#urakat/laadunseuranta/laatupoikkeamat?&hy=" (:evk_id rivi) "&u=" (:id rivi))
                                 :on-click #(siirtymat/siirry-annettuun-valilehteen
                                              (:evk_id rivi)
                                              (:id rivi)
                                              {:taso1 :urakat
                                               :taso2 :laadunseuranta
                                               :taso3 :laatupoikkeamat})}
       (if (> avoimet_laatupoikkeamat 0)
         (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly (str "Avoimia laatupoikkeamia: " avoimet_laatupoikkeamat))})
         (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Ei avoimia laatupoikkeamia")}))]]
     [yleiset/wrap-if true
      [yleiset/tooltip {} :% "Siirry turvallisuuspoikkeamiin"]
      [:a.klikattava.alleviivaa {:href (str "/#urakat/turvallisuuspoikkeamat?&hy=" (:evk_id rivi) "&u=" (:id rivi))
                                 :on-click #(siirtymat/siirry-annettuun-valilehteen
                                              (:evk_id rivi)
                                              (:id rivi)
                                              {:taso1 :urakat
                                               :taso2 :turvallisuuspoikkeamat
                                               :taso3 nil})}
       (if (> avoimet_turvallisuuspoikkeamat 0)
         (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly (str "Avoimia turvallisuuspoikkeamia: " avoimet_turvallisuuspoikkeamat))})
         (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Ei avoimia turvallisuuspoikkeamia")}))]]]))
