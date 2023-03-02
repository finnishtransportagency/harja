(ns harja.views.urakka.aikataulu
  "Ylläpidon urakoiden aikataulunäkymä"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [cljs-time.core :as t]
            [clojure.string :as str]

            [harja.domain.aikataulu :as aikataulu]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tiemerkinta :as tm-domain]
            [harja.domain.tierekisteri :as tr-domain]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :as yleiset :refer [vihje vihje-elementti]]
            [harja.ui.lomake :as lomake]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kumousboksi :as kumousboksi]
            [harja.ui.aikajana :as aikajana]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.ui.kentat :as kentat]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]

            [harja.tiedot.urakka.aikataulu :as tiedot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.tiedot.raportit :as raportit]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]

            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.tarkka-aikataulu :as tarkka-aikataulu]
            [harja.views.urakka.aikataulu-visuaalinen :as vis]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- vastaanottajien-tiedot [urakka-id]
  (komp/luo
    (komp/sisaan-ulos
      #(tiedot/hae-urakan-kayttajat-rooleissa urakka-id)

      #(tiedot/tyhjenna-kayttajatiedot))

    (fn [urakka-id]
      (when @tiedot/fimista-haetut-vastaanottajatiedot
        [:div.email-vastaanottajat
         [grid/muokkaus-grid
          {:otsikko "Valitse vastaanottajat" :voi-poistaa? (constantly false)
           :piilota-toiminnot? true :voi-lisata? false :voi-kumota? false
           :tyhja (if (nil? @tiedot/fimista-haetut-vastaanottajatiedot)
                    "Haetaan urakan henkilötietoja Väyläviraston palvelusta."
                    "Ulkoisesta palvelusta ei löytynyt vastaanottajien tietoja.")
           :jarjesta :roolit :tunniste :sahkoposti}

          [{:otsikko " " :nimi :valittu? :tasaa :keskita :tyyppi :checkbox :leveys 1 :vayla-tyyli? true}
           {:otsikko "Vastaanottaja" :nimi :sahkoposti :leveys 8
            :muokattava? (constantly false) :tyyppi :string}
           {:otsikko "Rooli" :nimi :roolit :leveys 7
            :muokattava? (constantly false) :tyyppi :string}]

          tiedot/fimista-haetut-vastaanottajatiedot]]))))

(defn valmis-tiemerkintaan-modal
  "Modaali, jossa joko merkitään kohde valmiiksi tiemerkintään tai perutaan aiemmin annettu valmius."
  []
  (let [{:keys [kohde-id urakka-id kohde-nimi vuosi valittu-lomake lomakedata
                nakyvissa?] :as data} @tiedot/valmis-tiemerkintaan-modal-data
        valmis-tiemerkintaan-lomake? (= :valmis-tiemerkintaan valittu-lomake)
        valmis-tallennettavaksi? (if valmis-tiemerkintaan-lomake?
                                   (some? (:valmis-tiemerkintaan lomakedata))
                                   true)]
    [modal/modal
     {:otsikko (if valmis-tiemerkintaan-lomake?
                 (str "Kohteen " kohde-nimi " merkitseminen valmiiksi tiemerkintään")
                 (str "Kohteen " kohde-nimi " tiemerkintävalmiuden peruminen"))
      :luokka "merkitse-valmiiksi-tiemerkintaan"
      :nakyvissa? nakyvissa?
      :sulje-fn #(swap! tiedot/valmis-tiemerkintaan-modal-data assoc :nakyvissa? false)
      :footer [:div
               [napit/palvelinkutsu-nappi
                (if valmis-tiemerkintaan-lomake?
                  "Merkitse valmiiksi ja lähetä viesti"
                  "Vahvista peruutus ja lähetä viesti")
                #(tiedot/merkitse-kohde-valmiiksi-tiemerkintaan
                   {:kohde-id kohde-id
                    :tiemerkintapvm (:valmis-tiemerkintaan lomakedata)
                    :kopio-itselle? (:kopio-itselle? lomakedata)
                    :saate (:saate lomakedata)
                    :muut-vastaanottajat (yleiset/sahkopostiosoitteet-str->set
                                           (:muut-vastaanottajat lomakedata))
                    :urakka-id urakka-id
                    :sopimus-id (first @u/valittu-sopimusnumero)
                    :vuosi vuosi})
                {:disabled (not valmis-tallennettavaksi?)
                 :luokka "nappi-ensisijainen pull-left"
                 :virheviesti "Lähetys epäonnistui. Yritä myöhemmin uudelleen."
                 :kun-onnistuu (fn [vastaus]
                                 (reset! tiedot/aikataulurivit vastaus)
                                 (swap! tiedot/valmis-tiemerkintaan-modal-data assoc :nakyvissa? false))}]
               [napit/peruuta
                (if valmis-tiemerkintaan-lomake?
                  "Peruuta"
                  "Älä perukaan")
                #(swap! tiedot/valmis-tiemerkintaan-modal-data assoc :nakyvissa? false)]]}
     [:div
      [vihje (if valmis-tiemerkintaan-lomake?
               "Päivämäärän asettamisesta lähetetään sähköpostilla tieto asianosaisille. Tarkista vastaanottajalista."
               "Tiemerkintävalmiuden perumisesta lähetetään sähköpostilla tieto asianosaisille. Tarkista vastaanottajalista.")
       "vihje-hento-korostus" 16]
      [vastaanottajien-tiedot (:suorittava-urakka-id data)]
      [lomake/lomake {:otsikko ""
                      :muokkaa! (fn [uusi-data]
                                  (reset! tiedot/valmis-tiemerkintaan-modal-data (merge data {:lomakedata uusi-data})))}
       [varmista-kayttajalta/modal-muut-vastaanottajat
        varmista-kayttajalta/modal-sahkopostikopio
        {:otsikko "" :tyyppi :komponentti :palstoja 3
         :komponentti (fn []
                        [:div.viestin-tiedot.fontti-16-vahvempi "Viestin tiedot"])}
        (when valmis-tiemerkintaan-lomake?
          {:otsikko "Tiemerkinnän saa aloittaa"
           :nimi :valmis-tiemerkintaan :pakollinen? true :tyyppi :pvm})
        varmista-kayttajalta/modal-saateviesti]
       lomakedata]]]))

(defn tiemerkinta-valmis
  "Modaali, jossa merkitään tiemerkintä valmiiksi.

   Kohteet on vector mappeja, joilla kohteen :id, :nimi ja :valmis-pvm"
  []
  (let [{:keys [kohteet urakka-id vuosi valittu-lomake lomakedata
                nakyvissa? muutos-taulukosta? valmis-fn peru-fn] :as data} @tiedot/tiemerkinta-valmis-modal-data]

    [modal/modal
     {:otsikko (if (= (count kohteet) 1)
                 (str "Kohteen " (:nimi (first kohteet)) " tiemerkinnän valmistuminen: " (if-let [valmis-pvm (:valmis-pvm (first kohteet))]
                                                                                           (pvm/pvm valmis-pvm)
                                                                                           "EI ASETETTU"))
                 (str "Usean kohteen tiemerkinnän valmistuminen"))
      ;:luokka "merkitse-valmiiksi-tiemerkintaan"
      :nakyvissa? nakyvissa?
      :sulje-fn #(do (swap! tiedot/tiemerkinta-valmis-modal-data assoc :nakyvissa? false)
                     (when peru-fn (peru-fn)))
      :footer [:div
               ;; Gridin kanssa tätä ei voi perua, sillä maili tullaan lähettämään joka tapauksessa
               ;; gridin tallennuksen yhteydessä.
               ;; Aikajanan kanssa muutosta ei tallenneta, jos perutaan toiminto modalista.
               (when-not muutos-taulukosta?
                 [napit/peruuta "Peruuta"
                  #(do (swap! tiedot/tiemerkinta-valmis-modal-data assoc :nakyvissa? false)
                       (when peru-fn (peru-fn)))])
               [napit/yleinen-ensisijainen
                ;; Olennainen ero: aikajanan kanssa muutos tullaan tallentamaan heti,
                ;; gridin kanssa vasta kun gridi tallennetaan.
                (if muutos-taulukosta? "Hyväksy" "Tallenna")
                #(do (swap! tiedot/tiemerkinta-valmis-modal-data assoc :nakyvissa? false)
                     (valmis-fn lomakedata))
                {:luokka "nappi-myonteinen"
                 :ikoni (ikonit/check)}]]}
     [:div
      [vihje-elementti
       [:span
        [:span
         [:span "Kohteen tiemerkinnän valmistumisen asettamisesta tai muuttamisesta lähetetään sähköpostilla tieto päällystys- ja tiemerkintäurakan urakanvalvojalle, rakennuttajakonsultille ja vastuuhenkilölle, mikäli valmistumispäivämäärä on tänään tai menneisyydessä. Muutoin sähköposti lähetetään valmistumispäivänä."]
         (if muutos-taulukosta?
           [:span.bold (str " Tämän kohteen sähköposti lähetetään "
                            (if (pvm/sama-tai-ennen? (:valmis-pvm (first kohteet)) (t/now))
                              "heti, kun tallennat muutokset taulukosta"
                              (str "valmistuspäivämääränä " (fmt/pvm-opt (:valmis-pvm (first kohteet)))))
                            ".")])]
        [:br] [:br]
        [:span "Halutessasi voit lisätä lähetettävään sähköpostiin ylimääräisiä vastaanottajia sekä vapaaehtoisen saateviestin."]]
       "vihje-hento-korostus"]
      [lomake/lomake {:otsikko ""
                      :muokkaa! (fn [uusi-data]
                                  (reset! tiedot/tiemerkinta-valmis-modal-data (merge data {:lomakedata uusi-data})))}
       [varmista-kayttajalta/modal-muut-vastaanottajat
        varmista-kayttajalta/modal-saateviesti
        varmista-kayttajalta/modal-sahkopostikopio]
       lomakedata]]]))

(defn- paallystys-aloitettu-validointi
  "Validoinnit päällystys aloitettu -kentälle"
  [optiot]
  (as-> [[:pvm-kentan-jalkeen :aikataulu-kohde-alku
          "Päällystys ei voi alkaa ennen kohteen aloitusta."]] validointi

        ;; Päällystysnäkymässä validoidaan, että alku on annettu
        (if (= (:nakyma optiot) :paallystys)
          (conj validointi
                [:toinen-arvo-annettu-ensin :aikataulu-kohde-alku
                 "Päällystystä ei voi merkitä alkaneeksi ennen kohteen aloitusta."])
          validointi)))

(defn- oikeudet
  "Tarkistaa aikataulunäkymän tarvitsemat oikeudet"
  [urakka-id]
  (let [saa-muokata?
        (oikeudet/voi-kirjoittaa? oikeudet/urakat-aikataulu urakka-id)

        saa-asettaa-valmis-takarajan?
        (oikeudet/on-muu-oikeus? "TM-takaraja"
                                 oikeudet/urakat-aikataulu
                                 urakka-id
                                 @istunto/kayttaja)

        saa-merkita-valmiiksi?
        (oikeudet/on-muu-oikeus? "TM-valmis"
                                 oikeudet/urakat-aikataulu
                                 urakka-id
                                 @istunto/kayttaja)]
    {:saa-muokata? saa-muokata?
     :saa-asettaa-valmis-takarajan? saa-asettaa-valmis-takarajan?
     :saa-merkita-valmiiksi? saa-merkita-valmiiksi?
     :voi-tallentaa? (or saa-muokata?
                         saa-merkita-valmiiksi?
                         saa-asettaa-valmis-takarajan?)}))


(defn- otsikoi-aikataulurivit
  "Lisää väliotsikot valmiille, keskeneräisille ja aloittamatta oleville kohteille."
  [{:keys [valmis kesken aloittamatta] :as _luokitellut-rivit}]
  (concat (when-not (empty? valmis)
            (into [(grid/otsikko "Valmiit kohteet")]
                  valmis))
          (when-not (empty? kesken)
            (into [(grid/otsikko "Keskeneräiset kohteet")]
                  kesken))
          (when-not (empty? aloittamatta)
            (into [(grid/otsikko "Aloittamatta olevat kohteet")]
                  aloittamatta))))

(defn valinnat [ur paallystys?]
  (let [{jarjestys :jarjestys} @tiedot/valinnat]
    [:span.aikataulu-valinnat.flex-row.alkuun.venyta
     [valinnat/urakan-vuosi ur {:vayla-tyyli? true}]
     [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero nil {:kentan-parametrit {:vayla-tyyli? true}
                                                                            :komponentin-optiot {:otsikon-luokka "alasvedon-otsikko-vayla"}}]
     [valinnat/tienumero yllapito-tiedot/tienumero nil {:kentan-parametrit {:vayla-tyyli? true}
                                                                            :komponentin-optiot {:otsikon-luokka "alasvedon-otsikko-vayla"}}]

     [yleiset/pudotusvalikko
      "Järjestä kohteet"
      {:valinta jarjestys
       :vayla-tyyli? true
       :valitse-fn tiedot/jarjesta-kohteet!
       :format-fn {:aika "Päällystyksen aloitusajan mukaan"
                   :paallystyksen-loppu "Päällystyksen lopetusajan mukaan"
                   :tiemerkinnan-voidaan-aloittaa "Tiemerkinnän voidaan aloittaa -ajan mukaan"
                   :tiemerkinnan-alku "Tiemerkinnän aloitusajan mukaan"
                   :tiemerkinnan-loppu "Tiemerkinnän lopetusajan mukaan"
                   :tiemerkinnan-valmis-viimeistaan "Tiemerkinnän valmis viimeistään ajan mukaan"
                   :paallystyskohde-valmis "Päällystyskohde valmis ajan mukaan"
                   :kohdenumero "Kohdenumeron mukaan"
                   :tr "Tieosoitteen mukaan"}}
      (into [] (keep identity) 
        [:aika :paallystyksen-loppu :tiemerkinnan-voidaan-aloittaa :tiemerkinnan-alku 
         :tiemerkinnan-loppu :tiemerkinnan-valmis-viimeistaan (when paallystys? :paallystyskohde-valmis) 
         :kohdenumero :tr])]

     [kentat/tee-otsikollinen-kentta
      {:otsikko "Aikajana"
       :otsikon-luokka "alasvedon-otsikko-vayla"
       :luokka "label-ja-kentta-puolikas"
       :kentta-params {:tyyppi :toggle
                       :vayla-tyyli? true
                       :paalle-teksti "Näytä aikajana"
                       :pois-teksti "Piilota aikajana"
                       :toggle! tiedot/toggle-nayta-aikajana!}
       :arvo-atom tiedot/nayta-aikajana?}]
     [kentat/tee-otsikko-ja-kentat
      {:otsikko "Aikajanan asetukset"
       :luokka "label-ja-kentta"
       :otsikon-luokka "alasvedon-otsikko-vayla"
       :kentat [{:kentta-params {:tyyppi :checkbox
                                 :teksti "Näytä tarkka aikataulu"}
                 :arvo-atom tiedot/nayta-tarkka-aikajana?}
                {:kentta-params {:tyyppi :checkbox
                                 :teksti "Näytä välitavoitteet"}
                 :arvo-atom tiedot/nayta-valitavoitteet?}]}]

     (let [parametrit (raportit/urakkaraportin-parametrit 
                        (:id ur) 
                        :yllapidon-aikataulu
                        {:jarjestys jarjestys
                         :nayta-tarkka-aikajana? @tiedot/nayta-tarkka-aikajana?
                         :nayta-valitavoitteet? @tiedot/nayta-valitavoitteet?
                         :vuosi @u/valittu-urakan-vuosi})] 
       [upotettu-raportti/raportin-vientimuodot 
        (assoc parametrit 
          :otsikko "PDF"
          :kasittelija :pdf)
        (assoc parametrit
          :otsikko "Excel"
          :kasittelija :excel)
        (-> parametrit 
          (assoc       
            :otsikko "Alikohteiden Excel"
            :kasittelija :excel)
          (assoc-in [:parametrit :alikohderaportti?] true))])]))

(defn- nayta-yhteystiedot?
  [rivi nakyma]
  (case nakyma
    :paallystys
    (:suorittava-tiemerkintaurakka rivi)
    true))

(defn- paallystysurakan-tarkka-aikataulu
  [urakka-id sopimus-id rivi vuosi nakyma voi-muokata-paallystys? voi-muokata-tiemerkinta?]
  [tarkka-aikataulu/tarkka-aikataulu
   {:rivi rivi
    :vuosi vuosi
    :nakyma nakyma
    :voi-muokata-paallystys? (voi-muokata-paallystys?)
    :voi-muokata-tiemerkinta? (voi-muokata-tiemerkinta? rivi)
    :urakka-id urakka-id
    :sopimus-id sopimus-id}])

(defn valmis-tiemerkintaan-sarake
  [rivi {:keys [muokataan? paallystys-valmis? suorittava-urakka-annettu? modalin-params
                voi-muokata-paallystys? optiot] :as data}]
  (if-not (= (:nakyma optiot) :paallystys)
    (if (:valmis-tiemerkintaan rivi)
      [:span (pvm/pvm-ilman-samaa-vuotta (:valmis-tiemerkintaan rivi) (:vuosi modalin-params))]
      [:span "Ei"])
    ;; Jos päällystyksessä, sopivilla oikeuksilla saa asettaa tai perua valmiuden
    (if muokataan?
      [:div (pvm/pvm-ilman-samaa-vuotta (:valmis-tiemerkintaan rivi) (:vuosi modalin-params))]
      [:div {:title (cond (not paallystys-valmis?) "Päällystys ei ole valmis."
                          (not suorittava-urakka-annettu?) "Tiemerkinnän suorittava urakka puuttuu."
                          :default nil)}

       (grid/arvo-ja-nappi
         {:sisalto (cond (not voi-muokata-paallystys?) :pelkka-arvo
                         (not (:valmis-tiemerkintaan rivi)) :pelkka-nappi
                         :default :arvo-ja-nappi)
          :pelkka-nappi-teksti "Aseta pvm"
          :pelkka-nappi-toiminto-fn #(reset! tiedot/valmis-tiemerkintaan-modal-data (merge modalin-params
                                                                                           {:nakyvissa? true
                                                                                            :valittu-lomake :valmis-tiemerkintaan}))
          :arvo-ja-nappi-napin-teksti "Peru"
          :arvo-ja-nappi-toiminto-fn #(reset! tiedot/valmis-tiemerkintaan-modal-data (merge modalin-params
                                                                                            {:nakyvissa? true
                                                                                             :valittu-lomake :peru-valmius-tiemerkintaan}))
          :nappi-optiot {:disabled (or
                                     (not paallystys-valmis?)
                                     (not suorittava-urakka-annettu?))}
          :arvo (pvm/pvm-opt (:valmis-tiemerkintaan rivi))})])))

(defn tiemerkinta-takarajan-voi-maarittaa-kasin?
  [{:keys [valmis-tiemerkintaan aikataulu-tiemerkinta-takaraja merkinta jyrsinta] :as rivi}
   {:keys [saa-asettaa-valmis-takarajan? optiot]}]
  (and
    (= :tiemerkinta (:nakyma optiot))
    saa-asettaa-valmis-takarajan?
    valmis-tiemerkintaan
    (or aikataulu-tiemerkinta-takaraja
        (and (not jyrsinta)
             (= :muu merkinta)))))


(defn aseta-tiemerkinta-valmis
  [rivi arvo]
  (assoc rivi :aikataulu-tiemerkinta-takaraja arvo
              ;; Jos käyttäjä haluaa automaattisen laskennan uudelleen käyttöön, on asetettava
              ;; pvm nilliksi, mikä puolestaan asettaa käsin-booleanin falseksi
              :aikataulu-tiemerkinta-takaraja-kasin (boolean arvo)))

(defn aseta-tiemerkinta-lisatieto
  [rivi arvo]
  (assoc rivi :aikataulu-tiemerkinta-lisatieto arvo))

(def ohje-syota-ensin-merkinta-ja-jyrsinta "Syötä ensin Merkintä ja Jyrsintä, jotta Valmis viimeistään -päivämäärä voidaan laskea automaattisesti tallennuksen yhteydessä.\n\n")

(def tarkat-kestot-merkinta-ja-jyrsinat "Massavaatimusteillä uusien päällysteiden tiemerkinnät tulee olla tehtynä 2 viikon kuluessa yksittäisen päällystyskohteen työn ilmoitetusta valmistumisesta seuraavasta arkipäivästä.\n\nTäristävien merkintöjen jyrsintäkohteiden merkinnät tulee olla tehtynä 3 viikon kuluessa yksittäisen päällystyskohteen työn ilmoitetusta valmistumisesta seuraavasta arkipäivästä. Jyrsinnät tulee olla tehtynä ennen kunkin kohteen merkintöjen tekemistä.\n\nMaalivaatimusteillä uusien päällysteiden tiemerkinnät tulee olla tehtynä 3 viikon kuluessa yksittäisen päällystyskohteen työn ilmoitetusta valmistumisesta seuraavasta arkipäivästä.")

(def ohje-tm-takaraja-kasin "Tiemerkinnän takaraja on laskettu automaattisesti merkintä- ja jyrsintätiedon perusteella. Sitä on kuitenkin mahdollista muokata myös käsin.")
(def ohje-tm-takaraja-muokattu-kasin "Takarajaa on jo muokattu käsin. Voit muokata uudestaaankin.")

(def muokkaa-tm-takarajaa (atom false))

(defn tiemerkinnan-takarajan-sarake
  "Gridin sarake, joka käsittelee tiemerkinnän takarajan näyttämisen ja asettamisen erityisten sääntöjen (Figma) pohjalta."
  [rivi params]
  (let [takarajan-saa-asettaa-kasin? (tiemerkinta-takarajan-voi-maarittaa-kasin? rivi params)
        kasin-syotetty-takaraja-atom (atom (:aikataulu-tiemerkinta-takaraja rivi))]
    (fn [{:keys [aikataulu-tiemerkinta-takaraja aikataulu-tiemerkinta-takaraja-kasin] :as rivi}
         {:keys [komp-muokkaa-fn muokataan? vuosi optiot] :as _params}]
      (cond
        (= :paallystys (:nakyma optiot))
        [:span (pvm/pvm-ilman-samaa-vuotta aikataulu-tiemerkinta-takaraja vuosi)]

        (and @muokkaa-tm-takarajaa muokataan? takarajan-saa-asettaa-kasin?)
        [:span.grid-kentta-wrapper
         [kentat/tee-kentta {:tyyppi :pvm :nimi :tiemerkinta-takaraja
                             :on-datepicker-select #(do
                                                      (komp-muokkaa-fn rivi %)
                                                      (reset! muokkaa-tm-takarajaa false))}
          kasin-syotetty-takaraja-atom]]

        (and muokataan? takarajan-saa-asettaa-kasin?)
        [:div.takarajan-kasin-syotto
         [:div (pvm/pvm-ilman-samaa-vuotta aikataulu-tiemerkinta-takaraja vuosi)]
         [yleiset/wrap-if true
          [yleiset/tooltip {} :% (if aikataulu-tiemerkinta-takaraja-kasin
                                   ohje-tm-takaraja-muokattu-kasin
                                   ohje-tm-takaraja-kasin)]
          [:span.tm-takaraja-muokkaa
           [yleiset/linkki (if aikataulu-tiemerkinta-takaraja-kasin
                             "Muokattu"
                             "Muokkaa") #(reset! muokkaa-tm-takarajaa true)]]]]

        (nil? aikataulu-tiemerkinta-takaraja)
        [:div
         [:span "?"]
         [yleiset/wrap-if true
          [yleiset/tooltip {} :% (str ohje-syota-ensin-merkinta-ja-jyrsinta
                                      tarkat-kestot-merkinta-ja-jyrsinat)]
          [napit/nappi ""
           #(println "on click")
           {:luokka "napiton-nappi tm-takaraja-info-btn"
            :ikoninappi? true
            :ikoni (ikonit/status-info-inline-svg nil)}]]]

        :else
        [:span
         (pvm/pvm-ilman-samaa-vuotta aikataulu-tiemerkinta-takaraja vuosi)
         (when aikataulu-tiemerkinta-takaraja-kasin
           [:div.bold "Muokattu"])]))))

(defn tm-lisatiedon-muokkaus
  "Mahdollistaa tiemerkinnän lisätiedon muokkaamisen"
  [rivi {:keys [nayta-lisatieto-modal?
                komp-muokkaa-fn
                muokataan?]}]
  (let [lisatiedot-atom (atom (:aikataulu-tiemerkinta-lisatieto rivi))
        kohdenumero (:kohdenumero rivi)
        nimi (:nimi rivi)
        kohteen-otsikko (str "Kohteen "
                             (when kohdenumero (str kohdenumero " "))
                             (when nimi (str nimi " "))
                             "lisätiedot")]
    [:div.lisatiedon-muokkaus-leijuke-wrapper
     [leijuke/leijuke {:otsikko kohteen-otsikko
                       :luokka "tm-lisatieto-leijuke"
                       :sulje! #(reset! nayta-lisatieto-modal? false)
                       :ankkuri "lisaa-nappi" :suunta :alas
                       :tallenna-fn (when muokataan?
                                      #(do
                                         (komp-muokkaa-fn rivi @lisatiedot-atom)
                                         (reset! nayta-lisatieto-modal? false)))
                       :tyhjenna-fn (when muokataan?
                                      #(reset! lisatiedot-atom nil))
                       :peruuta-fn (when muokataan?
                                     #(reset! nayta-lisatieto-modal? false))
                       :sulje-fn (when-not muokataan?
                                   #(reset! nayta-lisatieto-modal? false))}
      [:span.tm-leijuke-sisalto
       [kentat/tee-otsikollinen-kentta {:otsikko "Lisätiedot"
                                        :kentta-params {:nimi :aikataulu-tiemerkinta-lisatieto :tyyppi :text
                                                        :palstoja 3 :koko [80 5] :disabled? (not muokataan?)}
                                        :arvo-atom lisatiedot-atom
                                        :luokka ""}]]]]))

(defn tm-lisatietojen-sarake
  "Gridin sarake, joka näyttää ja muokkaa tiemerkintään liittyviä lisätietoja."
  [_ _]
  (let [nayta-lisatieto-modal? (atom false)]
    (fn [{:keys [aikataulu-tiemerkinta-lisatieto] :as rivi}
         {:keys [muokataan? komp-muokkaa-fn]}]
      (cond
        @nayta-lisatieto-modal?
        [tm-lisatiedon-muokkaus rivi {:nayta-lisatieto-modal? nayta-lisatieto-modal?
                                      :komp-muokkaa-fn komp-muokkaa-fn
                                      :muokataan? muokataan?}]

        (and muokataan? (not aikataulu-tiemerkinta-lisatieto))
        [:span.aikataulu-toiminnot
         [:div
          [napit/nappi-hover-vihjeella
           {:tyyppi :lisaa
            :hover-txt "Voit lisätä tiemerkintään liittyvää lisätietoa."
            :toiminto #(reset! nayta-lisatieto-modal? true)}]]]

        :else
        (if aikataulu-tiemerkinta-lisatieto
          [yleiset/linkki (if muokataan?
                            "Muok\u00ADkaa"
                            "Lue")
           #(reset! nayta-lisatieto-modal? true)]
          [:span (str aikataulu-tiemerkinta-lisatieto)])))))

(defn taulukon-ryhmittely-header
  [nakyma]
  (case nakyma
    :tiemerkinta
    [{:teksti "" :sarakkeita 5 :luokka "paallystys-tausta"}
     {:teksti "Päällystys" :sarakkeita 3 :luokka "paallystys-tausta-tumma"}
     {:teksti "Tiemerkintä" :sarakkeita 7 :luokka "tiemerkinta-tausta"}]

    ;; kaikki muut, käytännössä :paallystys
    [{:teksti "" :sarakkeita 5 :luokka "paallystys-tausta"}
     {:teksti "Päällystys" :sarakkeita 3 :luokka "paallystys-tausta-tumma"}
     {:teksti "Tiemerkintä" :sarakkeita 6 :luokka "tiemerkinta-tausta"}
     {:teksti "" :sarakkeita 1 :luokka "paallystys-tausta-tumma"}]))

(defn aikataulu-grid
  [{:keys [urakka-id urakka sopimus-id aikataulurivit urakkatyyppi
           vuosi voi-muokata-paallystys? voi-muokata-tiemerkinta?
           voi-tallentaa? saa-muokata? optiot saa-asettaa-valmis-takarajan?
           saa-merkita-valmiiksi? voi-muokata-paallystys? voi-muokata-tiemerkinta?]}]
  (let [aikataulurivit (map (fn [rivi]
                              (assoc rivi :aikataulu-tiemerkinta-loppu-alkuperainen (:aikataulu-tiemerkinta-loppu rivi)))
                            aikataulurivit)
        otsikoidut-aikataulurivit (if (tiedot/aikapohjainen-jarjestys-valittu? (:jarjestys @tiedot/valinnat))
                                    (otsikoi-aikataulurivit
                                      (tiedot/aikataulurivit-valmiuden-mukaan aikataulurivit urakkatyyppi))
                                    aikataulurivit)]
    [grid/grid
     {:otsikko [:span
                [:div.inline-block
                 "Kohteiden aikataulu"]
                [yllapitokohteet-view/vasta-muokatut-vinkki]]
      :rivi-ennen (taulukon-ryhmittely-header (:nakyma optiot))
      :voi-poistaa? (constantly false)
      :voi-lisata? false
      :voi-kumota? false ; Muuten voisi, mutta tiemerkinnän dialogin tietojen kumous vaatisi oman toteutuksen
      :piilota-toiminnot? true
      :salli-valiotsikoiden-piilotus? true
      :tyhja (if (nil? @tiedot/aikataulurivit)
               [yleiset/ajax-loader "Haetaan kohteita..."] "Ei kohteita")
      :ennen-muokkausta (fn []
                          ;; Hankalaa kumota visuaalisia muutoksia, jos gridiä muokataan.
                          (kumousboksi/ala-ehdota-kumoamista!))
      :tallenna (if voi-tallentaa?
                  #(tiedot/tallenna-aikataulu urakka-id sopimus-id vuosi %
                                              (fn [vastaus]
                                                (reset! tiedot/aikataulurivit vastaus)
                                                (reset! tiedot/kohteiden-sahkopostitiedot nil)))
                  :ei-mahdollinen)
      :vetolaatikot (yllapitokohteet-view/alikohteiden-vetolaatikot urakka-id
                                                                    (first @u/valittu-sopimusnumero)
                                                                    (atom aikataulurivit)
                                                                    (:kohdetyyppi optiot)
                                                                    (if (= :tiemerkinta (:nakyma optiot))
                                                                           false
                                                                           true)
                                                                    #{:raekoko :massamaara :toimenpide :tr-muokkaus}
                                                                    {:fn paallystysurakan-tarkka-aikataulu
                                                                     :nakyma (:nakyma optiot)
                                                                     :voi-muokata-paallystys? voi-muokata-paallystys?
                                                                     :voi-muokata-tiemerkinta? voi-muokata-tiemerkinta?}
                                                                    true)}
     [{:tyyppi :vetolaatikon-tila :leveys 2}
      {:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :leveys 3 :nimi :kohdenumero :tyyppi :komponentti
       :pituus-max 128 :muokattava? voi-muokata-paallystys? :otsikkorivi-luokka "kohdenumero-th"
       :komponentti yllapitokohteet-view/rivin-kohdenumero-ja-kello}
      {:otsikko "Koh\u00ADteen nimi" :leveys 8 :nimi :nimi :tyyppi :string :pituus-max 128
       :muokattava? voi-muokata-paallystys?}
      {:otsikko "TR-osoite" :nimi :tr-numero
       :tyyppi :tierekisteriosoite :leveys 6 :tasaa :oikea
       :hae #(select-keys % [:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys])
       :fmt #(tr-domain/tierekisteriosoite-tekstina % {:teksti-tie? false})
       :muokattava? (constantly false)}
      {:otsikko "Pit. (m)" :nimi :pituus :leveys 3
       :tyyppi :positiivinen-numero
       :tasaa :oikea
       :muokattava? (constantly false)}
      (when (= (:nakyma optiot) :paallystys) ;; Asiakkaan mukaan ei tarvi näyttää tiemerkkareille
        {:otsikko "Koh\u00ADteen aloi\u00ADtus" :leveys 8 :nimi :aikataulu-kohde-alku
         :tyyppi :pvm :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
         :otsikkorivi-luokka "paallystys-tausta-tumma"
         :muokattava? voi-muokata-paallystys?})
      {:otsikko "Aloi\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-alku
       :tyyppi :pvm :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :otsikkorivi-luokka "paallystys-tausta-tumma"
       :muokattava? voi-muokata-paallystys?
       :pvm-tyhjana #(:aikataulu-kohde-alku %)
       :validoi (paallystys-aloitettu-validointi optiot)}
      {:otsikko "Lope\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-loppu
       :tyyppi :pvm :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :otsikkorivi-luokka "paallystys-tausta-tumma"
       :pvm-tyhjana #(:aikataulu-paallystys-alku %)
       :muokattava? voi-muokata-paallystys?
       :validoi [[:toinen-arvo-annettu-ensin :aikataulu-paallystys-alku
                  "Päällystystä ei ole merkitty aloitetuksi."]
                 [:pvm-kentan-jalkeen :aikataulu-paallystys-alku
                  "Valmistuminen ei voi olla ennen aloitusta."]
                 [:ei-tyhja-jos-toinen-arvo-annettu :valmis-tiemerkintaan
                  "Arvoa ei voi poistaa, koska kohde on merkitty valmiiksi tiemerkintään"]
                 [:ei-tyhja-jos-toinen-arvo-annettu :aikataulu-paallystys-alku
                  "Anna päällystyksen valmistumisen aika tai aika-arvio."]]}
      (when (= (:nakyma optiot) :paallystys)
        {:otsikko "Tie\u00ADmer\u00ADkin\u00ADtäu\u00ADrak\u00ADka"
         :leveys 10 :nimi :suorittava-tiemerkintaurakka
         :tyyppi :valinta
         :otsikkorivi-luokka "tiemerkinta-tausta"
         :fmt (fn [arvo]
                (:nimi (some
                         #(when (= (:id %) arvo) %)
                         @tiedot/tiemerkinnan-suorittavat-urakat)))
         :valinta-arvo :id
         :valinta-nayta #(if % (:nimi %) "- Valitse urakka -")
         :valinnat @tiedot/tiemerkinnan-suorittavat-urakat
         :nayta-ryhmat [:sama-hallintayksikko :eri-hallintayksikko]
         :ryhmittely #(if (= (:hallintayksikko %) (:id (:hallintayksikko urakka)))
                        :sama-hallintayksikko
                        :eri-hallintayksikko)
         :ryhman-otsikko #(case %
                            :sama-hallintayksikko "Hallintayksikön tiemerkintäurakat"
                            :eri-hallintayksikko "Muut tiemerkintäurakat")
         :muokattava? (fn [rivi] (and saa-muokata? (:tiemerkintaurakan-voi-vaihtaa? rivi)))})
      {:otsikko "Yh\u00ADte\u00ADys\u00ADtie\u00ADdot"
       :leveys 4
       :nimi :yhteystiedot
       :tasaa :keskita
       :tyyppi :komponentti
       :otsikkorivi-luokka (if (= (:nakyma optiot) :tiemerkinta)
                             "paallystys-tausta-tumma"
                             "tiemerkinta-tausta")
       :komponentti (fn [rivi]
                      [napit/yleinen-toissijainen ""
                       #(yllapito-yhteyshenkilot/nayta-yhteyshenkilot-modal!
                          {:yllapitokohde-id (:id rivi)
                           :urakkatyyppi (case (:nakyma optiot)
                                           :tiemerkinta :paallystys
                                           :paallystys :tiemerkinta)
                           ;; Tuetaan urakan tietojen viemistä modaaliin vain tiemerkintäurakassa,
                           ;; koska päällystyksessä tiemerkintäurakalla oma sarake
                           :yhteysurakan-id (when (= :tiemerkinta (:nakyma optiot))
                                              (:urakka rivi))
                           :yhteysurakan-nimi (when (= :tiemerkinta (:nakyma optiot))
                                                (:paallystysurakka rivi))
                           :oikeus-paallystysurakkaan? (oikeudet/voi-lukea? oikeudet/urakat-aikataulu (:urakka rivi))})
                       {:disabled (not (nayta-yhteystiedot? rivi (:nakyma optiot)))
                        :ikoni (ikonit/user)
                        :luokka "btn-xs"}])}
      ;; Tiemerkintä-puoli näkee, Merkintä ja Jyrsinnät
      (when (= (:nakyma optiot) :tiemerkinta)
        {:otsikko "Merkin\u00ADtä\u00ADluok\u00ADka" :valinta-nayta #(tm-domain/merkinta-ja-jyrsinta-fmt % "Valitse")
         :fmt tm-domain/merkinta-ja-jyrsinta-fmt
         :leveys 6 :nimi :aikataulu-tiemerkinta-merkinta :tyyppi :valinta
         :otsikkorivi-luokka "tiemerkinta-tausta"
         :valinnat tm-domain/merkinta-vaihtoehdot
         :muokattava? (constantly voi-muokata-tiemerkinta?)})
      (when (= (:nakyma optiot) :tiemerkinta)
        {:otsikko "Jyrsin\u00ADtä" :valinta-nayta #(tm-domain/merkinta-ja-jyrsinta-fmt % "Valitse")
         :fmt tm-domain/merkinta-ja-jyrsinta-fmt
         :leveys 6 :nimi :aikataulu-tiemerkinta-jyrsinta :tyyppi :valinta
         :otsikkorivi-luokka "tiemerkinta-tausta"
         :valinnat tm-domain/jyrsinta-vaihtoehdot
         :muokattava? (constantly voi-muokata-tiemerkinta?)})


      {:otsikko "Voi\u00ADdaan aloit\u00ADtaa" :leveys 9
       :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :pvm-tyhjana #(:aikataulu-paallystys-loppu %)
       :otsikkorivi-luokka "tiemerkinta-tausta"
       :nimi :valmis-tiemerkintaan :tyyppi :komponentti :muokattava? (constantly saa-muokata?)
       :komponentti (fn [rivi {:keys [muokataan?]}]
                      (let [paallystys-valmis? (some? (:aikataulu-paallystys-loppu rivi))
                            suorittava-urakka-annettu? (some? (:suorittava-tiemerkintaurakka rivi))
                            modalin-params {:kohde-id (:id rivi)
                                            :kohde-nimi (:nimi rivi)
                                            :urakka-id urakka-id
                                            :vuosi vuosi
                                            :paallystys-valmis? (some? (:aikataulu-paallystys-loppu rivi))
                                            :suorittava-urakka-annettu? (some? (:suorittava-tiemerkintaurakka rivi))
                                            :suorittava-urakka-id (:suorittava-tiemerkintaurakka rivi)
                                            :lomakedata {:kopio-itselle? true}}]
                        [valmis-tiemerkintaan-sarake rivi {:muokataan? muokataan?
                                                           :paallystys-valmis? paallystys-valmis?
                                                           :suorittava-urakka-annettu? suorittava-urakka-annettu?
                                                           :modalin-params modalin-params
                                                           :voi-muokata-paallystys? voi-muokata-paallystys?
                                                           :optiot optiot}]))}
      {:otsikko "Val\u00ADmis vii\u00ADmeis\u00ADtään"
       :leveys 6 :nimi :aikataulu-tiemerkinta-takaraja :tyyppi :komponentti
       :otsikkorivi-luokka "tiemerkinta-tausta"
       :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :aseta aseta-tiemerkinta-valmis
       :komponentti (fn [rivi {:keys [muokataan? komp-muokkaa-fn]}]
                      [tiemerkinnan-takarajan-sarake rivi {:muokataan? muokataan?
                                                           :komp-muokkaa-fn komp-muokkaa-fn
                                                           :saa-asettaa-valmis-takarajan? saa-asettaa-valmis-takarajan?
                                                           :vuosi vuosi
                                                           :optiot optiot}])
       :muokattava? (fn [rivi]
                      (tiemerkinta-takarajan-voi-maarittaa-kasin? rivi
                                                                  {:saa-asettaa-valmis-takarajan? saa-asettaa-valmis-takarajan?
                                                                   :optiot optiot}))}
      {:otsikko "Aloi\u00ADtus"
       :leveys 6 :nimi :aikataulu-tiemerkinta-alku :tyyppi :pvm
       :otsikkorivi-luokka "tiemerkinta-tausta"
       :pvm-tyhjana #(:aikataulu-paallystys-loppu %)
       :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :muokattava? voi-muokata-tiemerkinta?}
      {:otsikko "Lope\u00ADtus"
       :leveys 6 :nimi :aikataulu-tiemerkinta-loppu :tyyppi :pvm
       :otsikkorivi-luokka "tiemerkinta-tausta"
       :pvm-tyhjana #(:aikataulu-tiemerkinta-alku %)
       :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :aseta (fn [rivi arvo]
                ;; Näytä dialogi, mikäli arvoa muutetaan
                (when (not (or (pvm/sama-pvm? (:aikataulu-tiemerkinta-loppu-alkuperainen rivi) arvo)
                               (and (nil? (:aikataulu-tiemerkinta-loppu-alkuperainen rivi)) (nil? arvo))))
                  (let [;; Näytetään modalissa aiemmat sähköpostitiedot, jos on (joko palvelimen palauttamat, tulevaisuudessa
                        ;; lähetettävät postit, tai ennen gridin tallennusta tehdyt muokkaukset).
                        aiemmat-sahkopostitiedot (merge
                                                   (:sahkopostitiedot rivi)
                                                   (get @tiedot/kohteiden-sahkopostitiedot (:id rivi)))]
                    (reset! tiedot/tiemerkinta-valmis-modal-data
                            {:nakyvissa? true
                             :muutos-taulukosta? true
                             :valmis-fn (fn [lomakedata]
                                          (swap! tiedot/kohteiden-sahkopostitiedot assoc (:id rivi)
                                                 {:muut-vastaanottajat (set (map :sahkoposti (vals (:muut-vastaanottajat lomakedata))))
                                                  :saate (:saate lomakedata)
                                                  :kopio-itselle? (:kopio-itselle? lomakedata)}))
                             :kohteet [{:id (:id rivi)
                                        :nimi (:nimi rivi)
                                        :valmis-pvm arvo}]
                             :urakka-id urakka-id
                             :vuosi vuosi

                             :lomakedata {:kopio-itselle? (or (:kopio-itselle? aiemmat-sahkopostitiedot) true)
                                          :muut-vastaanottajat (zipmap (iterate inc 1)
                                                                       (map #(-> {:sahkoposti %})
                                                                            (:muut-vastaanottajat aiemmat-sahkopostitiedot)))
                                          :saate (:saate aiemmat-sahkopostitiedot)}})))
                (assoc rivi :aikataulu-tiemerkinta-loppu arvo))
       :muokattava? voi-muokata-tiemerkinta?
       :validoi [[:toinen-arvo-annettu-ensin :aikataulu-tiemerkinta-alku
                  "Tiemerkintää ei ole merkitty aloitetuksi."]
                 [:pvm-kentan-jalkeen :aikataulu-tiemerkinta-alku
                  "Valmistuminen ei voi olla ennen aloitusta."]
                 [:ei-tyhja-jos-toinen-arvo-annettu :aikataulu-tiemerkinta-alku
                  "Anna tiemerkinnän valmistumisen aika tai aika-arvio."]]}
      (when (= (:nakyma optiot) :paallystys)
        {:otsikko "Koh\u00ADde val\u00ADmis" :leveys 6 :nimi :aikataulu-kohde-valmis :tyyppi :pvm
         :otsikkorivi-luokka "paallystys-tausta-tumma"
         :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
         :muokattava? voi-muokata-paallystys?
         :pvm-tyhjana #(:aikataulu-paallystys-loppu %)
         :validoi [[:pvm-kentan-jalkeen :aikataulu-kohde-alku
                    "Kohde ei voi olla valmis ennen kuin se on aloitettu."]]})
      (when (= :tiemerkinta (:nakyma optiot))
        {:otsikko "Lisä\u00ADtie\u00ADto"
         :leveys 2 :nimi :aikataulu-tiemerkinta-lisatieto :tyyppi :komponentti
         :otsikkorivi-luokka "tiemerkinta-tausta"
         :aseta aseta-tiemerkinta-lisatieto
         :komponentti (fn [rivi {:keys [muokataan? komp-muokkaa-fn]}]
                        [tm-lisatietojen-sarake rivi {:muokataan? muokataan?
                                                      :komp-muokkaa-fn komp-muokkaa-fn}])
         :muokattava? voi-muokata-tiemerkinta?})]
     otsikoidut-aikataulurivit]))

(defn aikataulu
  [_ _]
  (komp/luo
    (komp/lippu tiedot/aikataulu-nakymassa?)
    (fn [urakka optiot]
      (let [{urakka-id :id :as ur} @nav/valittu-urakka
            sopimus-id (first @u/valittu-sopimusnumero)
            aikataulurivit @tiedot/aikataulurivit-suodatettu-jarjestetty
            urakkatyyppi (:tyyppi urakka)
            vuosi @u/valittu-urakan-vuosi
            {:keys [voi-tallentaa? saa-muokata?
                    saa-asettaa-valmis-takarajan?
                    saa-merkita-valmiiksi?]} (oikeudet urakka-id)

            voi-muokata-paallystys? #(boolean (and (= (:nakyma optiot) :paallystys)
                                                   saa-muokata?))
            voi-muokata-tiemerkinta? (fn [rivi] (boolean (and (= (:nakyma optiot) :tiemerkinta)
                                                              saa-merkita-valmiiksi?
                                                              (:valmis-tiemerkintaan rivi))))
            aikajana? (:nayta-aikajana? @tiedot/valinnat)
            paallystys? (= (:nakyma optiot) :paallystys)]
        [:div.aikataulu
         [valinnat ur paallystys?]
         [vis/visuaalinen-aikataulu {:urakka-id urakka-id
                                     :aikajana? aikajana?
                                     :sopimus-id sopimus-id
                                     :aikataulurivit aikataulurivit
                                     :urakkatyyppi urakkatyyppi
                                     :vuosi vuosi
                                     :optiot optiot
                                     :voi-muokata-paallystys? voi-muokata-paallystys?
                                     :voi-muokata-tiemerkinta? voi-muokata-tiemerkinta?}]
         [:div.aikataulu-spacer]
         [aikataulu-grid {:urakka-id urakka-id
                          :urakka urakka
                          :sopimus-id sopimus-id
                          :aikataulurivit aikataulurivit
                          :urakkatyyppi urakkatyyppi
                          :vuosi vuosi
                          :voi-tallentaa? voi-tallentaa?
                          :saa-muokata? saa-muokata?
                          :optiot optiot
                          :saa-asettaa-valmis-takarajan? saa-asettaa-valmis-takarajan?
                          :saa-merkita-valmiiksi? saa-merkita-valmiiksi?
                          :voi-muokata-paallystys? voi-muokata-paallystys?
                          :voi-muokata-tiemerkinta? voi-muokata-tiemerkinta?}]
         [valmis-tiemerkintaan-modal]
         [tiemerkinta-valmis]]))))
