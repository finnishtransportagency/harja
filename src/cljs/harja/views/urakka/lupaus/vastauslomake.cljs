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
            [harja.ui.yleiset :as y]))

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
    [:p.sisalto (:sisalto vastaus)]]])

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
        ladataan? (boolean lahetetty-vastaus)
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
        kuukauden-vastaus-atom (atom (if lahetetty-vastaus
                                       (:lupaus-vaihtoehto-id lahetetty-vastaus)
                                       (:lupaus-vaihtoehto-id kuukauden-vastaus)))
        ;; Kyllä/Ei valinnassa vaihtoehdot on true/false
        vastaus-ke (if lahetetty-vastaus
                     (:vastaus lahetetty-vastaus)
                     (:vastaus kuukauden-vastaus))
        miten-kuukausi-meni-str (str "Miten " (pvm/kuukauden-lyhyt-nimi kohdekuukausi) "kuu meni?")]
    [:div.sivupalkki-footer {:class luokka}
     (if (lupaus-domain/yksittainen? lupaus)
       ;; Yksittäinen
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
       [:div.tiiviit-labelit {:style {:padding "0 32px 0 32px"}}
        [:div.lihavoitu miten-kuukausi-meni-str]
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
                                                             (str/includes? % "5") %
                                                             :else "ei valintaa")]
                                 [:div {:style {:flex-shrink 0 :flex-grow 1 :flex-direction "row" :display "flex"}}
                                  [:div {:style {:flex-grow 1 :text-align "left"}} (vaihtoehto-tekstiksi (:vaihtoehto arvo))]
                                  [:div {:style {:flex-grow 1 :text-align "right"}}
                                   (str " " (:pisteet arvo) (when (:pisteet arvo) " pistettä"))]]))
           :vaihtoehdot vaihtoehdot
           :valitse-fn (fn [valinta]
                         (let
                           [tulos (->> vaihtoehdot
                                       (filter #(= (:id %) valinta))
                                       first)]
                           (e! (lupaus-tiedot/->ValitseVaihtoehto
                                 (merge tulos {:kuukauden-vastaus-id (:id kuukauden-vastaus)})
                                 lupaus kohdekuukausi kohdevuosi))))
           :kaari-flex-row? false}
          kuukauden-vastaus-atom]]
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