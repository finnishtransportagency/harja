(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.nappaimisto
             :refer [numeronappain-painettu!
                     tyhjennyspainike-painettu! syotto-valmis!
                     kirjaa-kitkamittaus! kirjaa-lumisuus!
                     kirjaa-talvihoito-tasaisuus!
                     syoton-rajat syotto-validi?
                     soratienappaimiston-numeronappain-painettu!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja-laadunseuranta.ui.dom :as dom])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(def +lyhenna-teksteja-leveydessa+ 530)

(defn- lopeta-mittaus [{:keys [nimi avain lopeta-jatkuva-havainto] :as tiedot}]
  [nappi (str nimi " päättyy") {:on-click (fn [_]
                                            (.log js/console "Mittaus päättyy!")
                                            (lopeta-jatkuva-havainto avain))
                                :luokat-str "nappi-kielteinen nappi-peruuta"}])

(defn- mittaustiedot-keskiarvo [{:keys [mittaukset keskiarvo
                                        syotetty-arvo yksikko rajat]}]
  (let [arvo-liian-suuri? (if syotetty-arvo
                            (> syotetty-arvo (second rajat))
                            false)]
    [:div.mittaustiedot
     (if arvo-liian-suuri?
       [:div.nappaimiston-syottovaroitus
        [:span (str "Liian suuri!")
         [:br] "Max: " (second rajat) yksikko]]
       [:div
        [:div.mittaustieto (str "Mittauksia: " mittaukset)]
        [:div.mittaustieto (str "Keskiarvo: " (if (pos? mittaukset)
                                                keskiarvo
                                                "-"))]])]))

(defn- mittaustiedot [{:keys [mittaustyyppi mittaukset keskiarvo
                              syotetty-arvo yksikko rajat]}]
  (case mittaustyyppi
    :kitkamittaus [mittaustiedot-keskiarvo {:mittaukset mittaukset
                                            :keskiarvo keskiarvo
                                            :syotetty-arvo syotetty-arvo
                                            :yksikko yksikko
                                            :rajat rajat}]
    :lumisuus [mittaustiedot-keskiarvo {:mittaukset mittaukset
                                        :keskiarvo keskiarvo
                                        :syotetty-arvo syotetty-arvo
                                        :yksikko yksikko
                                        :rajat rajat}]
    :talvihoito-tasaisuus [mittaustiedot-keskiarvo {:mittaukset mittaukset
                                                    :keskiarvo keskiarvo
                                                    :syotetty-arvo syotetty-arvo
                                                    :yksikko yksikko
                                                    :rajat rajat}]
    [:div]))

(defn- syottokentta [syotto-atom yksikko]
  [:div.nappaimiston-syottokentta
   [:span.nappaimiston-nykyinen-syotto (:nykyinen-syotto @syotto-atom)]
   [:span.nappaimiston-kursori]
   (when yksikko
     [:span.nappaimiston-syottoyksikko yksikko])])

(defn- soratienappaimisto [{:keys [syotto-atom numeronappain-painettu] :as tiedot}]
  (let [painike-luokka (fn [syotto painike-tyyppi arvo]
                         (str "nappaimiston-painike "
                              (when (= (painike-tyyppi syotto) arvo)
                                "nappaimiston-painike-painettu")))]
    ;; NOTE Oikeaoppisesti nappien kuuluisi olla <button> elementtejä, mutta jostain
    ;; syystä iPadin safari piirtää tällöin vain kaksi nappia samalle riville.
    (fn []
      [:div.soratienappaimisto
       [:div.soratienappaimiston-sarake.soratienappaimiston-tasaisuus
        [:div.soratienappaimiston-sarake-otsikko (if (< @dom/leveys +lyhenna-teksteja-leveydessa+)
                                                   "Tas."
                                                   "Tasaisuus")]
        [:div
         {:class (painike-luokka @syotto-atom :tasaisuus 5)
          :id "soratienappaimiston-painike-tasaisuus-5"
          :on-click #(numeronappain-painettu 5 :tasaisuus syotto-atom)} "5"]
        [:div
         {:class (painike-luokka @syotto-atom :tasaisuus 4)
          :id "soratienappaimiston-painike-tasaisuus-4"
          :on-click #(numeronappain-painettu 4 :tasaisuus syotto-atom)} "4"]
        [:div
         {:class (painike-luokka @syotto-atom :tasaisuus 3)
          :id "soratienappaimiston-painike-tasaisuus-3"
          :on-click #(numeronappain-painettu 3 :tasaisuus syotto-atom)} "3"]
        [:div
         {:class (painike-luokka @syotto-atom :tasaisuus 2)
          :id "soratienappaimiston-painike-tasaisuus-2"
          :on-click #(numeronappain-painettu 2 :tasaisuus syotto-atom)} "2"]
        [:div
         {:class (painike-luokka @syotto-atom :tasaisuus 1)
          :id "soratienappaimiston-painike-tasaisuus-1"
          :on-click #(numeronappain-painettu 1 :tasaisuus syotto-atom)} "1"]]

       [:div.soratienappaimiston-sarake.soratienappaimiston-kiinteys
        [:div.soratienappaimiston-sarake-otsikko (if (< @dom/leveys +lyhenna-teksteja-leveydessa+)
                                                   "Kiint."
                                                   "Kiinteys")]
        [:div
         {:class (painike-luokka @syotto-atom :kiinteys 5)
          :id "soratienappaimiston-painike-kiinteys-5"
          :on-click #(numeronappain-painettu 5 :kiinteys syotto-atom)} "5"]
        [:div
         {:class (painike-luokka @syotto-atom :kiinteys 4)
          :id "soratienappaimiston-painike-kiinteys-4"
          :on-click #(numeronappain-painettu 4 :kiinteys syotto-atom)} "4"]
        [:div
         {:class (painike-luokka @syotto-atom :kiinteys 3)
          :id "soratienappaimiston-painike-kiinteys-3"
          :on-click #(numeronappain-painettu 3 :kiinteys syotto-atom)} "3"]
        [:div
         {:class (painike-luokka @syotto-atom :kiinteys 2)
          :id "soratienappaimiston-painike-kiinteys-2"
          :on-click #(numeronappain-painettu 2 :kiinteys syotto-atom)} "2"]
        [:div
         {:class (painike-luokka @syotto-atom :kiinteys 1)
          :id "soratienappaimiston-painike-kiinteys-1"
          :on-click #(numeronappain-painettu 1 :kiinteys syotto-atom)} "1"]]

       [:div.soratienappaimiston-sarake.soratienappaimiston-polyavyys
        [:div.soratienappaimiston-sarake-otsikko (if (< @dom/leveys +lyhenna-teksteja-leveydessa+)
                                                   "Pöl."
                                                   "Pölyävyys")]
        [:div
         {:class (painike-luokka @syotto-atom :polyavyys 5)
          :id "soratienappaimiston-painike-polyavyys-5"
          :on-click #(numeronappain-painettu 5 :polyavyys syotto-atom)} "5"]
        [:div
         {:class (painike-luokka @syotto-atom :polyavyys 4)
          :id "soratienappaimiston-painike-polyavyys-4"
          :on-click #(numeronappain-painettu 4 :polyavyys syotto-atom)} "4"]
        [:div
         {:class (painike-luokka @syotto-atom :polyavyys 3)
          :id "soratienappaimiston-painike-polyavyys-3"
          :on-click #(numeronappain-painettu 3 :polyavyys syotto-atom)} "3"]
        [:div
         {:class (painike-luokka @syotto-atom :polyavyys 2)
          :id "soratienappaimiston-painike-polyavyys-2"
          :on-click #(numeronappain-painettu 2 :polyavyys syotto-atom)} "2"]
        [:div
         {:class (painike-luokka @syotto-atom :polyavyys 1)
          :id "soratienappaimiston-painike-polyavyys-1"
          :on-click #(numeronappain-painettu 1 :polyavyys syotto-atom)} "1"]]])))

(defn- numeronappaimisto [{:keys [syotto-atom kirjaa-arvo mittaustyyppi
                                  numeronappain-painettu syotto-validi? syotto-valmis]
                           :as tiedot}]
  ;; NOTE Oikeaoppisesti nappien kuuluisi olla <button> elementtejä, mutta jostain
  ;; syystä iPadin safari piirtää tällöin vain kaksi nappia samalle riville.
  [:div.numeronappaimisto
   [:div.nappaimiston-painikekentat
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike7"
      :on-click #(numeronappain-painettu 7 mittaustyyppi syotto-atom)} "7"]
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike8"
      :on-click #(numeronappain-painettu 8 mittaustyyppi syotto-atom)} "8"]
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike9"
      :on-click #(numeronappain-painettu 9 mittaustyyppi syotto-atom)} "9"]

    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike4"
      :on-click #(numeronappain-painettu 4 mittaustyyppi syotto-atom)} "4"]
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike5"
      :on-click #(numeronappain-painettu 5 mittaustyyppi syotto-atom)} "5"]
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike6"
      :on-click #(numeronappain-painettu 6 mittaustyyppi syotto-atom)} "6"]

    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike1"
      :on-click #(numeronappain-painettu 1 mittaustyyppi syotto-atom)} "1"]
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike2"
      :on-click #(numeronappain-painettu 2 mittaustyyppi syotto-atom)} "2"]
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike3"
      :on-click #(numeronappain-painettu 3 mittaustyyppi syotto-atom)} "3"]

    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike-delete"
      :on-click #(tyhjennyspainike-painettu! mittaustyyppi syotto-atom)} [:span.livicon-undo]]
    [:div
     {:class "nappaimiston-painike"
      :id "nappaimiston-painike0"
      :on-click #(numeronappain-painettu! 0 mittaustyyppi syotto-atom)} "0"]
    [:div
     {:disabled (not (syotto-validi? mittaustyyppi (:nykyinen-syotto @syotto-atom)))
      :class "nappaimiston-painike"
      :id "nappaimiston-painike-ok"
      :on-click #(when (syotto-validi? mittaustyyppi (:nykyinen-syotto @syotto-atom))
                   (kirjaa-arvo (fmt/string->numero (:nykyinen-syotto @syotto-atom)))
                   (syotto-valmis mittaustyyppi syotto-atom))}
     [:span.livicon-check]]]])

(defn- nappaimistokomponentti [{:keys [mittaustyyppi] :as tiedot}]
  (let [nayta-syottokentta? (not= mittaustyyppi :soratie)]
    (fn [{:keys [nimi avain mittausyksikko mittaustyyppi mittaussyotto-atom
                 soratiemittaussyotto-atom lopeta-jatkuva-havainto] :as tiedot}]
      [:div.nappaimisto-container
       [:div.nappaimisto
        [:div.nappaimisto-vasen
         [lopeta-mittaus {:nimi nimi
                          :avain avain
                          :mittaustyyppi mittaustyyppi
                          :syottoarvot (:syotot @mittaussyotto-atom)
                          :lopeta-jatkuva-havainto lopeta-jatkuva-havainto}]
         [mittaustiedot
          {:mittaustyyppi mittaustyyppi
           :mittaukset (count (:syotot @mittaussyotto-atom))
           :keskiarvo (fmt/n-desimaalia
                        (math/avg (map fmt/string->numero (:syotot @mittaussyotto-atom)))
                        2)
           :syotetty-arvo (:nykyinen-syotto @mittaussyotto-atom)
           :yksikko mittausyksikko
           :rajat (mittaustyyppi syoton-rajat)}]
         (when nayta-syottokentta?
           [syottokentta mittaussyotto-atom mittausyksikko])]
        [:div.nappaimisto-oikea
         (case mittaustyyppi
           ;; "Erikoismittauksille" on omat näppäimistöt ja syotto-atomit
           :soratie
           [soratienappaimisto {:syotto-atom soratiemittaussyotto-atom
                                :numeronappain-painettu
                                soratienappaimiston-numeronappain-painettu!}]
           ;; Muille mittauksille normaali numeronäppäimistö
           [numeronappaimisto
            {:syotto-atom mittaussyotto-atom
             :mittaustyyppi mittaustyyppi
             :mittausyksikko mittausyksikko
             :numeronappain-painettu numeronappain-painettu!
             :syotto-validi? syotto-validi?
             :syotto-valmis syotto-valmis!
             :kirjaa-arvo (case mittaustyyppi
                            :kitkamittaus kirjaa-kitkamittaus!
                            :lumisuus kirjaa-lumisuus!
                            :talvihoito-tasaisuus kirjaa-talvihoito-tasaisuus!)}])]]])))

(defn nappaimisto [havainto]
  [nappaimistokomponentti
   {:mittaussyotto-atom s/mittaussyotto
    :soratiemittaussyotto-atom s/soratiemittaussyotto
    :mittaustyyppi (get-in havainto [:mittaus :tyyppi])
    :mittausyksikko (get-in havainto [:mittaus :yksikko])
    :nimi (get-in havainto [:mittaus :nimi])
    :avain (:avain havainto)
    :lopeta-jatkuva-havainto s/lopeta-jatkuvan-havainnon-mittaus!}])