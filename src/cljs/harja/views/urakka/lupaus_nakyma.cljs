(ns harja.views.urakka.lupaus-nakyma
  "Lupausten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :as tuck]
            [harja.loki :refer [log logt]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.lupaus-tiedot :as lupaus-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.bootstrap :as bs]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.views.urakka.lupaus.kuukausipaatos-tilat :as kuukausitilat]
            [harja.ui.kentat :as kentat]
            [harja.validointi :as validointi]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.roolit :as roolit]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.urakka.lupaus.vastauslomake :as vastauslomake]
            [harja.ui.yleiset :as y]
            [harja.asiakas.kommunikaatio :as k])
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

(defn- toteuma-tai-ennuste-luokka [{:keys [pisteet-toteuma]}]
  (cond (and pisteet-toteuma (pos? pisteet-toteuma)) "toteuma-pisteet-positiivnen"
        pisteet-toteuma "toteuma-pisteet-nolla"
        :else "ennuste-pisteet"))

(defn- lupaus-kuukausi-rivi [e! app {:keys [lupaus-kuukaudet] :as lupaus}]
  [:div.row.kk-tilanne
   [:div.col-xs-3 {:style {:padding-right "0px"}}
    [:div.lupaus-jarjestys-ja-kuvaus
     [:div.lupaus-jarjestys.semibold (str "Lupaus " (:lupaus-jarjestys lupaus))]
     [:div.lupaus-kuvaus.caption (:kuvaus lupaus)]]]
   [:div.col-xs-7.vastaus-kolumni.vasen-reuna
    [:div.row
     (for [lupaus-kuukausi lupaus-kuukaudet]
       ^{:key (str "kk-rivi-" lupaus-kuukausi "-" (hash lupaus-kuukausi))}
       [kuukausivastauksen-status e! app lupaus lupaus-kuukausi])]]
   [:div.col-xs-1.vastausrivi-pisteet
    {:class (toteuma-tai-ennuste-luokka lupaus)
     :style {:display "flex"
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
      [:div.col-xs-3.lupausryhma-nimi
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
      [:div.col-xs-7.kannanotto.vasen-reuna
       {:style {:display "flex" :align-items "center"}}
       (muodosta-kannanotto ryhma)]
      [:div.col-xs-1 {:style {:padding 0
                              :text-align "center"
                              :height "100%"
                              :display "flex"
                              :align-items "center"}
                      :class (toteuma-tai-ennuste-luokka ryhma)}
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
          (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
            (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
            @istunto/kayttaja)
          (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet
                                    (:id urakka))
          ;; Luvattuja pisteitä ei saa enää muokata, jos urakalle on tehty välikatselmus
          (false? (get-in app [:yhteenveto :valikatselmus-tehty-urakalle?])))
        input-id (str "input-sitoutuminen-pisteet")]
    (when muokkaa?
      ;; Aseta focus input kenttään, jos muokkaustila on laitettu päälle
      (yleiset/fn-viiveella
        #(some-> (.getElementById js/document input-id) .focus)
        200))
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
                            :validoi-kentta-fn (fn [numero] (validointi/validoi-numero numero 0 100 1))
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
        vanha-urakka? (lupaus-domain/urakka-19-20? urakka)]
    [:div.lupausten-yhteenveto
     [:div.otsikko-ja-kuukausi
      [:div "Yhteenveto"]
      (when (:valittu-hoitokausi app)
        [:div {:style {:font-size "20px"}}
         [:span.lihavoitu (str "Hoitovuosi " hoitokauden-jarj-nro ". ")]
         [:span (str "(" (pvm/pvm (first (:valittu-hoitokausi app))) " - " (pvm/pvm (second (:valittu-hoitokausi app))) ")")]])]

     ;; Näytetään vuonna 2019/2020 alkaville urakoille kuukausittaiset pistelaatikot, joihin ennusteen/toteuman voi syöttää
     (when vanha-urakka?
       [:div {:style {:display "flex" :flex-grow 1 :justify-content "right" :padding-right "32px"}}
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

               (and ennuste (not= :ei-viela-ennustetta (:ennusteen-tila yhteenveto)))
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
  (if (:yhteenveto app)
    (let [ennusteen-tila (get-in app [:yhteenveto :ennusteen-tila])
          bonusta? (and (not= :ei-viela-ennustetta ennusteen-tila)
                        (get-in app [:yhteenveto :bonus-tai-sanktio :bonus]))
          sanktiota? (and (not= :ei-viela-ennustetta ennusteen-tila)
                          (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio]))
          neutraali? (= :ei-viela-ennustetta ennusteen-tila)
          tavoite-taytetty? (get-in app [:yhteenveto :bonus-tai-sanktio :tavoite-taytetty])
          summa (cond
                  bonusta? (get-in app [:yhteenveto :bonus-tai-sanktio :bonus])
                  sanktiota? (get-in app [:yhteenveto :bonus-tai-sanktio :sanktio])
                  :else 0M)
          ennusteen-tila-teksti (if bonusta? "bonusta" "sanktioita")
          hoitokauden-jarj-nro (when (:valittu-hoitokausi app) (urakka-tiedot/hoitokauden-jarjestysnumero
                                                                 (pvm/vuosi (first (:valittu-hoitokausi app)))
                                                                 (-> @tila/yleiset :urakka :loppupvm)))
          tavoitehinta (get-in app [:yhteenveto :tavoitehinta])]
      [:div.lupausten-ennuste {:class (cond bonusta? " bonusta"
                                            tavoite-taytetty? " bonusta"
                                            sanktiota? " sanktiota"
                                            neutraali? " neutraali")}
       [:div {:style {:display "flex"
                      :align-items "center"}}
        [:div {:style {:flex "4 1 0"}}
         (cond
           (get-in app [:yhteenveto :luvatut-pisteet-puuttuu?])
           (ennuste-opaste [ikonit/harja-icon-status-alert]
                           (str "Luvattu pistemäärä puuttuu")
                           "Syötä urakoitsijan lupaama pistemäärä.")
           (get-in app [:yhteenveto :tavoitehinta-puuttuu?])
           (ennuste-opaste [ikonit/harja-icon-status-alert]
                           (str "Hoitokauden tavoitehinta puuttuu")
                           "Täytä tarjouksen alkuperäinen tavoitehinta suunnitteluosiossa valitulle hoitokaudelle.")
           (= :ei-viela-ennustetta ennusteen-tila)
           (ennuste-opaste [ikonit/harja-icon-status-help]
                           "Ei vielä ennustetta"
                           "Ensimmäiset ennusteet annetaan lokakuun alussa.")
           (= :ennuste ennusteen-tila)
           (ennuste-opaste [ikonit/harja-icon-status-info]
                           (if (not= 0 summa)
                             (str "Ennusteen mukaan urakalle on tulossa " ennusteen-tila-teksti)
                             (str "Ennusteen mukaan urakka on päässyt tavoitteeseen."))
                           "Kaikista lupauksista pitää olla viimeinen päättävä merkintä tehty ennen kuin toteuman voi laskea.")
           (= :alustava-toteuma ennusteen-tila)
           (ennuste-opaste [ikonit/harja-icon-status-info]
                           (if (not= 0 summa)
                             (str "Toteuman mukaan urakalle on tulossa " ennusteen-tila-teksti)
                             (str "Toteuman mukaan urakka pääsi tavoitteeseen."))
                           "Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.")
           (= :katselmoitu-toteuma ennusteen-tila)
           (ennuste-opaste [ikonit/harja-icon-status-info]
                           (str "Urakalle tuli " ennusteen-tila-teksti " " hoitokauden-jarj-nro ". hoitovuotena ")
                           "Tiedot on käyty läpi välikatselmuksessa.")
           :else
           [:div "Ennustetta ei voitu laskea"])]
        [:div {:style {:order 2
                       :width "135px"
                       :align-items "center"}}
         (when (and tavoitehinta (> tavoitehinta 0))
           (case ennusteen-tila
             :katselmoitu-toteuma
             [napit/muokkaa "Muokkaa" #(siirtymat/avaa-valikatselmus (:valittu-hoitokausi app)) {:luokka "napiton-nappi" :paksu? true}]

             :alustava-toteuma
             [napit/yleinen-ensisijainen "Välikatselmus" #(siirtymat/avaa-valikatselmus (:valittu-hoitokausi app))]

             nil))]
        (when (and summa tavoitehinta (> tavoitehinta 0) (not tavoite-taytetty?))
          [:div {:style {:order 3
                         :align-items "center"}}
           (when summa
             [:div {:style {:float "right"}}
              [:div [:span.lihavoitu {:style {:font-size "20px"}} (fmt/desimaaliluku summa 2 true) " €"]]
              (when tavoitehinta
                [:<>
                 [:div.vihje-teksti "Tarjouksen tavoitehinta:"]
                 [:div.vihje-teksti (fmt/desimaaliluku tavoitehinta 2 true) " €"]])])])]])
    [:div.flex-row.keskita
     [y/ajax-loader]]))

(defn nykyhetki [data]
  [:span.nykyhetki.label-ja-kentta
   [:span.kentan-otsikko "Aseta nykyhetki"]
   [:div.kentta
    [kentat/tee-kentta
     {:tyyppi :pvm}
     data]]])

(defn testausvalinnat [e! app]
  (when (k/kehitysymparistossa?)
    [:<>
     [:br]
     [:h3 "Testausta varten"]
     [:br]
     [nykyhetki (r/wrap
                  (:nykyhetki app)
                  #(e! (lupaus-tiedot/->AsetaNykyhetki %)))]
     [debug app {:otsikko "TUCK STATE"}]]))

(defn lupaukset-alempi-valilehti*
  [e! app]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
      (komp/sisaan-ulos
        #(do
           (when urakka
             (e! (lupaus-tiedot/->ValitseUrakka urakka))
             (e! (lupaus-tiedot/->HaeUrakanLupaustiedot urakka))))
        #(e! (lupaus-tiedot/->NakymastaPoistuttiin)))
      (komp/watcher nav/valittu-urakka
                    (fn [_ _ urakka]
                      ;; Näytetään välittömästi oikea hoitovuosi.
                      ;; Uudet lupaustiedot haetaan vähän myöhemmin :component-will-mount-vaiheessa
                      (when urakka
                        (e! (lupaus-tiedot/->ValitseUrakka urakka)))))
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
           (when-not (= :ei-viela-ennustetta (get-in app [:yhteenveto :ennusteen-tila]))
            [:div.row {:style (merge {}
                                     (when (not (empty? (:lupausryhmat app)))
                                       {:border-top "1px solid #D6D6D6"}))}
             (for [ryhma (:lupausryhmat app)]
               ^{:key (str "lupaustyhma" (:jarjestys ryhma))}
               [lupausryhma-rivi e! app ryhma (get (:lupaukset app) (:otsikko ryhma))])]))
         [testausvalinnat e! app]]))))

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