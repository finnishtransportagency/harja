(ns harja.views.urakka.paallystysilmoitukset
  "Urakan päällystysilmoitukset -listaus. Haaroittaa POT1 vs. POT2 valitun vuoden mukaisesti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! chan]]
            [cljs-time.core :as t]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]

            [harja.domain.paallystysilmoitus :as pot]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]

            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]

            [harja.views.urakka.pot1-lomake :as pot1-lomake]
            [harja.views.urakka.pot2.materiaalikirjasto :as massat-view]
            [harja.views.urakka.pot2.pot2-lomake :as pot2-lomake]
            [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as yhteiset]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.views.kartta :as kartta]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as u-valinnat]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))
;;;; PAALLYSTYSILMOITUKSET "PÄÄNÄKYMÄ" ;;;;;;;;


(defn- tayta-takuupvm [lahtorivi tama-rivi]
  ;; jos kohteella ei vielä ole POT:ia, ei kopioida takuupvm:ääkään
  (if (:id tama-rivi)
    (assoc tama-rivi :takuupvm (:takuupvm lahtorivi))
    tama-rivi))

(defn- lahetys-epaonnistunut? [{:keys [lahetys-onnistunut lahetysvirhe velho-lahetyksen-tila] :as rivi}]
  (or (and (not lahetys-onnistunut) (not-empty lahetysvirhe))
      (= "epaonnistunut" velho-lahetyksen-tila)))

(defn oliko-pelkka-tekninen-virhe? [{:keys [velho-lahetyksen-tila lahetysvirhe] :as rivi}]
  (or (re-matches #".*Ulkoiseen järjestelmään ei saada yhteyttä.*"
                  (or lahetysvirhe ""))
      (= "tekninen-virhe" velho-lahetyksen-tila)))

(defn kuvaile-ilmoituksen-tila [{:keys [tila paatos-tekninen-osa] :as rivi}]
  (cond
    (= :hylatty paatos-tekninen-osa)
    (ikonit/ikoni-ja-elementti (ikonit/denied-svg 14) [:span "Hylätty"])

    (= :aloitettu tila)
    [:span "Kesken"]

    (lahetys-epaonnistunut? rivi)
    (yhteiset/lahetys-virheet-nappi rivi :pitka)

    (= :valmis tila)
    (ikonit/ikoni-ja-elementti [ikonit/harja-icon-status-selected] [:span {:class "black-lighter"} "Valmis käsiteltäväksi"])

    (= :lukittu tila)
    [:span.tila-hyvaksytty
     (ikonit/ikoni-ja-elementti (ikonit/locked-svg 14) [:span {:class "black-lighter"} "Hyväksytty"])]

    :else
    [:span "Ei aloitettu"]))

(defn- lahetys-yha-velho-nappi [e! {:keys [oikeus urakka-id sopimus-id vuosi paallystysilmoitus kohteet-yha-velho-lahetyksessa]}]
  (let [lahetys-kaynnissa-fn #(e! (paallystys/->MuutaTila [:kohteet-yha-velho-lahetyksessa]
                                                          (if (not-empty kohteet-yha-velho-lahetyksessa)
                                                            (conj kohteet-yha-velho-lahetyksessa %)
                                                            #{%})))
        lahetys-tehty-fn #(e! (paallystys/->MuutaTila [:kohteet-yha-velho-lahetyksessa]
                                                      (disj kohteet-yha-velho-lahetyksessa %)))
        kun-onnistuu-fn #(e! (paallystys/->YHAVelhoVientiOnnistui %))
        kun-virhe-fn #(e! (paallystys/->YHAVelhoVientiEpaonnistui %))
        kohde-id (:paallystyskohde-id paallystysilmoitus)]
    [napit/palvelinkutsu-nappi
     (ikonit/ikoni-ja-teksti (ikonit/envelope) "Lähetä")
     #(do
        (log "[YHA/VELHO] Lähetetään urakan (id:" urakka-id ") sopimuksen (id: " sopimus-id
             ") kohde (id:" (pr-str kohde-id) ") YHA:n ja Velhoon (VELHO DISABLED)") ;; TODO enable VELHO
        (lahetys-kaynnissa-fn kohde-id)
        (k/post! :laheta-pot-yhaan-ja-velhoon {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :kohde-id kohde-id
                                               :vuosi vuosi}
                 nil
                 true))
     {:luokka :napiton-nappi
      :disabled (or false
                    (not (oikeudet/on-muu-oikeus? "sido" oikeus urakka-id @istunto/kayttaja)))
      :virheviestin-nayttoaika viesti/viestin-nayttoaika-pitka
      :kun-valmis #(do
                     ;; Tämä on jätetty tähän, koska paallystysilmoitukset atomia käytetään muuallakin kuin
                     ;; Päällystysilmoituksissa
                     (reset! paallystys/paallystysilmoitukset (:paallystysilmoitukset %))
                     (lahetys-tehty-fn kohde-id))
      :kun-onnistuu (fn [vastaus]
                      (kun-onnistuu-fn vastaus))
      :kun-virhe (fn [vastaus]
                   (log "[YHA] Lähetys epäonnistui osalle kohteista YHAan. Vastaus: " (pr-str vastaus)) ;; TODO enable VELHO
                   (kun-virhe-fn vastaus))
      :nayta-virheviesti? false}]))

(defn- laheta-pot-yhaan-velhoon-komponentti [rivi _ e! urakka valittu-sopimusnumero
                                             valittu-urakan-vuosi kohteet-yha-velho-lahetyksessa]
  (let [kohde-id (:paallystyskohde-id rivi)
        {:keys [muokattu lahetetty]} rivi
        muokattu-yhaan-lahettamisen-jalkeen? (when (and muokattu lahetetty)
                                               (> muokattu lahetetty))
        lahetys-kesken? (contains? kohteet-yha-velho-lahetyksessa kohde-id)
        ilmoituksen-voi-lahettaa? (fn [{:keys [paatos-tekninen-osa tila] :as paallystysilmoitus}]
                                    (and (= :hyvaksytty paatos-tekninen-osa)
                                         (contains? #{:valmis :lukittu} tila)
                                         (not lahetys-kesken?)))
        ilmoitus-on-lahetetty? (fn [{:keys [lahetys-onnistunut velho-lahetyksen-tila velho-lahetyksen-aika]
                                     :as paallystysilmoitus}]
                                 (and lahetys-onnistunut
                                      ; (= "valmis" velho-lahetyksen-tila)  TODO enable VELHO
                                      ; velho-lahetyksen-aika

                                      ))

        nayta-kielto? (<= valittu-urakan-vuosi 2019)
        nayta-nappi? (and (or (not (ilmoitus-on-lahetetty? rivi))
                              muokattu-yhaan-lahettamisen-jalkeen?)
                          (ilmoituksen-voi-lahettaa? rivi))
        nayta-lahetyksen-aika? (ilmoitus-on-lahetetty? rivi)
        nayta-lahetyksen-virhe? (lahetys-epaonnistunut? rivi)]
    (cond
      nayta-kielto?
      [:div "Kohdetta ei voi enää lähettää."]

      lahetys-kesken?
      [yleiset/ajax-loader-pieni "Lähetys käynnissä"]

      nayta-nappi?
      [lahetys-yha-velho-nappi e! {:oikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet
                                   :urakka-id (:id urakka) :sopimus-id (first valittu-sopimusnumero)
                                   :vuosi valittu-urakan-vuosi :paallystysilmoitus rivi
                                   :kohteet-yha-velho-lahetyksessa kohteet-yha-velho-lahetyksessa}]

      nayta-lahetyksen-aika?
      [:span.lahetyksen-aika
       [ikonit/ikoni-ja-teksti [ikonit/harja-icon-status-selected] (pvm/pvm-aika (or (:velho-lahetyksen-aika rivi)
                                                                                     ;; YHA-lähetyksen aika = :lahetetty
                                                                                     (:lahetetty rivi)))]]

      :else nil)))

(defn- paallystysilmoitukset-taulukko [e! {:keys [urakka urakka-tila paallystysilmoitukset paikkauskohteet?] :as app}]
  (fn [e! {urakka :urakka {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila paikkauskohteet? :paikkauskohteet?
           paallystysilmoitukset :paallystysilmoitukset kohteet-yha-velho-lahetyksessa :kohteet-yha-velho-lahetyksessa :as app}]
    (let [avaa-paallystysilmoitus-handler (fn [e! rivi]
                                            (if (>= valittu-urakan-vuosi pot/pot2-vuodesta-eteenpain)
                                              (e! (pot2-tiedot/->HaePot2Tiedot (:paallystyskohde-id rivi) (:paikkauskohde-id rivi)))
                                              (e! (paallystys/->AvaaPaallystysilmoitus (:paallystyskohde-id rivi)))))]
      [grid/grid
       {:otsikko ""
        :tunniste :paallystyskohde-id
        :tyhja (if (nil? paallystysilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
        :tallenna (fn [rivit]
                    ;; Tässä käytetään go-blockia koska gridi olettaa saavansa kanavan. Paluu arvolla ei tehdä mitään.
                    ;; 'takuupvm-tallennus-kaynnissa-kanava' käytetään sen takia, että gridi pitää 'tallenna' nappia
                    ;; disaploituna niin kauan kuin go-block ei palauta arvoa.
                    (go
                      (let [takuupvm-tallennus-kaynnissa-kanava (chan)]
                        (e! (paallystys/->TallennaPaallystysilmoitustenTakuuPaivamaarat rivit takuupvm-tallennus-kaynnissa-kanava))
                        (<! takuupvm-tallennus-kaynnissa-kanava))))
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? true
        :piilota-muokkaus? (when paikkauskohteet? true)     ; piilottaa muokkausnapin, kun sitä ei paikkauskohteiden kautta tarkastellessa käytetä
        :piilota-toiminnot? true
        :data-cy "paallystysilmoitukset-grid"}
       [{:otsikko "Kohde\u00ADnumero" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys 14}
        {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :tyyppi :string :leveys 14}
        {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 50}
        {:otsikko "YHA-id" :nimi :yhaid :muokattava? (constantly false) :tyyppi :numero :leveys 15}
        {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm :leveys 18 :muokattava? (fn [t] (not (nil? (:id t))))
         :fmt pvm/pvm-opt
         :tayta-alas? #(not (nil? %))
         :tayta-fn tayta-takuupvm
         :tayta-tooltip "Kopioi sama takuupvm alla oleville kohteille"}
        {:otsikko "Tila" :nimi :tila :muokattava? (constantly false)
         :tyyppi :komponentti :leveys 25
         :komponentti kuvaile-ilmoituksen-tila}
        (when (and (roolit/tilaajan-kayttaja? @istunto/kayttaja)
                   (< 2019 valittu-urakan-vuosi)
                   ;; paikkauskohteiden YHA-lähetys saa mennä päälle kehitysympäristössä
                   (or (not paikkauskohteet?) (k/kehitysymparistossa?)))
          ; TODO enable VELHO {:otsikko "Lähetys YHA / Velho:an" :nimi :lahetys-yha-velho :muokattava? (constantly false) :tyyppi :reagent-komponentti
          {:otsikko "Lähetys YHA:an" :nimi :lahetys-yha-velho :muokattava? (constantly false) :tyyppi :reagent-komponentti
           :leveys 25
           :komponentti laheta-pot-yhaan-velhoon-komponentti
           :komponentti-args [e! urakka valittu-sopimusnumero valittu-urakan-vuosi kohteet-yha-velho-lahetyksessa]})
        {:otsikko "" :nimi :paallystysilmoitus :muokattava? (constantly true) :leveys 25
         :tyyppi :komponentti
         :komponentti (fn [{:keys [tila] :as rivi}]
                        [:button.napiton-nappi
                         {:on-click #(avaa-paallystysilmoitus-handler e! rivi)}
                         (case tila
                           (:lukittu :valmis) (ikonit/ikoni-ja-teksti (ikonit/eye-open) " Avaa ilmoitus")
                           (:aloitettu) (ikonit/ikoni-ja-teksti (ikonit/pencil) " Muokkaa")
                           (ikonit/ikoni-ja-teksti (ikonit/livicon-document-full) " Aloita"))])}]
       paallystysilmoitukset])))

(def pot-vinkki-paikkaus "Paikkauskohteiden POT:ien osalta YHA-lähetys tulee käyttöön vasta lähiaikoina.")

(def pot-vinkki-paallystys "Huom! Osa POT-lomakkeista raportoi Harjassa YHA-lähetysvirhettä, jos prosessointi YHA:n päässä kestää yli sallitun maksimiajan (29s). Itse tiedot useassa tapauksessa ovat silti menneet onnistuneesti YHA:an perille, vaikka Harjan virheilmoitus sanoisi 'request timed out'. Selvittelemme tilannetta YHA-tiimin kanssa, pahoittelut asiasta.")

(defn ilmoitusluettelo
  [e! app]
  (komp/luo
    (komp/sisaan #(when-not (:paikkauskohteet? app)
                    (nav/vaihda-kartan-koko! :M)))
    (fn [e! {paikkauskohteet? :paikkauskohteet? ;; Päällystysilmoitukset renderöidään myös paikkaukset välilehden alle
             :as app}]
      [:div.paallystysilmoitusluettelo
       [yleiset/toast-viesti
        (if paikkauskohteet? pot-vinkki-paikkaus pot-vinkki-paallystys)]
       [:div {:style {:display "inline-block"
                      :position "relative"
                      :top "28px"}}
        (when-not paikkauskohteet?
          [:h3.inline-block "Päällystysilmoitukset"])
        (when-not paikkauskohteet?
          [pot2-lomake/avaa-materiaalikirjasto-nappi #(e! (mk-tiedot/->NaytaModal true))
           {:margin-left "24px"}])]
       [paallystysilmoitukset-taulukko e! app]
       ;; TODO: YHA-lähetykset eivät ole paikkauskohteilla vielä käytössä
       (when-not paikkauskohteet?
         [yleiset/vihje "Tilaajan täytyy hyväksyä ilmoitus ennen kuin se voidaan lähettää YHA:an"])])))

(defn valinnat [e! {:keys [urakka pot-jarjestys]}]
  [:div
   [valinnat/vuosi {:vayla-tyyli? true}
    (t/year (:alkupvm urakka))
    (t/year (:loppupvm urakka))
    urakka/valittu-urakan-vuosi
    #(do
       (urakka/valitse-urakan-vuosi! %)
       (e! (paallystys/->HaePaallystysilmoitukset)))]
   [u-valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero (fn [valittu-arvo]
                                                                         ;; Tämänkin voi ottaa pois, jos koko ylläpidon saa
                                                                         ;; joskus refaktoroitua
                                                                         (reset! yllapito-tiedot/kohdenumero valittu-arvo)
                                                                         (e! (paallystys/->SuodataYllapitokohteet)))]
   [u-valinnat/tienumero yllapito-tiedot/tienumero (fn [valittu-arvo]
                                                     ;; Tämänkin voi ottaa pois, jos koko ylläpidon saa
                                                     ;; joskus refaktoroitua
                                                     (reset! yllapito-tiedot/tienumero valittu-arvo)
                                                     (e! (paallystys/->SuodataYllapitokohteet)))
    {:otsikon-luokka "alasvedon-otsikko-vayla"}]
   [yleiset/pudotusvalikko
    "Järjestä kohteet"
    {:valinta pot-jarjestys
     :valitse-fn #(e! (paallystys/->JarjestaYllapitokohteet %))
     :vayla-tyyli? true
     :format-fn {:tila "Tilan mukaan"
                 :kohdenumero "Kohdenumeron mukaan"
                 :muokkausaika "Muokkausajan mukaan"}}
    [:tila :kohdenumero :muokkausaika]]])

(defn paallystysilmoitukset
  [e! app]
  (komp/luo
    (komp/lippu paallystys/paallystysilmoitukset-tai-kohteet-nakymassa?)
    (komp/sisaan-ulos
      (fn []
        (e! (paallystys/->MuutaTila [:paallystysilmoitukset-tai-kohteet-nakymassa?] true))
        (e! (paallystys/->HaePaallystysilmoitukset))
        (e! (mk-tiedot/->HaePot2MassatJaMurskeet))
        (e! (mk-tiedot/->HaeKoodistot)))
      (fn []
        (e! (paallystys/->MuutaTila [:paallystysilmoitukset-tai-kohteet-nakymassa?] false))))
    (fn [e! {:keys [urakka-tila paallystysilmoitus-lomakedata lukko urakka kayttaja paikkauskohteet?] :as app}]
      [:div.paallystysilmoitukset
       ;; Kartan paikka on hieman erilainen, kun nämä renderöidään paikkauskohteista
       (when-not (and paikkauskohteet? paallystysilmoitus-lomakedata)
         [:<>
          [kartta/kartan-paikka]])
       [debug app {:otsikko "TUCK STATE"}]
       ;; Toistaiseksi laitetaan sekä POT1 että POT2 tarvitsemat tiedot avaimeen
       ;; paallystysilmoitus-lomakedata, mutta tiedot tallennetaan eri rakenteella
       ;; Muistattava asettaa lomakedata arvoon nil, aina kun poistutaan lomakkeelta
       (if paallystysilmoitus-lomakedata
         (if (>= (:valittu-urakan-vuosi urakka-tila)
                 pot/pot2-vuodesta-eteenpain)
           [pot2-lomake/pot2-lomake e! (select-keys app #{:paallystysilmoitus-lomakedata
                                                          :massat :murskeet :materiaalikoodistot
                                                          :pot2-massa-lomake :pot2-murske-lomake
                                                          :paikkauskohteet?})
            lukko urakka kayttaja]
           [pot1-lomake/pot1-lomake e! paallystysilmoitus-lomakedata lukko urakka kayttaja])
         (when-not paikkauskohteet?
           [:div
            [valinnat e! (select-keys app #{:urakka :pot-jarjestys})]
            [ilmoitusluettelo e! app]]))
       (when-not paikkauskohteet?
         [massat-view/materiaalikirjasto-modal e! (select-keys app #{:massat :murskeet :materiaalikoodistot
                                                                     :pot2-massa-lomake :pot2-murske-lomake
                                                                     :tuonti-urakka
                                                                     :muut-urakat-joissa-materiaaleja
                                                                     :nayta-muista-urakoista-tuonti?
                                                                     :materiaalit-toisesta-urakasta})])])))
