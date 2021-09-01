(ns harja.views.urakka.lupaukset.vastauslomake
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.lupaukset :as lupaus-tiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.views.urakka.lupaukset.kuukausipaatos-tilat :as kuukausitilat]
            [harja.domain.lupaukset :as ld]))

(defn- kuukausivastauksen-status [e! lupaus-kuukausi lupaus app]
  (let [listauksessa? false
        valittu? (= (:kuukausi lupaus-kuukausi) (get-in app [:vastaus-lomake :vastauskuukausi]))]
    [kuukausitilat/kuukausi-wrapper2 e! lupaus lupaus-kuukausi listauksessa? valittu?]))

(defn- otsikko [e! app]
  (let [lupaus (:vastaus-lomake app)]
    [:div
     [:div.row
      (doall
        (for [lupaus-kuukausi (:lupaus-kuukaudet lupaus)]
          ^{:key (str "kk-vastaukset-" (hash lupaus-kuukausi))}
          [:div (when (ld/kayttaja-saa-vastata? @istunto/kayttaja lupaus-kuukausi)
                  {:on-click (fn [e]
                               (do
                                 (.preventDefault e)
                                 (e! (lupaus-tiedot/->ValitseVastausKuukausi (:kuukausi lupaus-kuukausi)))))})
           [kuukausivastauksen-status e! lupaus-kuukausi lupaus app]]))]]))

(defn- sisalto [e! vastaus]
  [:div {:id "vastauslomake-sisalto"}
   [:hr]
   [:div.row
    [:h2 "Ota kantaa lupauksiin"]
    [:span.lupausryhma-otsikko
     (str (ld/numero->kirjain (:lupausryhma-jarjestys vastaus)) ". " (:lupausryhma-otsikko vastaus))]
    [:div.flex-row
     [:div
      [:h3 (str "Lupaus " (:lupaus-jarjestys vastaus))]]
     [:div
      [:h3 {:style {:float "right"}} (if (= "yksittainen" (:lupaustyyppi vastaus))
                                       (:pisteet vastaus)
                                       (str "Pisteet 0 - " (:kyselypisteet vastaus)))]]]
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
       #(do
          (reset! lupaus-tiedot/saa-sulkea? false)
          (varmista-kayttajalta/varmista-kayttajalta
              {:otsikko "Poista kommentti"
               :sisalto "Haluatko poistaa kommentin?"
               :hyvaksy "Poista"
               :peruuta-txt "Peruuta"
               :toiminto-fn (fn []
                              (e! (lupaus-tiedot/->PoistaKommentti id)))}))
       {:ikoni (ikonit/harja-icon-action-delete)
        :luokka "btn-xs"}])]])

(defn- lisaa-kommentti-kentta [e! lisays-kaynnissa?]
  [:div.lisaa-kommentti
   (if lisays-kaynnissa?
     "Tallennetaan kommenttia..."
     (r/with-let [lisaa-kommentti? (r/atom false)
                  kommentti (r/atom nil)]
                 (if @lisaa-kommentti?
                   [:<>
                    [kentat/tee-kentta {:tyyppi :text
                                        :nimi :kommentti
                                        :placeholder "Lisää kommentti"
                                        :pituus-max 4000}
                     kommentti]
                    [:div.flex-row.margin-top-16
                     [napit/tallenna
                      "Tallenna"
                      #(do
                         (reset! lupaus-tiedot/saa-sulkea? false)
                         (e! (lupaus-tiedot/->LisaaKommentti @kommentti))
                         (reset! kommentti nil)
                         (reset! lisaa-kommentti? false)
                         (yleiset/fn-viiveella (fn [] (reset! lupaus-tiedot/saa-sulkea? true))))
                      {:disabled (str/blank? @kommentti)}]
                     [napit/peruuta #(reset! lisaa-kommentti? false)]]]
                   [yleiset/linkki
                    "Lisää kommentti"
                    #(do
                       (reset! lupaus-tiedot/saa-sulkea? false)
                       (reset! lisaa-kommentti? true)
                       (yleiset/fn-viiveella (fn [] (reset! lupaus-tiedot/saa-sulkea? true))))
                    {:id (str "lisaa-kommentti")
                     :ikoni (ikonit/livicon-kommentti)
                     :luokka "napiton-nappi btn-xs semibold"}])))])

(defn- kommentit [e! {:keys [haku-kaynnissa? lisays-kaynnissa? vastaus] :as kommentit}]
  [:div.lupaus-kommentit
   (if haku-kaynnissa?
     "Ladataan kommentteja..."
     [:<>
      (when (seq vastaus)
        [:<>
         [:div.body-text.semibold "Kommentit"]
         (doall
           (map-indexed
             (fn [i kommentti]
               ^{:key i}
               [kommentti-rivi e! kommentti])
             vastaus))])
      [lisaa-kommentti-kentta e! lisays-kaynnissa?]])])

(defn- sulje-nappi [e!]
  [:div {:style {:margin-left "auto"
                 :order 2}}
   [napit/yleinen-toissijainen
    "Sulje"
    #(e! (lupaus-tiedot/->SuljeLupausvastaus %))
    {:paksu? true}]])

(defn- yksittainen-lupaus? [app]
  (= "yksittainen" (get-in app [:vastaus-lomake :lupaustyyppi])))

(defn- lupaus-css-luokka [app]
  (if (yksittainen-lupaus? app) "kylla-ei" "monivalinta"))

(defn- vastaukset [e! app luokka]
  (let [kohdekuukausi (get-in app [:vastaus-lomake :vastauskuukausi])
        kohdevuosi (get-in app [:vastaus-lomake :vastausvuosi])
        lupaus (:vastaus-lomake app)
        vaihtoehdot (:lomake-lupauksen-vaihtoehdot app) ;; Monivalinnassa on vaihtoehtoja
        kuukauden-vastaus (first (filter (fn [vastaus]
                                           (when (= (:kuukausi vastaus) kohdekuukausi)
                                             vastaus))
                                         (get-in app [:vastaus-lomake :vastaukset])))

        ;; Lisätään vaihtoehtoinin myös "nil" vaihtoehto, jotta vahinkovalinnan voi poistaa - vain jos vastaus on jo annettu
        tyhja-vaihtoehto-templaatti (first vaihtoehdot)
        vaihtoehdot (merge vaihtoehdot
                           (-> tyhja-vaihtoehto-templaatti
                               (assoc :id nil)
                               (assoc :vaihtoehto "ei valintaa")
                               (assoc :pisteet nil)))
        kuukauden-vastaus-atom (atom (:lupaus-vaihtoehto-id kuukauden-vastaus))
        vastaus-ke (:vastaus kuukauden-vastaus) ;; Kyllä/Ei valinnassa vaihtoehdot on true/false
        voi-vastata? false
        ]
    [:div.sivupalkki-footer {:class luokka}
     [:div
      [:div.row {:style {:background-color "white"}}
       [:div.col-xs-4 {:style (merge {:padding "8px 32px 0 0" :font-weight 700}
                                     (when-not voi-vastata?
                                       {:opacity "0.3"}))}
        (str "Miten " (pvm/kuukauden-lyhyt-nimi kohdekuukausi) "kuu meni?")]
       (when (yksittainen-lupaus? app)
         [:div.col-xs-8 {:style (merge
                                  {:display "flex"}
                                  (when-not voi-vastata?
                                    {:style {:position "relative"}}))}
          (when-not voi-vastata?
            [:div {:style {:opacity "0.3"
                           :position "absolute"
                           :width "200px"
                           :height "40px"
                           :background-color "white"
                           :z-index 10}}])
          [:div.ke-valinta
           [:div.ke-vastaus {:class (str (if vastaus-ke
                                           "kylla-valittu"
                                           "kylla-valitsematta"))
                             :on-click #(e! (lupaus-tiedot/->ValitseKE {:vastaus true
                                                                        :kuukauden-vastaus-id (:id kuukauden-vastaus)}
                                                                       lupaus kohdekuukausi kohdevuosi))}
            [ikonit/harja-icon-status-completed]]
           [:div.ke-vastaus {:class (str (if-not (nil? vastaus-ke)
                                           "odottaa-valitsematta"
                                           "odottaa"))
                             :on-click #(e! (lupaus-tiedot/->ValitseKE {:vastaus nil
                                                                        :kuukauden-vastaus-id (:id kuukauden-vastaus)}
                                                                       lupaus kohdekuukausi kohdevuosi))}
            [ikonit/harja-icon-status-help]]
           [:div.ke-vastaus {:class (str (if (false? vastaus-ke)
                                           "ei-valittu"
                                           "ei-valitsematta"))
                             :on-click #(e! (lupaus-tiedot/->ValitseKE {:vastaus false
                                                                        :kuukauden-vastaus-id (:id kuukauden-vastaus)}
                                                                       lupaus kohdekuukausi kohdevuosi))}
            [ikonit/harja-icon-status-denied]]]
          [sulje-nappi e!]])]]
     (when-not (= "yksittainen" (:lupaustyyppi lupaus))
       [:div {:style {:padding "0 32px 0 32px"}}
        [:div.flex-row {:style (merge {:justify-content "flex-start"
                                       :align-items "flex-end"}
                                      (when-not voi-vastata?
                                        {:opacity "0.3"}))}
         [kentat/tee-kentta {:tyyppi :radio-group
                             :nimi :id
                             :nayta-rivina? false
                             :vayla-tyyli? true
                             ;:rivi-solun-tyyli {:padding-right "3rem"}
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
        [:div.row {:style {:display "flex"}}
         [sulje-nappi e!]]])]))


(defn vastauslomake [e! app]
  (komp/luo
    (komp/sisaan
      #(do
         ;; Alustavasti sulkeminen kiinni
         (reset! lupaus-tiedot/saa-sulkea? false)
         (e! (lupaus-tiedot/->HaeLupauksenVastausvaihtoehdot (:vastaus-lomake app)))
         (yleiset/fn-viiveella (fn []
                                 ;; Mahdollistetaan sulkeminen vähän viiveellä
                                 (reset! lupaus-tiedot/saa-sulkea? true)))))
    (komp/klikattu-ulkopuolelle (fn [e]
                                  (when (and (:vastaus-lomake app) @lupaus-tiedot/saa-sulkea?) ;; Jostain syystä kommentin lisääminen tulkitaan ulkopuolella olevaksi toiminnoksi
                                    (e! (lupaus-tiedot/->SuljeLupausvastaus e))))
                                {:tarkista-komponentti? true})
    (fn [e! app]
      [:<>
       [:div.overlay-oikealla {:style {:width "632px"}}
        [:div.sivupalkki-sisalto {:class (lupaus-css-luokka app)}
         [otsikko e! app]
         [sisalto e! (:vastaus-lomake app)]
         [kommentit e! (:kommentit app)]]
        [vastaukset e! app (lupaus-css-luokka app)]]])))