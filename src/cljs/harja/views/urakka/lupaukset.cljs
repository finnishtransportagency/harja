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
            [harja.ui.yleiset :as yleiset]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.urakka.lupaukset.vastauslomake :as vastauslomake])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- pisteet-div [pisteet teksti]
  [:div {:class (str "lupausryhman-" (str/lower-case teksti))
         :style {:margin "auto"}}
   [:div.pisteet (or pisteet 0)]
   [:div.teksti teksti]])

(defn- toteuma-tai-ennuste-div [{:keys [pisteet-toteuma pisteet-ennuste]}]
  (cond pisteet-toteuma
        [pisteet-div pisteet-toteuma "TOTEUMA"]

        pisteet-ennuste
        [pisteet-div pisteet-ennuste "ENNUSTE"]

        :else
        [:div]))

(defn- kuukausivastauksen-status [e! app lupaus lupaus-kuukausi]
  (let [listauksessa? true
        valittu? (and (= (get-in app [:vastaus-lomake :lupaus-id]) (:lupaus-id lupaus))
                      (= (:kuukausi lupaus-kuukausi) (get-in app [:vastaus-lomake :vastauskuukausi])))]
    [kuukausitilat/kuukausi-wrapper e! lupaus lupaus-kuukausi listauksessa? valittu? {}]))

(defn- lupaus-kuukausi-rivi [e! app {:keys [lupaus-kuukaudet] :as lupaus}]
  [:div.row.kk-tilanne

   [:div.col-xs-3 {:style {:border-right "1px solid #d6d6d6"
                           :padding-right "0px"}}
    [:div {:style {:display "flex"}}
     [:div {:style {:flex-grow 1
                    :border-left "3px solid blue"
                    :height "67px"}}]
     [:div.lupaus-kolumni {:style {:flex-grow 11}}
      (str "Lupaus " (:lupaus-jarjestys lupaus))]]]
   [:div.col-xs-7.vastaus-kolumni
    [:div.row
     (for [lupaus-kuukausi lupaus-kuukaudet]
       ^{:key (str "kk-rivi-" lupaus-kuukausi "-" (hash lupaus-kuukausi))}
       [kuukausivastauksen-status e! app lupaus lupaus-kuukausi])]]
   [:div.col-xs-1.oikea-raja.vastausrivi-pisteet {:style {:display "flex"
                                                          :align-items "center"
                                                          :padding 0}}
    [toteuma-tai-ennuste-div lupaus]]
   [:div.col-xs-1.vastausrivi-pisteet
    [:div {:style {:display "flex"
                   :align-items "center"
                   :padding 0}}
     (if (= "yksittainen" (:lupaustyyppi lupaus))
       [pisteet-div (:pisteet lupaus) "MAX"]
       [pisteet-div (:kyselypisteet lupaus) "MAX"])]]])

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
    [:div {:style {:display "flex"}}
     [:div.circle-16.keltainen {:style {:float "left"}}]
     [:span (str (:odottaa-kannanottoa ryhma) " lupausta odottaa kannanottoa.")]]
    :else [:div
           [:div.circle-16.vihrea {:style {:float "left"}}]
           [:span "Ei kannanottoja odottamassa."]]))

(defn- lupausryhma-rivi [e! app ryhma ryhman-vastaukset]
  (let [auki? (contains? (:avoimet-lupausryhmat app) (:kirjain ryhma))]
    [:div.lupausryhmalistaus {:style {:border-bottom "1px solid #D6D6D6"}}
     [:div.row.lupausryhma-rivi {:style {:align-items "center"}
                                 :on-click #(e! (lupaus-tiedot/->AvaaLupausryhma (:kirjain ryhma)))}
      [:div.col-xs-3.oikea-raja.lupausryhma-nimi
       [:div {:style {:display "flex"
                      :align-items "center"
                      :height "100%"}}
        [:div.navikaatio-ikonit {:style {:align-items "center"}}
         (if auki?
           [ikonit/navigation-ympyrassa :down]
           [ikonit/navigation-ympyrassa :right])]
        [:div {:style {:float "left"
                       :flex-grow 11
                       :align-items "center"
                       :padding-left "16px"}}
         (str (:kirjain ryhma) ". " (:otsikko ryhma))]]]
      [:div.col-xs-7.oikea-raja.kannanotto {:style {:display "flex" :align-items "center"}} (muodosta-kannanotto ryhma)]
      [:div.col-xs-1.oikea-raja {:style {:padding 0
                                         :text-align "center"
                                         :height "100%"
                                         :display "flex"
                                         :align-items "center"}}
       [toteuma-tai-ennuste-div ryhma]]
      [:div.col-xs-1 {:style {:height "100%"
                              :padding 0
                              :text-align "center"
                              :display "flex"
                              :align-items "center"}}
       [pisteet-div (:pisteet-max ryhma) "MAX"]]]
     (when auki?
       (for [lupaus (:lupaukset ryhma)]
         ^{:key (str "Lupausrivi" (hash lupaus))}
         [:div.row {:style {:clear "both"
                            :height "67px"}}
          [lupaus-kuukausi-rivi e! app lupaus]]))]))

(defn- pisteympyra
  "Pyöreä nappi, jonka numeroa voi tyypistä riippuen muokata."
  [e! tiedot toiminto urakka app muokkaa?]
  (assert (#{:ennuste :toteuma :lupaus} (:tyyppi tiedot)) "Tyypin on oltava ennuste, toteuma tai lupaus")
  (let [oikeus-asettaa-luvatut-pisteet?
        (and
          (roolit/tilaajan-kayttaja? @istunto/kayttaja)
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet
                                    (:id urakka))
          ;; Luvattuja pisteitä ei saa enää muokata, jos urakalle on tehty välikatselmus
          (false? (get-in app [:yhteenveto :valikatselmus-tehty-urakalle?])))
        ;; Aseta focus input kenttään, jos muokkaustila on laitettu päälle
        input-id (str "input-sitoutuminen-pisteet")
        _ (when muokkaa?
            (yleiset/fn-viiveella #(.focus (.getElementById js/document input-id)) 200))]
    [:div.inline-block.lupausympyra-container
     [:div {:on-click (when (and (not (true? muokkaa?)) toiminto oikeus-asettaa-luvatut-pisteet?)
                        toiminto)
            :style {:cursor (when (and toiminto oikeus-asettaa-luvatut-pisteet?)
                              "pointer")}
            :class ["lupausympyra" (:tyyppi tiedot)]}
      (if muokkaa?
        [kentat/tee-kentta {:elementin-id input-id
                            :tyyppi :positiivinen-numero
                            :koko 3
                            :vayla-tyyli? true
                            :input-luokka "lupaus-sitoutumis-pisteet"
                            :kokonaisluku? true
                            :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100 1))
                            :on-key-down #(when (or (= 13 (-> % .-keyCode)) (= 13 (-> % .-which)))
                                            (e! (lupaus-tiedot/->TallennaLupausSitoutuminen (:urakka @tila/yleiset))))
                            :on-blur #(e! (lupaus-tiedot/->TallennaLupausSitoutuminen (:urakka @tila/yleiset)))}
         (r/wrap (get-in app [:lupaus-sitoutuminen :pisteet])
                 (fn [pisteet]
                   (e! (lupaus-tiedot/->LuvattujaPisteitaMuokattu pisteet))))]
        [:div.pisteluku (:pisteet tiedot)])
      (when (and (not (true? muokkaa?)) toiminto oikeus-asettaa-luvatut-pisteet?)
        [:div.edit-ikoni (ikonit/action-edit)])]
     [:div.lupausympyran-tyyppi (when-let [tyyppi (:tyyppi tiedot)] (name tyyppi))]]))

(defn- yhteenveto [e! {:keys [kuukausipisteet muokkaa-luvattuja-pisteita? lupaus-sitoutuminen yhteenveto] :as app} urakka]
  (let [hoitokauden-jarj-nro (when (:valittu-hoitokausi app) (urakka-tiedot/hoitokauden-jarjestysnumero
                                                               (pvm/vuosi (first (:valittu-hoitokausi app)))
                                                               (-> @tila/yleiset :urakka :loppupvm)))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakka))
        vanha-urakka? (or (= 2020 urakan-alkuvuosi)
                          (= 2019 urakan-alkuvuosi))]
    [:div.lupausten-yhteenveto
     [:div.otsikko-ja-kuukausi
      [:div "Yhteenveto"]
      (when (:valittu-hoitokausi app)
        [:div {:style {:font-size "20px"}}
         [:span.lihavoitu (str "Hoitovuosi " hoitokauden-jarj-nro ". ")]
         [:span (str "(" (pvm/pvm (first (:valittu-hoitokausi app))) " - " (pvm/pvm (second (:valittu-hoitokausi app))) ")")]])]

     ;; Näytetään vuonna 2019/2020 alkaville urakoille kuukausittaiset pistelaatikot, joihin ennusteen/toteuman voi syöttää
     (when vanha-urakka?
       [:div {:style {:display "flex"}}
        (for [kp kuukausipisteet]
          ^{:key (str "kuukausipisteet-" (hash kp) )}
          [kuukausitilat/kuukausiennuste e! app kp urakka])])

     [:div.lupauspisteet
      (let [{:keys [toteuma ennuste]} (:pisteet yhteenveto)]
        ;; Ennuste / Toteuma
        (when-not vanha-urakka?
          [pisteympyra e!
           (cond toteuma
                 {:pisteet toteuma
                  :tyyppi :toteuma}

                 ennuste
                 {:pisteet ennuste
                  :tyyppi :ennuste}

                 :else
                 {:pisteet nil
                  :tyyppi :ennuste})
           nil urakka app false]))
      ;; Lupaus
      [pisteympyra e! (merge lupaus-sitoutuminen
                             {:tyyppi :lupaus})
       #(e! (lupaus-tiedot/->VaihdaLuvattujenPisteidenMuokkausTila))
       urakka
       app
       muokkaa-luvattuja-pisteita?]]]))

(defn- ennuste-opaste [ikoni otsikko-teksti opaste-teksti]
  [:div.ennuste-opaste.inline-block
   [:div.inline-block {:style {:font-size "20px" :margin-right "20px"}} ikoni]
   [:div.inline-block [:h3.otsikko otsikko-teksti]]
   [:div {:style {:margin-left "40px"}} opaste-teksti]])

(defn- ennuste
  "Näyttää käyttäjälle tuleeko sanktioita tai bonusta. Jos sanktiota/bonusta ei voida laskea esim. tavoitehinnan puuttuessa
  niin kerrotaan siitäkin käyttäjälle."
  [e! app]
  (let [ennusteen-tila (get-in app [:yhteenveto :ennusteen-tila])
        bonusta? (or (and (not= :ei-viela-ennustetta ennusteen-tila)
                          (not= :tavoitehinta-puuttuu ennusteen-tila)
                          (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])) false)
        sanktiota? (or (and (not= :ei-viela-ennustetta ennusteen-tila)
                            (not= :tavoitehinta-puuttuu ennusteen-tila)
                            (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio])) false)
        neutraali? (or (= :tavoitehinta-puuttuu ennusteen-tila) (= :ei-viela-ennustetta ennusteen-tila) false)
        summa (if bonusta?
                (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])
                (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio]))
        ennusteen-tila-teksti (if bonusta? "bonusta" "sanktioita")
        hoitokauden-jarj-nro (when (:valittu-hoitokausi app) (urakka-tiedot/hoitokauden-jarjestysnumero
                                                               (pvm/vuosi (first (:valittu-hoitokausi app)))
                                                               (-> @tila/yleiset :urakka :loppupvm)))
        tavoitehinta (get-in app [:yhteenveto :tavoitehinta])]
    [:div.lupausten-ennuste {:class (cond bonusta? " bonusta"
                                          sanktiota? " sanktiota"
                                          neutraali? " neutraali")}
     [:div {:style {:display "flex"
                    :align-items "center"}}
      [:div {:style {:flex "4 1 0"}}
       (case ennusteen-tila
         :ei-viela-ennustetta
         (ennuste-opaste [ikonit/harja-icon-status-help]
                         "Ei vielä ennustetta"
                         "Ensimmäiset ennusteet annetaan Marraskuun alussa, kun tiedot on syötetty ensimmäiseltä kuukaudelta.")
         :ennuste
         (ennuste-opaste [ikonit/harja-icon-status-info]
                         (str "Ennusteen mukaan urakalle on tulossa " ennusteen-tila-teksti)
                         "Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.")
         :alustava-toteuma
         (ennuste-opaste [ikonit/harja-icon-status-info]
                         (str "Toteuman mukaan urakalle on tulossa " ennusteen-tila-teksti)
                         "Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.")
         :katselmoitu-toteuma
         (ennuste-opaste [ikonit/harja-icon-status-info]
                         (str "Urakalle tuli " ennusteen-tila-teksti " " hoitokauden-jarj-nro ". hoitovuotena ")
                         "Tiedot on käyty läpi välikatselmuksessa.")
         :tavoitehinta-puuttuu
         (ennuste-opaste [ikonit/harja-icon-status-alert]
                         (str "Hoitokauden tavoitehinta puuttuu")
                         "Täytä tavoitehinta suunnitteluosiossa valitulle hoitokaudelle.")
         nil [:div "Ennustetta ei voitu laskea"])]
      [:div {:style {:order 2
                     :width "135px"
                     :align-items "center"}}
       (if (= :katselmoitu-toteuma ennusteen-tila)
         [napit/muokkaa "Muokkaa" #(siirtymat/avaa-valikatselmus (:valittu-hoitokausi app)) {:luokka "napiton-nappi" :paksu? true}]
         [napit/yleinen-ensisijainen "Välikatselmus" #(siirtymat/avaa-valikatselmus (:valittu-hoitokausi app))])]
      (when (and summa tavoitehinta)
        [:div {:style {:order 3
                       :align-items "center"}}
         (when summa
           [:div {:style {:float "right"}}
            [:div [:span.lihavoitu {:style {:font-size "20px"}} (fmt/desimaaliluku summa 2 true) " €"]]
            (when tavoitehinta
              [:div.vihje-teksti (str "Tavoitehinta " tavoitehinta " €")])])])]]))

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
           (:urakan-hoitokaudet app)
           #(e! (lupaus-tiedot/->HoitokausiVaihdettu urakka %))]]
         [yhteenveto e! app urakka]
         [ennuste e! app]
         ;; Näytetään vuonna 2021 alkaville urakoille lupausryhmät
         (when (= 2021 (pvm/vuosi (:alkupvm urakka)))
           [:div.row {:style (merge {}
                                    (when (not (empty? (:lupausryhmat app)))
                                      {:border-top "1px solid #D6D6D6"}))}
            (for [ryhma (:lupausryhmat app)]
              ^{:key (str "lupaustyhma" (:jarjestys ryhma))}
              [lupausryhma-rivi e! app ryhma (get (:lupaukset app) (:otsikko ryhma))])])
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