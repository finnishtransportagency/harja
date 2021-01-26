(ns harja.views.urakka.pot2.alusta
  "POT2-lomakkeen alustarivien näkymä"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.pot2 :as pot2-domain]
    [harja.domain.tierekisteri :as tr]
    [harja.domain.yllapitokohde :as yllapitokohteet-domain]
    [harja.loki :refer [log]]
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
    [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteiset])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn komponenttien-lista [e! komponentit]
  (fn [komponentit]
    [:div.haku-lista.container
     [:ul.haku-lista
      (map-indexed
        (fn [i komp]
          ^{:key (str i (hash komp))}
          [komp])
        komponentit)]]))

(defn lisaa-toimenpide-nappi
  [e! {:keys [kirjoitusoikeus? perustiedot] :as app}
   {:keys [murskeet mursketyypit materiaalikoodistot validointi]} alustarivit-atom grid-ohjauskahva]
  (let [alusta-toimenpiteet (:alusta-toimenpiteet materiaalikoodistot) ]
    [:div.haku
     [napit/nappi "Lisää toimenpide"
      #(e! (pot2-tiedot/->LisaaAlustaToimenpide))
      {:ikoni (ikonit/livicon-chevron-down)
       :ikoni-oikealle? true
       :luokka "nappi-toissijainen"
       :style {}}]
     (when (:tarjoa-alustatoimenpide app)
       (println "jarno  alusta-toimenpiteet " (pr-str alusta-toimenpiteet))
       #_[komponenttien-lista e! alusta-toimenpiteet])]))

(defn alustan-validointi [rivi taulukko]
  (let [{:keys [ilmoitustiedot vuodet tr-osien-tiedot]} (:paallystysilmoitus-lomakedata @paallystys/tila)
        alikohteet (vals (:osoitteet ilmoitustiedot))
        vuosi (first vuodet)
        validoitu (yllapitokohteet-domain/validoi-alustatoimenpide alikohteet [] rivi [] (get tr-osien-tiedot (:tr-numero rivi)) [] vuosi)]
    (yllapitokohteet-domain/validoi-alustatoimenpide-teksti (dissoc validoitu :alustatoimenpide-paallekkyys))))

(defn- alustalomakkeen-kentat [{:keys [alusta-toimenpiteet
                                       toimenpide
                                       verkon-sijainnit
                                       verkon-tyypit
                                       verkon-tarkoitukset]}]
  (println "verkon sijainnit " (pr-str verkon-sijainnit))
  (println "verkon-tarkoitukset " (pr-str verkon-tarkoitukset))
  (let [tr-kentat [(lomake/rivi
                     {:nimi :tr-numero
                      :palstoja 1
                      :otsikko "Tie"
                      :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-ajorata
                      :palstoja 1
                      :otsikko "Ajorata"
                      :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-kaista
                      :palstoja 1
                      :otsikko "Kaista"
                      :tyyppi :positiivinen-numero :kokonaisluku? true})
                   (lomake/rivi
                     {:nimi :tr-alkuosa
                      :palstoja 1
                      :otsikko "Aosa"
                      :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-alkuetaisyys
                      :palstoja 1
                      :otsikko "Aet"
                      :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-loppuosa
                      :palstoja 1
                      :otsikko "Losa"
                      :tyyppi :positiivinen-numero :kokonaisluku? true}
                     {:nimi :tr-loppuetaisyys
                      :palstoja 1
                      :otsikko "Let"
                      :tyyppi :positiivinen-numero :kokonaisluku? true})]
        verkon-kentat [(lomake/rivi {:otsikko "Sijainti" :nimi :verkon-sijainti :tyyppi :valinta
                                     :valinta-arvo ::pot2-domain/koodi :valinta-nayta ::pot2-domain/nimi
                                     :valinnat verkon-sijainnit
                                     :palstoja 3})
                       (lomake/rivi {:otsikko "Tarkoitus" :nimi :verkon-tarkoitus :tyyppi :valinta
                                     ;; TODO: verkon_sijainti :hae-pot2-koodistot palvelun kautta tänne
                                     :valinta-arvo ::pot2-domain/koodi :valinta-nayta ::pot2-domain/nimi
                                     :valinnat verkon-tarkoitukset
                                     :palstoja 3})]]
    (vec
      (concat tr-kentat (cond
                          (pot2-tiedot/onko-toimenpide-verkko? alusta-toimenpiteet toimenpide)
                          verkon-kentat

                          ;; TODO: Figmasta kaikkien muiden toimenpidetyyypien kentät

                          :else
                         [])))))

(defn alustalomake-nakyma
  [e! {:keys [alustalomake alusta-toimenpiteet murskeet materiaalikoodistot]}]
  [lomake/lomake
   {:luokka " overlay-oikealla"
    :otsikko-komp (fn []
                    [:div.col-xs-12 {:style {:margin-top "40px"}}
                     [:span.harmaa-tumma-teksti
                      "Toimenpide"]
                     [:div.fontti-20
                      {:style {:margin-top "4px"
                               :margin-bottom "24px"}}
                      (pot2-domain/ainetyypin-koodi->nimi alusta-toimenpiteet (:toimenpide alustalomake))]])
    :muokkaa! #(e! (pot2-tiedot/->PaivitaAlustalomake %))
    :ei-borderia? true
    :footer-fn (fn [data]
                 [:span
                  [napit/nappi "Lisää taulukkoon"
                   #(e! (pot2-tiedot/->TallennaAlustalomake data))
                   {:disabled false
                    :luokka "nappi-myonteinen"
                    :ikoni (ikonit/check)}] ;; todo: validointi oltava kunnossa
                  [napit/peruuta "Peruuta"
                   #(e! (pot2-tiedot/->SuljeAlustalomake))
                   {:disabled false}]])}
   (alustalomakkeen-kentat {:alusta-toimenpiteet alusta-toimenpiteet
                            :toimenpide          (:toimenpide alustalomake)
                            :verkon-sijainnit    (:verkon-sijainnit materiaalikoodistot)
                            :verkon-tyypit       (:verkon-tyypit materiaalikoodistot)
                            :verkon-tarkoitukset (:verkon-tarkoitukset materiaalikoodistot)})
   alustalomake])

(defn alusta
  "Alikohteiden päällysteiden alustakerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot alustalomake] :as app}
   {:keys [murskeet mursketyypit materiaalikoodistot validointi]} alustarivit-atom]
  (let [perusleveys 2
        alusta-toimenpiteet (:alusta-toimenpiteet materiaalikoodistot)]
    [:div
     (when alustalomake
       [alustalomake-nakyma e! {:alustalomake alustalomake
                                :alusta-toimenpiteet alusta-toimenpiteet
                                :murskeet murskeet
                                :materiaalikoodistot materiaalikoodistot}])
     [grid/muokkaus-grid
      {:otsikko "Alusta" :tunniste :id :piilota-toiminnot? true
       :uusi-rivi (fn [rivi]
                    (assoc rivi
                      :tr-numero (:tr-numero perustiedot)))
       :rivi-validointi (:rivi validointi)
       :taulukko-validointi (:taulukko validointi)
       :muokkaa-footer (fn [g]
                         [:div {:style {:margin-top "1rem"}}

                          [lisaa-toimenpide-nappi e! app
                           {:keys [murskeet mursketyypit materiaalikoodistot validointi]}
                           alustarivit-atom
                           g]
                          [yleiset/pudotusvalikko "Lisää toimenpide"
                           {:valinta ::pot2-domain/koodi
                            :format-fn ::pot2-domain/nimi
                            :valitse-fn #(e! (pot2-tiedot/->ValitseAlustatoimenpide (::pot2-domain/koodi %)))}
                           (:alusta-toimenpiteet materiaalikoodistot)]])
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
                                                 :luokka "btn-xs"}]])])
       :rivi-klikattu #(log "click")}
      [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :leveys perusleveys
        :tyyppi :valinta :valinnat alusta-toimenpiteet :valinta-arvo ::pot2-domain/koodi
        :valinta-nayta ::pot2-domain/lyhenne :validoi [[:ei-tyhja "Anna arvo"]]}
       {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-numero :validoi (:tr-numero validointi)}
       {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys perusleveys
        :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
        :tasaa :oikea :kokonaisluku? true}
       {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys perusleveys
        :valinnat pot/+kaistat+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi]
                         (if rivi
                           (:nimi rivi)
                           "- Valitse kaista -"))
        :tasaa :oikea :kokonaisluku? true}
       {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-alkuosa :validoi (:tr-alkuosa validointi)}
       {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validointi)}
       {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-loppuosa :validoi (:tr-loppuosa validointi)}
       {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validointi)}
       {:otsikko "Pit. (m)" :nimi :pituus :leveys perusleveys :tyyppi :numero :tasaa :oikea
        :muokattava? (constantly false)
        :hae #(paallystys/rivin-kohteen-pituus
                (paallystys/tien-osat-riville % paallystys/tr-osien-tiedot) %)}
       {:otsikko "Murske *)" :nimi :materiaali :leveys 3
        :tyyppi :valinta :valinnat murskeet :valinta-arvo ::pot2-domain/murske-id
        :valinta-nayta (fn [rivi]
                         (mk-tiedot/murskeen-rikastettu-nimi mursketyypit rivi :string))
        :validoi [[:ei-tyhja "Anna arvo"]]}
       {:otsikko "" :nimi :alusta-toiminnot :tyyppi :reagent-komponentti :leveys perusleveys
        :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? alustarivit-atom :alusta]
        :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
      alustarivit-atom]]))