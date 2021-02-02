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
    [harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset :as pot2-yhteiset]
    [harja.pvm :as pvm])
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
           murskeet
           verkon-sijainnit
           verkon-tyypit
           verkon-tarkoitukset]}]
  (let [kaikki-lisakentat {:lisatty-paksuus  {:nimi   :lisatty-paksuus :otsikko "Lisätty paksuus"
                                              :tyyppi :positiivinen-numero :kokonaisluku? true}
                           :massamaara       {:nimi   :massamaara :otsikko "Massamäärä"
                                              :tyyppi :positiivinen-numero :kokonaisluku? true}
                           :murske           {:otsikko      "Murske" :nimi :murske :tyyppi :valinta
                                              :valinta-arvo ::pot2-domain/murske-id :valinta-nayta ::pot2-domain/murske-id ;; mitä haluamme näyttää?
                                              :valinnat     murskeet}
                           :verkon-tyyppi    {:otsikko      "Verkon tyyppi" :nimi :verkon-tyyppi :tyyppi :valinta
                                              :valinta-arvo ::pot2-domain/koodi :valinta-nayta ::pot2-domain/nimi
                                              :valinnat     verkon-tyypit}
                           :verkon-sijainti  {:otsikko      "Sijainti" :nimi :verkon-sijainti :tyyppi :valinta
                                              :valinta-arvo ::pot2-domain/koodi :valinta-nayta ::pot2-domain/nimi
                                              :valinnat     verkon-sijainnit}
                           :verkon-tarkoitus {:otsikko      "Tarkoitus" :nimi :verkon-tarkoitus :tyyppi :valinta
                                              ;; TODO: verkon_sijainti :hae-pot2-koodistot palvelun kautta tänne
                                              :valinta-arvo ::pot2-domain/koodi :valinta-nayta ::pot2-domain/nimi
                                              :valinnat     verkon-tarkoitukset}}
        toimenpidespesifit-lisakentat (pot2-domain/alusta-toimenpide-lisaavaimet toimenpide)
        lisakentta-generaattori (fn [kentta]
                                  (lomake/rivi (merge (get kaikki-lisakentat kentta)
                                              {:palstoja 3 :pakollinen? true})))]
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
  [e! {:keys [alustalomake alusta-toimenpiteet murskeet materiaalikoodistot]}]
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
                            :murskeet murskeet
                            :verkon-sijainnit (:verkon-sijainnit materiaalikoodistot)
                            :verkon-tyypit (:verkon-tyypit materiaalikoodistot)
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
       {:otsikko "Toimenpiteen tie\u00ADdot" :nimi :toimenpiteen-tiedot :leveys 3 :muokattava? (constantly false)
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       [pot2-tiedot/toimenpiteen-tiedot rivi materiaalikoodistot])}
       {:otsikko "Materiaa\u00ADli" :nimi :materiaalin-tiedot :leveys 3 :muokattava? (constantly false)
        :tyyppi :string :hae (fn [rivi] [pot2-tiedot/materiaalin-tiedot rivi])}
       {:otsikko "" :nimi :alusta-toiminnot :tyyppi :reagent-komponentti :leveys perusleveys
        :tasaa :keskita :komponentti-args [e! app kirjoitusoikeus? alustarivit-atom :alusta]
        :komponentti pot2-yhteiset/rivin-toiminnot-sarake}]
      alustarivit-atom]]))