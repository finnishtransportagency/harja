(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.nappaimisto
             :refer [numeronappain-painettu! nykyisen-syotto-osan-max-merkkimaara-saavutettu?
                     tyhjennyspainike-painettu! syotto-onnistui!
                     lopeta-mittaus-painettu! desimaalierotin-painettu!
                     kirjaa-mittaus! syotto-validi? syottosaannot
                     soratienappaimiston-numeronappain-painettu!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.yleiset.napit :refer [nappi]]
            [harja-laadunseuranta.ui.yleiset.dom :as dom]
            [harja-laadunseuranta.tiedot.asetukset.kuvat :as kuvat]
            [clojure.string :as str])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn- lopeta-mittaus [{:keys [nimi avain lopeta-jatkuva-havainto] :as tiedot}]
  [nappi (str nimi " päättyy") {:on-click (fn [_]
                                            (.log js/console "Mittaus päättyy!")
                                            (lopeta-jatkuva-havainto nimi avain))
                                :luokat-str "nappi-kielteinen nappi-peruuta"}])

(defn- mittaustiedot-keskiarvo [{:keys [mittaukset keskiarvo mittaustyyppi
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
        [:div.mittaustieto (str "Mittaus: " mittaustyyppi)]
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
                                            :rajat rajat
                                            :mittaustyyppi "Kitka"}]
    :lumisuus [mittaustiedot-keskiarvo {:mittaukset mittaukset
                                        :keskiarvo keskiarvo
                                        :syotetty-arvo syotetty-arvo
                                        :yksikko yksikko
                                        :rajat rajat
                                        :mittaustyyppi "Lumisuus"}]
    :talvihoito-tasaisuus [mittaustiedot-keskiarvo {:mittaukset mittaukset
                                                    :keskiarvo keskiarvo
                                                    :syotetty-arvo syotetty-arvo
                                                    :yksikko yksikko
                                                    :rajat rajat
                                                    :mittaustyyppi "Tasaisuus"}]
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

(defn- numeronappaimiston-painike [{:keys [id on-click lisaluokat-str disabled sisalto]}]
  ;; NOTE Oikeaoppisesti nappien kuuluisi olla <button> elementtejä, mutta jostain
  ;; syystä iPadin safari piirtää tällöin vain kaksi nappia samalle riville.
  [:div
   {:class (str "nappaimiston-painike "
                (when disabled
                  "nappaimiston-painike-disabloitu ")
                lisaluokat-str)
    :id (str "nappaimiston-painike-" id)
    :on-click #(when-not disabled (on-click))}
   (when sisalto
     sisalto)])

(defn- numeronappaimiston-numero [{:keys [numeronappain-painettu numero disabled
                                          mittaustyyppi syotto-atom lisaluokat-str]}]
  [numeronappaimiston-painike {:sisalto numero
                               :id numero
                               :lisaluokat-str lisaluokat-str
                               :disabled disabled
                               :on-click #(numeronappain-painettu numero mittaustyyppi syotto-atom)}])

(defn- numeronappaimisto [{:keys [syotto-atom kirjaa-arvo mittaustyyppi
                                  numeronappain-painettu syotto-validi? syotto-onnistui]
                           :as tiedot}]
  (let [nykyinen-syotto (:nykyinen-syotto @syotto-atom)
        nykyinen-syotto-validi? (syotto-validi? mittaustyyppi nykyinen-syotto)]
    [:div.numeronappaimisto
     [:div.nappaimiston-painikekentat
      [numeronappaimiston-numero {:numero 7 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]
      [numeronappaimiston-numero {:numero 8 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]
      [numeronappaimiston-numero {:numero 9 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]

      [numeronappaimiston-numero {:numero 4 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]
      [numeronappaimiston-numero {:numero 5 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]
      [numeronappaimiston-numero {:numero 6 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]

      [numeronappaimiston-numero {:numero 1 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]
      [numeronappaimiston-numero {:numero 2 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]
      [numeronappaimiston-numero {:numero 3 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :mittaustyyppi mittaustyyppi :numeronappain-painettu numeronappain-painettu}]

      [numeronappaimiston-numero {:numero 0 :syotto-atom syotto-atom
                                  :disabled (nykyisen-syotto-osan-max-merkkimaara-saavutettu? mittaustyyppi nykyinen-syotto)
                                  :lisaluokat-str "nappaimiston-painike-leveys-tupla"
                                  :mittaustyyppi mittaustyyppi
                                  :numeronappain-painettu numeronappain-painettu}]
      [numeronappaimiston-painike {:id "pilkku"
                                   :disabled (or (not (get-in syottosaannot
                                                              [mittaustyyppi :salli-syottaa-desimaalierotin?]))
                                                 (empty? nykyinen-syotto) ;; Pilkku ei voi olla ensimmäinen
                                                 (number? (str/index-of nykyinen-syotto ","))) ;; Pilkku on jo annettu
                                   :on-click #(desimaalierotin-painettu! syotto-atom)
                                   :sisalto ","}]

      [numeronappaimiston-painike {:id "delete"
                                   :lisaluokat-str "nappaimiston-painike-leveys-puolet"
                                   :on-click #(tyhjennyspainike-painettu! mittaustyyppi syotto-atom)
                                   :sisalto [kuvat/svg-sprite "askelpalautin-24"]}]
      [numeronappaimiston-painike {:id "ok"
                                   :lisaluokat-str "nappaimiston-painike-ok nappaimiston-painike-leveys-puolet"
                                   :disabled (not nykyinen-syotto-validi?)
                                   :on-click #(when (kirjaa-arvo (fmt/string->numero nykyinen-syotto))
                                                (syotto-onnistui mittaustyyppi syotto-atom))
                                   :sisalto [kuvat/svg-sprite "tarkistus-24"]}]]]))

(defn- nappaimistokomponentti [{:keys [mittaustyyppi] :as tiedot}]
  (let [nayta-syottokentta? (not= mittaustyyppi :soratie)]
    (fn [{:keys [nimi avain mittausyksikko mittaustyyppi mittaussyotto-atom
                 soratiemittaussyotto-atom] :as tiedot}]
      [:div.nappaimisto-container
       [:div.nappaimisto
        [:div.nappaimisto-vasen
         [lopeta-mittaus {:nimi nimi
                          :avain avain
                          :mittaustyyppi mittaustyyppi
                          :syottoarvot (:syotot @mittaussyotto-atom)
                          :lopeta-jatkuva-havainto lopeta-mittaus-painettu!}]
         [mittaustiedot
          {:mittaustyyppi mittaustyyppi
           :mittaukset (count (:syotot @mittaussyotto-atom))
           :keskiarvo (fmt/n-desimaalia
                        (math/avg (map fmt/string->numero (:syotot @mittaussyotto-atom)))
                        2)
           :syotetty-arvo (:nykyinen-syotto @mittaussyotto-atom)
           :yksikko mittausyksikko
           :rajat (get-in syottosaannot [mittaustyyppi :rajat])}]
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
    :avain (:avain havainto)}])
