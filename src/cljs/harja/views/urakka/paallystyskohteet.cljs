(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [clojure.set :as clj-set]
            [harja.asiakas.kommunikaatio :as k]
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
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.paallystys :as paallystys])
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

(defn validointivirheet-modal []
  (let [modal-data @paallystys-tiedot/validointivirheet-modal ;; Pakko lukea eikä passata tänne, jotta ei aiheuta gridin renderiä
        validointivirheet-ryhmittain (group-by :validointivirhe (:validointivirheet modal-data))
        kohteet-paallekain-virheet (:kohteet-paallekain validointivirheet-ryhmittain)
        sulje-fn #(swap! paallystys-tiedot/validointivirheet-modal assoc :nakyvissa? false)]
    [modal/modal {:otsikko "Kohteiden tallennus epäonnistui!"
                  :otsikko-tyyli :virhe
                  :nakyvissa? (:nakyvissa? modal-data)
                  :sulje-fn sulje-fn
                  :footer [napit/sulje sulje-fn]}
     [:div
      (when (not (empty? kohteet-paallekain-virheet))
        [validointivirhe-kohteet-pallekkain kohteet-paallekain-virheet])]]))

(defn kohteen-poisto-modal
  [poistetut-kohteet]
  [:div
   "Seuraavat kohteet tullaan poistamaan myös YHA:sta"
   [:ul
    (for [poistettu-kohde poistetut-kohteet
          :let [{:keys [kohdenumero nimi tunnus]} poistettu-kohde]]
      ^{:key (:yhaid poistettu-kohde)}
      [:li (str (when kohdenumero
                  (str kohdenumero " : "))
                (when tunnus
                  (str tunnus " : "))
                nimi)])]])

(defn kohteen-poisto-modal-footer [kohteet tallenna-fn]
  [:div
   [napit/tallenna "Tallenna" (comp modal/piilota! (partial tallenna-fn kohteet))]
   [napit/peruuta "Peruuta" modal/piilota!]])

(defn paallystyskohteet [ur]
  (let [hae-tietoja (fn [urakan-tiedot]
                      (go (if-let [ch (indeksit/hae-paallystysurakan-indeksitiedot (:id urakan-tiedot))]
                            (reset! urakka/paallystysurakan-indeksitiedot (<! ch)))))
        tallenna-kohde (fn [kohteet]
                         (go
                           (let [poistetut-kohteet (filter (fn [kohde]
                                                             (and (:poistettu kohde)
                                                                  (:yhaid kohde)))
                                                           kohteet)
                                 tallenna-fn (fn [kohteet]
                                               (yllapitokohteet/kasittele-tallennettavat-kohteet!
                                                 kohteet
                                                 :paallystys
                                                 #(reset! paallystys-tiedot/yllapitokohteet %)
                                                 (fn [virhe]
                                                   (let [virheviestit (case (:status virhe)
                                                                        :validointiongelma (mapv (fn [virhe]
                                                                                                   (reduce-kv (fn [m k v]
                                                                                                                (assoc m k (distinct (flatten (vals (if (map? v)
                                                                                                                                                      v
                                                                                                                                                      (first v)))))))
                                                                                                              {} virhe))
                                                                                                 (:virheviesti virhe))
                                                                        :yha-virhe (mapv (fn [{:keys [selite kohteen-nimi]}]
                                                                                           {(keyword kohteen-nimi) [selite]})
                                                                                        (:virheviesti virhe)))]
                                                     (when (= :yha-virhe (:status virhe))
                                                       (reset! paallystys-tiedot/yllapitokohteet (:yllapitokohteet virhe)))
                                                     (paallystys-tiedot/virhe-modal {:virhe virheviestit})))
                                                 true false))]
                             ;; Ollaanko poistamassa kohdetta?
                             (if-not (empty? poistetut-kohteet)
                               (modal/nayta! {:otsikko (str "Olet poistamassa " (if (= 1 (count poistetut-kohteet))
                                                                                  "kohteen"
                                                                                  "kohteita"))
                                              :footer  [kohteen-poisto-modal-footer kohteet tallenna-fn]}
                                             [kohteen-poisto-modal poistetut-kohteet])
                               (tallenna-fn kohteet)))))
        kohteen-tallennus-onnistui (fn [_]
                                     (urakka/lukitse-urakan-yha-sidonta! (:id ur)))
        tallenna-muukohde (fn [kohteet]
                            (yllapitokohteet/kasittele-tallennettavat-kohteet!
                              kohteet
                              :paikkaus
                              #(reset! paallystys-tiedot/yllapitokohteet %) ;; TODO TESTI TÄLLE TALLENNUKSELLE (backend)
                              #(constantly nil)))]
    (hae-tietoja ur)
    (komp/kun-muuttuu (hae-tietoja ur))
    (komp/luo
     (komp/lippu paallystys-tiedot/paallystysilmoitukset-tai-kohteet-nakymassa?)
     (komp/sisaan-ulos #(add-watch paallystys-tiedot/yhan-paallystyskohteet :haetaanko-tr-osia-watch
                                   (fn [_ _ vanha-tila uusi-tila]
                                     (let [vanhat-tiet (into #{}
                                                             (map (juxt :tr-numero :tr-alkuosa :tr-loppuosa) vanha-tila))
                                           uudet-tiet (into #{}
                                                            (map (juxt :tr-numero :tr-alkuosa :tr-loppuosa) uusi-tila))
                                           mahdollisesti-haettavat-tiet (clj-set/difference (clj-set/union vanhat-tiet uudet-tiet)
                                                                                            (clj-set/intersection vanhat-tiet uudet-tiet))]
                                       (when-not (empty? mahdollisesti-haettavat-tiet)
                                         (go (doseq [tr-osoite mahdollisesti-haettavat-tiet]
                                               (let [[tr-numero tr-alkuosa tr-loppuosa] tr-osoite
                                                     jo-haetut-tien-tiedot (get @paallystys-tiedot/tr-osien-tiedot tr-numero)
                                                     [min-osa max-osa] (apply (juxt min max) (map :tr-osa jo-haetut-tien-tiedot))
                                                     pienin-tarvittava-osa (min min-osa tr-alkuosa)
                                                     suurin-tarvittava-osa (max max-osa tr-loppuosa)]
                                                 (when (not= [min-osa max-osa] [pienin-tarvittava-osa suurin-tarvittava-osa])
                                                   (swap! paallystys-tiedot/tr-osien-tiedot
                                                          assoc tr-numero (<! (k/post! :hae-tr-tiedot {:tr-numero tr-numero
                                                                                                       :tr-alkuosa pienin-tarvittava-osa
                                                                                                       :tr-loppuosa suurin-tarvittava-osa})))))))))))
                       #(remove-watch paallystys-tiedot/yhan-paallystyskohteet :haetaanko-tr-osia-watch))
      (fn [ur]
        [:div.paallystyskohteet
         [kartta/kartan-paikka]

         [valinnat/urakan-vuosi ur]
         [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
         [valinnat/tienumero yllapito-tiedot/tienumero]

         [validointivirheet-modal]

         [yllapitokohteet-view/yllapitokohteet
          ur
          paallystys-tiedot/yhan-paallystyskohteet
          {:otsikko      "YHA:sta tuodut päällystyskohteet"
           :kohdetyyppi  :paallystys
           :yha-sidottu? true
           :tallenna     (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
                           tallenna-kohde)
           :kun-onnistuu kohteen-tallennus-onnistui}]

         [yllapitokohteet-view/yllapitokohteet
          ur
          paallystys-tiedot/harjan-paikkauskohteet
          {:otsikko      "Harjan paikkauskohteet ja muut kohteet"
           :kohdetyyppi  :paikkaus
           :yha-sidottu? false
           :tallenna     (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
                           tallenna-muukohde)}] ;; Paikakuskohteet eivät sisällä validointeja palvelinpäässä

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
