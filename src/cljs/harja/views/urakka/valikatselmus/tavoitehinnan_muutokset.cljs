(ns harja.views.urakka.valikatselmus.tavoitehinnan-muutokset
  (:require [reagent.core :as r :refer [atom]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.urakka :as urakka]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.dom :as dom]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]
            [harja.tiedot.urakka.kulut.yhteiset :as kulut-yhteiset]
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
  [hoitokauden-oikaisut-atom {:keys [voi-muokata? poista-oikaisu-fn tallenna-oikaisu-fn]}]
  (let [virheet (atom {})
        uusi-id (if (empty? (keys @hoitokauden-oikaisut-atom))
                  0
                  (inc (apply max (keys @hoitokauden-oikaisut-atom))))
        oikaisut-summa (when @hoitokauden-oikaisut-atom (fmt/desimaaliluku-opt
                                                          (reduce
                                                            (fn [yhteensa hoitokauden-oikaisu]
                                                              (+ yhteensa (get hoitokauden-oikaisu :harja.domain.kulut.valikatselmus/summa)))
                                                            0
                                                            (vals @hoitokauden-oikaisut-atom))
                                                          2
                                                          true))
        muutettu-kattohinta (atom nil)]
    [:div
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
                              (tallenna-oikaisu-fn oikaisu i)
                              #_(when-not (or (seq (get @virheet i))
                                            (:koskematon (get @hoitokauden-oikaisut-atom i)))
                                  (let [oikaisu (cond-> oikaisu
                                                  true (update ::valikatselmus/summa Math/abs)

                                                  (= :vahennys (:lisays-tai-vahennys oikaisu))
                                                  (update ::valikatselmus/summa -))]
                                    (tallenna-oikaisu-fn oikaisu i))))
              :uusi-id uusi-id
              :virheet virheet
              :nayta-virheikoni? false
              :rivi-jalkeen (when @hoitokauden-oikaisut-atom
                              [{:teksti "Yhteensä" :luokka "yhteensa" }
                               {:teksti oikaisut-summa :sarakkeita 2 :tasaa :oikea :luokka "yhteensa-padding-oikea-24"}
                               {:teksti "" :sarakkeita 2 :luokka "yhteensa"}])}
        (when voi-muokata?
          {;; Lisää oikaisunappula taulukon yläpuolella oikealla
           :custom-toiminto {:teksti "Lisää muutos"
                             :toiminto #(do
                                          (swap! hoitokauden-oikaisut-atom assoc uusi-id
                                            {:id uusi-id :koskematon true :lisays-tai-vahennys :lisays}))
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
        :input-luokka "maara-input"
        :desimaalien-maara 2
        :validoi [[:ei-tyhja "Täytä arvo"]]
        :leveys 2
        :elementin-id (str "summa-" uusi-id)}]
      hoitokauden-oikaisut-atom]
     (when @hoitokauden-oikaisut-atom
       [:div.kattohinta-info [yleiset/info-laatikko :vahva-ilmoitus "Jos tavoitehinnan muutosten myötä myös kattohinta muuttuu, syötä muuttunut kattohinta."]
        [kentat/tee-otsikollinen-kentta {:otsikko "Muuttunut kattohinta"
                                         :kentta-params {:tyyppi :euro
                                                         :vayla-tyyli? true
                                                         :input-luokka "maara-input"}
                                         :arvo-atom muutettu-kattohinta
                                         :luokka ""}]])]))

(defn kattohinnan-oikaisu
  "Kattohinnan oikaisua tarvitsevat urkat, jotka ovat alkaneet -19-20 vuosina. Muille kattohinta on 110% tavoitehinnasta."
  [e! kattohinta tavoitehinta paatos-tehty?]
  (let [uusi-kattohinta-suurempi-kuin-tavoitehinta? (and kattohinta tavoitehinta (>= kattohinta tavoitehinta))
        uusi-kattohinta-validi? uusi-kattohinta-suurempi-kuin-tavoitehinta?
        muokkaustila? (not kattohinta)]
    [:<>
     [:div.oikaisu-paatos-varoitus
      [ikonit/harja-icon-status-alert]
      [:span "Jos tavoitehinnan oikaisun myötä myös kattohinta muuttuu, syötä uusi oikaistu kattohinta."]]
     [:div.caption.semibold {:style {:font-size "12px"}} "Oikaistu kattohinta"]
     [:div.flex-row.alkuun.valistys16
      (if muokkaustila?
        [kentat/tee-kentta
         {:tyyppi :positiivinen-numero
          :koko 20
          :vayla-tyyli? true
          :max-desimaalit 7
          :kokonaisosan-maara 9
          :fmt fmt/euro-opt}
         (r/wrap kattohinta
           (fn [kattohinta]
             (e! (valikatselmus-tiedot/->KattohinnanOikaisuaMuokattu kattohinta))))]
        [:span {:style {:min-width "173px"}}
         (fmt/euro-opt kattohinta)])

      (if muokkaustila?
        [napit/tallenna
         "Hyväksy uusi kattohinta"
         #(e! (valikatselmus-tiedot/->TallennaKattohinnanOikaisu))
         {:disabled (not uusi-kattohinta-validi?)}]
        [napit/muokkaa
         "Muokkaa"
         #(e! (valikatselmus-tiedot/->KattohinnanMuokkaaPainettu kattohinta))])
      (when (and muokkaustila? kattohinta)
        [napit/poista
         "Poista kattohinnan oikaisu"
         #(e! (valikatselmus-tiedot/->PoistaKattohinnanOikaisu))])]]))

(defn tavoitehinnan-muutokset [e! paatos voi-muokata? tallennus-kesken? hoitokauden-alkuvuosi avatut-paatokset tavoitehinnan-muutokset]
  (let [paatos-avain :tavoitehinnan-muutokset
        paatos-tehty? (boolean (:id paatos))
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        paatos-tehty? (boolean (:id paatos))
        on-oikeudet? (valikatselmus-yhteiset/onko-oikeudet-tehda-paatos? (-> @tila/yleiset :urakka :id))
        tavoitehinnan-muutokset (get tavoitehinnan-muutokset hoitokauden-alkuvuosi)
        kattohinta (:kattohinta paatos)
        tavoitehinta (:tavoitehinta paatos)
        hoitokauden-oikaisut-atom (atom tavoitehinnan-muutokset)
        urakan-alkuvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        poikkeusvuosi? (lupaus-domain/vuosi-19-20? urakan-alkuvuosi) ;;TODO:  Korjaa

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
     [valikatselmus-yhteiset/paatosotsikko-ja-avaus e! "Tavoitehinnan muutokset" paatos-tehty? paatos-avain avatut-paatokset
      avaa-tai-sulje-haitari (valikatselmus-tiedot/->AvaaPaatos paatos-avain)]
     (when (not (contains? avatut-paatokset paatos-avain))
       [:div
        [tavoitehinnan-oikaisut-taulukko hoitokauden-oikaisut-atom
         {:voi-muokata? (and voi-muokata? (not paatos-tehty?))
          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
          :poista-oikaisu-fn #(e! (valikatselmus-tiedot/->PoistaOikaisu %1 %2))
          :tallenna-oikaisu-fn #(e! (valikatselmus-tiedot/->TallennaOikaisu %1 %2))
          :paivita-oikaisu-fn #(e! (valikatselmus-tiedot/->PaivitaTavoitehinnanOikaisut %1 %2))}]

        (when (and paatos-tehty? voi-muokata?)
          [:div.oikaisu-paatos-varoitus
           [ikonit/harja-icon-status-alert]
           [:span "Tavoitehintaan liittyvä päätös on tallennettu. Jos aiot tehdä  uusia tavoitehinnan muutoksia, kumoa päätös ensin."]])

        (when kattohinnan-oikaisu-mahdollinen?
          [kattohinnan-oikaisu e! kattohinta kattohinta tavoitehinta paatos-tehty?])

        ;; Päätöksenteko napit
        [valikatselmus-yhteiset/paatosnapit paatos-tehty? on-oikeudet? paatoksen-tiedot tallennus-kesken? voi-muokata?
         #(e! (valikatselmus-tiedot/->TallennaTavoitehinnanMuutosPaatos paatoksen-tiedot))
         #(e! (valikatselmus-tiedot/->PoistaTavoitehinnanMuutosPaatos paatoksen-tiedot))]])]))
