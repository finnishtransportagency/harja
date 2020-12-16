(ns harja.views.hallinta.jms-jonot
  (:require [tuck.core :as t]
            [harja.tiedot.hallinta.jms-jonot :as tila]
            [harja.pvm :as pvm]
            [cljs-time.coerce :as tc]
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

(defn jono [e! [jonon-nimi {:keys [tuottaja vastaanottaja]}]]
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
   [:span "Viestit"]])

(defn istunto [e! {jonot :jonot istunnon-tila :istunnon-tila}]
  [:div.thumbnail
   [:span (str "Istunnon tila: " istunnon-tila)]
   (map #(with-meta
           [jono e! (first %)]
           {:key (first (keys %))})
        jonot)])

(defn tila [e! {palvelin :palvelin {:keys [olioiden-tilat]} :tila paivitetty :paivitetty}]
  (let [{:keys [istunnot yhteyden-tila]} olioiden-tilat]
    [:div.tilat
     [:div.thumbnail
      [:h2 {:class (if (pvm/ennen? (tc/to-local-date-time (pvm/sekunttia-sitten 20)) (tc/to-local-date-time paivitetty))
                     "bg-success" "bg-danger")}
       (str palvelin " (Päivitetty: " paivitetty ")")]
      [:hr]
      [:h3 "JMS"]
      [:span {:class (if (= yhteyden-tila "ACTIVE")
                       "bg-success" "bg-danger")}
       (str "Yhteyden tila: " yhteyden-tila)]
      [:div.istunnot
       (map-indexed #(with-meta
                       (when istunto
                         [istunto e! %2])
                       {:key %1})
                    istunnot)]]]))

(defn virhe [jarjestelma app]
  [:p (str jarjestelma " tilaa ei haettu...")])

(defn jmsjonot* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do (e! (tila/->AloitaJMSTilanHakeminen :sonja))
                           (e! (tila/->AloitaJMSTilanHakeminen :itmf)))
                      #(do (e! (tila/->LopetaJMSTilanHakeminen :sonja))
                           (e! (tila/->LopetaJMSTilanHakeminen :itmf))))
    (fn [e! {jarjestelmien-tilat :jarjestelmien-tilat :as app}]
      [:div
       [debug/debug app]
       [:div
        (if-let [sonjan-tila (:sonja jarjestelmien-tilat)]
          (map #(with-meta
                  [tila e! %]
                  {:key (:palvelin %)})
               sonjan-tila)
          [virhe "sonja" app])]
       [:div
        (if-let [itmfn-tila (:itmf jarjestelmien-tilat)]
          (map #(with-meta
                  [tila e! %]
                  {:key (:palvelin %)})
               itmfn-tila)
          [virhe "itmf" app])]])))

(defn jms-jonot []
  [t/tuck tila/tila jmsjonot*])