(ns harja.views.hallinta.sonjajonot
  (:require [tuck.core :as t]
            [harja.tiedot.hallinta.sonjajonot :as tila]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.debug :as debug]))

(defn kasittelija-esitys [e! kasittelija tila]
  (let [onko-vastaanottaja? (= kasittelija :vastaanottaja)
        {virheet :virheet kasittelija-tila (if onko-vastaanottaja?
                                             :vastaanottajan-tila
                                             :tuottajan-tila)} tila]
    [:div
     [:div {:class (if (= kasittelija-tila "ACTIVE")
                     "bg-success" "bg-danger")}
      (str (if onko-vastaanottaja?
             "Vastaanottaja: "
             "Tuottaja: ")
           kasittelija-tila)]
     (when virheet
       [:div
        [:span "Virheet"]
        [:div.jms-virheet
         (map #(with-meta
                 [:div.virhe
                  [:div.aika (:aika %)]
                  [:div.virhe-viesti (:viesti %)]]
                 {:key (:aika %)})
              virheet)]])]))

(defn jono [e! [jonon-nimi {:keys [tuottaja vastaanottaja jonon-viestit]}]]
  (let [viestit (map-indexed #(with-meta
                                (let [{:keys [message-id timestamp]} %2]
                                  [:div.viesti-tiedot
                                   [:span "Message-id: " message-id]
                                   [:span "Timestamp: " (.toString (js/Date. timestamp))]])
                                {:key %1})
                             jonon-viestit)]
    [:div.thumbnail.jono
     [:h3 jonon-nimi]
     [:div.viestin-kasittelijat
      (list
        (when tuottaja
          (with-meta
            (when kasittelija-esitys
              [kasittelija-esitys e! :tuottaja tuottaja])
            {:key "tuottajan-tila"}))
        (when vastaanottaja
          (with-meta
            (when kasittelija-esitys
              [kasittelija-esitys e! :vastaanottaja vastaanottaja])
            {:key "vastaanottajan-tila"})))]
     [:span "Viestit"]
     [:div.jms-viestit
      (if (empty? viestit)
        "Ei viestejä jonossa..."
        viestit)]]))

(defn istunto [e! {jonot :jonot istunnon-tila :istunnon-tila}]
  [:div.thumbnail
   [:span (str "Istunnon tila: " istunnon-tila)]
   (map #(with-meta
           [jono e! (first %)]
           {:key (first (keys %))})
        jonot)])

(defn tila [e! {palvelin :palvelin {:keys [olioiden-tilat saikeiden-tilat]} :tila paivitetty :paivitetty}]
  (let [{:keys [istunnot yhteyden-tila]} olioiden-tilat]
    [:div.tilat
     [:div.thumbnail
      [:h2 (str palvelin " (Päivitetty: " paivitetty ")")]
      [:hr]
      [:h3 "Säikeet"]
      (map (fn [{:keys [nimi status]}]
             (with-meta
               [:div.saije
                [:span "Nimi: " nimi]
                [:span "Status: " status]]
               {:key nimi}))
           (reverse (sort-by :nimi saikeiden-tilat)))
      [:hr]
      [:h3 "JMS"]
      [:span (str "Yhteyden tila: " yhteyden-tila)]
      [:div.istunnot
       (map-indexed #(with-meta
                       (when istunto
                         [istunto e! %2])
                       {:key %1})
                    istunnot)]]]))

(defn virhe [app]
  [:p "Sonjan tilaa ei haettu..."])

(defn sonjajonot* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tila/->AloitaSonjaTilanHakeminen))
                      #(e! (tila/->LopetaSonjaTilanHakeminen)))
    (fn [e! {sonjan-tila :sonjan-tila :as app}]
      [:div
       [debug/debug app]
       (if sonjan-tila
         (map #(with-meta
                 [tila e! %]
                 {:key (:palvelin %)})
              sonjan-tila)
         [virhe app])])))

(defn sonjajonot []
  [t/tuck tila/tila sonjajonot*])