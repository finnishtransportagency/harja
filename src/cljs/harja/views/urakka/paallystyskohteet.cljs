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
            [harja.ui.yleiset :as yleiset])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn- materiaalin-indeksisidontarivi
  [otsikko rivi raaka-aine-avain]
  (let [raaka-aine (raaka-aine-avain rivi)]
    (when (:id raaka-aine)
      [:div
       [:span.tietokentta (str otsikko ": ")]
       [:span.tietoarvo
        (str
          (:indeksinimi raaka-aine)
          (when (:lahtotason-arvo raaka-aine)
            (str " (lähtötaso "
                 (:lahtotason-vuosi rivi) "/" (:lahtotason-kuukausi rivi)
                 ": "
                 (:lahtotason-arvo raaka-aine) " €/t)")))]])))

(defn indeksitiedot
  [valittu-vuosi]
  (let [valittu-vuosi (if-not valittu-vuosi
                        (pvm/vuosi (pvm/nyt))
                        valittu-vuosi)
        vuoden-indeksitiedot (first (filter #(= valittu-vuosi (:urakkavuosi %))
                                            @urakka/paallystysurakan-indeksitiedot))]
    (when (map? vuoden-indeksitiedot)
      [:span
       [:h6 "Urakassa vuonna " valittu-vuosi " raaka-aineiden hinnat sidottu seuraaviin indekseihin"]
       (materiaalin-indeksisidontarivi "Raskas polttoöljy" vuoden-indeksitiedot :raskas)
       (materiaalin-indeksisidontarivi "Kevyt polttoöljy" vuoden-indeksitiedot :kevyt)
       (materiaalin-indeksisidontarivi "Nestekaasu" vuoden-indeksitiedot :nestekaasu)])))

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
         {:otsikko "Harjan paikkauskohteet"
          :kohdetyyppi :paikkaus
          :yha-sidottu? false
          :tallenna
          (yllapitokohteet/kasittele-tallennettavat-kohteet!
            #(oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
            :paikkaus
            #(reset! paallystys/harjan-paikkauskohteet (filter (comp not yllapitokohteet/yha-kohde?) %)))}]

        [yllapitokohteet-view/yllapitokohteet-yhteensa
         paallystys/kohteet-yhteensa {:nakyma :paallystys}]

        [vihje-elementti [:span
                          [:span "Huomioi etumerkki hinnanmuutoksissa. Ennustettuja määriä sisältävät kentät on värjätty "]
                          [:span.grid-solu-ennustettu "sinisellä"]
                          [:span "."]]]
        [indeksitiedot @urakka/valittu-urakan-vuosi]

        [:div.kohdeluettelon-paivitys
         [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paallystyskohteet]
         [yha/kohdeluettelo-paivitetty ur]]]))))
