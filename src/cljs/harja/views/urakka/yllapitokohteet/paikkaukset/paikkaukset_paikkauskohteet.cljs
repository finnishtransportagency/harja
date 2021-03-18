(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.geo :as geo]
            [cljs-time.core :as time-core]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.fmt :as fmt]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.transit :as transit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.debug :as debug]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake :as paikkauskohdelomake]
            ))

(def paikkauskohteiden-tilat
  ["kaikki" "ehdotettu" "hylatty" "tilattu" "valmis"])

(defn- urakan-vuodet [alkupvm loppupvm]
  (when (and (not (nil? alkupvm)) (not (nil? loppupvm)))
    (mapv
      (fn [aika]
        (time-core/year (first aika)))
      (pvm/urakan-vuodet alkupvm loppupvm))))

(defn- paikkauskohteet-taulukko [e! app]
  (let [;_ (js/console.log "roolit" (pr-str (roolit/osapuoli @istunto/kayttaja)))
        ;_ (js/console.log "urakkaroolit" (pr-str (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))))
        ;_
        #_(js/console.log "voi kirjoittaa kohteisiin" (pr-str (oikeudet/voi-kirjoittaa?
                                                                oikeudet/urakat-paikkaukset-paikkauskohteet
                                                                (-> @tila/tila :yleiset :urakka :id)
                                                                @istunto/kayttaja)))
        ;_
        #_(js/console.log "voi kirjoittaa kustannuksiin" (pr-str (oikeudet/voi-kirjoittaa?
                                                                   oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset
                                                                   (-> @tila/tila :yleiset :urakka :id)
                                                                   @istunto/kayttaja)))
        ;_
        #_(js/console.log "voi lukea kohteita" (pr-str (oikeudet/voi-lukea?
                                                         oikeudet/urakat-paikkaukset-paikkauskohteet
                                                         (-> @tila/tila :yleiset :urakka :id)
                                                         @istunto/kayttaja)))
        skeema [{:otsikko "Muokattu"
                 :leveys 2
                 :nimi :muokattu
                 :fmt pvm/pvm-aika-opt}
                {:otsikko "NRO"
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
                                               (= "hylatty" arvo) "tila-hylatty"
                                               :default "tila-ehdotettu"
                                               ))}]
                          [:span (str/capitalize arvo)]]])}
                {:otsikko "Menetelmä"
                 :leveys 2
                 :nimi :tyomenetelma}
                {:otsikko "Sijainti"
                 :leveys 4
                 :nimi :formatoitu-sijainti}
                {:otsikko "Aikataulu"
                 :leveys 4
                 :nimi :formatoitu-aikataulu
                 :fmt (fn [arvo]
                        [:span {:class (if (str/includes? arvo "arv")
                                         "prosessi-kesken"
                                         "")} arvo])}
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
                                  [:span
                                   [:span {:style {:padding-right "24px"}}
                                    (:urakoitsija rivi)]
                                   [napit/yleinen-toissijainen ""
                                    #(yllapito-yhteyshenkilot/nayta-paikkauskohteen-yhteyshenkilot-modal! (:urakka-id rivi))
                                    {:ikoni (ikonit/user)
                                     :luokka "btn-xs"}]])})
                ]
        paikkauskohteet (:paikkauskohteet app)
        yht-suunniteltu-hinta (reduce (fn [summa kohde]
                                        (+ summa (:suunniteltu-hinta kohde)))
                                      0
                                      paikkauskohteet)
        yht-tot-hinta (reduce (fn [summa kohde]
                                (+ summa (:toteutunut-hinta kohde)))
                              0
                              paikkauskohteet)]
    ;; Riippuen vähän roolista, taulukossa on enemmän dataa tai vähemmän dataa.
    ;; Niinpä kavennetaan sitä hieman, jos siihen tulee vähemmän dataa, luettavuuden parantamiseksi
    [:div.col-xs-12.col-md-12.col-lg-12 #_{:style {:display "flex"
                                                   :justify-content "flex-start"}}
     [grid/grid
      (merge {:tunniste :id
              :tyhja "Ei tietoja"
              :rivi-klikattu (fn [kohde]
                               (do
                                 ;(js/console.log "rivi-klikattu :: kohde" (pr-str kohde))
                                 ;; Näytä valittu rivi kartalla
                                 (when (not (nil? (:sijainti kohde)))
                                   ;(js/console.log "rivi-klikattu :: zoomataan" (pr-str (harja.geo/extent (:sijainti kohde))))
                                   (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{(:id kohde)})
                                   (kartta-tiedot/keskita-kartta-alueeseen! (harja.geo/extent (:sijainti kohde)))
                                   )
                                 ;; avaa lomake

                                 (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-katselu})))))
              }
             (when (> (count paikkauskohteet) 0)
               {:rivi-jalkeen-fn (fn [rivit]
                                   ^{:luokka "yhteenveto"}
                                   [{:teksti "Yht."}
                                    {:teksti (str (count paikkauskohteet) " kohdetta")}
                                    {:teksti ""}
                                    {:teksti ""}
                                    {:teksti ""}
                                    {:teksti ""}
                                    (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
                                      {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-suunniteltu-hinta)]})
                                    (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
                                      {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-tot-hinta)]})])}))
      skeema
      paikkauskohteet]]))

(defn kohteet [e! app]
  [:div.kohdelistaus
   [:div.row #_{:style {:display "flex"}} ;TODO: tähän class, mistä ja mikä?
    [:div.col-xs-12.col-md-4.col-lg-4 [:h2 (str (count (:paikkauskohteet app)) " paikkauskohdetta")]]
    (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
      [:div.col-xs-12.col-md-8.col-lg-8.inline-block {:style {:text-align "end"}}
       ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
       ;TODO: Napeista puuttuu myös kulmien pyöristys
       [napit/yleinen-ensisijainen "Näytä nappi DEBUG" #(harja.ui.viesti/nayta-toast! "Kohde Asdasads on luotu" :neutraali-ikoni)]
       [napit/lataa "Lataa Excel-pohja" #(js/console.log "Ladataan excel-pohja") {:luokka "napiton-nappi"}] ;TODO: Implementoi
       [:span.inline-block
        [:form {:style {:margin-left "auto"}
                :target "_blank" :method "POST"
                :action (komm/excel-url :paikkauskohteet-urakalle-excel)}
         [:input {:type "hidden" :name "parametrit"
                  :value (transit/clj->transit {:urakka-id (-> @tila/tila :yleiset :urakka :id)
                                                :tila (:valittu-tila app)
                                                :alkupvm (pvm/->pvm (str "1.1." (:valittu-vuosi app)))
                                                :loppupvm (pvm/->pvm (str "31.12." (:valittu-vuosi app)))
                                                :tyomenetelmat #{(:valittu-tyomenetelma app)}})}]
         [:button {:type "submit"
                   :class #{"nappi-ensisijainen napiton-nappi"}}
          [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Vie Exceliin"]]]]

       [liitteet/lataa-tiedosto
        (-> @tila/tila :yleiset :urakka :id)
        {:nappi-teksti "Tuo kohteet excelistä"
         :nappi-luokka "napiton-nappi"
         :url "lue-paikkauskohteet-excelista"
         :lataus-epaonnistui #(e! (t-paikkauskohteet/->TiedostoLadattu %))
         :tiedosto-ladattu #(e! (t-paikkauskohteet/->TiedostoLadattu %))}]
       [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]])]
   [:div.row [paikkauskohteet-taulukko e! app]]]
  )

(defn- filtterit [e! app]
  (let [vuodet (urakan-vuodet (:alkupvm (-> @tila/tila :yleiset :urakka)) (:loppupvm (-> @tila/tila :yleiset :urakka)))
        valittu-tila (:valittu-tila app)
        valittu-vuosi (:valittu-vuosi app)
        valittu-tyomenetelma (:valittu-tyomenetelma app)
        valittu-ely (:valittu-ely app)
        hallintayksikot (conj
                          (map (fn [h]
                                 (dissoc h :alue :type :liikennemuoto))
                               @hal/vaylamuodon-hallintayksikot)
                          {:id 0 :nimi "Kaikki" :elynumero 0})]
    [:div.filtterit {:style {:padding "16px"}} ;; Osa tyyleistä jätetty inline, koska muuten kartta rendataan päälle.
     [:div.row
      [:div.col-xs-2
       [:span.alasvedon-otsikko "ELY"]
       [yleiset/livi-pudotusvalikko {:valinta valittu-ely
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (t-paikkauskohteet/->FiltteriValitseEly %))
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
                                     :format-fn (fn [val]
                                                  (if (= (:nimi val) "Kaikki")
                                                    "Kaikki"
                                                    (str (:elynumero val) " " (:nimi val))))}
        hallintayksikot]]
      [:div.col-xs-2

       [:span.alasvedon-otsikko "Tila"]
       [yleiset/livi-pudotusvalikko {:valinta valittu-tila
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (t-paikkauskohteet/->FiltteriValitseTila %))
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
                                     :format-fn (fn [val]
                                                  (let [val (if (= "hylatty" val)
                                                              "hylätty"
                                                              val)
                                                        val (str/capitalize val)]
                                                    val))}
        paikkauskohteiden-tilat]]
      [:div.col-xs-2
       [:span.alasvedon-otsikko "Vuosi"]
       [yleiset/livi-pudotusvalikko
        {:valinta valittu-vuosi
         :vayla-tyyli? true
         :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
         :valitse-fn #(e! (t-paikkauskohteet/->FiltteriValitseVuosi %))}
        vuodet]]
      [:div.col-xs-2
       [:span.alasvedon-otsikko "Työmenetelmä"]
       [yleiset/livi-pudotusvalikko
        {:valinta valittu-tyomenetelma
         :vayla-tyyli? true
         :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
         :valitse-fn #(e! (t-paikkauskohteet/->FiltteriValitseTyomenetelma %))}
        t-paikkauskohteet/tyomenetelmat]]
      #_[:div.col-xs-2 "hae"]
      ]]))

(defn- paikkauskohteet-sivu [e! app]
  [:div
   [filtterit e! app]
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
