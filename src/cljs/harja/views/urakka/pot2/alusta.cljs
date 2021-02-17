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
        vuosi (pvm/vuosi (pvm/nyt))
        validoitu (yllapitokohteet-domain/validoi-alustatoimenpide alikohteet [] rivi [] (get tr-osien-tiedot (:tr-numero rivi)) [] vuosi)]
    (yllapitokohteet-domain/validoi-alustatoimenpide-teksti (dissoc validoitu :alustatoimenpide-paallekkyys))))

(defn- alustalomakkeen-lisakentat
  [{:keys [toimenpide
           massat
           massatyypit
           murskeet
           mursketyypit
           sideainetyypit
           sidotun-kantavan-kerroksen-sideaine
           verkon-sijainnit
           verkon-tyypit
           verkon-tarkoitukset] :as alusta}]
  (let [kaikki-lisakentat {:murske           {:nimi          :murske
                                              :valinta-nayta (fn [murske]
                                                               (if murske
                                                                 (let [[a b] (pot2-domain/mursken-rikastettu-nimi
                                                                               mursketyypit
                                                                               murske)]
                                                                   (str a b))
                                                                 "-"))
                                              :valinnat      murskeet}
                           :massa            {:nimi          :massa
                                              :valinta-nayta (fn [massa]
                                                               (if massa
                                                                 (let [[a b] (pot2-domain/massan-rikastettu-nimi
                                                                               massatyypit
                                                                               massa)]
                                                                   (str a b))
                                                                 "-"))
                                              :valinnat      massat}
                           :sideaine         {:nimi     :sideaine
                                              :valinnat sideainetyypit}
                           :sideaine2        {:nimi     :sideaine2
                                              :valinnat sidotun-kantavan-kerroksen-sideaine}
                           :verkon-tyyppi    {:nimi     :verkon-tyyppi
                                              :valinnat verkon-tyypit}
                           :verkon-sijainti  {:nimi     :verkon-sijainti
                                              :valinnat verkon-sijainnit}
                           :verkon-tarkoitus {:nimi     :verkon-tarkoitus
                                              :valinnat verkon-tarkoitukset}}
        toimenpidespesifit-lisakentat (pot2-domain/alusta-toimenpidespesifit-metadata toimenpide)
        lisakentta-generaattori (fn [{:keys [nimi pakollinen? otsikko yksikko jos] :as kentta-metadata}]
                                  (let [kentta (get kaikki-lisakentat nimi)
                                        valinnat (if pakollinen?
                                                   (:valinnat kentta)
                                                   (conj (:valinnat kentta) nil))]
                                    (when (or (nil? jos)
                                              (some? (get alusta jos)))
                                      (lomake/rivi (merge kentta-metadata
                                                          kentta
                                                          {:palstoja    3
                                                           :valinnat    valinnat}
                                                          (when (some? otsikko)
                                                            {:otsikko (str otsikko
                                                                           (when (some? yksikko)
                                                                           (str " (" yksikko ")")))}))))))]
    (map lisakentta-generaattori toimenpidespesifit-lisakentat)))

(defn- alustalomakkeen-kentat [{:keys [alusta-toimenpiteet
                                       toimenpide] :as alusta}]
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
  [e! {:keys [alustalomake alusta-toimenpiteet massat murskeet materiaalikoodistot]}]
  (println "petar lomake DA VIDIMO " (pr-str alustalomake))
  [lomake/lomake
   {:luokka " overlay-oikealla"
    :otsikko "Toimenpiteen tiedot"
    :muokkaa! #(e! (pot2-tiedot/->PaivitaAlustalomake %))
    :ei-borderia? true
    :footer-fn (fn [data]
                 [:span
                  [napit/nappi "Valmis"
                   #(e! (pot2-tiedot/->TallennaAlustalomake data false))
                   {:disabled false
                    :luokka "nappi-toissijainen"
                    :ikoni (ikonit/check)}] ;; todo: validointi oltava kunnossa
                  [napit/nappi "Lisää seuraava"
                   #(e! (pot2-tiedot/->TallennaAlustalomake data true))
                   {:disabled false
                    :luokka "nappi-toissijainen"
                    :ikoni (ikonit/check)}]
                  [napit/peruuta "Peruuta"
                   #(e! (pot2-tiedot/->SuljeAlustalomake))
                   {:disabled false}]])}
   (alustalomakkeen-kentat {:alusta-toimenpiteet alusta-toimenpiteet
                            :toimenpide (:toimenpide alustalomake)
                            :murske (:murske alustalomake)
                            :massat massat
                            :murskeet murskeet
                            :massatyypit (:massatyypit materiaalikoodistot)
                            :mursketyypit (:mursketyypit materiaalikoodistot)
                            :sideainetyypit (:sideainetyypit materiaalikoodistot)
                            :sidotun-kantavan-kerroksen-sideaine (:sidotun-kantavan-kerroksen-sideaine materiaalikoodistot)
                            :verkon-sijainnit (:verkon-sijainnit materiaalikoodistot)
                            :verkon-tyypit (:verkon-tyypit materiaalikoodistot)
                            :verkon-tarkoitukset (:verkon-tarkoitukset materiaalikoodistot)})
   alustalomake])

(defn materiaali [massat-tai-murskeet rivi]
  (first (filter #(= (::pot2-domain/koodi %) (:murske-id rivi))
                 massat-tai-murskeet)))

(defn alusta
  "Alikohteiden päällysteiden alustakerroksen rivien muokkaus"
  [e! {:keys [kirjoitusoikeus? perustiedot alustalomake massalomake murskelomake] :as app}
   {:keys [massat murskeet mursketyypit materiaalikoodistot validointi]} alustarivit-atom]
  (let [perusleveys 2
        alusta-toimenpiteet (:alusta-toimenpiteet materiaalikoodistot)]
    [:div
     (when alustalomake
       [alustalomake-nakyma e! {:alustalomake alustalomake
                                :alusta-toimenpiteet alusta-toimenpiteet
                                :massat massat
                                :murskeet murskeet
                                :materiaalikoodistot materiaalikoodistot}])
     [grid/muokkaus-grid
      {:otsikko "Alusta" :tunniste :id :piilota-toiminnot? true
       :voi-kumota? false :voi-lisata? false
       :custom-toiminto {:teksti "Lisää toimenpide"
                         :toiminto #(e! (pot2-tiedot/->LisaaAlustaToimenpide))
                         :opts {:ikoni (ikonit/livicon-plus)
                                :luokka "nappi-toissijainen"}}
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
                                                 :luokka "btn-xs"}]])])
       :rivi-klikattu #(log "click")}
      [{:otsikko "Toimen\u00ADpide" :nimi :toimenpide :leveys 3 :muokattava? (constantly false)
        :tyyppi :string
        :hae (fn [rivi]
               (if (pot2-tiedot/onko-toimenpide-verkko? alusta-toimenpiteet (:toimenpide rivi))
                 (pot2-domain/ainetyypin-koodi->nimi (:verkon-tyypit materiaalikoodistot) (:verkon-tyyppi rivi))
                 (pot2-domain/ainetyypin-koodi->lyhenne alusta-toimenpiteet (:toimenpide rivi))))
        :validoi [[:ei-tyhja "Anna arvo"]]}
       {:otsikko "Tie" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-numero :validoi (:tr-numero validointi)}
       {:otsikko "Ajor." :nimi :tr-ajorata :tyyppi :valinta :leveys perusleveys :elementin-id "alustan-ajor"
        :valinnat pot/+ajoradat-numerona+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi] (if rivi (:nimi rivi) "- Valitse Ajorata -"))
        :tasaa :oikea :kokonaisluku? true}
       {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys perusleveys :elementin-id "alustan-kaista"
        :valinnat pot/+kaistat+ :valinta-arvo :koodi
        :valinta-nayta (fn [rivi]
                         (if rivi (:nimi rivi) "- Valitse kaista -"))
        :tasaa :oikea :kokonaisluku? true}
       {:otsikko "Aosa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-alkuosa :validoi (:tr-alkuosa validointi)}
       {:otsikko "Aet" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-alkuetaisyys :validoi (:tr-alkuetaisyys validointi)}
       {:otsikko "Losa" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-loppuosa :validoi (:tr-loppuosa validointi)}
       {:otsikko "Let" :tyyppi :positiivinen-numero :tasaa :oikea :kokonaisluku? true
        :leveys perusleveys :nimi :tr-loppuetaisyys :validoi (:tr-loppuetaisyys validointi)}
       {:otsikko "Pituus" :nimi :pituus :leveys perusleveys :tyyppi :numero :tasaa :oikea
        :muokattava? (constantly false)
        :hae #(paallystys/rivin-kohteen-pituus
                (paallystys/tien-osat-riville % paallystys/tr-osien-tiedot) %)}
       {:otsikko "Toimenpiteen tie\u00ADdot" :nimi :toimenpiteen-tiedot :leveys 4
        :tyyppi :komponentti :muokattava? (constantly false)
        :komponentti (fn [rivi]
                       [pot2-tiedot/toimenpiteen-tiedot rivi])}
       {:otsikko "Materiaa\u00ADli" :nimi :materiaalin-tiedot :leveys 3
        :tyyppi :komponentti :muokattava? (constantly false)
        :komponentti (fn [rivi]
                       (when (or (:massa rivi) (:murske rivi))
                         [mm-yhteiset/materiaalin-tiedot (cond
                                                           (:massa rivi)
                                                           (materiaali massat rivi)

                                                           (:murske rivi)
                                                           (materiaali murskeet rivi)

                                                           :else
                                                           nil)
                          {:materiaalikoodistot materiaalikoodistot}
                          #(e! (pot2-tiedot/->NaytaMateriaalilomake rivi))]))}
       {:otsikko "" :nimi :alusta-toiminnot :tyyppi :reagent-komponentti :leveys perusleveys
        :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? alustarivit-atom :alusta]
        :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
      alustarivit-atom]]))