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
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake :as paikkauskohdelomake]))

(def paikkauskohteiden-tilat
  [{:nimi "Kaikki"} {:nimi "Ehdotettu"} {:nimi "Hylätty"} {:nimi "Tilattu"} {:nimi "Valmis"}])

(defn- urakan-vuodet [alkupvm loppupvm]
  (when (and (not (nil? alkupvm)) (not (nil? loppupvm)))
    (mapv
      (fn [aika]
        (time-core/year (first aika)))
      (pvm/urakan-vuodet alkupvm loppupvm))))

(defn- kayttaja-on-urakoitsija? [urakkaroolit]
  (let [urakkaroolit (if (set? urakkaroolit)
                       urakkaroolit
                       #{urakkaroolit})
        urakoitsijaroolit #{"Laatupaallikko"
                            "Kayttaja"
                            "vastuuhenkilo"
                            "Laadunvalvoja"
                            "Kelikeskus"
                            "Paivystaja"}]
    ;; Annetut roolit set voi olla kokonaan tyhjä
    (if (empty? urakkaroolit)
      ;; Jos tyhjä, ei ole urakoitsija
      false
      ;; Jos rooli on annettu, tarkista onko urakoitsija
      (some (fn [rooli]
              (true?
                (some #(= rooli %) urakoitsijaroolit)))
            urakkaroolit))))

(defn- kayttaja-on-tilaaja? [roolit]
  (let [roolit (if (set? roolit)
                 roolit
                 #{roolit})
        tilaajaroolit #{"Jarjestelmavastaava"
                        "Tilaajan_Asiantuntija"
                        "Tilaajan_Kayttaja"
                        "Tilaajan_Urakanvalvoja"
                        "Tilaajan_laadunvalvoja"
                        "Tilaajan_turvallisuusvastaava"
                        "Tilaajan_Rakennuttajakonsultti"}]
    ;; Järjestelmävastaava on aina tilaaja ja elyn urakanvavoja jolla on päällystysurakka tyyppinä on
    ;; myös aina tilaaja
    (if (or
          (roolit/jvh? @istunto/kayttaja)
          (= :tilaaja (roolit/osapuoli @istunto/kayttaja)))
      true
      (some (fn [rooli]
              (true?
                (some #(= rooli %) tilaajaroolit)))
            roolit))))

(defn- paikkauskohteet-taulukko [e! app]
  (let [;_ (js/console.log "käyttäjän urakan tiedot" (pr-str (-> @tila/tila :yleiset :urakka)))
        ;_ (js/console.log "roolit" (pr-str (roolit/osapuoli @istunto/kayttaja)))
        ;_ (js/console.log "urakkaroolit" (pr-str (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))))
        ;_ (js/console.log "organisaatioroolit" (pr-str (roolit/organisaatioroolit @istunto/kayttaja)) (pr-str (get-in @istunto/kayttaja [:organisaatio :id])))
        ;      _
        ;      (js/console.log "voi kirjoittaa kohteisiin" (pr-str (oikeudet/voi-kirjoittaa?
        ;                                                              oikeudet/urakat-paikkaukset-paikkauskohteet
        ;                                                              (-> @tila/tila :yleiset :urakka :id)
        ;                                                              @istunto/kayttaja)))
        ;      _
        ;      (js/console.log "voi kirjoittaa kustannuksiin" (pr-str (oikeudet/voi-kirjoittaa?
        ;                                                                 oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset
        ;                                                                 (-> @tila/tila :yleiset :urakka :id)
        ;                                                                 @istunto/kayttaja)))
        ;      _
        ;      (js/console.log "voi lukea kohteita" (pr-str (oikeudet/voi-lukea?
        ;                                                       oikeudet/urakat-paikkaukset-paikkauskohteet
        ;                                                       (-> @tila/tila :yleiset :urakka :id)
        ;                                                       @istunto/kayttaja)))
        ;_ (js/console.log "on urakoitsija?" (pr-str (kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)))))
        ;_
        ; (js/console.log "on tilaaja?" (pr-str (kayttaja-on-tilaaja? (roolit/osapuoli @istunto/kayttaja) (-> @tila/tila :yleiset :urakka :tyyppi))))
        urakkatyyppi (-> @tila/tila :yleiset :urakka :tyyppi)
        nayta-hinnat? (and
                        (or (= urakkatyyppi :paallystys)
                            (and (or (= urakkatyyppi :hoito) (= urakkatyyppi :teiden-hoito)) ))
                        (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
        skeema [
                (cond
                  ;; Tiemerkintäurakoitsijalle näytetään valmistusmipäivä, eikä muokkauspäivää
                  (= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
                  {:otsikko "Valmistuminen"
                   :leveys 2
                   :nimi :loppupvm-arvio}
                  ;; Tilaajalle näytetään ja päällysteurakalle näytetään muokkauspäivä. Mutta urakanvalvoja esiintyy myös
                  ;; päällystysurkoitsijana joten tarkistetaan myös urakkaroolit
                  (or (kayttaja-on-tilaaja? (roolit/osapuoli @istunto/kayttaja))
                      (and (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                           (kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)))))
                  {:otsikko "Muokattu"
                   :leveys 2
                   :nimi :paivays
                   :fmt (fn [arvo]
                          [:span {:style {:color "#646464"}} (pvm/pvm-aika-opt arvo)])}
                  ;; Defaulttina eli esim alueurakoitsijalle ei näytetä koko kenttää
                  :else nil
                  )
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
                          [:span (paikkaus/fmt-tila arvo)]]])}
                {:otsikko "Menetelmä"
                 :leveys 4
                 :nimi :tyomenetelma
                 :fmt #(paikkaus/kuvaile-tyomenetelma %)}
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
                ;; Alueurakoitsijat ja tiemerkkarit näkevät listassa muiden urakoiden tietoja
                ;; Niimpä varmistetaan, että käyttäjällä on kustannusoikeudet paikkauskohteisiin
                (when nayta-hinnat?
                  {:otsikko "Suun. hinta"
                   :leveys 2
                   :nimi :suunniteltu-hinta
                   :fmt fmt/euro-opt
                   :tasaa :oikea})
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
                (when nayta-hinnat?
                  {:otsikko "Tot. hinta"
                   :leveys 2
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
                                            (or (kayttaja-on-tilaaja? (roolit/osapuoli @istunto/kayttaja))
                                                ;; Päällystysurakoitsijat pääsee näkemään tarkempaa dataa
                                                (and (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                                                     (kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))))))
                                   (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-katselu}))))))
              :otsikkorivi-klikattu (fn [opts]
                                      (e! (t-paikkauskohteet/->JarjestaPaikkauskohteet (:nimi opts))))}
             (when (> (count paikkauskohteet) 0)
               {:rivi-jalkeen-fn (fn [rivit]
                                   ^{:luokka "yhteenveto"}
                                   [{:teksti "Yht."}
                                    {:teksti (str (count paikkauskohteet) " kohdetta")}
                                    (cond
                                      ;; Tiemerkintäurakoitsijalle näytetään valmistusmipäivä, eikä muokkauspäivää
                                      ;; Joten yhteenvetoriville tyhjä column
                                      (= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
                                      {:teksti ""}
                                      ;; Päällysteurakalle näytetään muokkauspäivä. Mutta urakanvalvoja esiintyy myös
                                      ;; päällystysurkoitsijana joten tarkistetaan myös urakkaroolit
                                      ;; Joten yhteenvetoriville tyhjä column
                                      (or (kayttaja-on-tilaaja? (roolit/osapuoli @istunto/kayttaja))
                                          (and (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                                               (kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)))))
                                      {:teksti ""}
                                      ;; Defaulttina eli esim alueurakoitsijalle ei näytetä koko kenttää
                                      ;; Joten ei tyhjää riviä
                                      :else nil
                                      )
                                    {:teksti ""}
                                    {:teksti ""}
                                    {:teksti ""}
                                    {:teksti ""}
                                    (when (and
                                            (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                                            (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
                                      {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-suunniteltu-hinta)]})
                                    (when (and (= (-> @tila/tila :yleiset :urakka :tyyppi) :paallystys)
                                               (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
                                      {:teksti [:div.tasaa-oikealle {:style {:margin-right "-12px"}} (fmt/euro-opt yht-tot-hinta)]})])}))
      skeema
      paikkauskohteet]]))

(defn kohteet [e! app]
  (let [loytyi-kohteita? (> (count (:paikkauskohteet app)) 0)
        piilota-napit? (:hae-aluekohtaiset-paikkauskohteet? app)]
    [:div.kohdelistaus
     (when (not loytyi-kohteita?)
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
           ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
           ;TODO: Napeista puuttuu myös kulmien pyöristys
           #_[napit/yleinen-ensisijainen "Näytä nappi DEBUG" #(harja.ui.viesti/nayta-toast! "Toast-notifiikaatio testi" :varoitus)]
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
                         :class #{"nappi-toissijainen-paksu napiton-nappi"}}
                [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Vie Exceliin"]]]])

           [liitteet/lataa-tiedosto
            (-> @tila/tila :yleiset :urakka :id)
            {:nappi-teksti "Tuo kohteet excelistä"
             :nappi-luokka "napiton-nappi nappi-toissijainen-paksu"
             :url "lue-paikkauskohteet-excelista"
             :lataus-epaonnistui #(e! (t-paikkauskohteet/->TiedostoLadattu %))
             :tiedosto-ladattu #(e! (t-paikkauskohteet/->TiedostoLadattu %))}]
           [napit/lataa "Lataa Excel-pohja" #(.open js/window "/excel/harja_paikkauskohteet_pohja.xlsx" "_blank") {:luokka "napiton-nappi" :paksu? true}]
           [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde})) {:paksu? true}]])])
     (when loytyi-kohteita?
       [:div.row [paikkauskohteet-taulukko e! app]])]))

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
      ;; Tiemerkintäurakalle ei haluta näyttää elyrajauksia.
      (when (not= (-> @tila/tila :yleiset :urakka :tyyppi) :tiemerkinta)
        [:div.col-xs-2
         [:label.alasvedon-otsikko-vayla "ELY"]
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
       [:label.alasvedon-otsikko-vayla "Vuosi"]
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
      #_[:div.col-xs-2 "hae"]
      ]]))

(defn- paikkauskohteet-sivu [e! app]
  [:div
   ;; Filtterit näytetään kaikille muille käyttäjille paitsi aluevastava (yksikkömuoto, jotta haku toimii) eli, joiden rooli on ELY_Urakanvalvoja
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
      (println "paikkauskohteet* aluekohtaiset" (:hae-aluekohtaiset-paikkauskohteet? app))
      [:div.row
       [paikkauskohteet-sivu e! app]])))

(defn paikkauskohteet [ur]
  (komp/luo
    (komp/sisaan #(do
                    (swap! tila/paikkauskohteet assoc :hae-aluekohtaiset-paikkauskohteet? false)
                    (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{})
                    (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                    (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [_]
      [tuck/tuck tila/paikkauskohteet paikkauskohteet*])))

;; Hoitourakoille voidaan näyttää joko alue-tai urakkakohtaiset paikkauskohteet, joten erottelu täytyy tehdä frontissa.
;; Tämän komponentin ainoa ero on, että paikkauskohteita hakiessa backendille läheteään lippu, jolla tiedetään,
;; kumpia paikkauskohteita halutaan hakea.
(defn aluekohtaiset-paikkauskohteet [ur]
  (komp/luo
    (komp/sisaan #(do
                    (swap! tila/paikkauskohteet assoc :hae-aluekohtaiset-paikkauskohteet? true)
                    (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{})
                    (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                    (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [_]
      [tuck/tuck tila/paikkauskohteet paikkauskohteet*])))

