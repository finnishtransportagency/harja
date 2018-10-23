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
  (let [viestit (map #(with-meta
                        (identity
                          [:span %])
                        {:key %})
                     jonon-viestit)]
    [:div.thumbnail.jono
     [:h3 jonon-nimi]
     [:div.viestin-kasittelijat
      (list
        (when tuottaja
          (with-meta
            [kasittelija-esitys e! :tuottaja tuottaja]
            {:key "tuottajan-tila"}))
        (when vastaanottaja
          (with-meta
            [kasittelija-esitys e! :vastaanottaja vastaanottaja]
            {:key "vastaanottajan-tila"})))]
     [:span "Viestit"]
     [:div.jms-viestit
      (if (empty? viestit)
        "Ei viestejä jonossa..."
        viestit)]]))

(defn istunto [e! {sonja-jono :jono istunnon-tila :istunnon-tila}]
  [:div.thumbnail
   [:span (str "Istunnon tila: " istunnon-tila)]
   (map #(with-meta
           [jono e! %]
           {:key (key %)})
        sonja-jono)])

(defn yhteys [e! {palvelin :palvelin {:keys [istunnot yhteyden-tila]} :tila}]
  [:div.yhteys
   [:div.thumbnail
    [:h2 palvelin]
    [:span (str "Yhteyden tila: " yhteyden-tila)]
    [:hr]
    [:div.istunnot
     (map-indexed #(with-meta
                     [istunto e! %2]
                     {:key %1})
                  istunnot)]]])

(defn virhe [app]
  [:p app])

(defn sonjajonot* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tila/->HaeSonjanTila))
                      #())
    (fn [e! {sonjan-tila :sonjan-tila :as app}]
      [:div
       [debug/debug app]
       (if sonjan-tila
         (map #(with-meta
                 [yhteys e! %]
                 {:key (:palvelin %)})
              sonjan-tila)
         [virhe app])])))

(defn sonjajonot []
  [t/tuck tila/tila sonjajonot*])