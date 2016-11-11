(ns harja-laadunseuranta.ui.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.nappaimisto :refer [numeronappain-painettu!
                                                             tyhjennyspainike-painettu!
                                                             syotto-valmis!]]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros
    [harja-laadunseuranta.macros :as m]
    [cljs.core.async.macros :refer [go go-loop]]
    [devcards.core :as dc :refer [defcard deftest]]))

(defn- lopeta-mittaus [tiedot]
  (let [kitkamittaus-valmis (fn [syottoarvot]
                              (.log js/console "Kitkamittaus-syötöt " (pr-str syottoarvot))
                              (let [keskiarvo (math/avg (map fmt/string->numero syottoarvot))]
                                (.log js/console "Syötetty keskiarvo: " (pr-str keskiarvo))))]

    (fn [{:keys [nimi avain lopeta-jatkuva-havainto
                 syottoarvot mittaustyyppi] :as tiedot}]
      [:button
       {:class "nappi nappi-kielteinen nappi-peruuta"
        :on-click (fn [_]
                    (.log js/console "Mittaus päättyy!")
                    (case mittaustyyppi
                      :kitkamittaus (kitkamittaus-valmis syottoarvot))
                    (lopeta-jatkuva-havainto avain))}
       (str nimi " päättyy")])))

(defn- mittaustiedot [{:keys [mittaukset keskiarvo]}]
  [:div.mittaustiedot
   [:div.mittaustieto (str "Mittauksia: " mittaukset)]
   [:div.mittaustieto (str "Keskiarvo: " (if (empty? mittaukset)
                                           "-"
                                           (fmt/kahdella-desimaalilla keskiarvo)))]])

(defn numeropainikkeet [syotto-atom mittaustyyppi]
  (let [syotto-kelpaa? (fn [syotto]
                         ;; Ainakin yksi desimaali
                         (> (count syotto) 2))]

    (fn []
      [:div.nappaimiston-painikkeet
       [:div.nappaimiston-syottokentta (:nykyinen-syotto @syotto-atom)
        [:div.nappaimiston-kursori]]
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
                      (syotto-valmis! mittaustyyppi syotto-atom))}
         [:span.livicon-check]]]])))

(defn- nappaimistokomponentti [tiedot]
  (let [syotto-atom (atom {:nykyinen-syotto "0,"
                           :syotot []})]

    (fn [{:keys [nimi avain lopeta-jatkuva-havainto mittaustyyppi] :as tiedot}]
      [:div.nappaimisto-container
       [:div.nappaimisto
        [lopeta-mittaus {:nimi nimi
                         :avain avain
                         :mittaustyyppi mittaustyyppi
                         :syottoarvot (:syotot @syotto-atom)
                         :lopeta-jatkuva-havainto lopeta-jatkuva-havainto}]
        [mittaustiedot
         (count (:syotot @syotto-atom))
         (math/avg (map fmt/string->numero (:syotot @syotto-atom)))]
        [numeropainikkeet syotto-atom mittaustyyppi]]])))

(defn nappaimisto [havainto]
  [nappaimistokomponentti {:mittaustyyppi (get-in havainto [:mittaus :tyyppi])
                           :nimi (get-in havainto [:mittaus :nimi])
                           :avain (:avain havainto)
                           :lopeta-jatkuva-havainto s/lopeta-jatkuvan-havainnon-mittaus!}])

;; TODO Kirjaa tähän tyyliin:
#_(kirjaa-kertakirjaus @s/idxdb
                       {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
                        :aikaleima (tc/to-long (lt/local-now))
                        :tarkastusajo @s/tarkastusajo-id
                        :havainnot @s/jatkuvat-havainnot
                        :mittaukset {:lumisuus @s/talvihoito-lumimaara
                                     :talvihoito-tasaisuus @s/talvihoito-tasaisuus
                                     :kitkamittaus @s/talvihoito-kitkamittaus
                                     :soratie-tasaisuus @s/soratie-tasaisuus
                                     :polyavyys @s/soratie-polyavyys
                                     :kiinteys @s/soratie-kiinteys}
                        ;; TODO Nämä tulee kai lomakkeelta? Pitää selvittää, miten toimii.
                        ;:kuvaus kuvaus
                        ;:laadunalitus (true? laadunalitus?)
                        ;:kuva kuva
                        })