(ns harja.views.urakka.valikatselmus.tavoitehinnan-muutokset
  (:require [reagent.core :as r :refer [atom]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.dom :as dom]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.views.urakka.valikatselmus.yhteiset :as valikatselmus-yhteiset]))

(defonce virheet-atom (atom {}))
(defonce tallenna-painettu (atom false))

(defn- rivi-painikkeet [e! uusi-id  muuttui?
                        uudet-simplified
                        tallennus-kesken? 
                        hoitokauden-alkuvuosi 
                        hoitokauden-oikaisut-atom
                        voi-muokata? rivilla-tyhja-elementti]
  [:div.painikkeet
   [napit/yleinen-toissijainen "Lisää rivi"
    #(do
       (reset! tallenna-painettu false)
       (swap! hoitokauden-oikaisut-atom assoc uusi-id
         {:id uusi-id
          ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
    {:ikoni (ikonit/livicon-plus)
     :disabled (or tallennus-kesken? (not voi-muokata?))}]

   [napit/yleinen-toissijainen "Tallenna muutokset"
    (if (and
          muuttui?
          (empty? @virheet-atom)
          (empty? rivilla-tyhja-elementti))
      #(do
         (reset! tallenna-painettu false)
         (e! (valikatselmus-tiedot/->TallennaOikaisut uudet-simplified hoitokauden-alkuvuosi)))
      #(do
         (valikatselmus-tiedot/scrollaa-muutoksiin)
         (reset! tallenna-painettu true)))
    {:disabled (or tallennus-kesken? (not voi-muokata?))}]])

(defn tavoitehinnan-oikaisut-taulukko
  "Tavoitehinnan oikaisujen taulukko.

  oikaisut-atom on hoitokausikohtainen atom, joka sisältää mapin.
  Välikatselmuksessa käytetään kursoria tuck-tilasta.

  Optiot ottaa vastaan:
  :voi-muokata?           Boolean, joka kertoo voiko muokata. Esim. roolit tai ajankohta voi pakottaa taulukon lukutilaan.
  :poista-oikaisu-fn      Funktio, jolla poistetaan oikaisu, esimerkiksi tuck-funktio joka tekee kutsun bäkkäriin.
  :tallenna-oikaisu-fn    Funktio, jolla tallennetaan oikaisu, esimerkiksi tuck-funktio joka tekee kutsun bäkkäriin.
  :tallenna-oikaisut-fn   Funktio, jolla päivitetään oikaisut, esimerkiksi tuck-funktio joka tekee kutsun bäkkäriin.
                          Kutsutaan jokaisesta muutoksesta."
  [e! hoitokauden-oikaisut-atom hoitokauden-alkuvuosi {:keys [voi-muokata? poista-oikaisu-fn]} tallennus-kesken?]
  (let [uusi-id (if (empty? (keys @hoitokauden-oikaisut-atom))
                  0
                  (inc (apply max (keys @hoitokauden-oikaisut-atom))))
        oikaisut-summa (when @hoitokauden-oikaisut-atom (fmt/euro-opt false true
                                                          (reduce
                                                            (fn [yhteensa hoitokauden-oikaisu]
                                                              (+ yhteensa (get hoitokauden-oikaisu :harja.domain.kulut.valikatselmus/summa)))
                                                            0
                                                            (vals @hoitokauden-oikaisut-atom))))
        alkuperaiset-oikaisut @valikatselmus-tiedot/tavoitehinnan-muutokset
        uudet-simplified (valikatselmus-tiedot/karsitut-tavoitehinnan-muutokset (vals @hoitokauden-oikaisut-atom))
        muuttui? (not (or (= uudet-simplified alkuperaiset-oikaisut) false))
        rivilla-tyhja-elementti (filter
                                  (fn [rivi]
                                    (or (nil? (::valikatselmus/hoitokauden-alkuvuosi rivi))
                                      (nil? (::valikatselmus/selite rivi))
                                      (nil? (::valikatselmus/otsikko rivi))
                                      (nil? (::valikatselmus/summa rivi))))
                                  uudet-simplified)]
    [:div.tavoitehinnan-muutokset
     [:div
      (when (or (and @tallenna-painettu (not (empty? @virheet-atom)))
              (and @tallenna-painettu (not (empty? rivilla-tyhja-elementti))))
        [:div.tallennus-varoitus
         [yleiset/info-laatikko :varoitus
          "Muutoksia ei voitu tallentaa. Tietoja puuttuu."
          nil nil {:sulje-nappi-id (gensym)}]])
      [:div
       [grid/muokkaus-grid
        {:tyhja "Ei muutoksia tavoitehintaan"
         :voi-kumota? false
         :voi-muokata? voi-muokata?
         :jarjesta-avaimen-mukaan identity
         ;; Älä anna käyttäjän naputella rivejä kesken tallennuksen
         :disabloi-rivi? (constantly tallennus-kesken?)
         :sisalto-kun-rivi-disabloitu :oletus
         :muutos #(do
                    (reset! tallenna-painettu false)
                    (reset! virheet-atom (grid/hae-virheet %)))
         ;; Roskakorinappula rivin päässä
         :toimintonappi-fn (fn [rivi _muokkaa! id]
                             (when (and voi-muokata? (not tallennus-kesken?))
                               [napit/poista ""
                                #(do
                                   (poista-oikaisu-fn rivi id))
                                {:luokka "napiton-nappi pelkka-ikoni"}]))
         :voi-lisata? false ;; Piilotetaan default lisää rivi -nappi. Se on korvattu custom-toiminnolla
         :validoi-uusi-rivi? false

         :uusi-id uusi-id
         :nayta-virheikoni? false
         :rivi-jalkeen (when @hoitokauden-oikaisut-atom
                         [{:teksti "Yhteensä" :luokka "yhteensa"}
                          {:teksti oikaisut-summa :sarakkeita 2 :tasaa :oikea :luokka "yhteensa-padding-oikea-24"}
                          {:teksti "" :sarakkeita 2 :luokka "yhteensa"}])}
        [{:otsikko "Muutos"
          :nimi ::valikatselmus/otsikko
          :tyyppi :valinta
          :valinnat (into [] (valikatselmus/luokat @nav/valittu-urakka))
          :validoi [[:ei-tyhja "Valitse arvo"]]
          :leveys 2
          :data-cy (str "luokka-" uusi-id)
          :elementin-id (str "luokka-" uusi-id)
          :aria-label "Muutos"}
         {:otsikko "Perustelu"
          :nimi ::valikatselmus/selite
          :tyyppi :text
          :koko [:auto 3]
          :validoi [[:ei-tyhja "Täytä arvo"]]
          :leveys 3
          :elementin-id (str "selite-" uusi-id)
          :aria-label "Perustelu"}
         {:otsikko "Vaikutus € (+/-)"
          :nimi ::valikatselmus/summa
          :tyyppi :euro
          :nayta-plus true
          :ei-yksikkoa? true
          :input-luokka "maara-input"
          :desimaalien-maara 2
          :validoi [[:ei-tyhja "Täytä arvo"]]
          :leveys 2
          :tasaa :oikea
          :fmt (partial fmt/euro-opt false true)
          :elementin-id (str "summa-" uusi-id)
          :aria-label "Vaikutus euroina"}]
        hoitokauden-oikaisut-atom]]
      
      ;; Tallenna / Lisää rivi 
      ;; Siirretty alas, koska "Vahvista päätös" voidaan sotkea tallenna- napiksi.
      (rivi-painikkeet e! uusi-id  muuttui?
        uudet-simplified
        tallennus-kesken?
        hoitokauden-alkuvuosi
        hoitokauden-oikaisut-atom
        voi-muokata? rivilla-tyhja-elementti)]]))

(defn kattohinnan-oikaisu
  "Kattohinnan oikaisua tarvitsevat urkat, jotka ovat alkaneet -19-20 vuosina. Muille kattohinta on 110% tavoitehinnasta."
  [e! kattohinta paatos-tehty? hoitokauden-alkuvuosi]
  (let [uusi-kattohinta (atom (if kattohinta kattohinta 0))]
    (if (not paatos-tehty?)
      [:<>
       [:div.valja
        [yleiset/info-laatikko :neutraali "Jos tavoitehinnan oikaisun myötä myös kattohinta muuttuu, syötä muuttunut kattohinta." nil nil]]
       [:div.flex-row
        [kentat/tee-otsikollinen-kentta {:otsikko "Muuttunut kattohinta"
                                         :otsikon-luokka "caption-small-strong"
                                         :luokka ""
                                         :kentta-params {:tyyppi :euro
                                                         :koko 20
                                                         :max-desimaalit 7
                                                         :kokonaisosan-maara 9
                                                         :fmt fmt/euro-opt
                                                         :vayla-tyyli? true
                                                         :input-luokka "kattohinta-muutettu"
                                                         :aria-label "Muuttunut kattohinta"
                                                         :on-blur #(when (not (= kattohinta @uusi-kattohinta))
                                                                     (e! (valikatselmus-tiedot/->TallennaKattohinnanOikaisu @uusi-kattohinta hoitokauden-alkuvuosi)))}
                                         :arvo-atom uusi-kattohinta}]]]
      [:<>
       [:div.small-caption.lihavoitu.valja "Muuttunut kattohinta"]
       [:div.flex-row.alkuun.valistys16
        [:span (fmt/euro-opt false kattohinta)]]])))

(defn tavoitehinnan-muutokset [e! paatos oikeudet-muokata? tallennus-kesken? avatut-paatokset tavoitehinnan-muutokset hoitovuosi-kesken?]
  (let [paatos-avain :tavoitehinnan-muutokset
        paatos-tehty? (boolean (:id paatos))
        hoitokauden-alkuvuosi (:hoitokauden_alkuvuosi paatos)
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        tavoitehinnan-muutokset (get tavoitehinnan-muutokset hoitokauden-alkuvuosi)
        kattohinta (:kattohinta paatos)
        hoitokauden-oikaisut-atom (atom tavoitehinnan-muutokset)
        poikkeusvuosi? (:muokkaa_kattohinta paatos)

        paatoksen-tiedot (merge
                           paatos
                           {:urakkaid (-> @tila/yleiset :urakka :id)})
        ;; Kattohintaa voi muokata 19/20 alkavat urakat
        kattohinnan-oikaisu-mahdollinen? (and
                                           (seq tavoitehinnan-muutokset)
                                           oikeudet-muokata?
                                           poikkeusvuosi?)]
    ^{:key (str "tavoitehinnan-muutokset-" (gensym))}
    [:div#tavhinnan-muutokset.paatos-komponentti-reunuksella
     
     (if hoitovuosi-kesken?
       [valikatselmus-yhteiset/paatosotsikko "Tavoitehinnan muutokset" paatos-tehty?]
       [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan muutokset" paatos-tehty? paatos-avain avatut-paatokset
        (partial valikatselmus-tiedot/avaa-tai-sulje-haitari)  (valikatselmus-tiedot/->AvaaPaatos paatos-avain)])
     
     (when tallennus-kesken?
       [yleiset/ajax-loader-pieni "Tallennetaan tietoja..."])
     
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [tavoitehinnan-oikaisut-taulukko e! hoitokauden-oikaisut-atom
         hoitokauden-alkuvuosi
         {:voi-muokata? (and on-oikeudet? (not paatos-tehty?))
          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
          :poista-oikaisu-fn #(e! (valikatselmus-tiedot/->PoistaOikaisu %1 %2))}
         tallennus-kesken?]

        (when (and paatos-tehty? on-oikeudet?)
          [:div.valja
           [yleiset/info-laatikko :neutraali
            "Tavoitehinnan muutokset on päätetty. Voit tehdä muutoksia perumalla päätöksen."
            nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])

        (when kattohinnan-oikaisu-mahdollinen?
          [kattohinnan-oikaisu e! kattohinta paatos-tehty? hoitokauden-alkuvuosi])

        [:div
         [:hr.paatos-hr]
         [:div.muokkaustoiminnot
          (when (:virheet paatos)
            [yleiset/info-laatikko :vahva-ilmoitus "Et voi vahvistaa päätöstä, sillä osa pohjatiedoista puuttuu"
             (:virheet paatos) nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}])
          [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatoksen-tiedot tallennus-kesken?
           (or (not hoitovuosi-kesken?) (not (:virheet paatos)))
           #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanMuutosPaatos paatoksen-tiedot))
           #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanMuutosPaatos paatoksen-tiedot))]]]])]))
