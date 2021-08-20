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
            [harja.views.urakka.lupaukset.kuukausipaatos-tilat :as kuukausitilat]))

;;TODO: tämä on jo bäkkärissä, refaktoroi
(defn- numero->kirjain [numero]
  (case numero
    1 "A"
    2 "B"
    3 "C"
    4 "D"
    5 "E"
    nil))

(defn- kuukausivastauksen-status [e! kohdekuukausi vastaus app]
  (let [vastaukset (:vastaukset vastaus)
        kohdevuosi (kuukausitilat/paattele-kohdevuosi kohdekuukausi vastaukset app)]
    [kuukausitilat/kuukausi-wrapper e! kohdekuukausi kohdevuosi vastaus (get-in app [:vastaus-lomake :vastauskuukausi]) false]))

(defn- otsikko [e! app]
  (let [vastaus (:vastaus-lomake app)]
    [:div
     [:div.row
      (for [kk (concat (range 10 13) (range 1 10))
            :let [;; Jokaiselle kuukaudelle ei voi antaa vastausta. Päätellään tässä, että voiko valitulle kuukaudelle antaa vastauksen
                  kk-voi-vastata? (or (some #(= kk %) (:kirjaus-kkt vastaus))
                                            (= kk (:paatos-kk vastaus)))]]
        ^{:key (str "kk-vastaukset-" kk)}
        [:div (merge (when kk-voi-vastata?
                       {:on-click (fn [e]
                                    (do
                                      (.preventDefault e)
                                      (e! (lupaus-tiedot/->ValitseVastausKuukausi kk))))})
                     {})
         [kuukausivastauksen-status e! kk vastaus app]])]]))

(defn- sisalto [e! vastaus]
  [:div
   [:hr]
   [:div.row
    [:h2 "Ota kantaa lupauksiin"]
    [:span {:style {:font-weight "600"}}
     (str (numero->kirjain (:lupausryhma-jarjestys vastaus)) ". " (:lupausryhma-otsikko vastaus))]
    [:div.flex-row
     [:div
      [:h3 (str "Lupaus " (:lupaus-jarjestys vastaus))]]
     [:div
      [:h3 {:style {:float "right"}} (if (= "yksittainen" (:lupaustyyppi vastaus))
                                       (:pisteet vastaus)
                                       (str "Pisteet 0 - " (:kyselypisteet vastaus)))]]]
    [:p (:sisalto vastaus)]]])

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
                          (e! (lupaus-tiedot/->PoistaKommentti id)))})
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
                         (e! (lupaus-tiedot/->LisaaKommentti @kommentti))
                         (reset! kommentti nil)
                         (reset! lisaa-kommentti? false))
                      {:disabled (str/blank? @kommentti)}]
                     [napit/peruuta #(reset! lisaa-kommentti? false)]]]
                   [yleiset/linkki
                    "Lisää kommentti"
                    #(reset! lisaa-kommentti? true)
                    {:ikoni (ikonit/livicon-kommentti)
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

(defn- footer [e! app]
  (let [lupaus (:vastaus-lomake app)
        vaihtoehdot (:lomake-lupauksen-vaihtoehdot app) ;; Monivalinnassa on vaihtoehtoja
        kohdekuukausi (get-in app [:vastaus-lomake :vastauskuukausi])
        kohdevuosi (get-in app [:vastaus-lomake :vastausvuosi])
        kuukauden-vastaus (first (filter (fn [vastaus]
                                           (when (= (:kuukausi vastaus) kohdekuukausi)
                                             vastaus))
                                         (get-in app [:vastaus-lomake :vastaukset])))
        kuukauden-vastaus-atom (atom (:lupaus-vaihtoehto-id kuukauden-vastaus))
        vastaus-ke (:vastaus kuukauden-vastaus) ;; Kyllä/Ei valinnassa vaihtoehdot on true/false
        ]
    [:div
     [:hr]
     [:div.row
      [:div.col-xs-4 {:style {:padding-right "32px"}} (str "Miten " (pvm/kuukauden-lyhyt-nimi kohdekuukausi) "kuu meni?")]
      (when-not (= "yksittainen" (:lupaustyyppi lupaus))
        [:div.col-xs-7 {:style {:height "200px"}}
         [kentat/tee-kentta {:tyyppi :radio-group
                             :nimi :id
                             :nayta-rivina? false
                             :vayla-tyyli? true
                             :rivi-solun-tyyli {:padding-right "3rem"}
                             :vaihtoehto-arvo :id
                             :vaihtoehto-nayta (fn [arvo] (str (:vaihtoehto arvo) " " (:pisteet arvo) " pistettä")) ;; HOX tämän pitäisi olla eri näköinen, ehkä diveillä?
                             :vaihtoehdot vaihtoehdot
                             :valitse-fn (fn [valinta]
                                           (let
                                             [tulos (->> vaihtoehdot
                                                         (filter #(= (:id %) valinta))
                                                         first)]
                                             (e! (lupaus-tiedot/->ValitseVaihtoehto
                                                   (merge tulos {:kuukauden-vastaus-id (:id kuukauden-vastaus)})
                                                   lupaus kohdekuukausi kohdevuosi))))}
          kuukauden-vastaus-atom]])
      (when (= "yksittainen" (:lupaustyyppi lupaus))
        [:div.col-xs-7 {:style {:display "flex"}}
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
                                          "odottaa"))}
           [ikonit/harja-icon-status-help]]
          [:div.ke-vastaus {:class (str (if (false? vastaus-ke)
                                          "ei-valittu"
                                          "ei-valitsematta"))
                            :on-click #(e! (lupaus-tiedot/->ValitseKE {:vastaus false
                                                                       :kuukauden-vastaus-id (:id kuukauden-vastaus)}
                                                                      lupaus kohdekuukausi kohdevuosi))}
           [ikonit/harja-icon-status-denied]]]])
      [:div.col-xs-1 {:style {:float "right"}}
       [napit/yleinen-toissijainen
        "Sulje"
        #(e! (lupaus-tiedot/->SuljeLupausvastaus %))
        {:paksu? true}]]]]))

(defn vastauslomake [e! app]
  (komp/luo
    (komp/sisaan
      #(e! (lupaus-tiedot/->HaeLupauksenVastausvaihtoehdot (:vastaus-lomake app))))
    (fn [e! app]
      [:div.overlay-oikealla {:style {:width "632px" :overflow "auto" :padding "32px"}}

       [otsikko e! app]
       [sisalto e! (:vastaus-lomake app)]
       [kommentit e! (:kommentit app)]
       [footer e! app]])))
