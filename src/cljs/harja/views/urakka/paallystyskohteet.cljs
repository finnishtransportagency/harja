(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko tietoja]]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.ui.yleiset :refer [vihje-elementti]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as urakka]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t]
            [harja.tiedot.hallinta.indeksit :as indeksit]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.paallystys-indeksit :as paallystys-indeksit])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn- materiaalin-indeksisidontarivi
  [{:keys [indeksi lahtotason-vuosi lahtotason-kuukausi]}]
  [:div
   [:span.tietokentta (paallystys-indeksit/raaka-aine-nimi (:raakaaine indeksi)) ": "]
   [:span.tietoarvo
    (str
     (:indeksinimi indeksi)
     (when (:arvo indeksi)
       (str " (lähtötaso "
            lahtotason-vuosi "/" lahtotason-kuukausi
            ": "
            (:arvo indeksi) " €/t)")))]])

(defn indeksitiedot
  []
  (let [indeksitiedot @urakka/paallystysurakan-indeksitiedot]
    [:span
     [:h6 "Urakassa raaka-aineiden hinnat sidottu seuraaviin indekseihin"]
     (for [idx indeksitiedot]
       ^{:key (:id idx)}
       [materiaalin-indeksisidontarivi idx])]))

(defn paallystyskohteet [ur]
  (let [hae-tietoja (fn [urakan-tiedot]
                      (go (reset! urakka/paallystysurakan-indeksitiedot
                                  (<! (indeksit/hae-paallystysurakan-indeksitiedot (:id urakan-tiedot))))))]
    (hae-tietoja ur)
    (komp/kun-muuttuu (hae-tietoja ur))
    (komp/luo
     (fn [ur]
       [:div.paallystyskohteet
        [kartta/kartan-paikka]

        [valinnat/vuosi {}
         (t/year (:alkupvm ur))
         (t/year (:loppupvm ur))
         urakka/valittu-urakan-vuosi
         urakka/valitse-urakan-vuosi!]

        [yllapitokohteet-view/yllapitokohteet
         ur
         paallystys/yhan-paallystyskohteet
         {:otsikko "YHA:sta tuodut päällystyskohteet"
          :kohdetyyppi :paallystys
          :yha-sidottu? true
          :tallenna
          (yllapitokohteet/kasittele-tallennettavat-kohteet!
            #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
            :paallystys
            #(reset! paallystys/yhan-paallystyskohteet (filter yllapitokohteet/yha-kohde? %)))
          :kun-onnistuu (fn [_]
                          (urakka/lukitse-urakan-yha-sidonta! (:id ur)))}]

        [yllapitokohteet-view/yllapitokohteet
         ur
         paallystys/harjan-paikkauskohteet
         {:otsikko "Harjan paikkauskohteet ja muut kohteet"
          :kohdetyyppi :paikkaus
          :yha-sidottu? false
          :tallenna
          (yllapitokohteet/kasittele-tallennettavat-kohteet!
            #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
            :paikkaus
            #(reset! paallystys/harjan-paikkauskohteet (filter (comp not yllapitokohteet/yha-kohde?) %)))}]

        [yllapitokohteet-view/yllapitokohteet-yhteensa
         paallystys/kaikki-kohteet {:nakyma :paallystys}]

        [vihje-elementti [:span
                          [:span "Huomioi etumerkki hinnanmuutoksissa. Ennustettuja määriä sisältävät kentät on värjätty "]
                          [:span.grid-solu-ennustettu "sinisellä"]
                          [:span "."]]]
        [indeksitiedot]

        [:div.kohdeluettelon-paivitys
         [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paallystyskohteet]
         [yha/kohdeluettelo-paivitetty ur]]]))))
