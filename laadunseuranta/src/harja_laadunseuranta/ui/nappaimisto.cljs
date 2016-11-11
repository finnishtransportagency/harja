(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.nappaimisto :refer [alusta-mittaussyotto!
                                                             numeronappain-painettu!
                                                             tyhjennyspainike-painettu!
                                                             syotto-valmis!
                                                             kirjaa-kitkamittaus!]]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn- lopeta-mittaus [{:keys [nimi avain lopeta-jatkuva-havainto
                               syottoarvot mittaustyyppi] :as tiedot}]
  [:button
   {:class "nappi nappi-kielteinen nappi-peruuta"
    :on-click (fn [_]
                (.log js/console "Mittaus päättyy!")
                (lopeta-jatkuva-havainto avain))}
   (str nimi " päättyy")])

(defn- mittaustiedot [mittaukset keskiarvo]
  [:div.mittaustiedot
   [:div.mittaustieto (str "Mittauksia: " mittaukset)]
   [:div.mittaustieto (str "Keskiarvo: " (if (pos? mittaukset)
                                           keskiarvo
                                           "-"))]])

(defn- syottokentta [syotto-atom]
  [:div.nappaimiston-syottokentta
   [:span.nappaimiston-nykyinen-syotto (:nykyinen-syotto @syotto-atom)]
   [:span.nappaimiston-kursori]])

(defn- numeropainikkeet [syotto-atom kirjaa-arvo! mittaustyyppi]
  (let [syotto-kelpaa? (fn [syotto]
                         ;; Ainakin yksi desimaali
                         (> (count syotto) 2))]
    (fn []
      [:div.nappaimiston-painikekentat
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike7"
          :on-click #(numeronappain-painettu! 7 syotto-atom)} "7"]
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike8"
          :on-click #(numeronappain-painettu! 8 syotto-atom)} "8"]
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike9"
          :on-click #(numeronappain-painettu! 9 syotto-atom)} "9"]

        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike4"
          :on-click #(numeronappain-painettu! 4 syotto-atom)} "4"]
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike5"
          :on-click #(numeronappain-painettu! 5 syotto-atom)} "5"]
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike6"
          :on-click #(numeronappain-painettu! 6 syotto-atom)} "6"]

        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike1"
          :on-click #(numeronappain-painettu! 1 syotto-atom)} "1"]
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike2"
          :on-click #(numeronappain-painettu! 2 syotto-atom)} "2"]
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike3"
          :on-click #(numeronappain-painettu! 3 syotto-atom)} "3"]

        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike-delete"
          :on-click #(tyhjennyspainike-painettu! mittaustyyppi syotto-atom)} [:span.livicon-undo]]
        [:button
         {:class "nappaimiston-painike"
          :id "nappaimiston-painike0"
          :on-click #(numeronappain-painettu! 0 syotto-atom)} "0"]
        [:button
         {:disabled (not (syotto-kelpaa? (:nykyinen-syotto @syotto-atom)))
          :class "nappaimiston-painike"
          :id "nappaimiston-painike-ok"
          :on-click #(when (syotto-kelpaa? (:nykyinen-syotto @syotto-atom))
                      (kirjaa-arvo! (fmt/string->numero (:nykyinen-syotto @syotto-atom)))
                      (syotto-valmis! mittaustyyppi syotto-atom))}
         [:span.livicon-check]]])))

(defn- nappaimistokomponentti [{:keys [mittaustyyppi mittaussyotto-atom] :as tiedot}]
  (alusta-mittaussyotto! mittaustyyppi mittaussyotto-atom)
  (fn [{:keys [nimi avain lopeta-jatkuva-havainto
               mittaustyyppi mittaussyotto-atom] :as tiedot}]
    [:div.nappaimisto-container
     [:div.nappaimisto
      [:div.nappaimisto-vasen
       [lopeta-mittaus {:nimi nimi
                       :avain avain
                       :mittaustyyppi mittaustyyppi
                       :syottoarvot (:syotot @mittaussyotto-atom)
                       :lopeta-jatkuva-havainto lopeta-jatkuva-havainto}]
      [mittaustiedot
       (count (:syotot @mittaussyotto-atom))
       (fmt/n-desimaalia
         (math/avg (map fmt/string->numero (:syotot @mittaussyotto-atom)))
         2)]
      [syottokentta mittaussyotto-atom]]
      [:div.nappaimisto-oikea
       [numeropainikkeet
       mittaussyotto-atom
       (case mittaustyyppi
         :kitkamittaus kirjaa-kitkamittaus!)
       mittaustyyppi]]]]))

(defn nappaimisto [havainto]
  [nappaimistokomponentti {:mittaussyotto-atom s/mittaussyotto
                           :mittaustyyppi (get-in havainto [:mittaus :tyyppi])
                           :nimi (get-in havainto [:mittaus :nimi])
                           :avain (:avain havainto)
                           :lopeta-jatkuva-havainto s/lopeta-jatkuvan-havainnon-mittaus!}])