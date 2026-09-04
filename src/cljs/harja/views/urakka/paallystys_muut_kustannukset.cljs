(ns harja.views.urakka.paallystys-muut-kustannukset
  (:require [harja.tiedot.urakka.siirtymat :as siirtymat]
            [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader]]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.paallystys-muut-kustannukset :as tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.validointi :as validointi]
            [cljs-time.core :as t]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

;; Ylläpitokohteiden sarakkeiden leveydet
(def kustannus-selite-leveys "auto")
(def kustannus-hinta-leveys "144px")
(def kustannus-pvm-leveys "144px")
(def kustannus-laji-leveys "75px")

(defn- rivi-poistettavissa? [m]
  (log "rivi-poistettavissa? " (pr-str m))
  (or (-> m :muokattava) (-> m :id neg?)))

(defn otsikkopaneeli []
  [:div
   [:h6 "Urakan muut kustannukset"]
   [:div.body-text {:style {:max-width "900px" :padding-top "16px"}} "Sopimuksen mukaiset sanktiot ja bonukset tulee syöttää"
    [:a.klikattava.alleviivaa {:href (str "/#urakat/laadunseuranta/sanktiot?&hy=" @nav/valittu-hallintayksikko-id "&u=" (-> @tila/yleiset :urakka :id))
                               :on-click #(siirtymat/siirry-annettuun-valilehteen
                                            @nav/valittu-hallintayksikko-id (-> @tila/yleiset :urakka :id)
                                            {:taso1 :urakat
                                             :taso2 :laadunseuranta
                                             :taso3 :sanktiot})}
     "Sanktiot ja bonukset"]
    "-osiossa. Laatupoikkeamat- tai “Sanktiot ja bonukset”-osion kautta syötetyt sanktiot ja bonukset tulevat näkyville urakan muihin kustannuksiin lisäyksen jälkeen."]])

(def grid-opts {:otsikko [otsikkopaneeli]
                :voi-lisata? true
                :voi-muokata-rivia? :muokattava
                :esta-poistaminen? (complement rivi-poistettavissa?)
                :esta-poistaminen-tooltip
                (fn [_] "Sanktioiden muokkaus tapahtuu Laadunseurannan Sakot ja bonukset -osiossa.")})


(def grid-skeema
  [{:otsikko "Pvm"
    :nimi :pvm
    :fmt pvm/pvm-opt
    :validoi [[:ei-tyhja "Anna päivämäärä"]]
    :tyyppi :pvm
    :vayla-tyyli? true
    :leveys kustannus-pvm-leveys}
   {:otsikko "Laji"
    :tyyppi :string
    :muokattava? (constantly false)
    :nimi :tyyppi
    :fmt (fn [tyyppi]
           (cond
             (= tyyppi :muu) "Muu"
             (= tyyppi :sanktio) "Sanktio"
             (= tyyppi :bonus) "Bonus"
             :else "Muu"))
    :leveys kustannus-laji-leveys}
   {:otsikko "Kustannuksen kuvaus" :nimi :selite
    :validoi [[:ei-tyhja "Anna kuvaus"]]
    :tyyppi :string :leveys kustannus-selite-leveys}
   {:otsikko "Summa (€)" :nimi :hinta :fmt #(fmt/euro-opt false %)
    :tyyppi :numero :leveys kustannus-hinta-leveys :tasaa :oikea
    :validoi [[:ei-tyhja "Anna hinta"]]}])

(defn muut-kustannukset [urakka]
  (komp/luo
   (komp/lippu tiedot/nakymassa?)
   (fn [urakka]
     [:div.muut-kustannukset
      [grid/grid (assoc grid-opts
                        :tallenna #(tiedot/tallenna-muut-kustannukset! urakka tiedot/muiden-kustannusten-tiedot %)
                        :tyhja (if (nil? @tiedot/muiden-kustannusten-tiedot)
                                 [ajax-loader "Haetaan kustannuksia..."]
                                 "Ei kustannuksia"))
       grid-skeema
       @tiedot/grid-tiedot]])))
