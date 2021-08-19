(ns harja.views.urakka.lupaukset
  "Lupausten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [goog.string :as gstring]
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
            [harja.tiedot.urakka :as u]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
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
    [kuukausitilat/kuukausi-wrapper kohdekuukausi kohdevuosi vastaus nil]))

(defn- lupaus-kuukausi-rivi [e! vastaus app]
  [:div.row.kk-tilanne {:style {:border-left "3px solid #0066CC"
                                :margin-left "16px"}}
   [:div.col-xs-4.vastaus-kolumni
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
    [:div.nuoli {:style {:float "right"}} (ikonit/navigation-right)]]])

(defn- lupausryhma-accordion [e! app ryhma ryhman-vastaukset]
  (let [auki? (contains? (:avoimet-lupausryhmat app) (:kirjain ryhma))]
    [:div.lupausryhmalistaus {:style {:border-bottom "1px solid #D6D6D6"}}
     [:div.row.lupausryhma-rivi {:on-click #(e! (lupaus-tiedot/->AvaaLupausryhma (:kirjain ryhma)))}
      [:div.col-xs-4.oikea-raja.lupausryhma-nimi {:style {:height "100%" :padding-top "5px"}}
       [:div {:style {:float "left" :padding-right "16px"}}
        (if auki?
          [ikonit/navigation-ympyrassa :down]
          [ikonit/navigation-ympyrassa :right])]
       [:div.ryhma-otsikko {:style {:float "left"}} (str (:kirjain ryhma) ". " (:otsikko ryhma))]]
      [:div.col-xs-6.oikea-raja {:style {:display "inline-block"
                                         :height "100%"}}
       [:div.circle-16.keltainen {:style {:float "left"}}]
       [:span "1 lupaus odottaa kannanottoa."]]
      [:div.col-xs-1.oikea-raja {:style {:text-align "center"
                                         :height "100%"}}
       [pisteet-div (:pisteet ryhma) "ENNUSTE"]]
      [:div.col-xs-1 {:style {:height "100%"}}
       [pisteet-div (+ (:pisteet ryhma) (:kyselypisteet ryhma)) "MAX"]]]
     (when auki?
       (for [vastaus ryhman-vastaukset]
         ^{:key (str "Lupausrivi" (hash vastaus))}
         [:div.row {:style {:clear "both"
                            :height "67px"}
                    :on-click (fn [e]
                                (do
                                  (.preventDefault e)
                                  (e! (lupaus-tiedot/->AvaaLupausvastaus vastaus))))}
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

(defn- yhteenveto [e! {:keys [muokkaa-luvattuja-pisteita? lupaus-sitoutuminen] :as app} urakka]
  [:div.lupausten-yhteenveto
   [:div.otsikko-ja-kuukausi
    [:div "Yhteenveto"]
    [:h2.kuukausi (str (pvm/kuluva-kuukausi-isolla) " " (pvm/vuosi (pvm/nyt)))]]
   [:div.lupauspisteet
    [pisteympyra {:pisteet 0
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

(defn- ennuste [e! app]
  [:div.lupausten-ennuste
   [:div "Ennusteen mukaan urakalle on tulossa sanktiota... (ominaisuus tekemättä)"]])

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
            [lupausryhma-accordion e! app ryhma (get (:lupaukset app) (:otsikko ryhma))])]
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