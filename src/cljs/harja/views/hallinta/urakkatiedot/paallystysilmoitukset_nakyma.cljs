(ns harja.views.hallinta.urakkatiedot.paallystysilmoitukset-nakyma
  "Päällystysilmoitusten näkymä"
  (:require
   [harja.domain.oikeudet :as oikeudet]
   [harja.pvm :as pvm]
   [harja.tiedot.hallinta.paallystysilmoitukset-tiedot :as tiedot]
   [harja.tiedot.istunto :as istunto]
   [harja.ui.debug :as debug]
   [harja.ui.grid :as grid]
   [harja.ui.ikonit :as ikonit]
   [harja.ui.komponentti :as komp]
   [harja.ui.valinnat :as valinnat]
   [harja.ui.yleiset :as yleiset]
   [harja.domain.roolit :as roolit]
   [harja.loki :refer [log logt tarkkaile!]]
   [harja.ui.napit :as napit]
   [harja.asiakas.kommunikaatio :as k]
   [harja.ui.viesti :as viesti]
   [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as yhteiset]
   [tuck.core :refer [tuck]]))

(def valittu-vuosi 2024)

(defn- lahetys-epaonnistunut? [{:keys [lahetys-onnistunut lahetysvirhe velho-lahetyksen-tila] :as rivi}]
  (or (and (not lahetys-onnistunut) (not-empty lahetysvirhe))
    (= "epaonnistunut" velho-lahetyksen-tila)))

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
  (let [kohde-id (:paallystyskohde-id paallystysilmoitus)]
    [napit/palvelinkutsu-nappi
     (ikonit/ikoni-ja-teksti (ikonit/envelope) "Lähetä")
     #(do
        (log "[YHA/VELHO] Lähetetään urakan (id:" urakka-id ") sopimuksen (id: " sopimus-id
          ") kohde (id:" (pr-str kohde-id) ") YHA:n")
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
      :kun-valmis #(do)
      :kun-onnistuu (fn [vastaus]
                      (log "[YHA/VELHO] Lähetys onnistui urakan (id:" urakka-id ") sopimuksen (id: " sopimus-id
                        ") kohde (id:" (pr-str kohde-id) ") YHA:an. Vastaus: " (pr-str vastaus)))
      :kun-virhe (fn [vastaus]
                   (log "[YHA] Lähetys epäonnistui osalle kohteista YHAan. Vastaus: " (pr-str vastaus)))
      :nayta-virheviesti? false}]))

(defn- kaikki-lahetys-yha-velho-nappi [e! {:keys [oikeus urakka-id sopimus-id vuosi paallystysilmoitus]}]
  (let [ilmoituksen-voi-lahettaa? (fn [{:keys [paatos-tekninen-osa tila lahettaja] :as paallystysilmoitus}]
                                    (and (= :hyvaksytty paatos-tekninen-osa)
                                      (contains? #{:valmis :lukittu} tila)
                                      (nil? lahettaja)))
        kohde-id (map #(:paallystyskohde-id %) (filter ilmoituksen-voi-lahettaa? paallystysilmoitus))]
    [napit/palvelinkutsu-nappi
     (ikonit/ikoni-ja-teksti (ikonit/envelope) "Kehittäjä: Lähetä kaikki valmiit kohteet YHA:aan")
     #(do
        (log "[YHA/VELHO] Lähetetään urakan (id:" urakka-id ") sopimuksen (id: " sopimus-id
          ") kohde (id:" (pr-str kohde-id) ") YHA:n ja Velhoon (VELHO DISABLED)") ;; TODO enable VELHO
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
      :kun-valmis #(do)
      :kun-onnistuu (fn [vastaus]
                      (log "[YHA/VELHO] Lähetys onnistui urakan (id:" urakka-id ") sopimuksen (id: " (first sopimus-id)
                        ") kohde (id:" (pr-str kohde-id) ") YHA:an. Vastaus: " (pr-str vastaus)))
      :kun-virhe (fn [vastaus]
                   (log "[YHA] Lähetys epäonnistui osalle kohteista YHAan. Vastaus: " (pr-str vastaus)) ;; TODO enable VELHO
                   )
      :nayta-virheviesti? false}]))

(defn- laheta-pot-yhaan-velhoon-komponentti [rivi _ e! urakka valittu-sopimusnumero
                                             valittu-urakan-vuosi kohteet-yha-velho-lahetyksessa kayttaja]
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
        nayta-kehittajan-nappi? (and (roolit/jvh? kayttaja)
                                  (ilmoituksen-voi-lahettaa? rivi)
                                  (nil? (:lahettaja rivi)))
        nayta-lahetyksen-aika? (ilmoitus-on-lahetetty? rivi)
        nayta-lahetyksen-virhe? (lahetys-epaonnistunut? rivi)]
    (cond
      nayta-kielto?
      [:div "Kohdetta ei voi enää lähettää."]

      lahetys-kesken?
      [yleiset/ajax-loader-pieni "Lähetys käynnissä"]

      nayta-kehittajan-nappi?
      [:div
       "Kehittäjän lähetys:"
       [lahetys-yha-velho-nappi e! {:oikeus oikeudet/hallinta-paallystysilmoitukset
                                    :urakka-id (:id urakka) :sopimus-id (first valittu-sopimusnumero)
                                    :vuosi valittu-urakan-vuosi :paallystysilmoitus rivi
                                    :kohteet-yha-velho-lahetyksessa kohteet-yha-velho-lahetyksessa}]
       [:div "Lähetetty viimeksi: " (pvm/pvm-aika (:lahetetty rivi))]]
      nayta-nappi?
      [lahetys-yha-velho-nappi e! {:oikeus oikeudet/hallinta-paallystysilmoitukset
                                   :urakka-id (:id urakka) :sopimus-id (first valittu-sopimusnumero)
                                   :vuosi valittu-urakan-vuosi :paallystysilmoitus rivi
                                   :kohteet-yha-velho-lahetyksessa kohteet-yha-velho-lahetyksessa}]

      nayta-lahetyksen-aika?
      [:div
       [:span.lahetyksen-aika
        [ikonit/ikoni-ja-teksti [ikonit/harja-icon-status-selected] (pvm/pvm-aika (or (:velho-lahetyksen-aika rivi)
                                                                                           ;; YHA-lähetyksen aika = :lahetetty
                                                                                    (:lahetetty rivi)))]]
       [:div
        "Lähetä uudelleen vaikka jo lähetetty:"
        [lahetys-yha-velho-nappi e! {:oikeus oikeudet/hallinta-paallystysilmoitukset
                                     :urakka-id (:id urakka) :sopimus-id (first valittu-sopimusnumero)
                                     :vuosi valittu-urakan-vuosi :paallystysilmoitus rivi
                                     :kohteet-yha-velho-lahetyksessa kohteet-yha-velho-lahetyksessa}]]]

      :else nil)))

(defn paallystysilmoitukset* [e! app]
  (komp/luo
    (komp/sisaan #(do
                    (e! (tiedot/->HaePaallystysUrakat {:vuosi valittu-vuosi}))))
    (fn [e! {:keys [urakat valittu-urakka urakan-paallystysilmoitukset] :as app}]
      (let [valittu-sopimusnumero [(:sopimus-id valittu-urakka)]
            urakka-id (:id valittu-urakka)
            valittu-urakan-vuosi valittu-vuosi]
        [:div
         [debug/debug app]
         [:h2 "Päällystysilmoitukset"]
         [:div "Pot-tietojen uudelleenlähetys YHA:an vuodelle 2024 kehittäjille. Konsultoi YHA-tiimiä ennen kuin lähetät jo lähetettyjä kohteita uudelleen koska se voi johtaa käyttäjän muokkausten häviämiseen YHA:ssa."]
             ;; Urakan valinta
         [yleiset/pudotusvalikko
          "Valitse urakka"
          {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
           :valinta valittu-urakka
           :format-fn #(cond
                         (:nimi %) (str (:nimi %) " - " (:lahettaja-puuttuu %) " lähettämättä / " (:lahetetty-onnistuneesti %))
                         :else "Valitse urakka")}
          (:urakat urakat)]
         (when (and (roolit/jvh? @istunto/kayttaja) urakka-id)
           [kaikki-lahetys-yha-velho-nappi e! {:oikeus oikeudet/hallinta-paallystysilmoitukset
                                               :urakka-id urakka-id :sopimus-id valittu-sopimusnumero
                                               :vuosi valittu-vuosi :paallystysilmoitus urakan-paallystysilmoitukset}])
         (when (seq urakan-paallystysilmoitukset)
           [grid/grid
            {:otsikko ""
             :tunniste :paallystyskohde-id
             :tyhja (if (nil? urakan-paallystysilmoitukset) "Haetaan ilmoituksia..." "Ei ilmoituksia")
             :voi-lisata? false
             :voi-kumota? false
             :voi-poistaa? (constantly false)
             :voi-muokata? false
             :piilota-toiminnot? true
             :data-cy "paallystysilmoitukset-grid"}
            [{:otsikko "Kohde\u00ADnumero" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :string :leveys 14}
             {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :tyyppi :string :leveys 14 :pituus-max 2}
             {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 50 :pituus-max 50}
             {:otsikko "Lähetetty" :nimi :lahetetty :tyyppi :pvm :leveys 18 :fmt pvm/pvm-opt}
             {:otsikko "Lähettäjä" :nimi :lahettaja :tyyppi :string :leveys 18}
             {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm :leveys 18
              :fmt pvm/pvm-opt
              :tayta-alas? #(not (nil? %))
              :tayta-tooltip "Kopioi sama takuupvm alla oleville kohteille"}
             {:otsikko "Tila" :nimi :tila :muokattava? (constantly false)
              :tyyppi :komponentti :leveys 25
              :komponentti kuvaile-ilmoituksen-tila}
             (when (roolit/jvh? @istunto/kayttaja)
               {:otsikko "Lähetys YHA:an" :nimi :lahetys-yha-velho :muokattava? (constantly false) :tyyppi :reagent-komponentti
                :leveys 25
                :komponentti laheta-pot-yhaan-velhoon-komponentti
                :komponentti-args [e! valittu-urakka valittu-sopimusnumero valittu-urakan-vuosi nil @istunto/kayttaja]})]
            urakan-paallystysilmoitukset])]))))

(defn paallystysilmoitukset []
  [tuck tiedot/tila paallystysilmoitukset*])