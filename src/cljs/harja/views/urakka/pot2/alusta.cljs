(ns harja.views.urakka.pot2.alusta
  "POT2-lomakkeen alustarivien näkymä"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.loki :refer [log]]
    [harja.pvm :as pvm]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.pot2 :as pot2-domain]
    [harja.domain.tierekisteri :as tr]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.ui.debug :refer [debug]]
    [harja.ui.grid :as grid]
    [harja.ui.komponentti :as komp]
    [harja.ui.lomake :as lomake]
    [harja.ui.napit :as napit]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.paallystys :as paallystys]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteiset]
    [harja.views.urakka.pot2.massa-ja-murske-yhteiset :as mm-yhteiset])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn alustan-validointi [rivi taulukko]
  (let [{:keys [tr-osien-tiedot]} (:paallystysilmoitus-lomakedata @paallystys/tila)
        alikohteet (vals @pot2-tiedot/kohdeosat-atom)
        toiset-alustatoimenpiteet (remove
                                    #(= rivi %)
                                    (vals @pot2-tiedot/alustarivit-atom))
        vuosi (pvm/vuosi (pvm/nyt))
        validoitu (yllapitokohteet-domain/validoi-alustatoimenpide alikohteet
                                                                   []
                                                                   rivi
                                                                   toiset-alustatoimenpiteet
                                                                   (get tr-osien-tiedot (:tr-numero rivi))
                                                                   []
                                                                   vuosi)]
    (yllapitokohteet-domain/validoi-alustatoimenpide-teksti validoitu)))

(defn- alustalomakkeen-lisakentat
  [{:keys [toimenpide massat murskeet koodistot] :as alusta}]
  (let [{massatyypit :massatyypit
         mursketyypit :mursketyypit} koodistot
        mukautetut-lisakentat {:murske {:nimi :murske
                                        :valinta-nayta (fn [murske]
                                                         (if murske
                                                           [mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit mursketyypit
                                                                                                     :materiaali murske
                                                                                                     :fmt :komponentti}]
                                                           "-"))
                                        :valinnat murskeet}
                               :massa {:nimi :massa
                                       :valinta-nayta (fn [massa]
                                                        (if massa
                                                          [mm-yhteiset/materiaalin-rikastettu-nimi {:tyypit massatyypit
                                                                                                    :materiaali massa
                                                                                                    :fmt :komponentti}]
                                                          "-"))
                                       :valinnat massat}}
        toimenpidespesifit-lisakentat (pot2-domain/alusta-toimenpidespesifit-metadata toimenpide)
        lisakentta-generaattori (fn [{:keys [nimi pakollinen? tyyppi valinnat-koodisto otsikko yksikko jos] :as kentta-metadata}]
                                  (let [kentta (get mukautetut-lisakentat nimi)
                                        valinnat (or (:valinnat kentta)
                                                     (get koodistot valinnat-koodisto))
                                        valinnat-ja-nil (if pakollinen?
                                                          valinnat
                                                          (conj valinnat nil))]
                                    (when (and (= tyyppi :valinta) (nil? valinnat-ja-nil))
                                      (println "Kenttä " nimi " on valinta mutta ei sisällä valinnat"))
                                    (when (or (nil? jos)
                                              (some? (get alusta jos)))
                                      (lomake/rivi (merge kentta-metadata
                                                          kentta
                                                          {:palstoja 3
                                                           :valinnat valinnat-ja-nil})))))]
    (map lisakentta-generaattori toimenpidespesifit-lisakentat)))

(defn- alustalomakkeen-kentat [{:keys [alusta-toimenpiteet toimenpide] :as alusta}]
  (let [toimenpide-kentta [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :palstoja 3
                            :tyyppi :valinta :valinnat alusta-toimenpiteet :valinta-arvo ::pot2-domain/koodi
                            :pakollinen? true
                            :valinta-nayta #(when %
                                              ;; Jos toimenpiteellä on lyhenne, näytetään LYHENNE (NIMI), muuten vain NIMI
                                              (str (or (::pot2-domain/lyhenne %) (::pot2-domain/nimi %))
                                                   (when (::pot2-domain/lyhenne %)
                                                     (str " (" (clojure.string/lower-case (::pot2-domain/nimi %)) ")"))))}]
        tr-kentat [(lomake/rivi
                     {:nimi :tr-numero :otsikko "Tie"
                      :tyyppi :positiivinen-numero :kokonaisluku? true :pakollinen? true}
                     {:otsikko "Ajorata" :nimi :tr-ajorata :pakollinen? true :tyyppi :valinta
                      :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
                      :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))}
                     {:otsikko "Kaista" :nimi :tr-kaista :pakollinen? true :tyyppi :valinta
                      :valinnat pot/+kaistat+ :valinta-arvo :koodi
                      :valinta-nayta (fn [rivi]
                                       (if rivi
                                         (:nimi rivi)
                                         "- Valitse kaista -"))})
                   (lomake/rivi
                     {:nimi :tr-alkuosa
                      :palstoja 1
                      :otsikko "Aosa"
                      :pakollinen? true :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-alkuetaisyys
                      :palstoja 1
                      :otsikko "Aet"
                      :pakollinen? true :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-loppuosa
                      :palstoja 1
                      :otsikko "Losa"
                      :pakollinen? true :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-loppuetaisyys
                      :palstoja 1
                      :otsikko "Let"
                      :pakollinen? true :tyyppi :positiivinen-numero :kokonaisluku? true})]
        lisakentat (alustalomakkeen-lisakentat alusta)]
    (vec
      (concat toimenpide-kentta
              (when toimenpide tr-kentat)
              (when toimenpide lisakentat)))))

(defn alustalomake-nakyma
  [e! {:keys [alustalomake alusta-toimenpiteet massat murskeet
              materiaalikoodistot voi-muokata?]}]
  (let [saa-sulkea? (atom false)
        muokkaustilassa? (atom false)]
    (komp/luo
      (komp/piirretty #(yleiset/fn-viiveella (fn []
                                               (reset! saa-sulkea? true))))
      (komp/klikattu-ulkopuolelle #(when (and @saa-sulkea?
                                              (not @muokkaustilassa?))
                                     (e! (pot2-tiedot/->SuljeAlustalomake)))
                                  {:tarkista-komponentti? true})
      (fn [e! {:keys [alustalomake alusta-toimenpiteet massat murskeet
                      materiaalikoodistot voi-muokata?]}]
        (when voi-muokata? (reset! muokkaustilassa? true))
        [:div.alustalomake {:on-click #(.stopPropagation %)}
         [lomake/lomake
          {:luokka " overlay-oikealla"
           :otsikko "Toimenpiteen tiedot"
           :voi-muokata? voi-muokata?
           :tarkkaile-ulkopuolisia-muutoksia? true
           :sulje-fn #(e! (pot2-tiedot/->SuljeAlustalomake))
           :muokkaa! #(e! (pot2-tiedot/->PaivitaAlustalomake %))
           :ei-borderia? true
           :footer-fn (fn [data]
                        [:span
                         (when-not voi-muokata?
                           [:div {:style {:margin-bottom "16px"}}
                            "Päällystysilmoitus lukittu, tietoja ei voi muokata."])
                         (when voi-muokata?
                           [napit/nappi "Valmis"
                            #(e! (pot2-tiedot/->TallennaAlustalomake data false))
                            {:disabled (not (lomake/validi? data))
                             :luokka "nappi-toissijainen"}])
                         (when voi-muokata?
                           [napit/nappi "Lisää seuraava"
                            #(e! (pot2-tiedot/->TallennaAlustalomake data true))
                            {:disabled (not (lomake/validi? data))
                             :luokka "nappi-toissijainen"}])
                         [napit/peruuta (if voi-muokata? "Peruuta" "Sulje")
                          #(e! (pot2-tiedot/->SuljeAlustalomake))
                          {:disabled false
                           :luokka "pull-right"}]])}
          (alustalomakkeen-kentat {:alusta-toimenpiteet alusta-toimenpiteet
                                   :toimenpide (:toimenpide alustalomake)
                                   :murske (:murske alustalomake)
                                   :massat massat
                                   :murskeet murskeet
                                   :koodistot materiaalikoodistot})
          alustalomake]]))))

(def gridin-perusleveys 2)

(defn alusta
  "Alikohteiden päällysteiden alustakerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot alustalomake massalomake murskelomake] :as app}
   {:keys [massat murskeet materiaalikoodistot validointi]} alustarivit-atom]
  (let [alusta-toimenpiteet (:alusta-toimenpiteet materiaalikoodistot)
        voi-muokata? (not= :lukittu (:tila perustiedot))]
    [:div
     (when alustalomake
       [alustalomake-nakyma e! {:alustalomake alustalomake
                                :alusta-toimenpiteet alusta-toimenpiteet
                                :massat massat
                                :murskeet murskeet
                                :materiaalikoodistot materiaalikoodistot
                                :voi-muokata? voi-muokata?}])
     [grid/muokkaus-grid
      {:otsikko "Alusta" :tunniste :id :piilota-toiminnot? true
       :voi-muokata? voi-muokata?
       :voi-kumota? false :voi-lisata? false
       :rivi-klikattu #(e! (pot2-tiedot/->AvaaAlustalomake %))
       :custom-toiminto {:teksti "Lisää toimenpide"
                         :toiminto #(e! (pot2-tiedot/->AvaaAlustalomake {}))
                         :opts {:ikoni (ikonit/livicon-plus)
                                :luokka "nappi-toissijainen"}}
       :muutos #(e! (pot2-tiedot/->Pot2Muokattu))
       :rivi-validointi (:rivi validointi)
       :taulukko-validointi (:taulukko validointi)
       ;; Gridin renderöinnin jälkeen lasketaan alikohteiden pituudet
       :luomisen-jalkeen (fn [grid-state]
                           (paallystys/hae-osan-pituudet grid-state paallystys/tr-osien-tiedot))
       :tyhja (if (nil? @alustarivit-atom) [ajax-loader "Haetaan kohdeosia..."]
                                           [:div
                                            [:div {:style {:display "inline-block"}} "Ei alustarivejä"]
                                            (when kirjoitusoikeus?
                                              [:div {:style {:display "inline-block"
                                                             :float "right"}}
                                               [napit/yleinen-ensisijainen "Lisää osa"
                                                #(reset! alustarivit-atom (yllapitokohteet/lisaa-uusi-kohdeosa @alustarivit-atom 1 (get-in app [:perustiedot :tr-osoite])))
                                                {:ikoni (ikonit/livicon-arrow-down)
                                                 :luokka "btn-xs"}]])])}
      [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :leveys 3 :muokattava? (constantly false)
        :tyyppi :string
        :hae (fn [rivi]
               (if (pot2-tiedot/onko-alustatoimenpide-verkko? (:toimenpide rivi))
                 (pot2-domain/ainetyypin-koodi->nimi (:verkon-tyypit materiaalikoodistot) (:verkon-tyyppi rivi))
                 (pot2-domain/ainetyypin-koodi->lyhenne alusta-toimenpiteet (:toimenpide rivi))))
        :validoi [[:ei-tyhja "Anna arvo"]]}
       {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys gridin-perusleveys :nimi :tr-numero :validoi (:tr-numero validointi)}
       {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys gridin-perusleveys :elementin-id "alustan-ajor"
        :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
        :tasaa :oikea :kokonaisluku? true}
       {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys gridin-perusleveys :elementin-id "alustan-kaista"
        :valinnat pot/+kaistat+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi]
                         (if rivi (:nimi rivi) "- Valitse kaista -"))
        :tasaa :oikea :kokonaisluku? true}
       {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys gridin-perusleveys :nimi :tr-alkuosa :validoi (:tr-alkuosa validointi)}
       {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys gridin-perusleveys :nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validointi)}
       {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys gridin-perusleveys :nimi :tr-loppuosa :validoi (:tr-loppuosa validointi)}
       {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys gridin-perusleveys :nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validointi)}
       {:otsikko "Pituus" :nimi :pituus :leveys gridin-perusleveys :tyyppi :numero :tasaa :oikea
        :muokattava? (constantly false)
        :hae #(paallystys/rivin-kohteen-pituus
                (paallystys/tien-osat-riville % paallystys/tr-osien-tiedot) %)}
       {:otsikko "Toimenpiteen tie\u00ADdot" :nimi :toimenpiteen-tiedot :leveys 4
        :tyyppi :komponentti :muokattava? (constantly false)
        :komponentti (fn [rivi]
                       [pot2-tiedot/toimenpiteen-tiedot {:koodistot materiaalikoodistot} rivi])}
       {:otsikko "Materiaa\u00ADli" :nimi :materiaalin-tiedot :leveys 3
        :tyyppi :komponentti :muokattava? (constantly false)
        :komponentti (fn [rivi]
                       ;; hieman erilainen formaatti riippuen tuleeko massa kulutuskerroksesta tai alustasta
                       (let [massa-id (or (:massa rivi) (:massa-id rivi))
                             murske-id (:murske rivi)]
                         (when (or massa-id murske-id)
                          [mm-yhteiset/materiaalin-tiedot (cond
                                                            massa-id
                                                            (mm-yhteiset/materiaali massat {:massa-id massa-id})

                                                            murske-id
                                                            (mm-yhteiset/materiaali murskeet {:murske-id murske-id})

                                                            :else
                                                            nil)
                           {:materiaalikoodistot materiaalikoodistot}
                           #(e! (pot2-tiedot/->NaytaMateriaalilomake rivi))])))}
       {:otsikko "petar" :nimi :kopioi-muille-kaistoille
        :tyyppi :reagent-komponentti :tasaa :oikea
        :komponentti-args [e! pot2-tiedot/alustarivit-atom]
        :komponentti pot2-yhteiset/kopioi-toimenpiteet-nappi
        :leveys gridin-perusleveys}
       {:otsikko "" :nimi :alusta-toiminnot :tyyppi :reagent-komponentti :leveys gridin-perusleveys
        :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? alustarivit-atom :alusta voi-muokata?]
        :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
      alustarivit-atom]]))