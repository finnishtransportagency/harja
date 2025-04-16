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
  [hoitokauden-oikaisut-atom hoitokauden-alkuvuosi {:keys [voi-muokata? poista-oikaisu-fn tallenna-oikaisu-fn]}]
  (let [virheet (atom {})
        uusi-id (if (empty? (keys @hoitokauden-oikaisut-atom))
                  0
                  (inc (apply max (keys @hoitokauden-oikaisut-atom))))
        oikaisut-summa (when @hoitokauden-oikaisut-atom (fmt/euro-opt false true
                                                            (reduce
                                                              (fn [yhteensa hoitokauden-oikaisu]
                                                                (+ yhteensa (get hoitokauden-oikaisu :harja.domain.kulut.valikatselmus/summa)))
                                                              0
                                                              (vals @hoitokauden-oikaisut-atom))))
        alkuperaiset-oikaisut @valikatselmus-tiedot/tavoitehinnan-muutokset
        uudet-simplified (valikatselmus-tiedot/karsitut-tavoitehinnan-muutokset (vals @hoitokauden-oikaisut-atom))]
    [:div.tavoitehinnan-muutokset
     [grid/muokkaus-grid
      (merge {:tyhja "Ei muutoksia tavoitehintaan"
              :voi-kumota? false
              :voi-muokata? voi-muokata?

              ;; Roskakorinappula rivin päässä
              :toimintonappi-fn (when voi-muokata?
                                  (fn [rivi _muokkaa! id]
                                    [napit/poista ""
                                     #(do
                                        (poista-oikaisu-fn rivi id))
                                     {:luokka "napiton-nappi pelkka-ikoni"}]))
              :voi-lisata? false ;; Piilotetaan default lisää rivi -nappi. Se on korvattu custom-toiminnolla
              :validoi-uusi-rivi? false
              :on-rivi-blur (fn [oikaisu i]
                              (let [muuttui? (not (or (=
                                                        (and (>= (dec (count uudet-simplified)) i) (nth uudet-simplified i))
                                                        (and (>= (dec (count alkuperaiset-oikaisut)) i) (nth alkuperaiset-oikaisut i)))
                                                    false))]
                                ;; Jos ei ole muutoksia, niin ei tallenneta mitään
                                (when muuttui? (tallenna-oikaisu-fn oikaisu i))))
              :uusi-id uusi-id
              :virheet virheet
              :nayta-virheikoni? false
              :rivi-jalkeen (when @hoitokauden-oikaisut-atom
                              [{:teksti "Yhteensä" :luokka "yhteensa"}
                               {:teksti oikaisut-summa :sarakkeita 2 :tasaa :oikea :luokka "yhteensa-padding-oikea-24"}
                               {:teksti "" :sarakkeita 2 :luokka "yhteensa"}])}
        (when voi-muokata?
          {;; Lisää oikaisunappula taulukon yläpuolella oikealla
           :custom-toiminto {:teksti "Lisää muutos"
                             :toiminto #(do
                                          (swap! hoitokauden-oikaisut-atom assoc uusi-id
                                            {:id uusi-id ;:koskematon true
                                             ;:lisays-tai-vahennys :lisays
                                             ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                                             }))
                             :keskita-vasemmalle true
                             :keskita-ylos true
                             :opts {:ikoni (ikonit/livicon-plus)
                                    :luokka "nappi-toissijainen"}}}))
      [{:otsikko "Muutos"
        :nimi ::valikatselmus/otsikko
        :tyyppi :valinta
        :valinnat (into [] (valikatselmus/luokat @nav/valittu-urakka))
        :validoi [[:ei-tyhja "Valitse arvo"]]
        :leveys 2
        :data-cy (str "luokka-" uusi-id)
        :elementin-id (str "luokka-" uusi-id)}
       {:otsikko "Perustelu"
        :nimi ::valikatselmus/selite
        :tyyppi :text
        :koko [:auto 3]
        :validoi [[:ei-tyhja "Täytä arvo"]]
        :leveys 3
        :elementin-id (str "selite-" uusi-id)}
       {:otsikko "Vaikutus € (+/-)"
        :nimi ::valikatselmus/summa
        :tyyppi :euro
        :nayta-plus true
        :input-luokka "maara-input"
        :desimaalien-maara 2
        :validoi [[:ei-tyhja "Täytä arvo"]]
        :leveys 2
        :tasaa :oikea
        :fmt (partial fmt/euro-opt false true)
        :elementin-id (str "summa-" uusi-id)}]
      hoitokauden-oikaisut-atom]]))

(defn kattohinnan-oikaisu
  "Kattohinnan oikaisua tarvitsevat urkat, jotka ovat alkaneet -19-20 vuosina. Muille kattohinta on 110% tavoitehinnasta."
  [e! kattohinta paatos-tehty?]
  (let [uusi-kattohinta (atom (if kattohinta kattohinta 0))]
    (if (not paatos-tehty?)
      [:<>
       [:div.valja
        [yleiset/info-laatikko :neutraali "Jos tavoitehinnan oikaisun myötä myös kattohinta muuttuu, syötä muuttunut kattohinta." nil nil]]
       [:div.flex-row.alkuun.valistys16
        [kentat/tee-otsikollinen-kentta {:otsikko "Muuttunut kattohinta"
                                         :otsikon-luokka "caption-small-strong valja"
                                         :luokka ""
                                         :kentta-params {:tyyppi :euro
                                                         :koko 20
                                                         :max-desimaalit 7
                                                         :kokonaisosan-maara 9
                                                         :fmt fmt/euro-opt
                                                         :vayla-tyyli? true
                                                         :input-luokka "kattohinta-muutettu"
                                                         :on-blur #(when (not (= kattohinta @uusi-kattohinta))
                                                                    (e! (valikatselmus-tiedot/->TallennaKattohinnanOikaisu @uusi-kattohinta)))}
                                         :arvo-atom uusi-kattohinta}]]]
      [:<>
       [:div.small-caption.lihavoitu.valja "Muuttunut kattohinta"]
       [:div.flex-row.alkuun.valistys16
        [:span (fmt/euro-opt false kattohinta)]]])))

(defn tavoitehinnan-muutokset [e! paatos voi-muokata? tallennus-kesken? avatut-paatokset tavoitehinnan-muutokset hoitovuosi-kesken?]
  (let [paatos-avain :tavoitehinnan-muutokset
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
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
                                           voi-muokata?
                                           poikkeusvuosi?)
        avaa-tai-sulje-haitari (fn [event]
                                 (when (dom/enter-nappain? event)
                                   (e! (valikatselmus-tiedot/->AvaaPaatos paatos-avain))))]
    ^{:key (str "tavoitehinnan-muutokset-" (gensym))}
    [:div.paatos-komponentti-border
     (if hoitovuosi-kesken?
       [valikatselmus-yhteiset/paatosotsikko "Tavoitehinnan muutokset" paatos-tehty?]
       [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan muutokset" paatos-tehty? paatos-avain avatut-paatokset
        avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)])
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [tavoitehinnan-oikaisut-taulukko hoitokauden-oikaisut-atom
         hoitokauden-alkuvuosi
         {:voi-muokata? (and voi-muokata? (not paatos-tehty?))
          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
          :poista-oikaisu-fn #(e! (valikatselmus-tiedot/->PoistaOikaisu %1 %2))
          :tallenna-oikaisu-fn #(e! (valikatselmus-tiedot/->TallennaOikaisu %1 %2))
          :paivita-oikaisu-fn #(e! (valikatselmus-tiedot/->PaivitaTavoitehinnanOikaisut %1 %2))}]

        (when (and paatos-tehty? voi-muokata?)
          [:div.valja
           [yleiset/info-laatikko :vahva-ilmoitus
            "Tavoitehintaan liittyvä päätös on tallennettu. Jos aiot tehdä  uusia tavoitehinnan muutoksia, kumoa päätös ensin."
            nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])

        (when kattohinnan-oikaisu-mahdollinen?
          [kattohinnan-oikaisu e! kattohinta paatos-tehty?])

        ;; Päätöksenteko napit
        (if-not (:virhe paatos)
          [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatoksen-tiedot tallennus-kesken? voi-muokata?
           #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanMuutosPaatos paatoksen-tiedot))
           #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanMuutosPaatos paatoksen-tiedot))]
          [:div.muokkaustoiminnot
           [yleiset/info-laatikko :vahva-ilmoitus (:virhe paatos) nil nil {:ikoni-fn #(ikonit/harja-icon-status-alert)}]])])]))
