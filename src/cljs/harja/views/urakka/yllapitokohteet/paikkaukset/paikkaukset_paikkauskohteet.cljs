(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
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
            [harja.ui.valinnat :as valinnat]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.debug :as debug]
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
  [{:nimi "Kaikki"} {:nimi "Ehdotettu"} {:nimi "Hylätty"} {:nimi "Tilattu"} {:nimi "Valmis"}])

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
                 :leveys 4
                 :nimi :tyomenetelma}
                {:otsikko "Sijainti"
                 :leveys 3
                 :nimi :formatoitu-sijainti}
                {:otsikko "Aikataulu"
                 :leveys 2
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
                              paikkauskohteet)
        rivi-valittu #(= (:id (:lomake app)) (:id %))]
    ;; Riippuen vähän roolista, taulukossa on enemmän dataa tai vähemmän dataa.
    ;; Niinpä kavennetaan sitä hieman, jos siihen tulee vähemmän dataa, luettavuuden parantamiseksi
    [:div.col-xs-12.col-md-12.col-lg-12 #_{:style {:display "flex"
                                                   :justify-content "flex-start"}}
     [grid/grid
      (merge {:tunniste :id
              :tyhja "Ei tietoja"
              :rivin-luokka #(str "paikkauskohderivi" (when (rivi-valittu %) " valittu"))
              :rivi-klikattu (fn [kohde]
                               (do
                                 ;; Näytä valittu rivi kartalla
                                 (when (not (nil? (:sijainti kohde)))
                                   (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{(:id kohde)})
                                   (kartta-tiedot/keskita-kartta-alueeseen! (harja.geo/extent (:sijainti kohde)))
                                   )
                                 ;; avaa lomake, jos käyttäjällä on kirjoitusoikeudet
                                 (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet (:urakka-id kohde))
                                   (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-katselu}))))))
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
       #_ [napit/yleinen-ensisijainen "Näytä nappi DEBUG" #(harja.ui.viesti/nayta-toast! "Toast-notifiikaatio testi" :varoitus)]
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
       [napit/lataa "Lataa Excel-pohja" #(.open js/window "/excel/Paikkausehdotukset_pohja.xlsx" "_blank") {:luokka "napiton-nappi"}]
       [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]])]
   [:div.row [paikkauskohteet-taulukko e! app]]])

(defn- filtterit [e! app]
  (let [vuodet (urakan-vuodet (:alkupvm (-> @tila/tila :yleiset :urakka)) (:loppupvm (-> @tila/tila :yleiset :urakka)))
        valitut-tilat (:valitut-tilat app)
        valittu-vuosi (:valittu-vuosi app)
        valitut-elyt (:valitut-elyt app)
        valitut-tyomenetelmat (:valitut-tyomenetelmat app)
        valittavat-elyt (conj
                          (map (fn [h]
                                 (-> h
                                     (dissoc h :alue :type :liikennemuoto)
                                     (assoc :valittu? (or (some #(= (:id h) %) valitut-elyt) ;; Onko kyseinen ely valittu
                                                          false))))
                               @hal/vaylamuodon-hallintayksikot)
                          {:id 0 :nimi "Kaikki" :elynumero 0 :valittu? (some #(= 0 %) valitut-elyt)})
        valittavat-tyomenetelmat (map (fn [t]
                                        {:nimi t
                                         :valittu? (or (some #(= t %) valitut-tyomenetelmat) ;; Onko kyseinen työmenetelmä valittu
                                                       false)})
                                      t-paikkauskohteet/tyomenetelmat)
        valittavat-tilat (map (fn [t]
                                (assoc t :valittu? (or (some #(= (:nimi t) %) valitut-tilat) ;; Onko kyseinen tila valittu
                                                       false)))
                              paikkauskohteiden-tilat)]
    [:div.filtterit {:style {:padding "16px"}} ;; Osa tyyleistä jätetty inline, koska muuten kartta rendataan päälle.
     [:div.row
      [:div.col-xs-2
       [:span.alasvedon-otsikko-vayla "ELY"]
       [valinnat/checkbox-pudotusvalikko
        valittavat-elyt
        (fn [ely valittu?]
          (e! (t-paikkauskohteet/->FiltteriValitseEly ely valittu?)))
        [" ELY valittu" " ELYä valittu"]
        {:vayla-tyyli? true}]]
      [:div.col-xs-2
       [:span.alasvedon-otsikko-vayla "Tila"]
       [valinnat/checkbox-pudotusvalikko
        valittavat-tilat
        (fn [tila valittu?]
          (e! (t-paikkauskohteet/->FiltteriValitseTila tila valittu?)))
        [" Tila valittu" " Tilaa valittu"]
        {:vayla-tyyli? true}]]
      [:div.col-xs-2
       [:span.alasvedon-otsikko-vayla "Vuosi"]
       [yleiset/livi-pudotusvalikko
        {:valinta valittu-vuosi
         :vayla-tyyli? true
         :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
         :valitse-fn #(e! (t-paikkauskohteet/->FiltteriValitseVuosi %))}
        vuodet]]
      [:div.col-xs-4
       [:span.alasvedon-otsikko-vayla "Työmenetelmä"]
       [valinnat/checkbox-pudotusvalikko
        valittavat-tyomenetelmat
        (fn [tyomenetelma valittu?]
          (e! (t-paikkauskohteet/->FiltteriValitseTyomenetelma tyomenetelma valittu?)))
        [" Työmenetelmä valittu" " Työmentelmää valittu"]
        {:vayla-tyyli? true}]]
      #_[:div.col-xs-2 "hae"]
      ]]))

(defn- paikkauskohteet-sivu [e! app]
  [:div
   ;; Filtterit näytetään kaikille muille käyttäjille paitsi aluevastaaville eli, joiden rooli on ELY_Urakanvalvoja
   (when (not
           (contains? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)) "ELY_Urakanvalvoja"))
     [filtterit e! app])
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
