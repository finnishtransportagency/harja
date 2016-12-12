(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.nappaimisto
             :refer [numeronappain-painettu!
                     tyhjennyspainike-painettu! syotto-onnistui!
                     lopeta-mittaus-painettu!
                     kirjaa-mittaus! syoton-rajat syotto-validi?
                     soratienappaimiston-numeronappain-painettu!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.napit :refer [nappi]]
            [harja-laadunseuranta.ui.dom :as dom]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn- lopeta-mittaus [{:keys [nimi avain lopeta-jatkuva-havainto] :as tiedot}]
  [nappi (str nimi " päättyy") {:on-click (fn [_]
                                            (.log js/console "Mittaus päättyy!")
                                            (lopeta-jatkuva-havainto nimi avain))
                                :luokat-str "nappi-kielteinen nappi-peruuta"}])

(defn- mittaustiedot-keskiarvo [{:keys [mittaukset keskiarvo mittauksia-sana
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
        [:div.mittaustieto (str (or mittauksia-sana "Mittauksia") ": ") mittaukset]
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
                                            :rajat rajat
                                            :mittauksia-sana "Kitkamittauksia"}]
    :lumisuus [mittaustiedot-keskiarvo {:mittaukset mittaukset
                                        :keskiarvo keskiarvo
                                        :syotetty-arvo syotetty-arvo
                                        :yksikko yksikko
                                        :rajat rajat
                                        :mittauksia-sana "Lumisuusmittauksia"}]
    :talvihoito-tasaisuus [mittaustiedot-keskiarvo {:mittaukset mittaukset
                                                    :keskiarvo keskiarvo
                                                    :syotetty-arvo syotetty-arvo
                                                    :yksikko yksikko
                                                    :rajat rajat
                                                    :mittauksia-sana "Tasaisuusmittauksia"}]
    [:div]))

(defn- syottokentta [syotto-atom yksikko]
  [:div.nappaimiston-syottokentta
   [:span.nappaimiston-nykyinen-syotto (:nykyinen-syotto @syotto-atom)]
   [:span.nappaimiston-kursori]
   (when yksikko
     [:span.nappaimiston-syottoyksikko yksikko])])

(defn- soratienappaimiston-numeropainike [{:keys [syotto-atom syottotyyppi numero
                                                  numeronappain-painettu]}]
  (let [painike-luokka (fn [syotto painike-tyyppi arvo]
                         (str "nappaimiston-painike "
                              (when (= (painike-tyyppi syotto) arvo)
                                "nappaimiston-painike-painettu")))]
    ;; NOTE Oikeaoppisesti nappien kuuluisi olla <button> elementtejä, mutta jostain
    ;; syystä iPadin safari piirtää tällöin vain kaksi nappia samalle riville.
    [:div
     {:class (painike-luokka @syotto-atom syottotyyppi numero)
      :id (str "soratienappaimiston-painike-tasaisuus-" numero)
      :on-click #(numeronappain-painettu numero syottotyyppi syotto-atom)} (str numero)]))

(defn- soratienappaimisto [{:keys [syotto-atom numeronappain-painettu] :as tiedot}]
  [:div.soratienappaimisto
   [:div.soratienappaimiston-sarake.soratienappaimiston-tasaisuus
    [:div.soratienappaimiston-sarake-otsikko (if (< @dom/leveys dom/+leveys-tabletti+)
                                               "Tas."
                                               "Tasaisuus")]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :tasaisuus
                                        :numero 5 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :tasaisuus
                                        :numero 4 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :tasaisuus
                                        :numero 3 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :tasaisuus
                                        :numero 2 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :tasaisuus
                                        :numero 1 :numeronappain-painettu numeronappain-painettu}]]

   [:div.soratienappaimiston-sarake.soratienappaimiston-kiinteys
    [:div.soratienappaimiston-sarake-otsikko (if (< @dom/leveys dom/+leveys-tabletti+)
                                               "Kiint."
                                               "Kiinteys")]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :kiinteys
                                        :numero 5 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :kiinteys
                                        :numero 4 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :kiinteys
                                        :numero 3 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :kiinteys
                                        :numero 2 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :kiinteys
                                        :numero 1 :numeronappain-painettu numeronappain-painettu}]]

   [:div.soratienappaimiston-sarake.soratienappaimiston-polyavyys
    [:div.soratienappaimiston-sarake-otsikko (if (< @dom/leveys dom/+leveys-tabletti+)
                                               "Pöl."
                                               "Pölyävyys")]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :polyavyys
                                        :numero 5 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :polyavyys
                                        :numero 4 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :polyavyys
                                        :numero 3 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :polyavyys
                                        :numero 2 :numeronappain-painettu numeronappain-painettu}]
    [soratienappaimiston-numeropainike {:syotto-atom syotto-atom :syottotyyppi :polyavyys
                                        :numero 1 :numeronappain-painettu numeronappain-painettu}]]])

(defn- numeronappaimisto [{:keys [syotto-atom kirjaa-arvo mittaustyyppi
                                  numeronappain-painettu syotto-validi? syotto-onnistui]
                           :as tiedot}]
  ;; NOTE Oikeaoppisesti nappien kuuluisi olla <button> elementtejä, mutta jostain
  ;; syystä iPadin safari piirtää tällöin vain kaksi nappia samalle riville.
  (let [syotto-validi? (syotto-validi? mittaustyyppi (:nykyinen-syotto @syotto-atom))]
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
        :on-click #(tyhjennyspainike-painettu! mittaustyyppi syotto-atom)}
       [kuvat/svg-sprite "askelpalautin-24"]]
      [:div
       {:class "nappaimiston-painike"
        :id "nappaimiston-painike0"
        :on-click #(numeronappain-painettu! 0 mittaustyyppi syotto-atom)} "0"]
      [:div
       {:disabled (not syotto-validi?)
        :class (str "nappaimiston-painike nappaimiston-painike-ok "
                    (when-not syotto-validi?
                      "nappaimiston-painike-disabloitu"))
        :id "nappaimiston-painike-ok"
        :on-click #(when syotto-validi?
                     (when (kirjaa-arvo (fmt/string->numero (:nykyinen-syotto @syotto-atom)))
                       (syotto-onnistui mittaustyyppi syotto-atom)))}
       [kuvat/svg-sprite "tarkistus-24"]]]]))

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
             :syotto-onnistui syotto-onnistui!
             :kirjaa-arvo kirjaa-mittaus!}])]]])))

(defn nappaimisto [havainto]
  [nappaimistokomponentti
   {:mittaussyotto-atom s/mittaussyotto
    :soratiemittaussyotto-atom s/soratiemittaussyotto
    :mittaustyyppi (get-in havainto [:mittaus :tyyppi])
    :mittausyksikko (get-in havainto [:mittaus :yksikko])
    :nimi (get-in havainto [:mittaus :nimi])
    :avain (:avain havainto)
    :lopeta-jatkuva-havainto lopeta-mittaus-painettu!}])
