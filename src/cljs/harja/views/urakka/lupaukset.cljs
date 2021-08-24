(ns harja.views.urakka.lupaukset
  "Lupausten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.lupaukset :as lupaus-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.bootstrap :as bs]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.views.urakka.lupaukset.kuukausipaatos-tilat :as kuukausitilat]
            [harja.ui.kentat :as kentat]
            [harja.validointi :as v]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.urakka.lupaukset.vastauslomake :as vastauslomake])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- pisteet-div [pisteet teksti]
  [:div {:class (str "lupausryhman-" (str/lower-case teksti))}
   [:div.pisteet (or pisteet 0)]
   [:div.teksti teksti]])


(defn- kuukausivastauksen-status [e! kohdekuukausi vastaus app]
  (let [vastaukset (:vastaukset vastaus)
        kohdevuosi (kuukausitilat/paattele-kohdevuosi kohdekuukausi vastaukset app)]
    [kuukausitilat/kuukausi-wrapper e! kohdekuukausi kohdevuosi vastaus nil true]))

(defn- lupaus-kuukausi-rivi [e! vastaus app]
  [:div.row.kk-tilanne
   [:div.col-xs-4.lupaus-kolumni
    (str "Lupaus " (:lupaus-jarjestys vastaus))]
   [:div.col-xs-6.vastaus-kolumni
    [:div.row
     (for [kk (concat (range 10 13) (range 1 10))]
       ^{:key (str "kk-rivi-" kk "-" (hash vastaus))}
       [kuukausivastauksen-status e! kk vastaus app])]]
   [:div.col-xs-1.oikea-raja.vastausrivi-pisteet
    [pisteet-div (:pisteet vastaus) "ENNUSTE"]]
   [:div.col-xs-1.vastausrivi-pisteet
    [:div {:style {:float "left"}}
     (if (= "yksittainen" (:lupaustyyppi vastaus))
       [pisteet-div (:pisteet vastaus) "MAX"]
       [pisteet-div (:kyselypisteet vastaus) "MAX"])]
    [:div.nuoli {:style {:float "right"}
                 :on-click (fn [e]
                             (do
                               (.preventDefault e)
                               (e! (lupaus-tiedot/->AvaaLupausvastaus vastaus nil nil))))}
     (ikonit/navigation-right)]]])

(defn muodosta-kannanotto [ryhma]
  (cond
    ;; 0 tai vähemmön
    (< (:odottaa-kannanottoa ryhma) 1)
    [:div
     [:div.circle-16.vihrea {:style {:float "left"}}]
     [:span "Ei kannanottoja merkittävänä."]]
    (= (:odottaa-kannanottoa ryhma) 1)
    [:div
     [:div.circle-16.keltainen {:style {:float "left"}}]
     [:span "1 lupaus odottaa kannanottoa."]]
    (> (:odottaa-kannanottoa ryhma) 1)
    [:div
     [:div.circle-16.keltainen {:style {:float "left"}}]
     [:span (str (:odottaa-kannanottoa ryhma) " lupausta odottaa kannanottoa.")]]
    :else [:div
           [:div.circle-16.vihrea {:style {:float "left"}}]
           [:span "Ei kannanottoja odottamassa."]]))

(defn- lupausryhma-rivi [e! app ryhma ryhman-vastaukset]
  (let [auki? (contains? (:avoimet-lupausryhmat app) (:kirjain ryhma))]
    [:div.lupausryhmalistaus {:style {:border-bottom "1px solid #D6D6D6"}}
     [:div.row.lupausryhma-rivi {:on-click #(e! (lupaus-tiedot/->AvaaLupausryhma (:kirjain ryhma)))}
      [:div.col-xs-4.oikea-raja.lupausryhma-nimi
       [:div {:style {:float "left" :padding-right "16px"}}
        (if auki?
          [ikonit/navigation-ympyrassa :down]
          [ikonit/navigation-ympyrassa :right])]
       [:div.ryhma-otsikko {:style {:float "left"}} (str (:kirjain ryhma) ". " (:otsikko ryhma))]]
      [:div.col-xs-6.oikea-raja {:style {:display "inline-block"
                                         :height "100%"}}
       (muodosta-kannanotto ryhma)]
      [:div.col-xs-1.oikea-raja {:style {:text-align "center"
                                         :height "100%"}}
       [pisteet-div (:pisteet ryhma) "ENNUSTE"]]
      [:div.col-xs-1 {:style {:height "100%"}}
       [pisteet-div (+ (:pisteet ryhma) (:kyselypisteet ryhma)) "MAX"]]]
     (when auki?
       (for [vastaus ryhman-vastaukset]
         ^{:key (str "Lupausrivi" (hash vastaus))}
         [:div.row {:style {:clear "both"
                            :height "67px"}}
          [lupaus-kuukausi-rivi e! vastaus app]]))]))


(defn- pisteympyra
  "Pyöreä nappi, jonka numeroa voi tyypistä riippuen ehkä muokata."
  [tiedot toiminto urakka]
  (assert (#{:ennuste :toteuma :lupaus} (:tyyppi tiedot)) "Tyypin on oltava ennuste, toteuma tai lupaus")
  (let [oikeus-asettaa-luvatut-pisteet?
        (and
          (roolit/tilaajan-kayttaja? @istunto/kayttaja)
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet
                                    (:id urakka)))]
    [:div.inline-block.lupausympyra-container
     [:div {:on-click (when (and toiminto oikeus-asettaa-luvatut-pisteet?)
                        toiminto)
            :style {:cursor (when (and toiminto oikeus-asettaa-luvatut-pisteet?)
                              "pointer")}
            :class ["lupausympyra" (:tyyppi tiedot)]}
      [:h3 (:pisteet tiedot)]
      (when (and toiminto oikeus-asettaa-luvatut-pisteet?)
        (ikonit/action-edit))]
     [:div.lupausympyran-tyyppi (when-let [tyyppi (:tyyppi tiedot)] (name tyyppi))]]))

(defn- yhteenveto [e! {:keys [muokkaa-luvattuja-pisteita? lupaus-sitoutuminen yhteenveto] :as app} urakka]
  [:div.lupausten-yhteenveto
   [:div.otsikko-ja-kuukausi
    [:div "Yhteenveto"]
    (cond
      (get-in app [:vastaus-lomake :vastauskuukausi])
      [:h2.kuukausi (str (pvm/kuukausi-isolla (get-in app [:vastaus-lomake :vastauskuukausi])) " " (pvm/vuosi (pvm/nyt)))]
      (:valittu-hoitokausi app)
      [:h2.kuukausi (str (pvm/pvm (first (:valittu-hoitokausi app))) " - " (pvm/pvm (second (:valittu-hoitokausi app))))])]
   [:div.lupauspisteet
    [pisteympyra {:pisteet (get-in yhteenveto [:pisteet :ennuste])
                  :tyyppi :ennuste} nil urakka]
    (if muokkaa-luvattuja-pisteita?
      [:div.lupauspisteen-muokkaus-container
       [:div.otsikko "Luvatut pisteet"]
       [kentat/tee-kentta {:tyyppi :positiivinen-numero :kokonaisluku? true
                           :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100 1))}
        (r/wrap (get-in app [:lupaus-sitoutuminen :pisteet])
                (fn [pisteet]
                  (e! (lupaus-tiedot/->LuvattujaPisteitaMuokattu pisteet))))]
       [napit/yleinen-ensisijainen "Valmis"
        #(e! (lupaus-tiedot/->TallennaLupausSitoutuminen (:urakka @tila/yleiset)))
        {:luokka "lupauspisteet-valmis"}]]
      [pisteympyra (merge lupaus-sitoutuminen
                          {:tyyppi :lupaus})
       #(e! (lupaus-tiedot/->VaihdaLuvattujenPisteidenMuokkausTila))
       urakka])]])

(defn- ennuste-opaste [ikoni otsikko-teksti opaste-teksti]
  [:div
   [:div.inline-block {:style {:font-size "20px" :margin-right "20px"}} ikoni]
   [:div.inline-block [:h3 otsikko-teksti]]
   [:div {:style {:margin-left "40px"}} opaste-teksti]])

(defn- ennuste [e! app]
  (let [kuukauden-nimi "testikuukausi"
        bonusta? (not (nil? (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])))
        sanktiota? (not (nil? (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio])))
        summa (if bonusta?
                (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])
                (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio]))
        ennusteen-tila-teksti (if bonusta? "bonusta" "sanktioita")
        hoitokauden-jarj-nro (when (:valittu-hoitokausi app) (urakka-tiedot/hoitokauden-jarjestysnumero
                                                               (pvm/vuosi (first (:valittu-hoitokausi app)))
                                                               (-> @tila/yleiset :urakka :loppupvm)))]
    [:div.lupausten-ennuste {:class (cond bonusta? " bonusta"
                                          sanktiota? " sanktiota"
                                          (and (false? bonusta?) (false? sanktiota?)) " neutraali")}
     [:div.row
      [:div.col-xs-8
       (cond
         (= (get-in app [:yhteenveto :ennusteen-tila]) :ei-viela-ennustetta)
         (ennuste-opaste [ikonit/harja-icon-status-help]
                         "Ei vielä ennustetta"
                         "Ensimmäiset ennusteet annetaan Marraskuun alussa, kun tiedot on syötetty ensimmäiseltä kuukaudelta.")
         (= (get-in app [:yhteenveto :ennusteen-tila]) :ennuste)
         (ennuste-opaste [ikonit/harja-icon-status-info]
                         (str kuukauden-nimi " ennusteen mukaan urakalle on tulossa " ennusteen-tila-teksti)
                         "Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.")
         (= (get-in app [:yhteenveto :ennusteen-tila]) :alustava-toteuma)
         (ennuste-opaste [ikonit/harja-icon-status-info]
                         (str "Toteuman mukaan urakalle on tulossa " ennusteen-tila-teksti)
                         "Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.")
         (= (get-in app [:yhteenveto :ennusteen-tila]) :katselmoitu-toteuma)
         (ennuste-opaste [ikonit/harja-icon-status-info]
                         (str "Urakalle tuli " ennusteen-tila-teksti " " hoitokauden-jarj-nro ". hoitovuotena ")
                         "Tiedot on käyty läpi välikatselmuksessa.")
         :else [:div "Ennusteen mukaan urakalle on tulossa sanktiota... (ominaisuus tekemättä)"])]
      [:div.col-xs-2
       [:div {:style {:float "right"}}  "välikatselmusnappi"]
       ]
      [:div.col-xs-2
       [:div {:style {:float "right"}}
        [:div [:h2 (fmt/desimaaliluku summa 2 true) " €"]]
        [:div.vihje-teksti "Tavoitehinta <summa>"]]]]]))

(defn lupaukset-alempi-valilehti*
  [e! app]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
      (komp/sisaan-ulos
        #(do
           (e! (lupaus-tiedot/->AlustaNakyma urakka))
           (e! (lupaus-tiedot/->HaeUrakanLupaustiedot urakka)))

        #(e! (lupaus-tiedot/->NakymastaPoistuttiin)))
      (fn [e! app]
        [:span.lupaukset-sivu
         (when (:vastaus-lomake app)
           [vastauslomake/vastauslomake e! app])
         [:div.otsikko-ja-hoitokausi
          [:h1 "Lupaukset"]
          [valinnat/urakan-hoitokausi-tuck (:valittu-hoitokausi app)
           (:urakan-hoitokaudet app) #(e! (lupaus-tiedot/->HoitokausiVaihdettu urakka %))]]
         [yhteenveto e! app urakka]
         [ennuste e! app]
         [:div.row {:style (merge {}
                                  (when (not (empty? (:lupausryhmat app)))
                                    {:border-top "1px solid #D6D6D6"}))}
          (for [ryhma (:lupausryhmat app)]
            ^{:key (str "lupaustyhma" (:jarjestys ryhma))}
            [lupausryhma-rivi e! app ryhma (get (:lupaukset app) (:otsikko ryhma))])]
         [debug app {:otsikko "TUCK STATE"}]]))))

(defn- valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :lupaukset (#{:teiden-hoito} tyyppi)
    false))

(defn lupaukset-paatason-valilehti [ur]
  (fn [{:keys [tyyppi] :as ur}]
    ;; vain MHU-urakoissa halutaan Lupaukset, jolloin alatabit näkyviin. Muutoin suoraan Välitavoitteet sisältö
    (if (= tyyppi :teiden-hoito)
      [bs/tabs
       {:style :tabs :classes "tabs-taso2"
        ;; huom: avain yhä valitavoitteet, koska Rooli-excel ja oikeudet
        :active (nav/valittu-valilehti-atom :valitavoitteet)}

       "Lupaukset" :lupaukset
       (when (valilehti-mahdollinen? :lupaukset ur)
         [tuck/tuck tila/lupaukset lupaukset-alempi-valilehti*])

       "Välitavoitteet" :valitavoitteet-nakyma
       [valitavoitteet/valitavoitteet ur]]
      [valitavoitteet/valitavoitteet ur])))