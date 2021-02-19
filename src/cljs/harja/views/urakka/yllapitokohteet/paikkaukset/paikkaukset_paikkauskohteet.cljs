(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.geo :as geo]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake :as paikkauskohdelomake]
            ))

(defn- paikkauskohteet-taulukko [e! app]
  (let [skeema [{:otsikko "NRO"
                 :leveys 2
                 :nimi :nro}
                {:otsikko "Nimi"
                 :leveys 4
                 :nimi :nimi}
                {:otsikko "Tila"
                 :leveys 2
                 :nimi :paikkauskohteen-tila
                 :fmt (fn [arvo]
                        [:div {:class (str arvo "-bg")}
                         [:div
                          [:div {:class (str "circle "
                                             (cond
                                               (= "tilattu" arvo) "tila-tilattu"
                                               (= "ehdotettu" arvo) "tila-ehdotettu"
                                               (= "valmis" arvo) "tila-valmis"
                                               :default "tila-ehdotettu"
                                               ))}]
                          [:span  (str/capitalize arvo)]]])}
                {:otsikko "Menetelmä"
                 :leveys 2
                 :nimi :tyomenetelma}
                {:otsikko "Sijainti"
                 :leveys 4
                 :nimi :formatoitu-sijainti}
                {:otsikko "Aikataulu"
                 :leveys 4
                 :nimi :formatoitu-aikataulu}
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
                (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
                  {:otsikko "Suun. hinta"
                   :leveys 2
                   :nimi :suunniteltu-hinta
                   :fmt fmt/euro-opt
                   :tasaa :oikea})
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
                (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
                  {:otsikko "Tot. hinta"
                   :leveys 2
                   :nimi :toteutunut-hinta
                   :fmt fmt/euro-opt
                   :tasaa :oikea})
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin näytetään yhteystiedot
                (when (false? (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
                  {:otsikko "Yh\u00ADte\u00ADys\u00ADtie\u00ADdot"
                   :leveys 3
                   :nimi :yhteystiedot
                   :tasaa :keskita
                   :tyyppi :komponentti
                   :komponentti (fn [rivi]
                                  [napit/yleinen-toissijainen ""
                                   #(yllapito-yhteyshenkilot/nayta-paikkauskohteen-yhteyshenkilot-modal! (:urakka-id rivi))
                                   {:ikoni (ikonit/user)
                                    :luokka "btn-xs"}])})
                ]
        paikkauskohteet (:paikkauskohteet app)]
    ;; Riippuen vähän roolista, taulukossa on enemmän dataa tai vähemmän dataa.
    ;; Niinpä kavennetaan sitä hieman, jos siihen tulee vähemmän dataa, luettavuuden parantamiseksi
    [:div.col-xs-12.col-md-12.col-lg-12 #_{:style {:display "flex"
                                                  :justify-content "flex-start"}}
     [grid/grid
      {:tunniste :id
       :tyhja "Ei tietoja"
       :rivi-klikattu (fn [kohde]
                        (do
                          ;(js/console.log "rivi-klikattu :: kohde" (pr-str kohde))
                          ;; Näytä valittu rivi kartalla
                          (when (not (nil? (:sijainti kohde)))
                            (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{(:id kohde)})
                            (kartta-tiedot/keskita-kartta-alueeseen! (harja.geo/extent (:sijainti kohde)))
                            )
                          ;; avaa lomake

                          (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-katselu})))))
       :rivi-jalkeen-fn (fn [rivit]
                          ^{:luokka "yhteenveto"}
                          [{:teksti "Yht."}
                           {:teksti (str (count paikkauskohteet) " kohdetta")}
                           {:teksti ""}
                           {:teksti ""}
                           {:teksti ""}
                           {:teksti ""}
                           {:teksti ""}
                           {:teksti ""}])}
      skeema
      paikkauskohteet]]))

(defn kohteet [e! app]
  [:div
   [:div.row #_{:style {:display "flex"}} ;TODO: tähän class, mistä ja mikä?
    [:div.col-xs-12.col-md-4.col-lg-4 [:h2 (str (count (:paikkauskohteet app)) " paikkauskohdetta")]]
    [:div.col-xs-12.col-md-8.col-lg-8.pull-right
     ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
     ;TODO: Napeista puuttuu myös kulmien pyöristys
     [napit/lataa "Lataa Excel-pohja" #(js/console.log "Ladataan excel-pohja") {:luokka "napiton-nappi"}] ;TODO: Implementoi
     [napit/laheta "Vie Exceliin" #(js/console.log "Viedään exceliin") {:luokka "napiton-nappi"}] ;TODO: Implementoi
     [napit/uusi "Tuo kohteet excelistä" #(js/console.log "Tuodaan Excelistä") {:luokka "napiton-nappi"}] ;TODO: Implementoi
     [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]]]
   [:div.row [paikkauskohteet-taulukko e! app]]]
  )

(defn- paikkauskohteet-sivu [e! app]
  [:div
   [kartta/kartan-paikka]
   [debug/debug app]
   (when (:lomake app)
     [paikkauskohdelomake/paikkauslomake e! (:lomake app)])
   [kohteet e! app]])

(defn paikkauskohteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do
                         (kartta-tasot/taso-pois! :paikkaukset-toteumat)
                         (kartta-tasot/taso-paalle! :organisaatio)
                         (e! (t-paikkauskohteet/->HaePaikkauskohteet))
                         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? true))
                      #(do
                         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? false)))
    (fn [e! app]
      [:div.row
       [paikkauskohteet-sivu e! app]])))

(defn paikkauskohteet [ur]
  (komp/luo
    (komp/sisaan #(do
                    (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{})
                    (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                    (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [_]
      [tuck/tuck tila/paikkauskohteet paikkauskohteet*])))
