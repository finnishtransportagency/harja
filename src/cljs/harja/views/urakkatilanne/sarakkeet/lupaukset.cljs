(ns harja.views.urakkatilanne.sarakkeet.lupaukset
  (:require [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]))


(defn lupauspisteet-sarake
  [rivi]
  (let [{:keys [luvatut_pisteet toteutuneet_pisteet hoitokauden_alkuvuosi]} rivi]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry lupausnäkymään"]
     [:a.klikattava.alleviivaa {:href (str "/#urakat/valitavoitteet/lupaukset?&hy=" (:evk_id rivi) "&u=" (:id rivi))
                                :on-click #(siirtymat/avaa-lupaukset-valitussa-urakassa (:evk_id rivi) (:id rivi) hoitokauden_alkuvuosi)}
      [:div.lupauspisteet
       (if (or (nil? luvatut_pisteet) (nil? toteutuneet_pisteet))
         (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly "Ei tavoitepistemäärää")})
         (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Ok")}))]]]))
