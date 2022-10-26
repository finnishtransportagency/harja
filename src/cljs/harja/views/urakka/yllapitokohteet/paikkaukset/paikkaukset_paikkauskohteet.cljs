(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [cljs-time.core :as time-core]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
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
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen :as t-yhteinen]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake :as paikkauskohdelomake]))

(def paikkauskohteiden-tilat
  [{:nimi "Kaikki"} {:nimi "Ehdotettu"} {:nimi "Hylätty"} {:nimi "Tilattu"} {:nimi "Valmis"}])

(defn urakan-vuodet [alkupvm loppupvm]
  (when (and (not (nil? alkupvm)) (not (nil? loppupvm)))
    (mapv
      (fn [aika]
        (time-core/year (first aika)))
      (pvm/urakan-vuodet alkupvm loppupvm))))

(defn- paikkauskohteet-taulukko [e! {:keys [haku-kaynnissa?] :as app}]
  (let [urakkatyyppi (-> @tila/tila :yleiset :urakka :tyyppi)
        tyomenetelmat (get-in app [:valinnat :tyomenetelmat])
        nayta-hinnat? (and
                        (or (= urakkatyyppi :paallystys)
                            (and (or (= urakkatyyppi :hoito) (= urakkatyyppi :teiden-hoito))
                                 (not (:hae-aluekohtaiset-paikkauskohteet? app))))
                        (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
        skeema [
                (cond
                  ;; Tiemerkintäurakoitsijalle näytetään valmistusmipäivä, eikä muokkauspäivää
                  (= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
                  {:otsikko "Valmistuminen"
                   :leveys 1.7
                   :nimi :loppupvm-arvio}
                  ;; Tilaajalle näytetään ja päällysteurakalle näytetään muokkauspäivä. Mutta urakanvalvoja esiintyy myös
                  ;; päällystysurkoitsijana joten tarkistetaan myös urakkaroolit
                  ;; Aluekohtaisia paikkauskohteita hakiessa, eli hoitourakan urakanvalvojana, alueen muita kohteita katsellessa,
                  ;; ei näytetä muokkaustietoa.
                  (and (not (:hae-aluekohtaiset-paikkauskohteet? app))
                       (or (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
                             (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                             @istunto/kayttaja)
                           (and (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                                (t-paikkauskohteet/kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))))))
                  {:otsikko "Muokattu"
                   :leveys 1.7
                   :nimi :paivays
                   :fmt (fn [arvo]
                          [:span {:style {:color "#646464"}} (pvm/pvm-aika-opt arvo)])}
                  ;; Defaulttina eli esim alueurakoitsijalle ei näytetä koko kenttää
                  :else nil
                  )
                {:otsikko "NRO"
                 :leveys 1.5
                 :nimi :ulkoinen-id}
                {:otsikko "Nimi"
                 :leveys 4
                 :nimi :nimi}
                {:otsikko "Tila"
                 :leveys 1.7
                 :nimi :paikkauskohteen-tila
                 :fmt (fn [arvo]
                        [yleiset/tila-indikaattori arvo {:fmt-fn paikkaus/fmt-tila}])
                 :solun-luokka (fn [arvo _] (str arvo "-bg"))}
                {:otsikko "Menetelmä"
                 :leveys 4
                 :nimi :tyomenetelma
                 :fmt #(paikkaus/tyomenetelma-id->nimi % tyomenetelmat)
                 :solun-luokka (fn [arvo _]
                                 ;; On olemassa niin pitkiä työmenetelmiä, että ne eivät mahdu soluun
                                 ;; Joten lisätään näille pitkille menetelmille class joka saa ne mahtumaan
                                 ;; soluun rivitettynä
                                 (when (> (count (paikkaus/tyomenetelma-id->nimi arvo tyomenetelmat)) 40)
                                   "grid-solulle-2-rivia"))}
                {:otsikko "Aikataulu"
                 :leveys 2.3
                 :nimi :formatoitu-aikataulu
                 :fmt (fn [arvo]
                        [:span {:class (if (str/includes? arvo "arv")
                                         "prosessi-kesken"
                                         "")} arvo])}
                {:otsikko "Sijainti"
                 :leveys 2.5
                 :nimi :formatoitu-sijainti}
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
                ;; Alueurakoitsijat ja tiemerkkarit näkevät listassa muiden urakoiden tietoja
                ;; Niimpä varmistetaan, että käyttäjällä on kustannusoikeudet paikkauskohteisiin
                (when nayta-hinnat?
                  {:otsikko "Suun. hinta"
                   :leveys 1.7
                   :nimi :suunniteltu-hinta
                   :fmt fmt/euro-opt
                   :tasaa :oikea})
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
                (when nayta-hinnat?
                  {:otsikko "Tot. hinta"
                   :leveys 1.7
                   :nimi :toteutunut-hinta
                   :fmt fmt/euro-opt
                   :tasaa :oikea})
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin näytetään yhteystiedot
                (when (not nayta-hinnat?)
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
        rivi-valittu #(= (:id (:lomake app)) (:id %))
        aluekohtaisissa? (:hae-aluekohtaiset-paikkauskohteet? app)]
    ;; Riippuen vähän roolista, taulukossa on enemmän dataa tai vähemmän dataa.
    ;; Niinpä kavennetaan sitä hieman, jos siihen tulee vähemmän dataa, luettavuuden parantamiseksi
    [:div.col-xs-12.col-md-12.col-lg-12 #_{:style {:display "flex"
                                                   :justify-content "flex-start"}}
     [grid/grid
      (merge {:tunniste :id
              :tyhja "Ei tietoja"
              :rivin-luokka #(str "paikkauskohderivi" (when (rivi-valittu %) " valittu"))
              :rivi-klikattu (fn [kohde]
                               (let [tilattu? (= "tilattu" (:paikkauskohteen-tila kohde))
                                     valmis? (= "valmis" (:paikkauskohteen-tila kohde))
                                     kustannukset-kirjattu? (:toteutunut-hinta kohde)
                                     kayttajaroolit (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                                     urakoitsija? (t-paikkauskohteet/kayttaja-on-urakoitsija? kayttajaroolit)
                                     tilaaja? (roolit/kayttaja-on-laajasti-ottaen-tilaaja? kayttajaroolit @istunto/kayttaja)
                                     oikeudet-kustannuksiin? (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))]
                                 (do
                                   ;; Näytä valittu rivi kartalla
                                   (if (not (nil? (:sijainti kohde)))
                                     ;; Jos sijainti on annettu, zoomaa valitulle reitille
                                     (let [alue (harja.geo/extent (:sijainti kohde))]
                                       (do
                                         (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{(:id kohde)})
                                         (js/setTimeout #(kartta-tiedot/keskita-kartta-alueeseen! alue) 200)))
                                     ;; Muussa tapauksessa poista valittu reitti kartalta (zoomaa kauemmaksi)
                                     (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{}))

                                   ;; Avaa lomake, jos käyttäjä on tilaaja tai urakoitsija
                                   ;; Käyttäjällä ei ole välttämättä muokkaus oikeuksia, mutta ne tarkistetaan erikseen myöhemmin
                                   (when (and (not aluekohtaisissa?)
                                              (or tilaaja?
                                                  ;; Päällystysurakoitsijat pääsee näkemään tarkempaa dataa
                                                  ;; Mikäli heillä on oikeudet kustannuksiin
                                                  (and (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                                                       urakoitsija?
                                                       oikeudet-kustannuksiin?)))

                                     (cond
                                       ;; Tilattu kohde avataan urakoitsijalle valmiiksi raportoinnin muokkaustilassa
                                       (and urakoitsija? tilattu?)
                                       (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-muokkaus})))
                                       ;; Kohteen ollessa valmis, mutta kustannuksia ei ole kirjattu, kohde avataan muokkaustilassa
                                       (and urakoitsija? valmis? (not kustannukset-kirjattu?))
                                       (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-muokkaus})))
                                       ;; Muussa tapauksessa kohde avatan lukutilassa
                                       :else
                                       (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-katselu}))))))))
              :otsikkorivi-klikattu (fn [opts]
                                      (e! (t-paikkauskohteet/->JarjestaPaikkauskohteet (:nimi opts))))}
             (when (> (count paikkauskohteet) 0)
               {:rivi-jalkeen-fn (fn [rivit]
                                   ^{:luokka "yhteenveto"}
                                   [{:teksti "Yht."}
                                    {:teksti ""}
                                    {:teksti (str (count paikkauskohteet) " kohdetta")}
                                    (cond
                                      ;; Tiemerkintäurakoitsijalle näytetään valmistusmipäivä, eikä muokkauspäivää
                                      ;; Joten yhteenvetoriville tyhjä column
                                      (= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
                                      {:teksti ""}
                                      ;; Päällysteurakalle näytetään muokkauspäivä. Mutta urakanvalvoja esiintyy myös
                                      ;; päällystysurkoitsijana joten tarkistetaan myös urakkaroolit
                                      ;; Joten yhteenvetoriville tyhjä column
                                      (or (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
                                            (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                                            @istunto/kayttaja)
                                          (and (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                                               (t-paikkauskohteet/kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)))))
                                      {:teksti ""}
                                      ;; Defaulttina eli esim alueurakoitsijalle ei näytetä koko kenttää
                                      ;; Joten ei tyhjää riviä
                                      :else nil
                                      )
                                    {:teksti ""}
                                    {:teksti ""}
                                    {:teksti ""}
                                    (when nayta-hinnat?
                                      {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-suunniteltu-hinta)]})
                                    (when nayta-hinnat?
                                      {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-tot-hinta)]})])}))
      skeema
      (if haku-kaynnissa? 
        [] 
        paikkauskohteet)]
     (when haku-kaynnissa? 
       [:div.row.col-xs-12 {:style {:text-align "center"}} 
        [yleiset/ajax-loader "Haku käynnissä, odota hetki"]])]))

(defn kohteet [e! app]
  (let [loytyi-kohteita? (> (count (:paikkauskohteet app)) 0)
        piilota-napit? (:hae-aluekohtaiset-paikkauskohteet? app)
        haku-kaynnissa? (true? (:haku-kaynnissa? app))]
    [:div.kohdelistaus
     (when (and (not haku-kaynnissa?) 
                (not loytyi-kohteita?))
       [:div.row.col-xs-12 [:h2 "Ei paikkauskohteita valituilla rajauksilla."]])
     (when-not piilota-napit?
       [:div.flex-row.tasaa-alas
        (when loytyi-kohteita?
          [:div.col-mimic
           [:h2 (str (count (:paikkauskohteet app)) " paikkauskohdetta")]])
        (when (and
                (not= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta) ;; Tiemerkintäurakoitsijalle ei näytetä nappeja
                (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
          [:div.col-mimic
           (when loytyi-kohteita?
             {:style {:text-align "end"}})
           (when loytyi-kohteita?
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
                         :class #{"nappi-toissijainen napiton-nappi"}}
                [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Vie Exceliin"]]]])

           [liitteet/lataa-tiedosto
            {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
            {:nappi-teksti "Tuo kohteet excelistä"
             :nappi-luokka "napiton-nappi"
             :url "lue-paikkauskohteet-excelista"
             :lataus-epaonnistui #(e! (t-paikkauskohteet/->TiedostoLadattu %))
             :tiedosto-ladattu #(e! (t-paikkauskohteet/->TiedostoLadattu %))}]
           [yleiset/tiedoston-lataus-linkki
            "Lataa Excel-pohja"
            (str (when-not (komm/kehitysymparistossa?) "/harja") "/excel/harja_paikkauskohteet_pohja.xlsx")]
           [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde})) {:paksu? true}]])])
     (if loytyi-kohteita?
       [:div.row [paikkauskohteet-taulukko e! app]]
       (when haku-kaynnissa?
       [:div.row.col-xs-12 {:style {:text-align "center"}}
        [yleiset/ajax-loader "Haku käynnissä, odota hetki"]]))]))

(defn- filtterit [e! app]
  (let [haku-fn (fn [] (e! (t-paikkauskohteet/->HaePaikkauskohteet)))]
    (fn [e! app]
      (let [vuodet (urakan-vuodet (:alkupvm (-> @tila/tila :yleiset :urakka)) (:loppupvm (-> @tila/tila :yleiset :urakka)))
            tyomenetelmat (get-in app [:valinnat :tyomenetelmat])
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
                                            {:nimi (or (::paikkaus/tyomenetelma-nimi t) t)
                                             :id (::paikkaus/tyomenetelma-id t)
                                             :valittu? (or (some #(or (= t %)
                                                                      (= (::paikkaus/tyomenetelma-id t) %)) valitut-tyomenetelmat) ;; Onko kyseinen työmenetelmä valittu
                                                           false)})
                                          (into ["Kaikki"] tyomenetelmat))
            valittavat-tilat (map (fn [t]
                                    (assoc t :valittu? (or (some #(= (:nimi t) %) valitut-tilat) ;; Onko kyseinen tila valittu
                                                           false)))
                                  paikkauskohteiden-tilat)]
        [:div.flex-row.alkuun.filtterit {:style {:padding "16px"}} ;; Osa tyyleistä jätetty inline, koska muuten kartta rendataan päälle.
         ;; Tiemerkintäurakalle ja hoito ei haluta näyttää elyrajauksia.

         (when (and
                (not= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
                (not= (-> @tila/tila :yleiset :urakka :tyyppi) :hoito))
           [:div.col-xs-2
            [:label {:class "alasvedon-otsikko-vayla" :for "filtteri-ely"} "ELY"]
            [valinnat/checkbox-pudotusvalikko
             valittavat-elyt
             (fn [ely valittu?]
               (e! (t-paikkauskohteet/->FiltteriValitseEly ely valittu?)))
             [" ELY valittu" " ELYä valittu"]
             {:vayla-tyyli? true}]])
         [:div.col-xs-2
          [:label.alasvedon-otsikko-vayla "Tila"]
          [valinnat/checkbox-pudotusvalikko
           valittavat-tilat
           (fn [tila valittu?]
             (e! (t-paikkauskohteet/->FiltteriValitseTila tila valittu?)))
           [" Tila valittu" " Tilaa valittu"]
           {:vayla-tyyli? true}]]
         [:div.col-xs-2
          [:label {:class "alasvedon-otsikko-vayla" :for "filtteri-vuosi"} "Vuosi"]
          [yleiset/livi-pudotusvalikko
           {:valinta valittu-vuosi
            :vayla-tyyli? true
            :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
            :valitse-fn #(e! (t-paikkauskohteet/->FiltteriValitseVuosi %))}
           vuodet]]
         [:div.col-xs-4
          [:label.alasvedon-otsikko-vayla "Työmenetelmä"]
          [valinnat/checkbox-pudotusvalikko
           valittavat-tyomenetelmat
           (fn [tyomenetelma valittu?]
             (e! (t-paikkauskohteet/->FiltteriValitseTyomenetelma tyomenetelma valittu?)))
           [" Työmenetelmä valittu" " Työmenetelmää valittu"]
           {:vayla-tyyli? true}]]
         [:span {:style {:align-self "flex-end"}}  
          [napit/yleinen-ensisijainen "Hae kohteita" haku-fn {:luokka "nappi-korkeus-36"}]]
         #_ [kartta/piilota-tai-nayta-kartta-nappula {:luokka #{"oikealle"}}]]))))

(defn- paikkauskohteet-sivu [e! app]
  [:div
   [filtterit e! app]
   [kartta/kartan-paikka]
   #_ [debug/debug app]
   (when (:lomake app)
     [paikkauskohdelomake/paikkauslomake e! app])
   [kohteet e! app]])

(defn wrap-paikkauskohteet [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do

                         (kartta-tasot/taso-pois! :paikkaukset-toteumat)
                         (kartta-tasot/taso-pois! :organisaatio)
                         (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                         (e! (t-paikkauskohteet/->HaePaikkauskohteet))
                         (when (empty? (get-in app [:valinnat :tyomenetelmat])) (e! (t-yhteinen/->HaeTyomenetelmat)))
                         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? true)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M))
                      #(do
                         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                         (e! (t-paikkauskohteet/->SuljeLomake))))
    (fn [e! app]
      [:div.row
       [paikkauskohteet-sivu e! app]])))

(defn paikkauskohteet [e! app-state]
  (swap! tila/paikkauskohteet assoc :hae-aluekohtaiset-paikkauskohteet? false)
  (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{})
  [wrap-paikkauskohteet e! app-state])

;; Hoitourakoille voidaan näyttää joko alue-tai urakkakohtaiset paikkauskohteet, joten erottelu täytyy tehdä frontissa.
;; Tämän komponentin ainoa ero on, että paikkauskohteita hakiessa backendille läheteään lippu, jolla tiedetään,
;; kumpia paikkauskohteita halutaan hakea.
(defn aluekohtaiset-paikkauskohteet [e! app-state]
  (swap! tila/paikkauskohteet assoc :hae-aluekohtaiset-paikkauskohteet? true)
  (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{})
  [wrap-paikkauskohteet e! app-state])

