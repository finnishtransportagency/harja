(ns harja.views.urakka.lupaukset.vastauslomake
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.lupaukset :as lupaus-tiedot]))

;;TODO: tämä on jo bäkkärissä, refaktoroi
(defn- numero->kirjain [numero]
  (case numero
    1 "A"
    2 "B"
    3 "C"
    4 "D"
    5 "E"
    nil))

(defn- kuukausittaiset-ke-vastaukset [e! kohdekuukausi paatoskuukausi vastaukset odottaa-kirjausta?]
  (let [vastaus-olemassa? (some #(= kohdekuukausi (:kuukausi %)) vastaukset)]
    [:div.pallo-ja-kk {:class (when (= kohdekuukausi paatoskuukausi) "paatoskuukausi")
                       :on-click (fn [e]
                                   (do
                                     (.preventDefault e)
                                     (e! (lupaus-tiedot/->ValitseVastausKuukausi kohdekuukausi))))}
     (cond
       vastaus-olemassa? [:div "V"]
       (and (not vastaus-olemassa?) odottaa-kirjausta?) [:div "?"]
       (not odottaa-kirjausta?) [:div "-"]
       :else [:div.circle-8])
     [:div.kk-nimi (pvm/kuukauden-lyhyt-nimi kohdekuukausi)]]))

(defn- kuukausittaiset-kysely-vastaukset [e! kohdekuukausi paatoskuukausi vastaukset]
  (let [pisteet (first (keep (fn [vastaus]
                               (when (= kohdekuukausi (:kuukausi vastaus))
                                 (:pisteet vastaus)))
                             vastaukset))]
    [:div.pallo-ja-kk {:class (when (= kohdekuukausi paatoskuukausi) "paatoskuukausi")
                       :on-click (fn [e]
                                   (do
                                     (.preventDefault e)
                                     (e! (lupaus-tiedot/->ValitseVastausKuukausi kohdekuukausi))))}
     (cond
       pisteet [:div pisteet]
       :else [:div "--"])
     [:div.kk-nimi (pvm/kuukauden-lyhyt-nimi kohdekuukausi)]]))

(defn- otsikko [e! vastaus]
  [:div
   [:div.row
    (for [kk (concat (range 10 13) (range 1 10))]
      (if (= "yksittainen" (:lupaustyyppi vastaus))
        ^{:key (str "kk-vastaukset-" kk)}
        [kuukausittaiset-ke-vastaukset e! kk (:paatos-kk vastaus) (:vastaukset vastaus)
         ;; Odottaa kirjausta
         (some #(= kk %) (:kirjaus-kkt vastaus))]

        ^{:key (str "kk-tilanne-" kk)}
        [kuukausittaiset-kysely-vastaukset e! kk (:paatos-kk vastaus) (:vastaukset vastaus)]))]])

(defn- sisalto [e! vastaus]
  [:div

   [:hr]
   [:div.row
    [:h2 "Ota kantaa lupauksiin"]
    [:span {:style {:font-weight "600"}}
     (str (numero->kirjain (:lupausryhma-jarjestys vastaus)) ". " (:lupausryhma-otsikko vastaus))]
    [:div.row
     [:div.col-xs-6
      [:h3 (str "Lupaus " (:lupaus-jarjestys vastaus))]]
     [:div.col-xs-6
      [:h3 {:style {:float "right"}} (str "Pisteet 0 - " (:kyselypisteet vastaus))]]]
    [:p (:sisalto vastaus)]]
   ])

(defn- footer [e! app]
  (let [lupaus (:vastaus-lomake app)
        vaihtoehdot (:lomake-lupauksen-vaihtoehdot app)
        kohdekuukausi (get-in app [:vastaus-lomake :vastauskuukausi])
        kohdevuosi (get-in app [:vastaus-lomake :vastausvuosi])
        kuukauden-vastaus (first (filter (fn [vastaus]
                                           (when (= (:kuukausi vastaus) kohdekuukausi)
                                             vastaus))
                                         (get-in app [:vastaus-lomake :vastaukset])))
        kuukauden-vastaus-atom (atom (:lupaus-vaihtoehto-id kuukauden-vastaus))]
    [:div
     [:hr]
     [:div.row
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
                                          (e! (lupaus-tiedot/->ValitseVaihtoehto tulos lupaus kohdekuukausi kohdevuosi))))}
       kuukauden-vastaus-atom]]
     [:a {:href "#"
          :on-click (fn [e]
                      (do
                        (.preventDefault e)
                        (e! (lupaus-tiedot/->SuljeLupausvastaus e))))}
      "Sulje "]]))

(defn vastauslomake [e! app]
  (komp/luo
    (komp/sisaan
      #(e! (lupaus-tiedot/->HaeLupauksenVastausvaihtoehdot (:vastaus-lomake app))))
    (fn [e! app]
      [:div.overlay-oikealla {:style {:width "632px" :overflow "auto" :padding "32px"}}

       [otsikko e! (:vastaus-lomake app)]
       [sisalto e! (:vastaus-lomake app)]
       [footer e! app]])))
