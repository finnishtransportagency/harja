(ns harja.views.urakka.lupaukset.vastauslomake
  (:require [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
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

(defn- kuukausittaiset-vastaukset [e! kohdekuukausi vastaukset odottaa-kirjausta?]
  (let [vastaus-olemassa? (some #(= kohdekuukausi (:kuukausi %)) vastaukset)]
    [:div.pallo-ja-kk
     (cond
       vastaus-olemassa? [:div "V"]
       (and (not vastaus-olemassa?) odottaa-kirjausta?) [:div "?"]
       (not odottaa-kirjausta?) [:div "-"]
       :else [:div.circle-8])
     [:div.kk-nimi (pvm/kuukauden-lyhyt-nimi kohdekuukausi)]]))

(defn- kuukausittaiset-pisteet [e! kohdekuukausi vastaukset]
  (let [_ (js/console.log "kuukausittaiset-pisteet :: vastaukset" (pr-str vastaukset))
        pisteet (first (keep (fn [vastaus]
                               (when (= kohdekuukausi (:kuukausi vastaus))
                                 (:pisteet vastaus)))
                             vastaukset))]
    [:div.pallo-ja-kk
     (cond
       pisteet [:div pisteet]
       :else [:div "--"])
     [:div.kk-nimi (pvm/kuukauden-lyhyt-nimi kohdekuukausi)]]))

(defn- otsikko [e! vastaus]
  (do
    (js/console.log "otsikko :: vastaus" (pr-str vastaus))
    [:div
     [:div.row
      (for [kk (concat (range 10 13) (range 1 10))]
        (if (= "yksittainen" (:lupaustyyppi vastaus))
          ^{:key (str "kk-vastaukset-" kk)}
          [kuukausittaiset-vastaukset e! kk (:vastaukset vastaus)
           ;; Odottaa kirjausta
           (some #(= kk %) (:kirjaus-kkt vastaus))]

          ^{:key (str "kk-tilanne-" kk)}
          [kuukausittaiset-pisteet e! kk (:vastaukset vastaus)]))]]))

(defn- sisalto [e! vastaus]
  [:div

   [:hr]
   [:div.row
    [:h2 "Ota kantaan lupauksiin"]
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
  (let [vaihtoehdot (:lomake-lupauksen-vaihtoehdot app)]
    [:div
        [:hr]
        [:div.row
         (for [rivi vaihtoehdot]
           ^{:key (str "vaihtoehdot-" (hash rivi))}
           [:div.row
            [:span "checkbox"]
            [:span (:vaihtoehto rivi)]
            [:span (:pisteet rivi)]]

           )]
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
