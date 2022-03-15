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
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [vihje vihje-elementti]]
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
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tyokalut.functor :refer [fmap]]

            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.tarkka-aikataulu :as tarkka-aikataulu]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet-view]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

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
               [napit/peruuta
                (if valmis-tiemerkintaan-lomake?
                  "Peruuta"
                  "Älä perukaan")
                #(swap! tiedot/valmis-tiemerkintaan-modal-data assoc :nakyvissa? false)]

               [napit/palvelinkutsu-nappi
                (if valmis-tiemerkintaan-lomake?
                  "Merkitse"
                  "Vahvista peruutus")
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
                 :luokka "nappi-myonteinen"
                 :ikoni (ikonit/check)
                 :virheviesti "Lähetys epäonnistui. Yritä myöhemmin uudelleen."
                 :kun-onnistuu (fn [vastaus]
                                 (reset! tiedot/aikataulurivit vastaus)
                                 (swap! tiedot/valmis-tiemerkintaan-modal-data assoc :nakyvissa? false))}]]}
     [:div
      [vihje (if valmis-tiemerkintaan-lomake?
               "Päivämäärän asettamisesta lähetetään sähköpostilla tieto tiemerkintäurakan urakanvalvojalle, rakennuttajakonsultille ja vastuuhenkilölle."
               "Kohteen tiemerkintävalmiuden perumisesta lähetetään sähköpostilla tieto tiemerkintäurakan urakanvalvojalle, rakennuttajakonsultille ja vastuuhenkilölle.")]
      [lomake/lomake {:otsikko ""
                      :muokkaa! (fn [uusi-data]
                                  (reset! tiedot/valmis-tiemerkintaan-modal-data (merge data {:lomakedata uusi-data})))}
       [(when valmis-tiemerkintaan-lomake?
          {:otsikko "Tiemerkinnän saa aloittaa"
           :nimi :valmis-tiemerkintaan :pakollinen? true :tyyppi :pvm})
        varmista-kayttajalta/modal-muut-vastaanottajat
        varmista-kayttajalta/modal-saateviesti
        varmista-kayttajalta/modal-sahkopostikopio]
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
         [:span "Kohteen tiemerkinnän valmistumisen asettamisesta tai muuttamisesta lähetetään sähköpostilla tieto päällystysurakan urakanvalvojalle, rakennuttajakonsultille ja vastuuhenkilölle, mikäli valmistumispäivämäärä on tänään tai menneisyydessä. Muutoin sähköposti lähetetään valmistumispäivänä."]
         (if muutos-taulukosta?
           [:span.bold (str " Tämän kohteen sähköposti lähetetään "
                            (if (pvm/sama-tai-ennen? (:valmis-pvm (first kohteet)) (t/now))
                              "heti, kun tallennat muutokset taulukosta"
                              (str "valmistuspäivämääränä " (fmt/pvm-opt (:valmis-pvm (first kohteet)))))
                            ".")])]
        [:br] [:br]
        [:span "Halutessasi voit lisätä lähetettävään sähköpostiin ylimääräisiä vastaanottajia sekä vapaaehtoisen saateviestin."]]]
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
  [{:keys [valmis kesken aloittamatta] :as luokitellut-rivit}]
  (concat (when-not (empty? valmis)
            (into [(grid/otsikko "Valmiit kohteet")]
                  valmis))
          (when-not (empty? kesken)
            (into [(grid/otsikko "Keskeneräiset kohteet")]
                  kesken))
          (when-not (empty? aloittamatta)
            (into [(grid/otsikko "Aloittamatta olevat kohteet")]
                  aloittamatta))))

(defn valinnat [ur]
  (let [{aikajana? :nayta-aikajana?
         jarjestys :jarjestys
         :as valinnat} @tiedot/valinnat]
    [:span.aikataulu-valinnat
     [valinnat/urakan-vuosi ur]
     [valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero]
     [valinnat/tienumero yllapito-tiedot/tienumero]

     [yleiset/pudotusvalikko
      "Järjestä kohteet"
      {:valinta jarjestys
       :valitse-fn tiedot/jarjesta-kohteet!
       :format-fn {:aika "Päällystyksen aloitus"
                   :paallystyksen-loppu "Päällystyksen lopetus"
                   :tiemerkinnan-alku "Tiemerkinnän aloitus"
                   :tiemerkinnan-loppu "Tiemerkinnän lopetus"
                   :tiemerkinnan-valmius "Tiemerkintävalmius"
                   :tiemerkinnan-takaraja "Tiemerkinnän takaraja"
                   :kohdenumero "Kohdenumero"
                   :tr "Tieosoite"}}
      [:aika :paallystyksen-loppu :tiemerkinnan-alku :tiemerkinnan-loppu
       :tiemerkinnan-valmius :tiemerkinnan-takaraja :kohdenumero :tr]]

     [kentat/tee-otsikollinen-kentta
      {:otsikko "Aikajana"
       :luokka "label-ja-kentta-puolikas"
       :kentta-params {:tyyppi :toggle
                       :paalle-teksti "Näytä aikajana"
                       :pois-teksti "Piilota aikajana"
                       :toggle! tiedot/toggle-nayta-aikajana!}
       :arvo-atom tiedot/nayta-aikajana?}]
     [kentat/tee-otsikko-ja-kentat
      {:otsikko "Aikajanan asetukset"
       :luokka "label-ja-kentta"
       :kentat [{:kentta-params {:tyyppi :checkbox
                                 :teksti "Näytä tarkka aikataulu"}
                 :arvo-atom tiedot/nayta-tarkka-aikajana?}
                {:kentta-params {:tyyppi :checkbox
                                 :teksti "Näytä välitavoitteet"}
                 :arvo-atom tiedot/nayta-valitavoitteet?}]}]

     [upotettu-raportti/raportin-vientimuodot
      (raportit/urakkaraportin-parametrit (:id ur) :yllapidon-aikataulu
                                          {:jarjestys jarjestys
                                           :nayta-tarkka-aikajana? @tiedot/nayta-tarkka-aikajana?
                                           :nayta-valitavoitteet? @tiedot/nayta-valitavoitteet?
                                           :vuosi @u/valittu-urakan-vuosi})]]))

(defn- nayta-yhteystiedot?
  [rivi nakyma]
  (case nakyma
    :paallystys
    (:suorittava-tiemerkintaurakka rivi)
    true))

(defn visuaalinen-aikataulu
  [{:keys [urakka-id sopimus-id aikataulurivit urakkatyyppi aikajana? optiot
           vuosi voi-muokata-paallystys? voi-muokata-tiemerkinta?]}]
  (when aikajana?
    [:div
     [kumousboksi/kumousboksi {:nakyvissa? (kumousboksi/ehdotetaan-kumamomista?)
                               :nakyvissa-sijainti {:top "250px" :right 0 :bottom "auto" :left "auto"}
                               :piilossa-sijainti {:top "250px" :right "-170px" :bottom "auto" :left "auto"}
                               :kumoa-fn #(kumousboksi/kumoa-muutos! (fn [edellinen-tila kumottu-fn]
                                                                       (tiedot/tallenna-aikataulu
                                                                         urakka-id sopimus-id vuosi edellinen-tila
                                                                         (fn [vastaus]
                                                                           (reset! tiedot/aikataulurivit vastaus)
                                                                           (kumottu-fn)))))
                               :sulje-fn kumousboksi/ala-ehdota-kumoamista!}]
     [leijuke/otsikko-ja-vihjeleijuke 6 "Aikajana"
      {:otsikko "Visuaalisen muokkauksen ohje"}
      [leijuke/multipage-vihjesisalto
       [:div
        [:h6 "Aikajanan alun / lopun venytys"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus.gif"
                ;; Kuva ei lataudu heti -> leijukkeen korkeus määrittyy väärin -> avautumissuunta määrittyy väärin -> asetetaan height
                :style {:height "200px"}}]
         [:figcaption
          [:p "Tartu hiiren kursorilla kiinni janan alusta tai lopusta, raahaa eteen- tai taaksepäin pitämällä nappia pohjassa ja päästämällä irti. Muutos tallennetaan heti."]]]]
       [:div
        [:h6 "Aikajanan siirtäminen"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus2.gif"
                :style {:height "200px"}}]
         [:figcaption
          [:p "Tartu hiiren kursorilla kiinni janan keskeltä, raahaa eteen- tai taaksepäin pitämällä nappia pohjassa ja päästämällä irti. Muutos tallennetaan heti."]]]]
       [:div
        [:h6 "Usean aikajanan siirtäminen"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus3.gif"
                :style {:height "200px"}}]
         [:figcaption
          [:p "Paina CTRL-painike pohjaan ja klikkaa aikajanaa valitakseksi sen. Siirrä aikajanaa normaalisti, jolloin kaikki aikajanat liikkuvat samaan suuntaan yhtä paljon."]
          [:p "Voit perua janan valinnan CTRL-klikkaamalla sitä uudestaan. Voit perua kaikkien janojen valinnan klikkaamalla tyhjään alueeseen. Valinnat poistuvat myös sivua vaihtamalla."]]]]
       [:div
        [:h6 "Usean aikajanan alun / lopun venytys"]
        [:figure
         [:img {:src "images/yllapidon_aikataulu_visuaalisen_muokkauksen_ohje_raahaus4.gif"
                :style {:height "200px"}}]
         [:figcaption
          [:p "Paina CTRL-painike pohjaan ja klikkaa aikajanaa valitakseksi sen. Venytä aikajanaa normaalisti alusta tai lopusta, jolloin kaikki aikajanat venyvät samaan suuntaan yhtä paljon."]]]]]]
     [aikajana/aikajana
      {:ennen-muokkausta
       (fn [drag valmis! peru!]
         ;; Näytä modal jos raahattujen joukossa oli tiemerkinnän valmistumispvm, muuten tallenna suoraan
         (let [tiemerkinnan-valmistumiset (filter #(and (= (second (::aikajana/drag %)) :tiemerkinta)
                                                        (not (pvm/sama-pvm? (::aikajana/loppu %) (::aikajana/alkup-loppu %))))
                                                  drag)]
           (if (not (empty? tiemerkinnan-valmistumiset))
             ;; Tehdään muokaus modalin kautta.
             ;; Mikäli siirrettiin useampaa kohdetta, niin modalilla on tarkoitus antaa samat
             ;; mailitiedot kaikille muokatuille kohteille.
             (reset! tiedot/tiemerkinta-valmis-modal-data
                     {:valmis-fn (fn [lomakedata]
                                   ;; Lisätään modalissa kirjoitetut mailitiedot kaikille muokatuille kohteille
                                   (doseq [kohde-id (map #(first (::aikajana/drag %)) tiemerkinnan-valmistumiset)]
                                     (swap! tiedot/kohteiden-sahkopostitiedot assoc kohde-id
                                            {:muut-vastaanottajat (yleiset/sahkopostiosoitteet-str->set
                                                                    (:muut-vastaanottajat lomakedata))
                                             :saate (:saate lomakedata)
                                             :kopio-itselle? (:kopio-itselle? lomakedata)}))
                                   (valmis!))
                      :peru-fn peru!
                      :nakyvissa? true
                      :kohteet (map (fn [raahaus]
                                      (-> {:id (first (::aikajana/drag raahaus))
                                           :nimi (::aikajana/kohde-nimi raahaus)
                                           :valmis-pvm (::aikajana/loppu raahaus)}))
                                    drag)
                      :urakka-id urakka-id
                      :vuosi vuosi
                      ;; Ota olemassa olevat sähköpostitiedot ja "yhdistä" ne koskemaan kaikkia
                      ;; muokattuja kohteita.
                      :lomakedata {:kopio-itselle? (or (some :kopio-lahettajalle? (map ::aikajana/sahkopostitiedot tiemerkinnan-valmistumiset))
                                                       true)
                                   :muut-vastaanottajat (zipmap (iterate inc 1)
                                                                (map #(-> {:sahkoposti %})
                                                                     (set (mapcat (fn [jana] (get-in jana [::aikajana/sahkopostitiedot :muut-vastaanottajat]))
                                                                                  tiemerkinnan-valmistumiset))))
                                   :saate (str/join " " (map #(get-in % [::aikajana/sahkopostitiedot :saate])
                                                             tiemerkinnan-valmistumiset))}})
             ;; Ei muokattujen tiemerkintöjen valmistumisia, tallenna suoraan
             (valmis!))))
       :muuta! (fn [drag]
                 (go (let [paivitetty-aikataulu (aikataulu/raahauksessa-paivitetyt-aikataulurivit aikataulurivit drag)
                           paivitetyt-aikataulu-idt (set (map :id paivitetty-aikataulu))
                           paivitettyjen-vanha-tila (filter #(paivitetyt-aikataulu-idt (:id %)) @tiedot/aikataulurivit)]

                       (if (aikataulu/aikataulu-validi? paivitetty-aikataulu)
                         (when (not (empty? paivitetty-aikataulu)) ; Tallenna ja ehdota kumoamista vain jos muutoksia
                           (<! (tiedot/tallenna-aikataulu urakka-id sopimus-id vuosi paivitetty-aikataulu
                                                          (fn [vastaus]
                                                            (reset! tiedot/aikataulurivit vastaus)
                                                            (kumousboksi/ehdota-kumoamista! paivitettyjen-vanha-tila)))))
                         (viesti/nayta! "Virheellistä päällystysajankohtaa ei voida tallentaa!" :danger)))))}
      (some->> aikataulurivit
        (map #(aikataulu/aikataulurivi-jana % {:nakyma (:nakyma optiot)
                                              :urakka-id urakka-id
                                              :voi-muokata-paallystys? voi-muokata-paallystys?
                                              :voi-muokata-tiemerkinta? voi-muokata-tiemerkinta?
                                              :nayta-tarkka-aikajana? @tiedot/nayta-tarkka-aikajana?
                                              :nayta-valitavoitteet? @tiedot/nayta-valitavoitteet?}))
               (filter #(not (empty? (:harja.ui.aikajana/ajat %)))))]]))

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

(def ohje-syota-ensin-merkinta-ja-jyrsinta "Syötä ensin Merkintä ja Jyrsintä, jotta Valmis viimeistään -päivämäärä voidaan laskea automaattisesti tallennuksen yhteydessä.\n\nTiemerkintäajan laskenta alkaa seuraavasta arkipäivästä, ja on maalivaatimustien ja jyrsinnän tapauksessa 21vrk, massavaatimustiellä 14vrk.")

(def ohje-tm-takaraja-kasin "Tiemerkinnän takaraja on laskettu automaattisesti merkintä- ja jyrsintätiedon perusteella. Sitä on kuitenkin mahdollista muokata myös käsin.")
(def ohje-tm-takaraja-muokattu-kasin "Takarajaa on jo muokattu käsin. Voit muokata uudestaaankin.")

(def muokkaa-tm-takarajaa (atom false))

(defn tiemerkinnan-takarajan-sarake
  "Gridin sarake, joka käsittelee tiemerkinnän takarajan näyttämisen ja asettamisen erityisten sääntöjen (Figma) pohjalta."
  [{:keys [valmis-tiemerkintaan aikataulu-tiemerkinta-takaraja aikataulu-tiemerkinta-takaraja-kasin merkinta jyrsinta] :as rivi}
   {:keys [komp-muokkaa-fn muokataan? saa-asettaa-valmis-takarajan? vuosi optiot] :as params}]
  (let [takarajan-saa-asettaa-kasin? (tiemerkinta-takarajan-voi-maarittaa-kasin? rivi params)
        kasin-syotetty-takaraja-atom (atom (:aikataulu-tiemerkinta-takaraja rivi))]
    (fn [{:keys [valmis-tiemerkintaan aikataulu-tiemerkinta-takaraja aikataulu-tiemerkinta-takaraja-kasin merkinta jyrsinta] :as rivi}
         {:keys [komp-muokkaa-fn muokataan? saa-asettaa-valmis-takarajan? vuosi optiot] :as params}]
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
          [yleiset/tooltip {} :% ohje-syota-ensin-merkinta-ja-jyrsinta]
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
  [{:keys [aikataulu-tiemerkinta-lisatieto] :as rivi}
   {:keys [muokataan? komp-muokkaa-fn]}]
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

(defn aikataulu-grid
  [{:keys [urakka-id urakka sopimus-id aikataulurivit urakkatyyppi
           vuosi voi-muokata-paallystys? voi-muokata-tiemerkinta?
           voi-tallentaa? saa-muokata? optiot saa-asettaa-valmis-takarajan?
           saa-merkita-valmiiksi? voi-muokata-paallystys? voi-muokata-tiemerkinta?]}]
  (let [aikataulurivit (map (fn [rivi]
                              (assoc rivi :aikataulu-tiemerkinta-loppu-alkuperainen (:aikataulu-tiemerkinta-loppu rivi)))
                            aikataulurivit)
        otsikoidut-aikataulurivit (if (= :aika (:jarjestys @tiedot/valinnat))
                                    (otsikoi-aikataulurivit
                                      (tiedot/aikataulurivit-valmiuden-mukaan aikataulurivit urakkatyyppi))
                                    aikataulurivit)]
    [grid/grid
     {:otsikko [:span
                [:div.inline-block
                 "Kohteiden aikataulu"
                 [yleiset/tietyoilmoitus-siirtynyt-toast]]
                [yllapitokohteet-view/vasta-muokatut-vinkki]]
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
      {:otsikko "PK-lk"
       :nimi :yllapitoluokka :leveys 4 :tyyppi :string
       :fmt yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi
       :muokattava? (constantly false)}
      (when (= (:nakyma optiot) :paallystys) ;; Asiakkaan mukaan ei tarvi näyttää tiemerkkareille
        {:otsikko "Koh\u00ADteen aloi\u00ADtus" :leveys 8 :nimi :aikataulu-kohde-alku
         :tyyppi :pvm :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
         :muokattava? voi-muokata-paallystys?})
      {:otsikko "Pääl\u00ADlystyk\u00ADsen aloi\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-alku
       :tyyppi :pvm :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :muokattava? voi-muokata-paallystys?
       :pvm-tyhjana #(:aikataulu-kohde-alku %)
       :validoi (paallystys-aloitettu-validointi optiot)}
      {:otsikko "Pääl\u00ADlystyk\u00ADsen lope\u00ADtus" :leveys 8 :nimi :aikataulu-paallystys-loppu
       :tyyppi :pvm :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
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
        {:otsikko "Tie\u00ADmer\u00ADkin\u00ADnän suo\u00ADrit\u00ADta\u00ADva u\u00ADrak\u00ADka"
         :leveys 10 :nimi :suorittava-tiemerkintaurakka
         :tyyppi :valinta
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
       :komponentti (fn [rivi]
                      [napit/yleinen-toissijainen ""
                       #(yllapito-yhteyshenkilot/nayta-yhteyshenkilot-modal!
                          (:id rivi)
                          (case (:nakyma optiot)
                            :tiemerkinta :paallystys
                            :paallystys :tiemerkinta))
                       {:disabled (not (nayta-yhteystiedot? rivi (:nakyma optiot)))
                        :ikoni (ikonit/user)
                        :luokka "btn-xs"}])}
      ;; Tiemerkintä-puoli näkee, Merkintä ja Jyrsinnät
      (when (= (:nakyma optiot) :tiemerkinta)
        {:otsikko "Merkin\u00ADtä" :valinta-nayta #(tm-domain/merkinta-ja-jyrsinta-fmt % "Valitse")
         :fmt tm-domain/merkinta-ja-jyrsinta-fmt
         :leveys 6 :nimi :aikataulu-tiemerkinta-merkinta :tyyppi :valinta
         :valinnat tm-domain/merkinta-vaihtoehdot
         :muokattava? (constantly voi-muokata-tiemerkinta?)})
      (when (= (:nakyma optiot) :tiemerkinta)
        {:otsikko "Jyrsin\u00ADtä" :valinta-nayta #(tm-domain/merkinta-ja-jyrsinta-fmt % "Valitse")
         :fmt tm-domain/merkinta-ja-jyrsinta-fmt
         :leveys 6 :nimi :aikataulu-tiemerkinta-jyrsinta :tyyppi :valinta
         :valinnat tm-domain/jyrsinta-vaihtoehdot
         :muokattava? (constantly voi-muokata-tiemerkinta?)})


      {:otsikko "Val\u00ADmis tie\u00ADmerkin\u00ADtään" :leveys 9
       :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :pvm-tyhjana #(:aikataulu-paallystys-loppu %)
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
                                            :lomakedata {:kopio-itselle? true}}]
                        [valmis-tiemerkintaan-sarake rivi {:muokataan? muokataan?
                                                           :paallystys-valmis? paallystys-valmis?
                                                           :suorittava-urakka-annettu? suorittava-urakka-annettu?
                                                           :modalin-params modalin-params
                                                           :voi-muokata-paallystys? voi-muokata-paallystys?
                                                           :optiot optiot}]))}
      {:otsikko "Tie\u00ADmerkin\u00ADtä val\u00ADmis vii\u00ADmeis\u00ADtään"
       :leveys 6 :nimi :aikataulu-tiemerkinta-takaraja :tyyppi :komponentti
       :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :aseta (fn [rivi arvo]
                (assoc rivi :aikataulu-tiemerkinta-takaraja arvo
                            :aikataulu-tiemerkinta-takaraja-kasin true))
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
      {:otsikko "Tiemer\u00ADkinnän aloi\u00ADtus"
       :leveys 6 :nimi :aikataulu-tiemerkinta-alku :tyyppi :pvm
       :pvm-tyhjana #(:aikataulu-paallystys-loppu %)
       :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
       :muokattava? voi-muokata-tiemerkinta?}
      {:otsikko "Tiemer\u00ADkinnän lope\u00ADtus"
       :leveys 6 :nimi :aikataulu-tiemerkinta-loppu :tyyppi :pvm
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
        {:otsikko "Pääl\u00ADlystys\u00ADkoh\u00ADde val\u00ADmis" :leveys 6 :nimi :aikataulu-kohde-valmis :tyyppi :pvm
         :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
         :muokattava? voi-muokata-paallystys?
         :pvm-tyhjana #(:aikataulu-paallystys-loppu %)
         :validoi [[:pvm-kentan-jalkeen :aikataulu-kohde-alku
                    "Kohde ei voi olla valmis ennen kuin se on aloitettu."]]})
      (when (= :tiemerkinta (:nakyma optiot))
        {:otsikko "Lisä\u00ADtieto"
         :leveys 2 :nimi :aikataulu-tiemerkinta-lisatieto :tyyppi :komponentti
         :aseta (fn [rivi arvo]
                  (assoc rivi :aikataulu-tiemerkinta-lisatieto arvo))
         :komponentti (fn [rivi {:keys [muokataan? komp-muokkaa-fn]}]
                        [tm-lisatietojen-sarake rivi {:muokataan? muokataan?
                                                      :komp-muokkaa-fn komp-muokkaa-fn}])
         :muokattava? voi-muokata-tiemerkinta?})]
     otsikoidut-aikataulurivit]))

(defn aikataulu
  [urakka optiot]
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
            aikajana? (:nayta-aikajana? @tiedot/valinnat)]
        [:div.aikataulu
         [valinnat ur]
         [visuaalinen-aikataulu {:urakka-id urakka-id
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
