(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.yhteiset
  "Käyttöliittymän yhteisiä komponentteja, joita käytetään eri osioissa
   kustannussuunnitelman tarkastelussa ja muokkauksessa.
   Tämä sisältää esimerkiksi otsikkotiedot ja tallennusnapit."
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as k-tiedot]))


(defonce tallenna-painettu (atom false))
(defonce virheet-atom (atom {}))
(defonce grid-hankinnat-atom (atom [{}]))
(defonce grid-erillishankinnat-atom (atom [{}]))
(defonce grid-hoidonjohtopalkkiot-atom (atom [{}]))
(defonce grid-johto-ja-hallintokorvaukset-atom (atom [{}]))

;; Rajavuotta aiemmilla ei ole pysyviä muutoksia
(def rajavuosi 2025)

(defn otsikkotiedot [valittu-hoitokausi kustannussuunnitelma otsikko tarjouksen-maara
                     pysyvamuutos-maara suunniteltu-yhteensa suunniteltu-yhteensa-indeksikorjattu
                     {:keys [div1 div2 div3 div4] :as opts} valittu-vuosi]
  (let [urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        urakan-loppuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm))
        hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
        indeksikerroin (:indeksikerroin kustannussuunnitelma)
        indeksikerroin-str (:indeksikerroin-str kustannussuunnitelma)
        tarjous-pysyvat-yhteensa (+ tarjouksen-maara pysyvamuutos-maara)
        tarjous-pysyvat-yhteensa-indeksikorjattu (* tarjous-pysyvat-yhteensa indeksikerroin)]
    [:div
     [:div.row
      [:div.col-xs-12
       [:h2 otsikko]
       [:div.body-text {:style {:margin-top "-15px"}} (fmt/hoitokauden-jarjestysluku-ja-vuodet (pvm/vuosi (first valittu-hoitokausi)) hoitovuodet "Hoitovuosi")]]]

     [:div.row {:style {:padding-top "1rem"}}
      (when div1
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Tarjouksen määrä"]
         [:div.body-text (if tarjouksen-maara (fmt/euro-opt true tarjouksen-maara) "0,00 €")]])

      ;; -24 vuodesta eteenpäin näytetään pysyvät muutokset, jos tämä osio aiotaan näyttää
      (when (and div2 (>= valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Pysyvät muutokset"]
         (if (istunto/ominaisuus-kaytossa? :mhu-muutokset)
           [:div.body-text (if pysyvamuutos-maara (fmt/euro-opt true pysyvamuutos-maara) "Ei muutoksia")]
           [:div.body-text "Ei muutoksia"])
         [:div.body-text
          [yleiset/linkki "Siirry osioon"
           #(do
              (siirrin/siirry-elementin-id "pysyvat-muutokset-elementti" 500))]]])

      ;; -24 vuodesta eteenpäin näytetään tarjous + pysyvät muutokset, jos tämä osio aiotaan näyttää
      (when (and div3 (>= valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Yhteensä"]
         [:div.body-text (if tarjous-pysyvat-yhteensa (fmt/euro-opt true tarjous-pysyvat-yhteensa) "0,00 €")]])

      ;; -23 vuoteen asti näytetään yhteensä suunniteltu määrä
      (when (and div3 (< valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Suunniteltu määrä"]
         [:div.body-text (if suunniteltu-yhteensa (fmt/euro-opt true suunniteltu-yhteensa) "0,00 €")]])

      ;; -24 vuodesta eteenpäin näytetään indeksikorjattu määrä tarjouksen hinnalle, jos tämä osio aiotaan näyttää
      (when (and div4 (>= valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Indeksikorjattu"]
         [:div.body-text (if indeksikerroin (fmt/euro-opt true tarjous-pysyvat-yhteensa-indeksikorjattu) "Indeksilukua ei ole saatavilla")]
         (when indeksikerroin
           [:div.body-text
            (str "(" indeksikerroin-str " * " (if tarjous-pysyvat-yhteensa (fmt/euro-opt false tarjous-pysyvat-yhteensa) "0,00 €") " )")])])

      ;; -23 vuoteen asti näytetään indeksikorjattu määrä suunnitellulle summalle, koska tarjousihintoja ja pysyviä muutoksia ei ole ollut
      (when (and div4 (< valittu-vuosi rajavuosi))
        [:div.col-xs-12.col-md-3
         [:div.small-text.bold "Indeksikorjattu"]
         [:div.body-text (if indeksikerroin (fmt/euro-opt true suunniteltu-yhteensa-indeksikorjattu) "Indeksilukua ei ole saatavilla")]
         (when indeksikerroin
           [:div.body-text
            (str "(" indeksikerroin-str " * " (if suunniteltu-yhteensa (fmt/euro-opt false suunniteltu-yhteensa) "0,00 €") " )")])])]]))

(defn tallenna-painike-rivi [viimeisin-muokkaus viimeisin-muokkaaja tallennus-kesken?
                             tallenna-fn jaa-tasan-fn kopioi-tuleville-hoitovuosille-fn
                             tulevaisuudessa-arvoja? onko-muutoksia?]
  [:div {:style {:padding-top "1rem" :padding-right "1rem"}}
   [:div.painikkeet.text-right
    ;; Kopioi tuleville hoitovuosille.
    (when kopioi-tuleville-hoitovuosille-fn
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Kopioi tuleville hoitovuosille"
        (fn []
          (if tulevaisuudessa-arvoja?
            (varmista-kayttajalta/varmista-kayttajalta
              {:otsikko "Tulevilla hoitovuosilla on jo tietoja"
               :sisalto (str "Tulevilla hoitovuosilla on jo tietoja. Ylikirjoitetaanko tiedot? Ylikirjoitetut tiedot menetetään pysyvästi.")
               :hyvaksy "Ylikirjoita"
               :toiminto-fn #(do
                               (reset! tallenna-painettu false)
                               (kopioi-tuleville-hoitovuosille-fn))})
            (kopioi-tuleville-hoitovuosille-fn)))
        {:disabled tallennus-kesken?
         :luokka "ikoni-16"
         :ikoni (ikonit/action-copy)}]])
    (when jaa-tasan-fn
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Jaa tasan joka kuukaudelle"
        #(do
           (reset! tallenna-painettu false)
           (jaa-tasan-fn))
        {:disabled tallennus-kesken?}]])

    [:span {:style {:margin-left "1rem"}}
     [napit/yleinen-ensisijainen "Tallenna tiedot"
      #(do
         (reset! tallenna-painettu false)
         (tallenna-fn))
      {:disabled tallennus-kesken?}]]]

   [:div.painikkeet.text-right {:style {:margin-top "0.5rem"}}
    ;; Viimeisin muokkaaja
    [:div.grid-status-viestit
     (cond
       (and onko-muutoksia? viimeisin-muokkaus)
       [:<>
        [:div.status-viesti.tallennettu
         (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]
        [:div.status-viesti.tallentamatta
         "Tallentamattomia muutoksia"]]

       onko-muutoksia?
       [:div.status-viesti.tallentamatta
        "Tallentamattomia muutoksia"]

       viimeisin-muokkaus
       [:div.status-viesti.tallennettu
        (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]

       :else
       [:div.status-viesti.ei-muutoksia
        "Ei tallennettuja muutoksia"])]]])

(defn grid-perusasetukset [voi-muokata? tunniste]
  {:tyhja "Ei tietoja."
   :luokat ["matala-panel"]
   :muokkaa-aina voi-muokata?
   :voi-muokata? voi-muokata?
   :muokattava? (constantly voi-muokata?)
   :voi-poistaa? (constantly false)
   :voi-lisata? false
   :voi-kumota? false
   :piilota-toiminnot? false
   :tunniste tunniste
   :rivin-luokka (fn [_] "korkea")})
