(ns harja.views.urakka.lupaus.vastauslomake
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.lupaus-tiedot :as lupaus-tiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.views.urakka.lupaus.kuukausipaatos-tilat :as kuukausitilat]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.ui.yleiset :as y]
            [harja.ui.debug :refer [debug]]))

(defn- kuukausivastauksen-status [e! lupaus-kuukausi lupaus app]
  (let [listauksessa? false
        valittu? (= (:kuukausi lupaus-kuukausi) (get-in app [:vastaus-lomake :vastauskuukausi]))]
    [kuukausitilat/kuukausi-wrapper e! lupaus lupaus-kuukausi listauksessa? valittu? (get-in app [:kommentit :lupaus->kuukausi->kommentit])]))

(defn- otsikko [e! app]
  (let [lupaus (:vastaus-lomake app)]
    [:div
     [:div.row
      (doall
        (for [lupaus-kuukausi (:lupaus-kuukaudet lupaus)]
          ^{:key (str "kk-vastaukset-" (hash lupaus-kuukausi))}
          [:div (when (lupaus-domain/kayttaja-saa-vastata? @istunto/kayttaja lupaus-kuukausi)
                  {:on-click (fn [e]
                               (do
                                 (.preventDefault e)
                                 (e! (lupaus-tiedot/->ValitseVastausKuukausi (:kuukausi lupaus-kuukausi) (:vuosi lupaus-kuukausi)))))})
           [kuukausivastauksen-status e! lupaus-kuukausi lupaus app]]))]]))

(defn kustannusennuste-taulukko []
  [:div.kustannusennuste-taulukko
   [:p "Ennustamme urakan hoitovuoden lopun tavoitehintaa ja toteutuvia kustannuksia 4 kertaa vuodessa alla mainittuihin määräpäiviin mennessä."]
  
   [:table.table.table-striped
    [:thead
     [:tr.paivamaara
      [:th {:colSpan 2} "15.10."]
      [:th {:colSpan 2} "15.1."]
      [:th {:colSpan 2} "30.4."]
      [:th {:colSpan 2} "30.6."]]
     [:tr.aliotsikko
      [:th "Tarkkuus"] [:th "Pisteet"]
      [:th "Tarkkuus"] [:th "Pisteet"]
      [:th "Tarkkuus"] [:th "Pisteet"]
      [:th "Tarkkuus"] [:th "Pisteet"]]]
    [:tbody
     [:tr
      [:td "> 9,0 %"] [:td "1"]
      [:td "> 6,0 %"] [:td "1"]
      [:td "> 3,0 %"] [:td "1"]
      [:td "> 2,0 %"] [:td "1"]]
     [:tr
      [:td "≤ 9,0 %"] [:td "4"]
      [:td "≤ 6,0 %"] [:td "4"]
      [:td "≤ 3,0 %"] [:td "4"]
      [:td "≤ 2,0 %"] [:td "4"]]
     [:tr
      [:td "≤ 7,0 %"] [:td "8"]
      [:td "≤ 4,0 %"] [:td "8"]
      [:td "≤ 2,0 %"] [:td "8"]
      [:td "≤ 1,0 %"] [:td "8"]]]]
  
   [:p "Hoitovuoden toteutuneet lupauspisteet todetaan laskemalla lupaustaulukon mukaan saatujen pisteiden keskiarvo. "
    [:strong "Mikäli jotain ennustetta ei tehdä määräaikaan mennessä, ennusteesta ei saa yhtään pistettä."]]])

(defn- sisalto [e! vastaus]
  [:div {:id "vastauslomake-sisalto"}
   [:hr]
   [:div.row
    [:h2 "Ota kantaa lupauksiin"]
    [:span.lupausryhma-otsikko
     (str (lupaus-domain/numero->kirjain (:lupausryhma-jarjestys vastaus)) ". " (:lupausryhma-otsikko vastaus))]
    [:div.flex-row
     [:h3.vastauslomake-lupaus-jarjestys
      (str "Lupaus " (:lupaus-jarjestys vastaus))]
     [:h3.vastauslomake-lupaus-pisteet
      (if (= "yksittainen" (:lupaustyyppi vastaus))
        (:pisteet vastaus)
        (str "Pisteet 0 - " (:kyselypisteet vastaus)))]]
    [:div.caption.vastauslomake-lupaus-kuvaus (:kuvaus vastaus)]
    [:div.sisalto {:dangerouslySetInnerHTML {:__html (:sisalto vastaus)}}]
    [kustannusennuste-taulukko]
    ]])

(defn- kommentti-rivi [e! {:keys [id luotu luoja etunimi sukunimi kommentti poistettu]}]
  [:div.kommentti-rivi
   [:div.luomistiedot
    [:span.luotu (pvm/pvm-aika luotu)]
    [:span.luoja (str etunimi " " sukunimi)]]
   [:div.kommentti-laatikko.flex-row
    (if-not poistettu
      [:span.kommentti-teksti kommentti]
      [:span.kommentti-teksti.poistettu "Tämä viesti on poistettu"])
    (when (and (= luoja (-> @istunto/kayttaja :id))
               (not poistettu))
      [napit/yleinen-reunaton ""
       #(varmista-kayttajalta/varmista-kayttajalta
          {:otsikko "Poista kommentti"
           :sisalto "Haluatko poistaa kommentin?"
           :hyvaksy "Poista"
           :peruuta-txt "Peruuta"
           :toiminto-fn (fn []
                          (e! (lupaus-tiedot/->PoistaKommentti id)))
           :modal-luokka "ei-sulje-sivupaneelia"})
       {:ikoni (ikonit/harja-icon-action-delete)
        :luokka "btn-xs"}])]])

(defn- lisaa-kommentti-kentta [e! lisays-kaynnissa?]
  [:div.lisaa-kommentti
   (r/with-let [lisaa-kommentti? (r/atom false)
                kommentti (r/atom nil)]
     [:<>
      [:div (when-not @lisaa-kommentti?
              {:style {:display "none"}})
       [kentat/tee-kentta {:tyyppi :text
                           :nimi :kommentti
                           :placeholder "Lisää kommentti"
                           :pituus-max 4000}
        kommentti]
       [:div.flex-row.margin-top-16
        [napit/tallenna
         "Tallenna"
         #(do
            (e! (lupaus-tiedot/->LisaaKommentti @kommentti))
            (reset! kommentti nil)
            (reset! lisaa-kommentti? false))
         {:disabled (str/blank? @kommentti)}]
        [napit/peruuta
         "Peruuta"
         #(reset! lisaa-kommentti? false)]]]

      [yleiset/linkki
       "Lisää kommentti"
       #(reset! lisaa-kommentti? true)
       {:style (when (or @lisaa-kommentti?)
                 {:display "none"})
        :id (str "lisaa-kommentti")
        :ikoni (ikonit/livicon-kommentti)
        :luokka "napiton-nappi btn-xs semibold"}]])])

(defn- kommentit [e!
                  {:keys [haku-kaynnissa? lisays-kaynnissa? poisto-kaynnissa? lupaus->kuukausi->kommentit]}
                  {:keys [vastauskuukausi lupaus-id]}]
  [:div.lupaus-kommentit
   [y/himmennys {:himmenna? (or haku-kaynnissa? lisays-kaynnissa? poisto-kaynnissa?)
                 :himmennyksen-sisalto [y/ajax-loader]}
    [:<>
     (when-let [kommentit (get-in lupaus->kuukausi->kommentit [lupaus-id vastauskuukausi])]
       [:<>
        [:div.body-text.semibold "Kommentit"]
        (doall
          (map-indexed
            (fn [i kommentti]
              ^{:key i}
              [kommentti-rivi e! kommentti])
            kommentit))])
     [lisaa-kommentti-kentta e! lisays-kaynnissa?]]]])

(defn- sulje-nappi
  ([e!]
   (sulje-nappi e! {}))
  ([e! {:keys [luokka]}]
   [napit/yleinen-toissijainen
    "Sulje"
    #(e! (lupaus-tiedot/->SuljeLupausvastaus))
    {:paksu? true
     :luokka luokka}]))

(defn- yksittainen-lupaus? [app]
  (= "yksittainen" (get-in app [:vastaus-lomake :lupaustyyppi])))

(defn- lupaus-css-luokka [app]
  (if (yksittainen-lupaus? app) "kylla-ei" "monivalinta"))

(defn- monivalinta [e! {:keys [vaihtoehdot
                               lupaus
                               kohdekuukausi
                               kohdevuosi
                               kuukauden-vastaus
                               kuukauden-vastaus-atom
                               otsikko
                               disabled?
                               ladataan?]}]
  [:fieldset.tiiviit-labelit.margin-bottom-16 {:style {:padding "0 32px 0 32px"}}
   (when otsikko
     [:legend.lihavoitu.sivupalkki-footer-otsikko.margin-bottom-4  otsikko])
   [y/himmennys {:himmenna? disabled?
                 :himmennyksen-sisalto (when ladataan?
                                         [y/ajax-loader])}
    [kentat/tee-kentta
     {:tyyppi :radio-group
      :radio-luokka "tiivis"
      :nimi :id
      :disabloitu? disabled?
      :nayta-rivina? false
      :vayla-tyyli? true
      :vaihtoehto-arvo :id
      :vaihtoehto-nayta (fn [arvo]
                          (let [vaihtoehto-tekstiksi #(cond
                                                        (nil? %) ""
                                                        (str/includes? % "<=") (str/replace % "<=" "alle tai yhtäsuuri kuin")
                                                        (str/includes? % ">") (str/replace % ">" "suurempi kuin") 
                                                        :else %)]
                            [:div {:style {:flex-shrink 0 :flex-grow 1 :flex-direction "row" :display "flex"}}
                             [:div {:style {:flex-grow 1 :text-align "left"}} (vaihtoehto-tekstiksi (:vaihtoehto arvo))]
                             [:div {:style {:flex-grow 1 :text-align "right"}}
                              (when-not (:vaihtoehto-seuraava-ryhma-id arvo)
                                (str " " (:pisteet arvo) (when (:pisteet arvo) " pistettä")))]]))
      :vaihtoehdot vaihtoehdot
      :valitse-fn (fn [valinta]
                    (let
                      [tulos (->> vaihtoehdot
                               (filter #(= (:id %) valinta))
                               first)]
                      (if (int? (:vaihtoehto-seuraava-ryhma-id tulos))
                        (e! (lupaus-tiedot/->NaytaSeuraavatVaihtoehdot tulos))
                        (e! (lupaus-tiedot/->ValitseVaihtoehto
                              (merge tulos {:kuukauden-vastaus-id (:id kuukauden-vastaus)})
                              lupaus kohdekuukausi kohdevuosi)))))
      :kaari-flex-row? false}
     kuukauden-vastaus-atom]]])

(defn- kustannusennuste-syottokentit [e! {:keys [kohdekuukausi kohdevuosi lupaus disabled? ladataan?]}]
  [:div.kustannusennuste-syottokentit
   
   ;; Ensimmäinen rivi - Tavoitehinta ja Ennuste
   [:div.row
    [:div.lihavoitu.sivupalkki-footer-otsikko.col-xs-12.col-md-12
     [:h5 "Kustannusennusteen tiedot"]]
    [:div.col-xs-12.col-md-6
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Tavoitehinta € *"
       :luokka "poista-label-top-margin"
       :vayla-tyyli? true
       :arvo-atom (r/atom nil)
       :kentta-params {:tyyppi :numero
                       :vayla-tyyli? true
                       :disabled? disabled?
                       :kokonaisosan-maara 10
                       :desimaalien-maara 2
                       :placeholder "0,00"}}]]

    [:div.col-xs-12.col-md-6
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Ennuste € *"
       :luokka "poista-label-top-margin"
       :vayla-tyyli? true
       :arvo-atom (r/atom nil)
       :kentta-params {:tyyppi :numero
                       :vayla-tyyli? true
                       :disabled? disabled?
                       :kokonaisosan-maara 10
                       :desimaalien-maara 2
                       :placeholder "0,00"}}]]]

   ;; Toinen rivi - Toteutunut ja Poikkeama
   [:div.row
    [:div.col-xs-12.col-md-6
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Toteutuneet kustannukset € *"
       :luokka "poista-label-top-margin"
       :vayla-tyyli? true
       :arvo-atom (r/atom nil)
       :kentta-params {:tyyppi :numero
                       :vayla-tyyli? true
                       :disabled? disabled?
                       :kokonaisosan-maara 10
                       :desimaalien-maara 2
                       :placeholder "0,00"}}]]

    [:div.col-xs-12.col-md-6
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Poikkeama %"
       :luokka "poista-label-top-margin"
       :vayla-tyyli? true
       :arvo-atom (r/atom nil)
       :kentta-params {:tyyppi :numero
                       :vayla-tyyli? true
                       :disabled? true ;; Laskettu automaattisesti
                       :kokonaisosan-maara 3
                       :desimaalien-maara 1
                       :placeholder "0,0"}}]]]

   ;; Kolmas rivi - Pisteet (keskitetty)
   [:div.row
    [:div.col-xs-12.col-md-6
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Pisteet"
       :luokka "poista-label-top-margin"
       :vayla-tyyli? true
       :arvo-atom (r/atom nil)
       :kentta-params {:tyyppi :numero
                       :vayla-tyyli? true
                       :disabled? true ;; Laskettu automaattisesti
                       :kokonaisosan-maara 2
                       :desimaalien-maara 0
                       :placeholder "0"}}]]]])


(defn- vastaukset [e! app luokka]
  (let [kohdekuukausi (get-in app [:vastaus-lomake :vastauskuukausi])
        lupaus-kuukausi (lupaus-domain/etsi-lupaus-kuukausi (get-in app [:vastaus-lomake :lupaus-kuukaudet]) kohdekuukausi)
        kohdevuosi (get-in app [:vastaus-lomake :vastausvuosi])
        lupaus (:vastaus-lomake app)
        vaihtoehdot (:vaihtoehdot lupaus)                   ;; Monivalinnassa on vaihtoehtoja
        ;; Lupaustietojen mukana saatu viimeisin vastaus
        kuukauden-vastaus (:vastaus lupaus-kuukausi)
        ;; Palvelimelle lähetetty vastaus, joka näytetään siihen asti, että uudet lupaustiedot on haettu
        lahetetty-vastaus (get-in app [:vastaus-lomake :lahetetty-vastaus])
        ladataan? (and (= (get-in app [:lupausta-lahetataan :kohdekuukausi]) kohdekuukausi) 
                        (= (get-in app [:lupausta-lahetataan :lupaus-id]) (:lupaus-id lupaus))) 
        saa-vastata? (and (not ladataan?)
                          (lupaus-domain/kayttaja-saa-vastata? @istunto/kayttaja lupaus-kuukausi)
                          (lupaus-domain/ennusteen-tila->saa-vastata? (get-in app [:yhteenveto :ennusteen-tila])))
        disabled? (not saa-vastata?)
        ;; Lisätään vaihtoehtoinin myös "nil" vaihtoehto, jotta vahinkovalinnan voi poistaa
        tyhja-vaihtoehto-templaatti (first vaihtoehdot)
        vaihtoehdot (merge vaihtoehdot
                           (-> tyhja-vaihtoehto-templaatti
                               (assoc :id nil)
                               (assoc :vaihtoehto "ei valintaa")
                               (assoc :pisteet nil)))
        vaihdoehdot-vaiheet (sort (group-by :vaihtoehto-askel vaihtoehdot))
        naytettavat-vaiheet (:naytettavat-valinnat lupaus) 
        lahetetty-vastaus-id (:lupaus-vaihtoehto-id lahetetty-vastaus)
        valittu-arvo (:lupaus-vaihtoehto-id kuukauden-vastaus)
        kuukauden-vastaus-atom (atom (if lahetetty-vastaus
                                       lahetetty-vastaus-id
                                       valittu-arvo))
        ;; Kyllä/Ei valinnassa vaihtoehdot on true/false
        vastaus-ke (if lahetetty-vastaus
                     (:vastaus lahetetty-vastaus)
                     (:vastaus kuukauden-vastaus)) 
        miten-kuukausi-meni-str (str "Miten " (pvm/kuukauden-lyhyt-nimi kohdekuukausi) "kuu meni?")]
    [:div.sivupalkki-footer {:class luokka}
     (cond
       ;; Kustannusennustelupaus - näytä syöttökentät
       (= "kustannusennuste" (:lupaustyyppi lupaus))
       [:div
        [kustannusennuste-syottokentit e! {:kohdekuukausi kohdekuukausi
                                           :kohdevuosi kohdevuosi
                                           :lupaus lupaus
                                           :disabled? disabled?
                                           :ladataan? ladataan?}]
        [sulje-nappi e! {:luokka "pull-right"}]]
    
       ;; Yksittäinen lupaus (kyllä/ei)
       (lupaus-domain/yksittainen? lupaus)
       [:div.flex-row
        [:div.lihavoitu {:style {:margin-left "1rem"}} miten-kuukausi-meni-str]
        [kentat/kylla-ei-valinta
         {:on-click #(e! (lupaus-tiedot/->ValitseKE {:vastaus %
                                                     :kuukauden-vastaus-id (:id kuukauden-vastaus)}
                           lupaus kohdekuukausi kohdevuosi))
          :ladataan? ladataan?
          :disabled? disabled?}
         vastaus-ke]
        [sulje-nappi e!]]
    
       ;; Monivalinta
       :else
       [:div
        [:div.lihavoitu.sivupalkki-footer-otsikko {:style {:padding "0 32px 0 32px"}}
         [:h4 miten-kuukausi-meni-str]]
        (if (every? #(nil? (get % :vaihtoehto-askel)) vaihtoehdot)
          ;; Yksi monivalinta
          [monivalinta e! {:vaihtoehdot vaihtoehdot
                           :lupaus lupaus
                           :kohdekuukausi kohdekuukausi
                           :kohdevuosi kohdevuosi
                           :kuukauden-vastaus kuukauden-vastaus
                           :kuukauden-vastaus-atom kuukauden-vastaus-atom
                           :disabled? disabled?
                           :ladataan? ladataan?}]
          ;; Useita ketjutettuja monivalintoja
          (let [edeltavat-arvot (lupaus-domain/etsi-edeltavat-monivalinnan-valitut-arvot valittu-arvo vaihtoehdot)
                lahetetty-askel (:vaihtoehto-askel lahetetty-vastaus)]
            (map (fn [[valinta-askel vaihtoehdot]]
                   (let [otsikko (:ryhma-otsikko (first vaihtoehdot))
                         edellinen-arvo (some #(when (= (:vaihtoehto-askel %) valinta-askel) %) edeltavat-arvot)
                         edellinen-askel (:vaihtoehto-askel edellinen-arvo)
                         edellinen-valittu-id (:id edellinen-arvo)
                         monivalinta-ketju-atom (atom
                                                  (cond
                                                    (and lahetetty-vastaus-id (= valinta-askel lahetetty-askel)) lahetetty-vastaus-id
                                                    (= valinta-askel edellinen-askel) edellinen-valittu-id
                                                    :else (:lupaus-vaihtoehto-id kuukauden-vastaus)))]
                     (when (some #{valinta-askel} naytettavat-vaiheet)
                       ^{:key (str "monivalinta-" (hash valinta-askel))}
                       [monivalinta e! {:vaihtoehdot vaihtoehdot
                                        :valinta-askel valinta-askel
                                        :lupaus lupaus
                                        :kohdekuukausi kohdekuukausi
                                        :kohdevuosi kohdevuosi
                                        :kuukauden-vastaus kuukauden-vastaus
                                        :kuukauden-vastaus-atom monivalinta-ketju-atom
                                        :otsikko otsikko
                                        :disabled? disabled?
                                        :ladataan? ladataan?}])))
              vaihdoehdot-vaiheet)))
        [sulje-nappi e! {:luokka "pull-right"}]])]))


(defn vastauslomake [e! app]
  (komp/luo
    ;; Sivupaneeli suljetaan, kun klikataan minne tahansa paitsi sivupaneeliin, kuukauden valintaan
    ;; tai varmistusmodaaliin.
    (komp/klikattu-luokan-ulkopuolelle
      {:luokat #{"ei-sulje-sivupaneelia"}
       :ulkopuolella-fn #(e! (lupaus-tiedot/->SuljeLupausvastaus))})
    (fn [e! app]
      [:div.overlay-oikealla.ei-sulje-sivupaneelia {:style {:width "632px"}
                                                    :id "lupaukset-sivupaneeli"}
       [:div.sivupalkki-sisalto {:class (lupaus-css-luokka app)}
        [otsikko e! app]
        [sisalto e! (:vastaus-lomake app)]
        [kommentit e! (:kommentit app) (:vastaus-lomake app)]]
       [vastaukset e! app (lupaus-css-luokka app)]])))