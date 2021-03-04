(ns harja.views.urakka.paallystysilmoitukset
  "Urakan päällystysilmoitukset -listaus. Haaroittaa POT1 vs. POT2 valitun vuoden mukaisesti"
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [cljs.spec.alpha :as s]
            [cljs-time.core :as t]
            [goog.events.EventType :as event-type]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]

            [harja.domain.paallystysilmoitus :as pot]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tierekisteri :as tr]

            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]

            [harja.views.urakka.pot1-lomake :as pot1-lomake]
            [harja.views.urakka.pot2.materiaalikirjasto :as massat-view]
            [harja.views.urakka.pot2.pot2-lomake :as pot2-lomake]

            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.views.kartta :as kartta]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as u-valinnat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))
;;;; PAALLYSTYSILMOITUKSET "PÄÄNÄKYMÄ" ;;;;;;;;


(defn- tayta-takuupvm [lahtorivi tama-rivi]
  ;; jos kohteella ei vielä ole POT:ia, ei kopioida takuupvm:ääkään
  (if (:id tama-rivi)
    (assoc tama-rivi :takuupvm (:takuupvm lahtorivi))
    tama-rivi))

(defn- paallystysilmoitukset-taulukko [e! {:keys [urakka urakka-tila paallystysilmoitukset] :as app}]
  (let [urakka-id (:id urakka)
        valittu-vuosi (:valittu-urakan-vuosi urakka-tila)
        avaa-paallystysilmoitus-handler (fn [e! rivi]
                                          (if (>= valittu-vuosi pot/pot2-vuodesta-eteenpain)
                                            (e! (pot2-tiedot/->HaePot2Tiedot (:paallystyskohde-id rivi)))
                                            (e! (paallystys/->AvaaPaallystysilmoitus (:paallystyskohde-id rivi)))))]
    [grid/grid
     {:otsikko      ""
      :tunniste     :paallystyskohde-id
      :tyhja        (if (nil? paallystysilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
      :tallenna     (fn [rivit]
                      ;; Tässä käytetään go-blockia koska gridi olettaa saavansa kanavan. Paluu arvolla ei tehdä mitään.
                      ;; 'takuupvm-tallennus-kaynnissa-kanava' käytetään sen takia, että gridi pitää 'tallenna' nappia
                      ;; disaploituna niin kauan kuin go-block ei palauta arvoa.
                      (go
                        (let [takuupvm-tallennus-kaynnissa-kanava (chan)]
                          (e! (paallystys/->TallennaPaallystysilmoitustenTakuuPaivamaarat rivit takuupvm-tallennus-kaynnissa-kanava))
                          (<! takuupvm-tallennus-kaynnissa-kanava))))
      :voi-lisata?  false
      :voi-kumota?  false
      :voi-poistaa? (constantly false)
      :voi-muokata? true
      :piilota-toiminnot? true
      :data-cy      "paallystysilmoitukset-grid"}
     [{:otsikko "Kohde\u00ADnumero" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys 14}
      {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :tyyppi :string :leveys 14}
      {:otsikko "YHA-id" :nimi :yhaid :muokattava? (constantly false) :tyyppi :numero :leveys 15}
      {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 50}
      {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys 20
       :hae     (fn [rivi]
                  (paallystys-ja-paikkaus/kuvaile-ilmoituksen-tila (:tila rivi)))}
      {:otsikko       "Takuupvm" :nimi :takuupvm :tyyppi :pvm :leveys 18 :muokattava? (fn [t] (not (nil? (:id t))))
       :fmt           pvm/pvm-opt
       :tayta-alas?   #(not (nil? %))
       :tayta-fn      tayta-takuupvm
       :tayta-tooltip "Kopioi sama takuupvm alla oleville kohteille"}
      {:otsikko     "Päätös" :nimi :paatos-tekninen-osa :muokattava? (constantly false) :tyyppi :komponentti
       :leveys      20
       :komponentti (fn [rivi]
                      [paallystys-ja-paikkaus/nayta-paatos (:paatos-tekninen-osa rivi)])}
      {:otsikko "Päällystys\u00ADilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly true) :leveys 25
       :tyyppi :komponentti
       :komponentti (fn [rivi]
                      (if (:tila rivi)
                        [:button.nappi-toissijainen.nappi-grid
                         {:on-click #(avaa-paallystysilmoitus-handler e! rivi)}
                         [:span (ikonit/eye-open) " Päällystysilmoitus"]]
                        [:button.nappi-toissijainen.nappi-grid {:on-click #(avaa-paallystysilmoitus-handler e! rivi)}
                         [:span "Aloita päällystysilmoitus"]]))}]
     paallystysilmoitukset]))

(defn- nayta-lahetystiedot [rivi kohteet-yha-lahetyksessa]
  (if (some #(= % (:paallystyskohde-id rivi)) kohteet-yha-lahetyksessa)
    [:span.tila-odottaa-vastausta "Lähetys käynnissä " [yleiset/ajax-loader-pisteet]]
    (if (:lahetetty rivi)
      (if (:lahetys-onnistunut rivi)
        [:span.tila-lahetetty
         (str "Lähetetty onnistuneesti: " (pvm/pvm-aika (:lahetetty rivi)))]
        [:span.tila-virhe
         (str "Lähetys epäonnistunut: " (pvm/pvm-aika (:lahetetty rivi)))])
      [:span "Ei lähetetty"])))

(defn- yha-lahetykset-taulukko [e! app]
  (let [lahetys-kaynnissa-fn #(e! (paallystys/->MuutaTila [:kohteet-yha-lahetyksessa] %))
        kun-onnistuu-fn #(e! (paallystys/->YHAVientiOnnistui %))
        kun-virhe-fn #(e! (paallystys/->YHAVientiEpaonnistui %))
        edellinen-yha-lahetys-komponentti (fn [rivi _ kohteet-yha-lahetyksessa]
                                            [nayta-lahetystiedot rivi kohteet-yha-lahetyksessa])
        laheta-yhaan-komponentti (fn [rivi _ urakka valittu-sopimusnumero valittu-urakan-vuosi kohteet-yha-lahetyksessa]
                                   (if (> valittu-urakan-vuosi 2019)
                                     [yha/yha-lahetysnappi {:oikeus       oikeudet/urakat-kohdeluettelo-paallystyskohteet :urakka-id (:id urakka) :sopimus-id (first valittu-sopimusnumero)
                                                            :vuosi        valittu-urakan-vuosi :paallystysilmoitukset [rivi] :lahetys-kaynnissa-fn lahetys-kaynnissa-fn
                                                            :kun-onnistuu kun-onnistuu-fn :kun-virhe kun-virhe-fn :kohteet-yha-lahetyksessa kohteet-yha-lahetyksessa}]
                                     [:div "Kohdetta ei voi enää lähettää."]))
        false-fn (constantly false)]
    (fn [e! {urakka                :urakka {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
             paallystysilmoitukset :paallystysilmoitukset kohteet-yha-lahetyksessa :kohteet-yha-lahetyksessa :as app}]
      [grid/grid
       {:otsikko  ""
        :tyhja    (if (nil? paallystysilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
        :tunniste hash}
       [{:otsikko "Kohde\u00ADnumero" :nimi :kohdenumero :muokattava? false-fn :tyyppi :numero :leveys 12}
        {:otsikko "Tunnus" :nimi :tunnus :muokattava? false-fn :tyyppi :string :leveys 14}
        {:otsikko "YHA-id" :nimi :yhaid :muokattava? false-fn :tyyppi :numero :leveys 15}
        {:otsikko "Nimi" :nimi :nimi :muokattava? false-fn :tyyppi :string :leveys 45}
        {:otsikko          "Edellinen lähetys YHAan" :nimi :edellinen-lahetys :muokattava? false-fn :tyyppi :reagent-komponentti
         :leveys           45
         :komponentti      edellinen-yha-lahetys-komponentti
         :komponentti-args [kohteet-yha-lahetyksessa]}
        (when (< 2019 valittu-urakan-vuosi)
        {:otsikko          "Lähetä YHAan" :nimi :laheta-yhan :muokattava? false-fn :leveys 20 :tyyppi :reagent-komponentti
         :komponentti      laheta-yhaan-komponentti
         :komponentti-args [urakka valittu-sopimusnumero valittu-urakan-vuosi kohteet-yha-lahetyksessa]})]
       paallystysilmoitukset])))


(defn- ilmoitusluettelo
  [e! app]
  (komp/luo
    (komp/sisaan #(nav/vaihda-kartan-koko! :M))
    (fn [e! {urakka                :urakka {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
             paallystysilmoitukset :paallystysilmoitukset kohteet-yha-lahetyksessa :kohteet-yha-lahetyksessa :as app}]
      (let [urakka-id (:id urakka)
            sopimus-id (first valittu-sopimusnumero)]
        [:div
         [:div
          [:h3 {:style {:display "inline-block"}}
           "Päällystysilmoitukset"]
          ;; HUOM! ei päästetä materiaalikirjastoa vielä tuotantoon, eli tämä oltava kommentoituna develop-haarassa
          #_[napit/nappi "Muokkaa urakan materiaaleja"
           #(e! (mk-tiedot/->NaytaModal true))
           {:ikoni (ikonit/livicon-pen)
            :luokka "napiton-nappi"
            :style {:background-color "#fafafa"
                    :margin-left "2rem"}}]]


         [paallystysilmoitukset-taulukko e! app]
         [:h3 "YHA-lähetykset"]
         [yleiset/vihje "Ilmoituksen täytyy olla merkitty valmiiksi ja kokonaisuudessaan hyväksytty ennen kuin se voidaan lähettää YHAan."]
         [yha-lahetykset-taulukko e! (select-keys app #{:urakka :urakka-tila :paallystysilmoitukset :kohteet-yha-lahetyksessa})]]))))

(defn valinnat [e! {:keys [urakka pot-jarjestys]}]
  [:div
   [valinnat/vuosi {}
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
                                                     (e! (paallystys/->SuodataYllapitokohteet)))]
   [yleiset/pudotusvalikko
    "Järjestä kohteet"
    {:valinta    pot-jarjestys
     :valitse-fn #(e! (paallystys/->JarjestaYllapitokohteet %))
     :format-fn  {:tila         "Tilan mukaan"
                  :kohdenumero  "Kohdenumeron mukaan"
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
    (fn [e! {:keys [urakka-tila paallystysilmoitus-lomakedata lukko urakka kayttaja] :as app}]
      [:div.paallystysilmoitukset
       [kartta/kartan-paikka]
       [debug app {:otsikko "TUCK STATE"}]
       ;; Toistaiseksi laitetaan sekä POT1 että POT2 tarvitsemat tiedot avaimeen
       ;; paallystysilmoitus-lomakedata, mutta tiedot tallennetaan eri rakenteella
       ;; Muistattava asettaa lomakedata arvoon nil, aina kun poistutaan lomakkeelta
       (if paallystysilmoitus-lomakedata
         (if (>= (:valittu-urakan-vuosi urakka-tila)
                 pot/pot2-vuodesta-eteenpain)
           [pot2-lomake/pot2-lomake e! (select-keys app #{:paallystysilmoitus-lomakedata
                                                          :massat :murskeet :materiaalikoodistot
                                                          :pot2-massa-lomake :pot2-murske-lomake})
            lukko urakka kayttaja]
           [pot1-lomake/pot1-lomake e! paallystysilmoitus-lomakedata lukko urakka kayttaja])
         [:div
          [valinnat e! (select-keys app #{:urakka :pot-jarjestys})]
          [ilmoitusluettelo e! app]])
       [massat-view/materiaalikirjasto-modal e! (select-keys app #{:massat :murskeet :materiaalikoodistot
                                                                   :pot2-massa-lomake :pot2-murske-lomake})]])))
