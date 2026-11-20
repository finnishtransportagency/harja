(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.alasvedot
  (:require [reagent.core :as r]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :refer [register!]]))

(defn- livi-pudotusvalikko [komponentti]
  (r/with-let [valinta (r/atom "Urakat")]
    [kehys/kortti komponentti
     [:p "Valinta välitetään :valitse-fn-funktiolle. Komponentti renderöi nykyisellään .dropdown- ja .dropdown-menu-rakenteen."]
     [yleiset/pudotusvalikko
      "Näkymä"
      {:data-cy (kehys/ankkuri komponentti "-valikko")
       :valinta @valinta
       :valitse-fn #(reset! valinta %)
       :format-fn str}
      ["Urakat" "Raportit" "Hallinta"]]
     [:p {:data-cy (kehys/ankkuri komponentti "-arvo")}
      (str "Valittu: " @valinta)]
     [napit/nappi "Palauta oletus" #(reset! valinta "Urakat")
      {:data-cy (kehys/ankkuri komponentti "-palauta")
       :luokka "nappi-toissijainen"}]]))

(register! :livi-pudotusvalikko {:esikatselu livi-pudotusvalikko})
