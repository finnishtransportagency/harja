(ns harja.views.hallinta.kojelauta
  (:require [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [tuck.core :refer [tuck]]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallintayksikot :as hy]
            [harja.tiedot.hallinta.kojelauta :as tiedot]))

(defn suodattimet [e! {:keys [valinnat kriteerit urakkavuodet urakkahaku] :as app}]
  [:<>
   [yleiset/pudotusvalikko
    "ELY"
    {:valitse-fn #(e! (tiedot/->Valitse :ely %))
     :valinta (:ely valinnat)
     :format-fn #(or (:nimi %) "Kaikki")
     :vayla-tyyli? true}
    (into [nil] @hy/vaylamuodon-hallintayksikot)]
   [yleiset/pudotusvalikko
    "Kriteerit"
    {:valitse-fn #(e! (tiedot/->Valitse :kriteeri %))
     :valinta (:kriteerit valinnat)
     :format-fn #(or (:nimi %) "Kaikki")
     :vayla-tyyli? true}
    (into [nil] kriteerit)]
   [yleiset/pudotusvalikko
    "Urakkavuosi"
    {:valitse-fn #(e! (tiedot/->Valitse :urakkavuosi %))
     :valinta (:urakkavuosi valinnat)
     :format-fn #(or % "Kaikki")
     :vayla-tyyli? true}
    (into [nil] urakkavuodet)]

   [:div.label-ja-alasveto
    [:label.alasvedon-otsikko-vayla "Hae urakkaa"]
    [kentat/tee-kentta
     {:tyyppi :haku
      :nayta :nimi :fmt :nimi
      :hae-kun-yli-n-merkkia 0
      :vayla-tyyli? true
      :lahde urakkahaku
      :monivalinta? true
      :tarkkaile-ulkopuolisia-muutoksia? true
      :hakuikoni? true
      :monivalinta-teksti #(case (count %)
                             0 "Kaikki valittu"
                             1 (:nimi (first %))
                             (str (count %) " urakkaa valittu"))}
     (r/wrap (:urakat valinnat) #(e! (tiedot/->Valitse :urakat %)))]]])


(defn kojelauta* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! app]
      [:div.kojelauta-hallinta
       [:h1 "Etusivu"]
       [suodattimet e! app]
       [debug/debug app]])))

(defn kojelauta []
  [tuck tiedot/tila kojelauta*])
