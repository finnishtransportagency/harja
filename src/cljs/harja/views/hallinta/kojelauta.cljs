(ns harja.views.hallinta.kojelauta
  (:require [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.valinnat :as valinnat]
            [reagent.core :as r]
            [tuck.core :refer [tuck]]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.hallintayksikot :as hy]
            [harja.tiedot.hallinta.kojelauta :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- mahdolliset-hoitokauden-alkuvuodet [pvm-nyt]
  (range (- (pvm/vuosi pvm-nyt) 2)
    (+ 6 (pvm/vuosi pvm-nyt))))

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
    "Hoitokauden alkuvuosi"
    {:valitse-fn  #(e! (tiedot/->Valitse :urakkavuosi %))
     :valinta (:urakkavuosi valinnat)
     :format-fn #(or % "Kaikki")
     :vayla-tyyli? true}
    (mahdolliset-hoitokauden-alkuvuodet (pvm/nyt))]

   [:div.label-ja-alasveto
    [:label.alasvedon-otsikko-vayla {:for "urakkahaku"} "Hae urakkaa"]
    [kentat/tee-kentta
     {:tyyppi :haku
      :input-id "urakkahaku"
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



(defn listaus [e! {:keys [valinnat urakat urakoiden-tilat] :as app}]
  (let [valitut-urakat (:urakat valinnat)
        urakat (if (empty? valitut-urakat)
                 urakat
                 (filter #((into #{} (map :id valitut-urakat)) (:id %)) urakat))]
    [:div
     [debug/debug urakat]
     [grid/grid
      {:otsikko (str "Urakoiden tilat")
       :tyhja (if (nil? urakat) [ajax-loader "Ladataan tietoja"] "Ei tietoja")}
      [{:otsikko "Urakka"
        :tyyppi :string
        :nimi :nimi
        :leveys 10
        :muokattava? (constantly false)}
       {:otsikko "Kustannus\u00ADsuunnitelma"
        :muokattava? (constantly false)
        :nimi :ks_tila :leveys 5
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       (prn "Jarno  ks tila RIVI " rivi)
                       (let [ks-tila (:ks_tila rivi)]
                         (cond
                           (= "aloittamatta" (:suunnitelman_tila ks-tila))
                           [:div "Aloittamatta"]

                           (= "aloitettu" (:suunnitelman_tila ks-tila))
                           [:div "Aloitettu"]

                           (= "vahvistettu" (:suunnitelman_tila ks-tila))
                           [:div "Vahvistettu"])))}]
      urakat]]))


(defn kojelauta* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! app]
      [:div.kojelauta-hallinta
       [:h1 "Etusivu"]
       [suodattimet e! app]
       [debug/debug app]
       [listaus e! app]])))

(defn kojelauta []
  [tuck tiedot/tila kojelauta*])
