(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [cljs-time.core :as time-core]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tiedot.istunto :as istunto]
            [harja.fmt :as fmt]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.transit :as transit]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.leijuke :as leijuke]
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
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake :as paikkauskohdelomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-apurit :as paikkaukset-apurit]))

(def paikkauskohteiden-tilat
  [{:nimi "Kaikki"} {:nimi "Ehdotettu"} {:nimi "Hylätty"} {:nimi "Tilattu"} {:nimi "Valmis"}])

(defn urakan-vuodet [alkupvm loppupvm]
  (when (and (not (nil? alkupvm)) (not (nil? loppupvm)))
    (mapv
      (fn [aika]
        (time-core/year (first aika)))
      (pvm/urakan-vuodet alkupvm loppupvm))))

(defn- tiemerkinta-tila-vihje [nakyvissa? urakkatyyppi toggle-fn]
  (let [sisalto
        [:div
         [:p.body-caption (if (= urakkatyyppi :tiemerkinta)
                            "Tiemerkinnän tilan voi asettaa, kun paikkausten tila on “Valmis”."
                            (str
                              "Jos kohteelle on ilmoitettu tuhoutunut tiemerkintä, näkyy se tässä käsittelemättömänä. "
                              "Tiemerkinnän tila asetetaan tämän jälkeen tiemerkintäurakassa."))]]]

    [:div.tiemerkinta-vihje-leijuke {:style {:display (if nakyvissa? "block" "none")}}
     [leijuke/avattava-ulkoinen-vihje
      {:otsikko ""}
      sisalto
      nakyvissa?
      toggle-fn]]))

(defn luo-taulukon-skeema [e! {:keys [tilaustila-aktiivinen? hae-aluekohtaiset-paikkauskohteet? valitut-tilattavat-kohteet
                                      haku-kaynnissa?] :as app}]
  (let [urakkatyyppi (-> @tila/tila :yleiset :urakka :tyyppi)
        tyomenetelmat (get-in app [:valinnat :tyomenetelmat])
        nayta-hinnat? (and
                        (or (= urakkatyyppi :paallystys)
                          (and (or (= urakkatyyppi :hoito) (= urakkatyyppi :teiden-hoito))
                            (not hae-aluekohtaiset-paikkauskohteet?)))
                        (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
        kaikki-valittu? (= (count valitut-tilattavat-kohteet) (count (filter #(= "ehdotettu" (:paikkauskohteen-tila %)) (:paikkauskohteet app))))]
    [(when tilaustila-aktiivinen?
       {:otsikko-komp (fn []
                        [kentat/raksiboksi {:disabled? false
                                            :toiminto #(e! (t-paikkauskohteet/->ValitseKaikkiPaikkauskohteet (-> % .-target .-checked)))}
                         kaikki-valittu?])
        :leveys 0.6
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       [kentat/raksiboksi {:disabled? (not= "ehdotettu" (:paikkauskohteen-tila rivi))
                                           :toiminto #(e! (t-paikkauskohteet/->ValitsePaikkauskohde rivi (-> % .-target .-checked)))}
                        (boolean (some #(= % (:id rivi)) valitut-tilattavat-kohteet))])})
     (cond
       ;; Tiemerkintäurakoitsijalle näytetään valmistusmipäivä, eikä muokkauspäivää
       (= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
       {:otsikko "Valmistuminen"
        :leveys 2.5
        :nimi :loppupvm-arvio
        :otsikkorivi-luokka "klikattava"}
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
               [:span {:style {:color "#646464"}} (pvm/pvm-aika-opt arvo)])
        :otsikkorivi-luokka "klikattava"}
       ;; Defaulttina eli esim alueurakoitsijalle ei näytetä koko kenttää
       :else nil)

     {:otsikko "NRO"
      :leveys 1.2
      :nimi :ulkoinen-id
      :otsikkorivi-luokka "klikattava"}
     {:otsikko "Nimi"
      :leveys 3.5
      :nimi :nimi
      :otsikkorivi-luokka "klikattava"}
     {:otsikko "Tila"
      :leveys 2
      :nimi :paikkauskohteen-tila
      :fmt (fn [arvo]
             [yleiset/tila-indikaattori arvo {:fmt-fn paikkaus/fmt-tila}])
      :solun-luokka (fn [arvo _]
                      (str arvo "-bg"))
      :otsikkorivi-luokka "klikattava"}

     ;; Tiemerkinnän tilan indikointi
     {:otsikko-komp (fn [_ _]
                      [:div.tiemerkrinta-vihje.klikattava
                       {:on-click #(e! (t-paikkauskohteet/->AvaaVihje))}
                       "Tiemerkinnän tila " [yleiset/vihje ""]])
      :leveys 3
      :nimi :tiemerkinnan-tila
      ;; Tiemerkinnällä tämä on alasveto, tasaa se vasemmalle
      ;; Muilla urakoilla tämä on vaan teksti
      :tasaa (if (not= urakkatyyppi :tiemerkinta) :vasen :oikea)
      :solun-luokka (fn [arvo _rivi]
                      ;; Korosta "käsittelemättä" sarake paikkaus urakoille
                      (when (and
                              (= arvo "kasittelematta")
                              (not= urakkatyyppi :tiemerkinta))
                        "ehdotettu-bg"))
      :tyyppi :komponentti
      :komponentti (fn [{:keys [tiemerkinnan-tila
                                alasveto-valinnat paikkauskohteen-tila] :as rivi}]
                     ;;
                     ;; ==== Jos ei olla tiemerkintä urakassa, näytä vaan tila  ====
                     (if (not= urakkatyyppi :tiemerkinta)
                       [yleiset/tila-indikaattori tiemerkinnan-tila {:fmt-fn #(get t-paikkauskohteet/tiemerkinta-tila-valinnat (keyword %))}]

                       ;;
                       ;; ==== Tiemerkintä urakat voi asettaa tiemerkinnän tilan ====
                       [:div {:on-click #(.stopPropagation %)} ;; Älä avaa lomaketta kun inffonappia painetaan
                        [valinnat/checkbox-pudotusvalikko

                         ;; Alasvedon valinnat, vectorissa, esim   [{:nimi Käsittelemättä, :arvo :kasittelematta, :valittu? false}]
                         (remove #(= (:arvo %) :ei-tiemerkintaa) alasveto-valinnat)
                         (fn [tila _valittu?]
                           (e! (t-paikkauskohteet/->AsetaTiemerkinnanTila tila rivi))) ;; Muokkaa fn
                         (get t-paikkauskohteet/tiemerkinta-tila-valinnat (keyword tiemerkinnan-tila)) ;; Alasvedon teksti
                         ;; Komponentin optiot
                         {:vayla-tyyli? true :disabled (or
                                                         haku-kaynnissa?
                                                         ;; Jos tiemerkintää ei tehdä, disabloidaan alasveto
                                                         (= tiemerkinnan-tila "ei-tiemerkintaa")
                                                         ;; Jos paikkauskohde ei ole valmis, ei voida asettaa tiemerkinnän tilaa vielä
                                                         (not= paikkauskohteen-tila "valmis"))}]]))}

     {:otsikko "Menetelmä"
      :leveys 4
      :nimi :tyomenetelma
      :fmt #(paikkaus/tyomenetelma-id->nimi % tyomenetelmat)
      :solun-luokka (fn [arvo _]
                      ;; On olemassa niin pitkiä työmenetelmiä, että ne eivät mahdu soluun
                      ;; Joten lisätään näille pitkille menetelmille class joka saa ne mahtumaan
                      ;; soluun rivitettynä
                      (when (> (count (paikkaus/tyomenetelma-id->nimi arvo tyomenetelmat)) 40)
                        "grid-solulle-2-rivia"))
      :otsikkorivi-luokka "klikattava"}
     {:otsikko "Aikataulu"
      :leveys 2.1
      :nimi :formatoitu-aikataulu
      :fmt (fn [arvo]
             [:span {:class (if (str/includes? arvo "arv")
                              "prosessi-kesken"
                              "")} arvo])
      :otsikkorivi-luokka "klikattava"}
     {:otsikko "Sijainti"
      :leveys 2.3
      :nimi :formatoitu-sijainti
      :otsikkorivi-luokka "klikattava"}
     ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
     ;; Alueurakoitsijat ja tiemerkkarit näkevät listassa muiden urakoiden tietoja
     ;; Niimpä varmistetaan, että käyttäjällä on kustannusoikeudet paikkauskohteisiin
     (when nayta-hinnat?
       {:otsikko "Suunn. hinta"
        :leveys 1.8
        :nimi :suunniteltu-hinta
        :fmt fmt/euro-opt
        :tasaa :oikea
        :otsikkorivi-luokka "klikattava"})
     ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
     (when nayta-hinnat?
       {:otsikko "Tot. hinta"
        :leveys 1.8
        :nimi :toteutunut-hinta
        :fmt fmt/euro-opt
        :tasaa :oikea
        :otsikkorivi-luokka "klikattava"})
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
                          :luokka "btn-xs"}]])
        :otsikkorivi-luokka "klikattava"})]))


(defn- paikkauskohteet-taulukko [e! {:keys [haku-kaynnissa? nayta-vihje? tilaustila-aktiivinen? valitut-tilattavat-kohteet] :as app}]
  (let [urakkatyyppi (-> @tila/tila :yleiset :urakka :tyyppi)
        nayta-hinnat? (and
                        (or (= urakkatyyppi :paallystys)
                          (and (or (= urakkatyyppi :hoito) (= urakkatyyppi :teiden-hoito))
                            (not (:hae-aluekohtaiset-paikkauskohteet? app))))
                        (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))

        skeema (luo-taulukon-skeema e! app)
        paikkauskohteet (:paikkauskohteet app)
        skeema-filteroitu (filter some? skeema)
        yht-suunniteltu-hinta (reduce (fn [summa kohde]
                                        (+ summa (:suunniteltu-hinta kohde)))
                                0
                                paikkauskohteet)
        yht-tot-hinta (reduce (fn [summa kohde]
                                (+ summa (:toteutunut-hinta kohde)))
                        0
                        paikkauskohteet)
        rivi-valittu #(= (:id (:lomake app)) (:id %))
        aluekohtaisissa? (:hae-aluekohtaiset-paikkauskohteet? app)
        loytyi-kohteita? (> (count (:paikkauskohteet app)) 0)
        kohteet-count (count (:paikkauskohteet app))]
    ;; Riippuen vähän roolista, taulukossa on enemmän dataa tai vähemmän dataa.
    ;; Niinpä kavennetaan sitä hieman, jos siihen tulee vähemmän dataa, luettavuuden parantamiseksi
    [:div.col-xs-12.col-md-12.col-lg-12.paikkauskohde-nakyma
     [paikkaukset-apurit/raportointi-modal! e! app]
     [paikkaukset-apurit/tilaa-paikkauskohteet-modal! e! app]
     [grid/grid
      (merge {:sivuta 25
              :tunniste :id
              :otsikko [:div
                        (when-not aluekohtaisissa?
                          [:div
                           [:div.flex-row.tasaa-alas
                            (when-not haku-kaynnissa?
                              [:h2 {:style {:white-space "nowrap"}} (str kohteet-count (if (= kohteet-count 1) " paikkauskohde" " paikkauskohdetta"))])
                            (when (and
                                    (not= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta) ;; Tiemerkintäurakoitsijalle ei näytetä nappeja
                                    (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
                              [:div.flex-row {:style {:justify-content "flex-end"}}
                               (when loytyi-kohteita?
                                 [:span.inline-block
                                  [:form {:style {:margin-left "auto"}
                                          :target "_blank" :method "POST"
                                          :action (komm/excel-url :paikkauskohteet-urakalle-excel)}
                                   [:input {:type "hidden" :name "parametrit"
                                            :value (transit/clj->transit {:urakka-id (-> @tila/tila :yleiset :urakka :id)
                                                                          :tila (:valittu-tila app)
                                                                          :evkt (:valitut-evkt app)
                                                                          :alkupvm (pvm/->pvm (str "1.1." (:valittu-vuosi app)))
                                                                          :loppupvm (pvm/->pvm (str "31.12." (:valittu-vuosi app)))
                                                                          :tyomenetelmat #{(:valittu-tyomenetelma app)}})}]
                                   [:button {:type "submit"
                                             :class #{"nappi-toissijainen"}}
                                    [ikonit/ikoni-ja-teksti (ikonit/livicon-download) "Tallenna Excel"]]]])

                               [liitteet/lataa-tiedosto
                                {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
                                {:nappi-teksti "Tuo kohteet Excelistä"
                                 :url "lue-paikkauskohteet-excelista"
                                 :lataus-epaonnistui #(e! (t-paikkauskohteet/->TiedostoLadattu %))
                                 :tiedosto-ladattu #(e! (t-paikkauskohteet/->TiedostoLadattu %))}]
                               [yleiset/tiedoston-lataus-linkki
                                "Lataa Excel-pohja"
                                "/excel/harja_paikkauskohteet_pohja.xlsx"
                                {:luokat ["padding-top-8"]}]
                               [napit/yleinen-toissijainen
                                "Tilaa kohteita"
                                #(e! (t-paikkauskohteet/->TilaustilaAktivoituToggle))
                                {:paksu? true
                                 :disabled tilaustila-aktiivinen?
                                 :data-attributes {:data-cy "tilaa-paikkauskohteita"}}]
                               [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))
                                {:paksu? true
                                 :data-attributes {:data-cy "lisaa-paikkauskohde"}}]])]
                           ;; Vihje tilaustilan ollessa päällä
                           (when tilaustila-aktiivinen?
                             [:div.tilaa-kohteita-vihje {:id "tilaa-kohteita-vihje"}
                              [:h3.otsikko "Tilaa kohteita"]
                              [:div.flex-row.alkuun
                               [:p.valitut-maara
                                (str "Kohteita valittu yhteensä "
                                  (count valitut-tilattavat-kohteet)
                                  " kpl.")]]
                              [:div.flex-row.alkuun.napit
                               [napit/yleinen-ensisijainen "Tee tilaus"
                                #(e! (t-paikkauskohteet/->RaportointitapaModalToggle))
                                {:paksu? true
                                 :disabled (zero? (count valitut-tilattavat-kohteet))
                                 :data-attributes {:data-cy "tee-tilaus"}}]
                               [napit/peruuta "Peruuta"
                                #(e! (t-paikkauskohteet/->TilaustilaAktivoituToggle))
                                {:paksu? true}]]])])
                        ;; Gridin päällä oleva vihje tiemerkinnöille
                        [tiemerkinta-tila-vihje nayta-vihje? urakkatyyppi #(e! (t-paikkauskohteet/->AvaaVihje))]]
              :tyhja (if haku-kaynnissa?
                       [yleiset/ajax-loader-pieni "Haku käynnissä..."]
                       "Ei paikkauskohteita valituilla rajauksilla.")
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
              :otsikkorivi-klikattu (fn [{:keys [nimi] :as opts}]
                                      ;; Ei tarvi sortata tätä, columni on infolaatikko
                                      (when-not (= nimi :tiemerkinnan-tila)
                                        (e! (t-paikkauskohteet/->JarjestaPaikkauskohteet (:nimi opts)))))}
        (when (> (count paikkauskohteet) 0)
          {:rivi-jalkeen-fn (fn [_rivit]
                              ^{:luokka "yhteenveto"}
                              [(when tilaustila-aktiivinen?
                                 {:teksti ""})
                               {:teksti "Yht."}
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
                                 :else nil)
                               {:teksti ""}
                               {:teksti ""}
                               {:teksti ""}
                               (when nayta-hinnat?
                                 {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-suunniteltu-hinta)]})
                               (when nayta-hinnat?
                                 {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-tot-hinta)]})])}))
      skeema-filteroitu
      paikkauskohteet]]))

(defn- filtterit [e! {:keys [haku-kaynnissa?] :as app}]
  (let [haku-fn (fn [] (e! (t-paikkauskohteet/->HaePaikkauskohteet false)))
        vuodet (urakan-vuodet (:alkupvm (-> @tila/tila :yleiset :urakka)) (:loppupvm (-> @tila/tila :yleiset :urakka)))
        tyomenetelmat (get-in app [:valinnat :tyomenetelmat])
        valitut-tilat (:valitut-tilat app)
        valittu-vuosi (:valittu-vuosi app)
        valitut-evkt (:valitut-evkt app)
        valitut-tyomenetelmat (:valitut-tyomenetelmat app)
        valittavat-evkt (conj
                          (map (fn [h]
                                 (-> h
                                   (dissoc h :alue :type :liikennemuoto)
                                   (assoc :valittu? (or (some #(= (:id h) %) valitut-evkt) ;; Onko kyseinen evk valittu
                                                      false))))
                            @hal/vaylamuodon-hallintayksikot)
                          {:id 0 :nimi "Kaikki" :evknumero 0 :valittu? (some #(= 0 %) valitut-evkt)})
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

     ;; Tiemerkintäurakalle ja hoito ei haluta näyttää evkrajauksia.
     (when (and
             (not= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
             (not= (-> @tila/tila :yleiset :urakka :tyyppi) :hoito))
       [:div.col-xs-2
        [:label {:class "alasvedon-otsikko" :for "filtteri-evk"} "Elinvoimakeskus"]
        [valinnat/checkbox-pudotusvalikko valittavat-evkt (fn [evk valittu?]
                                                            (e! (t-paikkauskohteet/->FiltteriValitseElinvoimakeskus evk valittu?)))
         [" Elinvoimakeskus valittu" " Elinvoimakeskusta valittu"]
         {:vayla-tyyli? true}]])

     ;; Kohteen tila 
     [:div.col-xs-2
      [:label.alasvedon-otsikko "Tila"]
      [valinnat/checkbox-pudotusvalikko valittavat-tilat (fn [tila valittu?]
                                                           (e! (t-paikkauskohteet/->FiltteriValitseTila tila valittu?)))
       [" Tila valittu" " Tilaa valittu"]
       {:vayla-tyyli? true :disabled haku-kaynnissa?}]]

     ;; Vuosivalinnat
     [:div.col-xs-2 {:data-cy "paikkauskohde-vuosivalinta"}
      [:label
       {:class "alasvedon-otsikko" :for "filtteri-vuosi"} "Vuosi"]
      [yleiset/livi-pudotusvalikko {:valinta valittu-vuosi
                                    :vayla-tyyli? true
                                    :disabled haku-kaynnissa?
                                    :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}
                                    :valitse-fn #(e! (t-paikkauskohteet/->FiltteriValitseVuosi %))}
       vuodet]]

     ;; Työmenetelmä 
     [:div.col-xs-4
      [:label.alasvedon-otsikko "Työmenetelmä"]
      [valinnat/checkbox-pudotusvalikko valittavat-tyomenetelmat (fn [tyomenetelma valittu?]
                                                                   (e! (t-paikkauskohteet/->FiltteriValitseTyomenetelma tyomenetelma valittu?)))
       [" Työmenetelmä valittu" " Työmenetelmää valittu"]
       {:vayla-tyyli? true :disabled haku-kaynnissa?}]]

     ;; Hae
     [:span {:style {:align-self "flex-end"}}
      [napit/yleinen-ensisijainen "Hae kohteita" haku-fn {:luokka "nappi-korkeus-36"
                                                          :disabled haku-kaynnissa?
                                                          :data-attributes {:data-cy "hae-paikkauskohteita"}}]]]))

(defn- paikkauskohteet-sivu [e! app]
  [:div
   [:h1 "Paikkauskohteet"]
   [filtterit e! app]
   [kartta/kartan-paikka]
   (when (:lomake app) [paikkauskohdelomake/paikkauslomake e! app])
   [paikkauskohteet-taulukko e! app]])

(defn wrap-paikkauskohteet [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do
                         (kartta-tasot/taso-pois! :paikkaukset-toteumat)
                         (kartta-tasot/taso-pois! :organisaatio)
                         (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                         (when (empty? (get-in app [:valinnat :tyomenetelmat])) (e! (t-yhteinen/->HaeTyomenetelmat)))
                         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? true)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M)
                         (e! (t-paikkauskohteet/->HaePaikkauskohteet true)))
      #(do
         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
         (e! (t-paikkauskohteet/->SuljeLomake))))
    (fn [e! app]
      [:div.row
       [paikkauskohteet-sivu e! app]
       [debug/debug app]])))

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
