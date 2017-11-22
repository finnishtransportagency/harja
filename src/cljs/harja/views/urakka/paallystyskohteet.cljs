(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.urakka.paallystys :as paallystys-tiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.views.urakka.paallystys-muut-kustannukset :as muut-kustannukset-view]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [vihje-elementti]]
            [harja.views.kartta :as kartta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.tiedot.urakka :as urakka]
            [cljs-time.core :as t]
            [harja.tiedot.hallinta.indeksit :as indeksit]
            [harja.views.urakka.paallystys-indeksit :as paallystys-indeksit]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.views.urakka.valinnat :as valinnat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def sivu "Päällystyskohteet")

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
    (when-not (empty? indeksitiedot)
      [:span
       [:h6 "Urakassa raaka-aineiden hinnat sidottu seuraaviin indekseihin"]
       (for [idx indeksitiedot]
         ^{:key (:id idx)}
         [materiaalin-indeksisidontarivi idx])])))

(defn paallystyskohteet [ur]
  (let [hae-tietoja (fn [urakan-tiedot]
                      (go (if-let [ch (indeksit/hae-paallystysurakan-indeksitiedot (:id urakan-tiedot))]
                            (reset! urakka/paallystysurakan-indeksitiedot (<! ch)))))]
    (hae-tietoja ur)
    (komp/kun-muuttuu (hae-tietoja ur))
    (komp/kirjaa-kaytto! sivu)
    (komp/luo
     (fn [ur]
       [:div.paallystyskohteet
        [kartta/kartan-paikka]

        [valinnat/urakan-vuosi ur]
        [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
        [valinnat/tienumero yllapito-tiedot/tienumero]

        [yllapitokohteet-view/yllapitokohteet
         ur
         paallystys-tiedot/yhan-paallystyskohteet
         {:otsikko "YHA:sta tuodut päällystyskohteet"
          :kohdetyyppi :paallystys
          :yha-sidottu? true
          :tallenna
          (yllapitokohteet/kasittele-tallennettavat-kohteet!
            #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
            :paallystys
            #(reset! paallystys-tiedot/yllapitokohteet %))
          :kun-onnistuu (fn [_]
                          (urakka/lukitse-urakan-yha-sidonta! (:id ur)))}]

        [yllapitokohteet-view/yllapitokohteet
         ur
          paallystys-tiedot/harjan-paikkauskohteet
          {:otsikko "Harjan paikkauskohteet ja muut kohteet"
          :kohdetyyppi :paikkaus
          :yha-sidottu? false
          :tallenna
          (yllapitokohteet/kasittele-tallennettavat-kohteet!
            #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
            :paikkaus
            #(reset! paallystys-tiedot/yllapitokohteet %))}]

        [muut-kustannukset-view/muut-kustannukset ur]

        [yllapitokohteet-view/yllapitokohteet-yhteensa
         paallystys-tiedot/kaikki-kohteet {:nakyma :paallystys}]

        [vihje-elementti [:span
                          [:span "Huomioi etumerkki hinnanmuutoksissa. Ennustettuja määriä sisältävät kentät on värjätty "]
                          [:span.grid-solu-ennustettu "sinisellä"]
                          [:span "."]]]
        [indeksitiedot]

        [:div.kohdeluettelon-paivitys
         [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paallystyskohteet]
         [yha/kohdeluettelo-paivitetty ur]]]))))
