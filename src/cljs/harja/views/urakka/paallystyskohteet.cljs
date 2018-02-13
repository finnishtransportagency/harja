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
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.modal :as modal]
            [harja.domain.tierekisteri :as tr]
            [harja.ui.napit :as napit])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))

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

(defn- validointivirhe-kohteet-pallekkain [kohteet-paallekain-virheet]
  [:div
   [:p "Seuraavat saman vuoden kohteet menevät päällekkäin:"]
   (for* [virhe kohteet-paallekain-virheet]
     (let [kohteet (:kohteet virhe)
           kohde1 (first kohteet)
           kohde2 (second kohteet)]
       [:ul
        [:li (str (:kohdenumero kohde1) " " (:nimi kohde1)
                  ", " (tr/tierekisteriosoite-tekstina kohde1 {:teksti-tie? false})
                  ", " (:urakka kohde1))]
        [:li (str (:kohdenumero kohde2) " " (:nimi kohde2)
                  ", " (tr/tierekisteriosoite-tekstina kohde2 {:teksti-tie? false})
                  ", " (:urakka kohde2))]]))])

(defn validointivirheet-modal [{:keys [validointivirheet] :as modal-data}]
  (let [validointivirheet-ryhmittain (group-by :validointivirhe validointivirheet)
        kohteet-paallekain-virheet (:kohteet-paallekain validointivirheet-ryhmittain)
        sulje-fn #(swap! paallystys-tiedot/validointivirheet-modal assoc :nakyvissa? false)]
    [modal/modal {:otsikko "Ongelma kohteiden tallennuksessa"
                  :nakyvissa? (:nakyvissa? modal-data)
                  :sulje-fn sulje-fn
                  :footer [napit/sulje sulje-fn]}
     [:div
      (when (not (empty? kohteet-paallekain-virheet))
        [validointivirhe-kohteet-pallekkain kohteet-paallekain-virheet])]]))

(defn paallystyskohteet [ur]
  (let [hae-tietoja (fn [urakan-tiedot]
                      (go (if-let [ch (indeksit/hae-paallystysurakan-indeksitiedot (:id urakan-tiedot))]
                            (reset! urakka/paallystysurakan-indeksitiedot (<! ch)))))]
    (hae-tietoja ur)
    (komp/kun-muuttuu (hae-tietoja ur))
    (komp/luo
      (fn [ur]
        [:div.paallystyskohteet
         [kartta/kartan-paikka]

         [valinnat/urakan-vuosi ur]
         [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
         [valinnat/tienumero yllapito-tiedot/tienumero]

         [validointivirheet-modal @paallystys-tiedot/validointivirheet-modal]

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
             #(reset! paallystys-tiedot/yllapitokohteet %)
             #(reset! paallystys-tiedot/validointivirheet-modal (assoc % :nakyvissa? true)))
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
             #(reset! paallystys-tiedot/yllapitokohteet %)
             #(constantly nil))}] ;; Paikakuskohteet eivät sisällä validointeja palvelinpäässä

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
