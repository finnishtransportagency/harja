(ns harja.views.urakkatilanne.sarakkeet.valikatselmus
  (:require [harja.pvm :as pvm]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]))


(defn valikatselmus-sarake
  [rivi]
  (let [{:keys [tehdyt-paatokset-count mahdolliset-paatokset-count
                _urakan_alkuvuosi _hoitokauden_alkuvuosi]} rivi
        paatokset-kesken? (and
                            (number? tehdyt-paatokset-count)
                            (pos? mahdolliset-paatokset-count)
                            (not= tehdyt-paatokset-count mahdolliset-paatokset-count))]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry välikatselmukseen"]
     [:a.klikattava.alleviivaa {:href (str "/#urakat/valikatselmus?&hy=" (:evk_id rivi) "&u=" (:id rivi))
                                :on-click #(siirtymat/avaa-valikatselmus
                                             (:evk_id rivi) (:id rivi)
                                             [(pvm/hoitokauden-alkupvm (:hoitokauden_alkuvuosi rivi))
                                              (pvm/hoitokauden-loppupvm (inc (:hoitokauden_alkuvuosi rivi)))])}

      [:div.tavoitehintapaatos
       (if paatokset-kesken?
         (yleiset/tila-indikaattori "kesken" {:fmt-fn (constantly
                                                        (str
                                                          "Kesken (" tehdyt-paatokset-count
                                                          "/" mahdolliset-paatokset-count ")"))})

         (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Valmis")}))]]]))
