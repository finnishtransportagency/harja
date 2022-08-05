(ns harja.views.urakka.paallystyskohteet
  "Päällystyskohteet"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.set :as clj-set]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.yleiset :refer [ajax-loader vihje-elementti ajax-loader-pieni] :as yleiset]
            [harja.tiedot.urakka.paallystys :as paallystys-tiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.views.urakka.paallystys-muut-kustannukset :as muut-kustannukset-view]
            [harja.ui.komponentti :as komp]
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
            [harja.ui.ikonit :as ikonit]
            [harja.transit :as transit]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.viesti :as viesti])
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

(defn kohteen-poisto-modal-footer [kohteet tallenna-fn vastaus-status]
  [:div
   [napit/tallenna "Tallenna" (comp modal/piilota!
                                    (partial tallenna-fn kohteet))]
   [napit/peruuta "Peruuta" #(do (modal/piilota!)
                                 (go (>! vastaus-status {:status :ei-paiviteta})))]])

(def nayta-kustannusexcelin-tuonti-alkaen-vuodesta 2022)

(defn excel-toiminnot [tiedot]
  [:div.paallystyskustannusten-toiminnot-container
   [:div.paallystyskustannusten-toiminnot
    [:span.inline-block
     [:form {:style {:margin-left "auto"}
             :target "_blank" :method "POST"
             :action (k/excel-url :paallystyskohteet-excel)}
      [:input {:type "hidden" :name "parametrit"
               :value (transit/clj->transit tiedot)}]
      [:button {:type "submit"
                :class #{"nappi-toissijainen napiton-nappi"}}
       [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Lataa kustannus-Excel"]]]]
    (when (>= (:vuosi tiedot) nayta-kustannusexcelin-tuonti-alkaen-vuodesta)
      [liitteet/lataa-tiedosto
       {:urakka-id (-> @tila/tila :yleiset :urakka :id)
        :vuosi (:vuosi tiedot)}
       {:nappi-teksti "Tuo kustannukset excelistä"
        :nappi-luokka "napiton-nappi"
        :url "tuo-paallystyskustannukset-excelista"
        :lataus-epaonnistui #(viesti/nayta! "Toiminto epäonnistui." :danger)
        :tiedosto-ladattu #(do
                             (paallystys-tiedot/paivita-yllapitokohteet!)
                             (viesti/nayta! "Kustannukset päivitetty." :success))}])]])

(defn paallystyskohteet [ur]
  (let [tallennus-gif? (atom false)
        hae-tietoja (fn [urakan-tiedot]
                      (go (if-let [ch (indeksit/hae-paallystysurakan-indeksitiedot (:id urakan-tiedot))]
                            (reset! urakka/paallystysurakan-indeksitiedot (<! ch)))))
        tallenna-kohde (fn [kohteet]
                         (go
                           (let [poistetut-kohteet (filter (fn [kohde]
                                                             (and (:poistettu kohde)
                                                                  (:yhaid kohde)))
                                                           kohteet)
                                 ;; Jos kohteita poistetaan, halutaan yha-virheen sattuessa nollata muokkaustiedot tallennuksen
                                 ;; jälkeen. Se tieto annetaan tuohon kanavaan, jota perus grid käyttää.
                                 vastaus-status (chan)
                                 tallenna-fn (fn [kohteet]
                                               (reset! tallennus-gif? true)
                                               (yllapitokohteet/kasittele-tallennettavat-kohteet!
                                                 kohteet
                                                 :paallystys
                                                 #(do (reset! paallystys-tiedot/yllapitokohteet %)
                                                      (reset! tallennus-gif? false)
                                                      (go (>! vastaus-status {:status :ok})))
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
                                                     (paallystys-tiedot/virhe-modal {:virhe virheviestit} "Kohteen tallennus epäonnistui!")
                                                     (reset! tallennus-gif? false)
                                                     (go (>! vastaus-status virhe))))
                                                 true false))]
                             ;; Ollaanko poistamassa kohdetta?
                             (if-not (empty? poistetut-kohteet)
                               (do (modal/nayta! {:otsikko (str "Olet poistamassa " (if (= 1 (count poistetut-kohteet))
                                                                                      "kohteen"
                                                                                      "kohteita"))
                                                  :footer [kohteen-poisto-modal-footer kohteet tallenna-fn vastaus-status]}
                                                 [kohteen-poisto-modal poistetut-kohteet])
                                   (<! vastaus-status))
                               (tallenna-fn kohteet)))))
        kohteen-tallennus-onnistui (fn [_]
                                     (urakka/lukitse-urakan-yha-sidonta! (:id ur)))
        tallenna-muukohde (fn [kohteet]
                            (yllapitokohteet/kasittele-tallennettavat-kohteet!
                              kohteet
                              :paikkaus
                              #(reset! paallystys-tiedot/yllapitokohteet %) ;; TODO TESTI TÄLLE TALLENNUKSELLE (backend)
                              #(constantly nil)))
        yha-kohteet-otsikko (fn [vuosi]
                              (if (< vuosi 2020)
                                "YHA:sta tuodut päällystyskohteet (kaistatiedot edeltävät kaistauudistusta)"
                                "YHA:sta tuodut päällystyskohteet"))]
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
       (let [vuosi @urakka/valittu-urakan-vuosi
             [sopimus-id _] @urakka/valittu-sopimusnumero]
         [:div.paallystyskohteet
          [kartta/kartan-paikka]

          [valinnat/urakan-vuosi ur {:vayla-tyyli? true}]
          [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
          [valinnat/tienumero yllapito-tiedot/tienumero nil {:otsikon-luokka "alasvedon-otsikko-vayla"}]

          [validointivirheet-modal]
          [excel-toiminnot {:urakka-id (:id ur)
                            :sopimus-id sopimus-id
                            :vuosi vuosi
                            :vain-yha-kohteet? true}]
          [yllapitokohteet-view/yllapitokohteet
           ur
           paallystys-tiedot/yhan-paallystyskohteet
           {:otsikko (if @tallennus-gif?
                       [ajax-loader-pieni (yha-kohteet-otsikko vuosi)]
                       (yha-kohteet-otsikko vuosi))
            :kohdetyyppi :paallystys
            :yha-sidottu? true :valittu-vuosi vuosi
            :piilota-tallennus? (when (< vuosi 2020) true) ;; 2020 kaistamuutosta edeltäviä kohteita ei saa enää muokata.
            :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
                        tallenna-kohde)
            :kun-onnistuu kohteen-tallennus-onnistui}]

          [yllapitokohteet-view/yllapitokohteet
           ur
           paallystys-tiedot/muut-kuin-yha-kohteet
           {:otsikko (if (< vuosi 2020)
                       "Muut kohteet (kaistatiedot edeltävät kaistauudistusta)"
                       "Muut kohteet")
            :kohdetyyppi :paikkaus
            :yha-sidottu? false :valittu-vuosi vuosi
            :piilota-tallennus? (when (< vuosi 2020) true) ;; 2020 kaistamuutosta edeltäviä kohteita ei saa enää muokata.
            :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
                        tallenna-muukohde)}] ;; Paikakuskohteet eivät sisällä validointeja palvelinpäässä

          [muut-kustannukset-view/muut-kustannukset ur]

          [yllapitokohteet-view/yllapitokohteet-yhteensa
           paallystys-tiedot/kaikki-kohteet {:nakyma :paallystys
                                             :valittu-vuosi vuosi}]

          [vihje-elementti [:span
                            [:span "Huomioi etumerkki hinnanmuutoksissa. Ennustettuja määriä sisältävät kentät on värjätty "]
                            [:span.grid-solu-ennustettu "sinisellä"]
                            [:span "."]]]
          [indeksitiedot]

          [:div.kohdeluettelon-paivitys
           [yha/paivita-kohdeluettelo ur oikeudet/urakat-kohdeluettelo-paallystyskohteet]
           [yha/kohdeluettelo-paivitetty ur]]])))))
