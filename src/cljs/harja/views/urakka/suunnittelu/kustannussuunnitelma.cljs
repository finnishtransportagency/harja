(ns harja.views.urakka.suunnittelu.kustannussuunnitelma
  (:require [reagent.core :as r :refer [atom]]
            [clojure.string :as clj-str]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.grid-osan-vaihtaminen :as gov]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [goog.dom :as dom])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]
                   [harja.ui.taulukko.grid :refer [defsolu]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(defonce e! nil)

(defn summa-formatointi [teksti]
  (let [teksti (clj-str/replace (str teksti) "," ".")]
    (if (or (= "" teksti) (js/isNaN teksti))
      "0,00"
      (fmt/desimaaliluku teksti 2 true))))

(defn summa-formatointi-uusi [teksti]
  (if (nil? teksti)
    ""
    (let [teksti (clj-str/replace (str teksti) "," ".")]
      (if (or (= "" teksti) (js/isNaN teksti))
        "0,00"
        (fmt/desimaaliluku teksti 2 true)))))

(defn summa-formatointi-aktiivinen [teksti]
  (let [teksti-ilman-pilkkua (clj-str/replace (str teksti) "," ".")]
    (cond
      (or (nil? teksti) (= "" teksti)) ""
      (= t/vaihtelua-teksti teksti) t/vaihtelua-teksti
      (re-matches #".*\.0*$" teksti-ilman-pilkkua) (apply str (fmt/desimaaliluku teksti-ilman-pilkkua nil true)
                                                          (drop 1 (re-find #".*(\.|,)(0*)" teksti)))
      :else (fmt/desimaaliluku teksti-ilman-pilkkua nil true))))

(defn yhteenveto-format [teksti]
  (let [teksti (if (or (= "" teksti) (.isNaN js/Number teksti))
                 "0,00"
                 (str teksti))
        oletettu-numero (clj-str/replace teksti "," ".")
        numero? (and (not (js/isNaN (js/Number oletettu-numero)))
                     (not= "" (clj-str/trim teksti)))
        teksti (if numero?
                 oletettu-numero
                 teksti)]
    (if numero?
      (fmt/desimaaliluku teksti 2 true)
      teksti)))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))

;;;;; TAULUKOT ;;;;;;;
(defsolu VaylaCheckbox
  [vaihda-fn txt]
  {:pre [(fn? vaihda-fn)]}
  (fn suunnittele-kuukausitasolla-filter [this]
    (let [taman-data (solu/taman-derefable this)
          kuukausitasolla? (or @taman-data false)
          osan-id (str (grid/hae-osa this :id))]
      [:div
       [:input.vayla-checkbox {:id osan-id
                               :type "checkbox" :checked kuukausitasolla?
                               :on-change (partial (:vaihda-fn this) this)}]
       [:label {:for osan-id} (:txt this)]])))

(defsolu LaajennaSyote
  [aukaise-fn auki-alussa? toiminnot kayttaytymiset parametrit fmt fmt-aktiivinen]
  (fn [this]
    (let [auki? (r/atom auki-alussa?)
          input-osa (solu/syote {:toiminnot (into {}
                                                  (map (fn [[k f]]
                                                         [k (fn [x]
                                                              (binding [solu/*this* (::tama-komponentti solu/*this*)]
                                                                (f x)))])
                                                       toiminnot))
                                 :kayttaytymiset kayttaytymiset
                                 :parametrit parametrit
                                 :fmt fmt
                                 :fmt-aktiivinen fmt-aktiivinen})]
      (fn [this]
        (let [ikoni-auki ikonit/livicon-chevron-up
              ikoni-kiinni ikonit/livicon-chevron-down]
          [:div {:style {:position "relative"}}
           [grid/piirra (assoc input-osa ::tama-komponentti this
                               :parametrit (update (:parametrit this) :style assoc :width "100%" :height "100%")
                               :harja.ui.taulukko.impl.grid/osan-derefable (grid/solun-asia this :osan-derefable))]
           [:span {:style {:position "absolute"
                           :display "flex"
                           :right "0px"
                           :top "0px"
                           :height "100%"
                           :align-items "center"}
                   :class "solu-laajenna klikattava"
                   :on-click
                   #(do (.preventDefault %)
                        (swap! auki? not)
                        (aukaise-fn this @auki?))}
            (if @auki?
              ^{:key "laajenna-auki"}
              [ikoni-auki]
              ^{:key "laajenna-kiini"}
              [ikoni-kiinni])]])))))

(defonce ^{:mutable true
          :doc "Jos halutaan estää blur event focus eventin jälkeen, niin tätä voi käyttää"}
         esta-blur_ false)

(defn esta-blur-ja-lisaa-vaihtelua-teksti [event]
  (if esta-blur_
    (do (set! esta-blur_ false)
        (set! (.. event -target -value) t/vaihtelua-teksti)
        nil)
    event))

(defn tayta-alas-napin-toiminto [asettajan-nimi tallennettava-asia maara-solun-index rivit-alla arvo]
  (when (and arvo (not (empty? rivit-alla)))
    (doseq [rivi rivit-alla
            :let [maara-solu (grid/get-in-grid rivi [maara-solun-index])
                  piillotettu? (grid/piillotettu? rivi)]]
      (when-not piillotettu?
        (t/paivita-solun-arvo {:paivitettava-asia asettajan-nimi
                               :arvo arvo
                               :solu maara-solu
                               :ajettavat-jarejestykset #{:mapit}}
                              true)))
    (when (= asettajan-nimi :aseta-rahavaraukset!)
      (e! (t/->TallennaKustannusarvoitu tallennettava-asia
                             (vec (keep (fn [rivi]
                                          (let [maara-solu (grid/get-in-grid rivi [1])
                                                piillotettu? (grid/piillotettu? rivi)]
                                            (when-not piillotettu?
                                              (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                                        rivit-alla))))
      (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))

(defn maara-solujen-disable! [data-sisalto disabled?]
  (grid/post-walk-grid! data-sisalto
                        (fn [osa]
                          (when (or (instance? solu/Syote osa)
                                    (instance? g-pohjat/SyoteTaytaAlas osa))
                            (grid/paivita-osa! osa
                                               (fn [solu]
                                                 (assoc-in solu [:parametrit :disabled?] disabled?)))))))

(defn syote-solu
  [{:keys [nappi? fmt paivitettava-asia solun-index blur-tallenna!
           nappia-painettu-tallenna!]}]
  (merge
    {:tyyppi (if nappi?
               :syote-tayta-alas
               :syote)
     :luokat #{"input-default"}
     :toiminnot {:on-change (fn [arvo]
                              (when esta-blur_
                                (set! esta-blur_ false))
                              (when arvo
                                (t/paivita-solun-arvo {:paivitettava-asia paivitettava-asia
                                                       :arvo arvo
                                                       :solu solu/*this*
                                                       :ajettavat-jarejestykset #{:mapit}}
                                                      false)))
                 :on-focus (fn [event]
                             (if nappi?
                               (grid/paivita-osa! solu/*this*
                                                  (fn [solu]
                                                    (assoc solu :nappi-nakyvilla? true)))
                               (let [arvo (.. event -target -value)]
                                 (when (= arvo t/vaihtelua-teksti)
                                   (set! esta-blur_ true)
                                   (set! (.. event -target -value) nil)))))
                 :on-blur (fn [arvo]
                            (when nappi?
                              (grid/paivita-osa! solu/*this*
                                                 (fn [solu]
                                                   (assoc solu :nappi-nakyvilla? false))))
                            (when arvo
                              (t/paivita-solun-arvo {:paivitettava-asia paivitettava-asia
                                                     :arvo arvo
                                                     :solu solu/*this*
                                                     :ajettavat-jarejestykset true
                                                     :triggeroi-seuranta? true}
                                                    true)
                              (blur-tallenna!)
                              (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))))
                 :on-key-down (fn [event]
                                (when (= "Enter" (.. event -key))
                                  (.. event -target blur)))}
     :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                  {:eventin-arvo {:f poista-tyhjat}}]
                      :on-blur (if nappi?
                                 [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]
                                 [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}} {:oma {:f esta-blur-ja-lisaa-vaihtelua-teksti}}])}
     :parametrit {:size 2}
     :fmt fmt
     :fmt-aktiivinen summa-formatointi-aktiivinen}
    (when nappi?
      {:nappia-painettu! (fn [rivit-alla arvo]
                           (when (and arvo (not (empty? rivit-alla)))
                             (doseq [rivi rivit-alla
                                     :let [maara-solu (grid/get-in-grid rivi [solun-index])
                                           piillotettu? (grid/piillotettu? rivi)]]
                               (when-not piillotettu?
                                 (t/paivita-solun-arvo {:paivitettava-asia paivitettava-asia
                                                        :arvo arvo
                                                        :solu maara-solu
                                                        :ajettavat-jarejestykset true
                                                        :triggeroi-seuranta? true}
                                                       true)))
                             (nappia-painettu-tallenna! rivit-alla)
                             (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))))
       :nappi-nakyvilla? false})))


(defn rivi->rivi-kuukausifiltterilla [pohja? kuukausitasolla?-rajapinta kuukausitasolla?-polku rivi]
  (let [sarakkeiden-maara (count (grid/hae-grid rivi :lapset))]
    (with-meta
      (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                  :koko (assoc-in konf/auto
                                  [:rivi :nimet]
                                  {::t/yhteenveto 0
                                   ::t/valinta 1})
                  :osat [(-> rivi
                             (grid/aseta-nimi ::t/yhteenveto)
                             (grid/paivita-grid! :parametrit
                                                 (fn [parametrit]
                                                   (assoc parametrit :style {:z-index 10})))
                             (grid/paivita-grid! :lapset
                                                 (fn [osat]
                                                   (mapv (fn [osa]
                                                           (if (or (instance? solu/Laajenna osa)
                                                                   (instance? LaajennaSyote osa))
                                                             (assoc osa :auki-alussa? true)
                                                             osa))
                                                         osat))))
                         (grid/rivi {:osat (vec
                                             (cons (vayla-checkbox (fn [this event]
                                                                     (.preventDefault event)
                                                                     (let [kuukausitasolla? (not (grid/arvo-rajapinnasta (grid/osien-yhteinen-asia this :datan-kasittelija)
                                                                                                                         kuukausitasolla?-rajapinta))]
                                                                       (e! (tuck-apurit/->MuutaTila kuukausitasolla?-polku kuukausitasolla?))))
                                                                   "Haluan suunnitella jokaiselle kuukaudelle määrän erikseen")
                                                   (repeatedly (dec sarakkeiden-maara) (fn [] (solu/tyhja)))))
                                     :koko {:seuraa {:seurattava (if pohja?
                                                                   ::g-pohjat/otsikko
                                                                   ::otsikko)
                                                     :sarakkeet :sama
                                                     :rivit :sama}}
                                     :nimi ::t/valinta}
                                    [{:sarakkeet [0 sarakkeiden-maara] :rivit [0 1]}])]})
      {:key "foo"})))

(defn rivi-kuukausifiltterilla->rivi [rivi-kuukausifiltterilla]
  (grid/aseta-nimi (grid/paivita-grid! (grid/get-in-grid rivi-kuukausifiltterilla [::t/yhteenveto])
                                       :lapset
                                       (fn [osat]
                                         (mapv (fn [osa]
                                                 (if (or (instance? solu/Laajenna osa)
                                                         (instance? LaajennaSyote osa))
                                                   (assoc osa :auki-alussa? false)
                                                   osa))
                                               osat)))
                   ::data-yhteenveto))

(defn rivi-kuukausifiltterilla!
  [laajennasolu pohja? kuukausitasolla?-rajapinta kuukausitasolla?-polku & datan-kasittely]
  (apply grid/vaihda-osa!
         (-> laajennasolu grid/vanhempi)
         (partial rivi->rivi-kuukausifiltterilla pohja? kuukausitasolla?-rajapinta kuukausitasolla?-polku)
         datan-kasittely))

(defn rivi-ilman-kuukausifiltteria!
  [laajennasolu & datan-kasittely]
  (apply grid/vaihda-osa!
         (-> laajennasolu grid/vanhempi grid/vanhempi)
         rivi-kuukausifiltterilla->rivi
         datan-kasittely))

(defsolu SuunnitelmienTilaOtsikko []
  (fn suunnitelman-selitteet [this]
    [:div#suunnitelman-selitteet
     [:span [ikonit/ok] "Kaikki kentätä täytetty"]
     [:span [ikonit/livicon-question] "Keskeneräinen"]
     [:span [ikonit/remove] "Suunnitelma puuttuu"]]))

(defn hankintojen-pohja [taulukon-id
                         root-asetus!
                         root-asetukset
                         nappia-painettu!
                         on-change
                         on-blur]
  (let [nyt (pvm/nyt)]
    (g-pohjat/uusi-taulukko {:header [{:tyyppi :teksti
                                       :leveys 3
                                       :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                      {:tyyppi :teksti
                                       :leveys 2
                                       :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                      {:tyyppi :teksti
                                       :leveys 2
                                       :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                      {:tyyppi :teksti
                                       :leveys 1
                                       :luokat #{"table-default" "table-default-header" "harmaa-teksti" "lihavoitu"}}]
                             :body (mapv (fn [hoitokauden-numero]
                                           {:tyyppi :taulukko
                                            :osat [{:tyyppi :rivi
                                                    :nimi ::data-yhteenveto
                                                    :osat [{:tyyppi :laajenna
                                                            :aukaise-fn (fn [this auki?]
                                                                          (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data]))
                                                            :auki-alussa? false
                                                            :luokat #{"table-default" "lihavoitu"}}
                                                           {:tyyppi :teksti
                                                            :luokat #{"table-default"}}
                                                           {:tyyppi :teksti
                                                            :luokat #{"table-default"}
                                                            :fmt yhteenveto-format}
                                                           {:tyyppi :teksti
                                                            :luokat #{"table-default" "harmaa-teksti"}
                                                            :fmt yhteenveto-format}]}
                                                   {:tyyppi :taulukko
                                                    :nimi ::data-sisalto
                                                    :luokat #{"piillotettu"}
                                                    :osat (vec (repeatedly 12
                                                                           (fn []
                                                                             {:tyyppi :rivi
                                                                              :osat [{:tyyppi :teksti
                                                                                      :luokat #{"table-default"}
                                                                                      :fmt (fn [paivamaara]
                                                                                             (when paivamaara
                                                                                               (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                                     mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                                  (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                                      (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                                 (if mennyt?
                                                                                                   (str teksti " (mennyt)")
                                                                                                   teksti))))}
                                                                                     {:tyyppi :syote-tayta-alas
                                                                                      :nappi-nakyvilla? false
                                                                                      :nappia-painettu! (partial nappia-painettu! hoitokauden-numero)
                                                                                      :toiminnot {:on-change (partial on-change hoitokauden-numero)
                                                                                                  :on-focus (fn [_]
                                                                                                              (grid/paivita-osa! solu/*this*
                                                                                                                                 (fn [solu]
                                                                                                                                   (assoc solu :nappi-nakyvilla? true))))
                                                                                                  :on-blur (partial on-blur hoitokauden-numero)
                                                                                                  :on-key-down (fn [event]
                                                                                                                 (when (= "Enter" (.. event -key))
                                                                                                                   (.. event -target blur)))}
                                                                                      :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                   {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                       :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                      :parametrit {:size 2}
                                                                                      :luokat #{"input-default"}
                                                                                      :fmt summa-formatointi-uusi
                                                                                      :fmt-aktiivinen summa-formatointi-aktiivinen}
                                                                                     {:tyyppi :teksti
                                                                                      :luokat #{"table-default"}
                                                                                      :fmt summa-formatointi}
                                                                                     {:tyyppi :teksti
                                                                                      :luokat #{"table-default" "harmaa-teksti"}}]})))}]})
                                         (range 1 6))
                             :footer [{:tyyppi :teksti
                                       :luokat #{"table-default" "table-default-sum"}}
                                      {:tyyppi :teksti
                                       :luokat #{"table-default" "table-default-sum"}}
                                      {:tyyppi :teksti
                                       :luokat #{"table-default" "table-default-sum"}
                                       :fmt yhteenveto-format}
                                      {:tyyppi :teksti
                                       :luokat #{"table-default" "table-default-sum" "harmaa-teksti"}
                                       :fmt yhteenveto-format}]
                             :taulukon-id taulukon-id
                             :root-asetus! root-asetus!
                             :root-asetukset root-asetukset})))

(defn maarataulukon-pohja [taulukon-id
                           polun-osa
                           root-asetus!
                           root-asetukset
                           kuukausitasolla?-polku
                           on-change
                           on-blur
                           nappia-painettu!
                           on-change-kk
                           on-blur-kk]
  {:pre [(string? taulukon-id)
         (every? fn? #{nappia-painettu! on-change on-blur on-change-kk on-blur-kk})]}
  (let [nyt (pvm/nyt)
        yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
                                             :solun-polun-pituus 1
                                             :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                             :datan-kasittely (fn [yhteenveto]
                                                                (mapv (fn [[_ v]]
                                                                        v)
                                                                      yhteenveto))
                                             :tunnisteen-kasittely (fn [osat _]
                                                                     (mapv (fn [osa]
                                                                             (when (instance? solu/Syote osa)
                                                                               :maara))
                                                                           (grid/hae-grid osat :lapset)))}
        g (g-pohjat/uusi-taulukko {:header [{:tyyppi :teksti
                                             :leveys 3
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 2
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 2
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 1
                                             :luokat #{"table-default" "table-default-header" "harmaa-teksti" "lihavoitu"}}]
                                   :body [{:tyyppi :taulukko
                                           :osat [{:tyyppi :rivi
                                                   :nimi ::data-yhteenveto
                                                   :osat [{:tyyppi :laajenna
                                                           :aukaise-fn (fn [this auki?]
                                                                         (if auki?
                                                                           (rivi-kuukausifiltterilla! this
                                                                                                      true
                                                                                                      :kuukausitasolla?
                                                                                                      kuukausitasolla?-polku
                                                                                                      [:. ::t/yhteenveto] yhteenveto-grid-rajapinta-asetukset
                                                                                                      [:. ::t/valinta] {:rajapinta :kuukausitasolla?
                                                                                                                        :solun-polun-pituus 1
                                                                                                                        :datan-kasittely (fn [kuukausitasolla?]
                                                                                                                                           [kuukausitasolla? nil nil nil])})
                                                                           (do
                                                                             (rivi-ilman-kuukausifiltteria! this
                                                                                                            [:.. ::data-yhteenveto] yhteenveto-grid-rajapinta-asetukset)
                                                                             (e! (tuck-apurit/->MuutaTila [:gridit polun-osa :kuukausitasolla?] false))))
                                                                         (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                           :auki-alussa? false
                                                           :luokat #{"table-default" "lihavoitu"}}
                                                          {:tyyppi :syote
                                                           :luokat #{"input-default"}
                                                           :toiminnot {:on-change (fn [arvo]
                                                                                    (when esta-blur_
                                                                                      (set! esta-blur_ false))
                                                                                    (on-change arvo))
                                                                       :on-focus (fn [event]
                                                                                   (let [arvo (.. event -target -value)]
                                                                                     (when (= arvo t/vaihtelua-teksti)
                                                                                       (set! esta-blur_ true)
                                                                                       (set! (.. event -target -value) nil))))
                                                                       :on-blur (fn [arvo]
                                                                                  (when arvo
                                                                                    (on-blur arvo)))
                                                                       :on-key-down (fn [event]
                                                                                      (when (= "Enter" (.. event -key))
                                                                                        (.. event -target blur)))}
                                                           :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                        {:eventin-arvo {:f poista-tyhjat}}]
                                                                            :on-blur [:numero-pisteella
                                                                                      :positiivinen-numero
                                                                                      {:eventin-arvo {:f poista-tyhjat}}
                                                                                      {:oma {:f esta-blur-ja-lisaa-vaihtelua-teksti}}]}
                                                           :parametrit {:size 2}
                                                           :fmt yhteenveto-format
                                                           :fmt-aktiivinen summa-formatointi-aktiivinen}
                                                          {:tyyppi :teksti
                                                           :luokat #{"table-default"}
                                                           :fmt yhteenveto-format}
                                                          {:tyyppi :teksti
                                                           :luokat #{"table-default" "harmaa-teksti"}
                                                           :fmt yhteenveto-format}]}
                                                  {:tyyppi :taulukko
                                                   :nimi ::data-sisalto
                                                   :luokat #{"piillotettu"}
                                                   :osat (vec (repeatedly 12
                                                                          (fn []
                                                                            {:tyyppi :rivi
                                                                             :osat [{:tyyppi :teksti
                                                                                     :luokat #{"table-default"}
                                                                                     :fmt (fn [paivamaara]
                                                                                            (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                                  mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                               (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                                   (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                              (if mennyt?
                                                                                                (str teksti " (mennyt)")
                                                                                                teksti)))}
                                                                                    {:tyyppi :syote-tayta-alas
                                                                                     :nappi-nakyvilla? false
                                                                                     :nappia-painettu! nappia-painettu!
                                                                                     :toiminnot {:on-change on-change-kk
                                                                                                 :on-focus (fn [_]
                                                                                                             (grid/paivita-osa! solu/*this*
                                                                                                                                (fn [solu]
                                                                                                                                  (assoc solu :nappi-nakyvilla? true))))
                                                                                                 :on-blur on-blur-kk
                                                                                                 :on-key-down (fn [event]
                                                                                                                (when (= "Enter" (.. event -key))
                                                                                                                  (.. event -target blur)))}
                                                                                     :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                  {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                      :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                     :parametrit {:size 2}
                                                                                     :luokat #{"input-default"}
                                                                                     :fmt summa-formatointi-uusi
                                                                                     :fmt-aktiivinen summa-formatointi-aktiivinen}
                                                                                    {:tyyppi :teksti
                                                                                     :luokat #{"table-default"}
                                                                                     :fmt summa-formatointi}
                                                                                    {:tyyppi :teksti
                                                                                     :luokat #{"table-default" "harmaa-teksti"}}]})))}]}]
                                   :footer [{:tyyppi :teksti
                                             :luokat #{"table-default" "table-default-sum"}}
                                            {:tyyppi :teksti
                                             :luokat #{"table-default" "table-default-sum"}}
                                            {:tyyppi :teksti
                                             :luokat #{"table-default" "table-default-sum"}
                                             :fmt yhteenveto-format}
                                            {:tyyppi :teksti
                                             :luokat #{"table-default" "table-default-sum" "harmaa-teksti"}
                                             :fmt yhteenveto-format}]
                                   :taulukon-id taulukon-id
                                   :root-asetus! root-asetus!
                                   :root-asetukset root-asetukset})]
    g))


(defn suunnitelmien-tila-grid []
  (let [taulukon-id "suunnitelmien-taulukko"
        kuluva-hoitokauden-numero (-> @tila/suunnittelu-kustannussuunnitelma :domain :kuluva-hoitokausi :hoitokauden-numero)
        viimeinen-vuosi? (= kuluva-hoitokauden-numero 5)
        mene-idlle (fn [id]
                     (.scrollIntoView (dom/getElement id)))
        g (g-pohjat/uusi-taulukko {:header [{:tyyppi :oma
                                             :constructor (fn [_] (suunnitelmien-tila-otsikko))
                                             :leveys 9}
                                            {:tyyppi :teksti
                                             :leveys 1
                                             :luokat #{"table-default" "keskita" "alas"
                                                       (when-not viimeinen-vuosi?
                                                         "aktiivinen-vuosi")}}
                                            {:tyyppi :teksti
                                             :leveys 1
                                             :luokat #{"table-default" "keskita" "alas"
                                                       (when viimeinen-vuosi?
                                                         "aktiivinen-vuosi")}}
                                            {:tyyppi :teksti
                                             :leveys 1
                                             :luokat #{"table-default" "harmaa-teksti"}}]
                                   :header-luokat #{"suunnitelma-ikonien-varit"}
                                   :header-korkeus "auto"
                                   :body (vec
                                           (concat [{:tyyppi :rivi
                                                     :nimi ::hankintakustannukset
                                                     :osat [{:tyyppi :nappi
                                                             :toiminnot {:on-click (fn [_] (mene-idlle "hankintakustannukset"))}
                                                             :luokat #{"table-default" "linkki"}}
                                                            {:tyyppi :ikoni
                                                             :luokat #{"table-default" "keskita"}}
                                                            {:tyyppi :ikoni
                                                             :luokat #{"table-default" "keskita"
                                                                       (when-not viimeinen-vuosi?
                                                                         "harmaa-teksti")}}
                                                            {:tyyppi :teksti
                                                             :luokat #{"table-default" "harmaa-teksti"}
                                                             :fmt yhteenveto-format}]}]
                                                   (vec (map-indexed (fn [index toimenpide]
                                                                       (let [rahavarausrivit (t/toimenpiteen-rahavaraukset toimenpide)]
                                                                         {:tyyppi :taulukko
                                                                          :nimi toimenpide
                                                                          :osat [{:tyyppi :rivi
                                                                                  :nimi ::toimenpide-yhteenveto
                                                                                  :luokat #{"toimenpide-rivi"
                                                                                            "suunnitelma-rivi"}
                                                                                  :osat [{:tyyppi :laajenna
                                                                                          :aukaise-fn (fn [this auki?]
                                                                                                        (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data]))
                                                                                          :auki-alussa? false
                                                                                          :ikoni "triangle"
                                                                                          :luokat #{"table-default" "ikoni-vasemmalle" "solu-sisenna-1"}}
                                                                                         {:tyyppi :ikoni
                                                                                          :luokat #{"table-default" "keskita"}}
                                                                                         {:tyyppi :ikoni
                                                                                          :luokat #{"table-default" "keskita"
                                                                                                    (when-not viimeinen-vuosi?
                                                                                                      "harmaa-teksti")}}
                                                                                         {:tyyppi :teksti
                                                                                          :luokat #{"table-default" "harmaa-teksti"}
                                                                                          :fmt yhteenveto-format}]}
                                                                                 {:tyyppi :taulukko
                                                                                  :nimi ::data-sisalto
                                                                                  :luokat #{"piillotettu"}
                                                                                  :osat (mapv (fn [rahavaraus]
                                                                                                {:tyyppi :rivi
                                                                                                 :nimi rahavaraus
                                                                                                 :luokat #{"suunnitelma-rivi"}
                                                                                                 :osat [{:tyyppi :teksti
                                                                                                         :luokat #{"table-default" "solu-sisenna-2"}}
                                                                                                        {:tyyppi :ikoni
                                                                                                         :luokat #{"table-default" "keskita"}}
                                                                                                        {:tyyppi :ikoni
                                                                                                         :luokat #{"table-default" "keskita"
                                                                                                                   (when-not viimeinen-vuosi?
                                                                                                                     "harmaa-teksti")}}
                                                                                                        {:tyyppi :teksti
                                                                                                         :luokat #{"table-default" "harmaa-teksti"}
                                                                                                         :fmt yhteenveto-format}]})
                                                                                              rahavarausrivit)}]}))
                                                                     (sort-by t/toimenpiteiden-jarjestys t/toimenpiteet)))
                                                   (vec
                                                     (cons
                                                       {:tyyppi :rivi
                                                        :nimi ::hallinnolliset-toimenpiteet
                                                        :luokat #{"suunnitelma-rivi"}
                                                        :osat [{:tyyppi :nappi
                                                                :toiminnot {:on-click (fn [_] (mene-idlle "hallinnolliset-toimenpiteet"))}
                                                                :luokat #{"table-default" "linkki"}}
                                                               {:tyyppi :ikoni
                                                                :luokat #{"table-default" "keskita"}}
                                                               {:tyyppi :ikoni
                                                                :luokat #{"table-default" "keskita"
                                                                          (when-not viimeinen-vuosi?
                                                                            "harmaa-teksti")}}
                                                               {:tyyppi :teksti
                                                                :luokat #{"table-default" "harmaa-teksti"}
                                                                :fmt yhteenveto-format}]}
                                                       (map (fn [[_ id]]
                                                              {:tyyppi :rivi
                                                               :nimi id
                                                               :luokat #{"suunnitelma-rivi"}
                                                               :osat [{:tyyppi :nappi
                                                                       :toiminnot {:on-click (fn [_] (mene-idlle (str id "-osio")))}
                                                                       :luokat #{"table-default" "linkki" "solu-sisenna-1"}}
                                                                      {:tyyppi :ikoni
                                                                       :luokat #{"table-default" "keskita"}}
                                                                      {:tyyppi :ikoni
                                                                       :luokat #{"table-default" "keskita"
                                                                                 (when-not viimeinen-vuosi?
                                                                                   "harmaa-teksti")}}
                                                                      {:tyyppi :teksti
                                                                       :luokat #{"table-default" "harmaa-teksti"}
                                                                       :fmt yhteenveto-format}]})
                                                            t/hallinnollisten-idt)))))
                                   :taulukon-id taulukon-id
                                   :body-luokat #{"suunnitelma-ikonien-varit"}

                                   :root-asetus! (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :suunnitelmien-tila :grid] g)))
                                   :root-asetukset {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :suunnitelmien-tila :grid]))
                                                    :paivita! (fn [f]
                                                                (swap! tila/suunnittelu-kustannussuunnitelma
                                                                       (fn [tila]
                                                                         (update-in tila [:gridit :suunnitelmien-tila :grid] f))))}})]
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/suunnitelmien-tila-rajapinta
                                       (t/suunnitelmien-tila-dr kuluva-hoitokauden-numero)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [[:selite :kuluva-hoitovuosi :seuraava-hoitovuosi :ajanjakso]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/data ::hankintakustannukset] {:rajapinta :hankintakustannukset
                                                                                         :solun-polun-pituus 1
                                                                                         :datan-kasittely identity}
                                               [::g-pohjat/data ::hallinnolliset-toimenpiteet] {:rajapinta :hallinnolliset-toimenpiteet
                                                                                         :solun-polun-pituus 1
                                                                                         :datan-kasittely identity}}
                                              (reduce (fn [grid-kasittelijat toimenpide]
                                                        (merge grid-kasittelijat
                                                               {[::g-pohjat/data toimenpide ::toimenpide-yhteenveto] {:rajapinta (keyword (str "toimenpide-" toimenpide))
                                                                                                                      :solun-polun-pituus 1
                                                                                                                      :datan-kasittely identity}}
                                                               (reduce (fn [rahavarauksien-kasittelijat rahavaraus]
                                                                         (merge rahavarauksien-kasittelijat
                                                                                {[::g-pohjat/data toimenpide ::data-sisalto rahavaraus] {:rajapinta (keyword (str "rahavaraus-" rahavaraus "-" toimenpide))
                                                                                                                                         :solun-polun-pituus 1
                                                                                                                                         :datan-kasittely identity}}))
                                                                       {}
                                                                       (t/toimenpiteen-rahavaraukset toimenpide))))
                                                      {}
                                                      t/toimenpiteet)
                                              (reduce (fn [grid-kasittelijat [_ id]]
                                                        (merge grid-kasittelijat
                                                               {[::g-pohjat/data id] {:rajapinta (keyword (str "hallinnollinen-" id))
                                                                                      :solun-polun-pituus 1
                                                                                      :datan-kasittely identity}}))
                                                      {}
                                                      t/hallinnollisten-idt)))))

(defn suunnittellut-hankinnat-grid []
  (let [g (hankintojen-pohja "suunnittellut-hankinnat-taulukko"
                             (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :suunnittellut-hankinnat :grid] g)))
                             {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :suunnittellut-hankinnat :grid]))
                              :paivita! (fn [f]
                                          (swap! tila/suunnittelu-kustannussuunnitelma
                                                 (fn [tila]
                                                   (update-in tila [:gridit :suunnittellut-hankinnat :grid] f))))}
                             (fn [hoitokauden-numero rivit-alla arvo]
                               (when (and arvo (not (empty? rivit-alla)))
                                 (doseq [rivi rivit-alla
                                         :let [maara-solu (grid/get-in-grid rivi [1])
                                               piillotettu? (grid/piillotettu? rivi)]]
                                   (when-not piillotettu?
                                     (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnittellut-hankinnat!
                                                            :arvo arvo
                                                            :solu maara-solu
                                                            :ajettavat-jarejestykset #{:mapit}
                                                            :triggeroi-seuranta? true}
                                                           true
                                                           hoitokauden-numero
                                                           :hankinnat)))
                                 (e! (t/->TallennaHankintojenArvot :hankintakustannus
                                                                   hoitokauden-numero
                                                                   (vec (keep (fn [rivi]
                                                                                (let [maara-solu (grid/get-in-grid rivi [1])
                                                                                      piillotettu? (grid/piillotettu? rivi)]
                                                                                  (when-not piillotettu?
                                                                                    (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                                                                              rivit-alla))))
                                 (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))))
                             (fn [hoitokauden-numero arvo]
                               (when arvo
                                 (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnittellut-hankinnat!
                                                        :arvo arvo
                                                        :solu solu/*this*
                                                        :ajettavat-jarejestykset #{:mapit}
                                                        :triggeroi-seuranta? false}
                                                       false
                                                       hoitokauden-numero
                                                       :hankinnat)))
                             (fn [hoitokauden-numero arvo]
                               (grid/paivita-osa! solu/*this*
                                                  (fn [solu]
                                                    (assoc solu :nappi-nakyvilla? false)))
                               (when arvo
                                 (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnittellut-hankinnat!
                                                        :arvo arvo
                                                        :solu solu/*this*
                                                        :ajettavat-jarejestykset true
                                                        :triggeroi-seuranta? true}
                                                       true
                                                       hoitokauden-numero
                                                       :hankinnat)
                                 (e! (t/->TallennaHankintojenArvot :hankintakustannus hoitokauden-numero [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))
                                 (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))]
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/suunnittellut-hankinnat-rajapinta
                                       (t/suunnittellut-hankinnat-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                        :solun-polun-pituus 1
                                                                        :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                        :datan-kasittely (fn [yhteensa]
                                                                                           (mapv (fn [[_ nimi]]
                                                                                                   nimi)
                                                                                                 yhteensa))}}

                                              (reduce (fn [grid-kasittelijat hoitokauden-numero]
                                                        (merge grid-kasittelijat
                                                               {[::g-pohjat/data (dec hoitokauden-numero) ::data-yhteenveto] {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                                                                                                                              :solun-polun-pituus 1
                                                                                                                              :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                              :datan-kasittely (fn [yhteenveto]
                                                                                                                                                 (mapv (fn [[_ v]]
                                                                                                                                                         v)
                                                                                                                                                       yhteenveto))
                                                                                                                              :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                      (mapv (fn [osa]
                                                                                                                                                              (when (instance? solu/Syote osa)
                                                                                                                                                                :maara))
                                                                                                                                                            (grid/hae-grid osat :lapset)))}
                                                                [::g-pohjat/data (dec hoitokauden-numero) ::data-sisalto] {:rajapinta (keyword (str "suunnittellut-hankinnat-" hoitokauden-numero))
                                                                                                                           :solun-polun-pituus 2
                                                                                                                           :jarjestys [{:keyfn :aika
                                                                                                                                        :comp (fn [aika-1 aika-2]
                                                                                                                                                (pvm/ennen? aika-1 aika-2))}
                                                                                                                                       ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                                           :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                                              (mapv (fn [rivi]
                                                                                                                                                      (mapv (fn [[_ v]]
                                                                                                                                                              v)
                                                                                                                                                            rivi))
                                                                                                                                                    vuoden-hoidonjohtopalkkiot))
                                                                                                                           :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                                   (vec
                                                                                                                                                     (map-indexed (fn [i rivi]
                                                                                                                                                                    (vec
                                                                                                                                                                      (map-indexed (fn [j osa]
                                                                                                                                                                                     (when (or (instance? solu/Syote osa)
                                                                                                                                                                                               (instance? g-pohjat/SyoteTaytaAlas osa))
                                                                                                                                                                                       {:osa :maara
                                                                                                                                                                                        :aika (:aika (get data j))
                                                                                                                                                                                        :osan-paikka [i j]}))
                                                                                                                                                                                   (grid/hae-grid rivi :lapset))))
                                                                                                                                                                  (grid/hae-grid data-sisalto-grid :lapset))))}}))
                                                      {}
                                                      (range 1 6))))))

(defn hankinnat-laskutukseen-perustuen-grid []
  (let [g (hankintojen-pohja "suunnittellut-hankinnat-laskutukseen-perustuen-taulukko"
                                   (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :laskutukseen-perustuvat-hankinnat :grid] g)))
                                   {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :laskutukseen-perustuvat-hankinnat :grid]))
                                    :paivita! (fn [f]
                                                (swap! tila/suunnittelu-kustannussuunnitelma
                                                       (fn [tila]
                                                         (update-in tila [:gridit :laskutukseen-perustuvat-hankinnat :grid] f))))}
                                   (fn [hoitokauden-numero rivit-alla arvo]
                                     (when (and arvo (not (empty? rivit-alla)))
                                       (doseq [rivi rivit-alla
                                               :let [maara-solu (grid/get-in-grid rivi [1])
                                                     piillotettu? (grid/piillotettu? rivi)]]
                                         (when-not piillotettu?
                                           (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                                                  :arvo arvo
                                                                  :solu maara-solu
                                                                  :ajettavat-jarejestykset #{:mapit}
                                                                  :triggeroi-seuranta? true}
                                                                 true
                                                                 hoitokauden-numero
                                                                 :hankinnat)))
                                       (e! (t/->TallennaHankintojenArvot :laskutukseen-perustuva-hankinta
                                                                         hoitokauden-numero
                                                                         (vec (keep (fn [rivi]
                                                                                      (let [maara-solu (grid/get-in-grid rivi [1])
                                                                                            piillotettu? (grid/piillotettu? rivi)]
                                                                                        (when-not piillotettu?
                                                                                          (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                                                                                    rivit-alla))))
                                       (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))))
                                   (fn [hoitokauden-numero arvo]
                                     (when arvo
                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                                              :arvo arvo
                                                              :solu solu/*this*
                                                              :ajettavat-jarejestykset #{:mapit}
                                                              :triggeroi-seuranta? false}
                                                             false
                                                             hoitokauden-numero
                                                             :hankinnat)))
                                   (fn [hoitokauden-numero arvo]
                                     (grid/paivita-osa! solu/*this*
                                                        (fn [solu]
                                                          (assoc solu :nappi-nakyvilla? false)))
                                     (when arvo
                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                                              :arvo arvo
                                                              :solu solu/*this*
                                                              :ajettavat-jarejestykset true
                                                              :triggeroi-seuranta? true}
                                                             true
                                                             hoitokauden-numero
                                                             :hankinnat)
                                       (e! (t/->TallennaHankintojenArvot :laskutukseen-perustuva-hankinta hoitokauden-numero [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))
                                       (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))]
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/laskutukseen-perustuvat-hankinnat-rajapinta
                                       (t/laskutukseen-perustuvat-hankinnat-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                        :solun-polun-pituus 1
                                                                        :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                                        :datan-kasittely (fn [yhteensa]
                                                                                           (mapv (fn [[_ nimi]]
                                                                                                   nimi)
                                                                                                 yhteensa))}}

                                              (reduce (fn [grid-kasittelijat hoitokauden-numero]
                                                        (merge grid-kasittelijat
                                                               {[::g-pohjat/data (dec hoitokauden-numero) ::data-yhteenveto] {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                                                                                                                              :solun-polun-pituus 1
                                                                                                                              :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                              :datan-kasittely (fn [yhteenveto]
                                                                                                                                                 (mapv (fn [[_ v]]
                                                                                                                                                         v)
                                                                                                                                                       yhteenveto))
                                                                                                                              :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                      (mapv (fn [osa]
                                                                                                                                                              (when (instance? solu/Syote osa)
                                                                                                                                                                :maara))
                                                                                                                                                            (grid/hae-grid osat :lapset)))}
                                                                [::g-pohjat/data (dec hoitokauden-numero) ::data-sisalto] {:rajapinta (keyword (str "laskutukseen-perustuvat-hankinnat-" hoitokauden-numero))
                                                                                                                           :solun-polun-pituus 2
                                                                                                                           :jarjestys [{:keyfn :aika
                                                                                                                                        :comp (fn [aika-1 aika-2]
                                                                                                                                                (pvm/ennen? aika-1 aika-2))}
                                                                                                                                       ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                                           :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                                              (mapv (fn [rivi]
                                                                                                                                                      (mapv (fn [[_ v]]
                                                                                                                                                              v)
                                                                                                                                                            rivi))
                                                                                                                                                    vuoden-hoidonjohtopalkkiot))
                                                                                                                           :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                                   (vec
                                                                                                                                                     (map-indexed (fn [i rivi]
                                                                                                                                                                    (vec
                                                                                                                                                                      (map-indexed (fn [j osa]
                                                                                                                                                                                     (when (or (instance? solu/Syote osa)
                                                                                                                                                                                               (instance? g-pohjat/SyoteTaytaAlas osa))
                                                                                                                                                                                       {:osa :maara
                                                                                                                                                                                        :aika (:aika (get data j))
                                                                                                                                                                                        :osan-paikka [i j]}))
                                                                                                                                                                                   (grid/hae-grid rivi :lapset))))
                                                                                                                                                                  (grid/hae-grid data-sisalto-grid :lapset))))}}))
                                                      {}
                                                      (range 1 6))))))

(defn rahavarausten-grid []
  (let [nyt (pvm/nyt)
        dom-id "rahavaraukset-taulukko"
        tyyppi->tallennettava-asia (fn [tyyppi]
                                     (case tyyppi
                                       "vahinkojen-korjaukset" :kolmansien-osapuolten-aiheuttamat-vahingot
                                       "akillinen-hoitotyo" :akilliset-hoitotyot
                                       "muut-rahavaraukset" :rahavaraus-lupaukseen-1))
        g (grid/grid {:nimi ::root
                      :dom-id dom-id
                      :root-fn (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :rahavaraukset :grid]))
                      :paivita-root! (fn [f]
                                       (swap! tila/suunnittelu-kustannussuunnitelma
                                              (fn [tila]
                                                (update-in tila [:gridit :rahavaraukset :grid] f))))
                      :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                      :koko (-> konf/auto
                                (assoc-in [:rivi :nimet]
                                          {::otsikko 0
                                           ::data 1
                                           ::yhteenveto 2})
                                (assoc-in [:rivi :korkeudet] {0 "40px"
                                                              2 "40px"}))
                      :osat [(grid/rivi {:nimi ::otsikko
                                         :koko (-> konf/livi-oletuskoko
                                                   (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                  3 "1fr"})
                                                   (assoc-in [:sarake :oletus-leveys] "2fr"))
                                         :osat (mapv (fn [index]
                                                       (if (= 3 index)
                                                         (solu/teksti {:parametrit {:class #{"table-default" "table-default-header" "harmaa-teksti"}}})
                                                         (solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}})))
                                                     (range 4))
                                         :luokat #{"salli-ylipiirtaminen"}}
                                        [{:sarakkeet [0 4] :rivit [0 1]}])
                             (grid/dynamic-grid {:nimi ::data
                                                 :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                                 :koko konf/auto
                                                 :luokat #{"salli-ylipiirtaminen"}
                                                 :osien-maara-muuttui! (fn [g _] (t/paivita-raidat! (grid/osa-polusta (grid/root g) [::data])))
                                                 :toistettavan-osan-data (fn [{:keys [arvot valittu-toimenpide hoitokauden-numero]}]
                                                                           {:valittu-toimenpide valittu-toimenpide
                                                                            :hoitokauden-numero hoitokauden-numero
                                                                            :tyypit (mapv key arvot)})
                                                 :toistettava-osa (fn [{:keys [tyypit valittu-toimenpide hoitokauden-numero]}]
                                                                    (mapv (fn [tyyppi]
                                                                            (with-meta
                                                                              (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                                                                          :nimi ::datarivi
                                                                                          :koko (-> konf/auto
                                                                                                    (assoc-in [:rivi :nimet]
                                                                                                              {::data-yhteenveto 0
                                                                                                               ::data-sisalto 1})
                                                                                                    #_(assoc-in [:rivi :korkeudet] {0 "40px"}))
                                                                                          :luokat #{"salli-ylipiirtaminen"}
                                                                                          :osat [(with-meta (grid/rivi {:nimi ::data-yhteenveto
                                                                                                                        :koko {:seuraa {:seurattava ::otsikko
                                                                                                                                        :sarakkeet :sama
                                                                                                                                        :rivit :sama}}
                                                                                                                        :osat [(solu/laajenna {:aukaise-fn
                                                                                                                                               (fn [this auki?]
                                                                                                                                                 (let [rajapinta (keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero))]
                                                                                                                                                   (if auki?
                                                                                                                                                     (do
                                                                                                                                                       (rivi-kuukausifiltterilla! this
                                                                                                                                                                                  false
                                                                                                                                                                                  (keyword (str "rahavaraukset-kuukausitasolla-" tyyppi "?"))
                                                                                                                                                                                  [:gridit :rahavaraukset :kuukausitasolla? tyyppi]
                                                                                                                                                                                  [:. ::t/yhteenveto] {:rajapinta rajapinta
                                                                                                                                                                                                       :solun-polun-pituus 1
                                                                                                                                                                                                       :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                                                                                                       :datan-kasittely (fn [yhteenveto]
                                                                                                                                                                                                                          (mapv (fn [[_ v]]
                                                                                                                                                                                                                                  v)
                                                                                                                                                                                                                                yhteenveto))
                                                                                                                                                                                                       :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                                                                                               (mapv (fn [osa]
                                                                                                                                                                                                                                       (when (instance? solu/Syote osa)
                                                                                                                                                                                                                                         {:osa :maara
                                                                                                                                                                                                                                          :tyyppi tyyppi}))
                                                                                                                                                                                                                                     (grid/hae-grid osat :lapset)))}
                                                                                                                                                                                  [:. ::t/valinta] {:rajapinta (keyword (str "rahavaraukset-kuukausitasolla-" tyyppi "?"))
                                                                                                                                                                                                    :solun-polun-pituus 1
                                                                                                                                                                                                    :datan-kasittely (fn [kuukausitasolla?]
                                                                                                                                                                                                                       [kuukausitasolla? nil nil nil])})
                                                                                                                                                       (grid/triggeroi-tapahtuma! this :rahavaraukset-disablerivit))
                                                                                                                                                     (rivi-ilman-kuukausifiltteria! this
                                                                                                                                                                                    [:.. ::data-yhteenveto] {:rajapinta rajapinta
                                                                                                                                                                                                             :solun-polun-pituus 1
                                                                                                                                                                                                             :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                                                                                                                             :datan-kasittely (fn [yhteenveto]
                                                                                                                                                                                                                                (mapv (fn [[_ v]]
                                                                                                                                                                                                                                        v)
                                                                                                                                                                                                                                      yhteenveto))
                                                                                                                                                                                                             :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                                                                                                     (mapv (fn [osa]
                                                                                                                                                                                                                                             (when (instance? solu/Syote osa)
                                                                                                                                                                                                                                               {:osa :maara
                                                                                                                                                                                                                                                :tyyppi tyyppi}))
                                                                                                                                                                                                                                           (grid/hae-grid osat :lapset)))})))
                                                                                                                                                 (t/laajenna-solua-klikattu this auki? dom-id [::data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                                                                                                               :auki-alussa? false
                                                                                                                                               :parametrit {:class #{"table-default" "lihavoitu"}}})
                                                                                                                               (solu/syote {:toiminnot {:on-change (fn [arvo]
                                                                                                                                                                     (when arvo
                                                                                                                                                                       (when esta-blur_
                                                                                                                                                                         (set! esta-blur_ false))
                                                                                                                                                                       (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset-yhteenveto!
                                                                                                                                                                                              :arvo arvo
                                                                                                                                                                                              :solu solu/*this*
                                                                                                                                                                                              :ajettavat-jarejestykset #{:mapit}})))
                                                                                                                                                        :on-focus (fn [event]
                                                                                                                                                                    (let [arvo (.. event -target -value)]
                                                                                                                                                                      (when (= arvo t/vaihtelua-teksti)
                                                                                                                                                                        (set! esta-blur_ true)
                                                                                                                                                                        (set! (.. event -target -value) nil))))
                                                                                                                                                        :on-blur (fn [arvo]
                                                                                                                                                                   (when arvo
                                                                                                                                                                     (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                                            :arvo arvo
                                                                                                                                                                                            :solu solu/*this*
                                                                                                                                                                                            :ajettavat-jarejestykset true
                                                                                                                                                                                            :triggeroi-seuranta? true}
                                                                                                                                                                                           true)
                                                                                                                                                                     (let [vanhempiosa (grid/osa-polusta solu/*this* [:.. :..])
                                                                                                                                                                           tallennettavien-arvojen-osat (if (= ::datarivi (grid/hae-osa vanhempiosa :nimi))
                                                                                                                                                                                                          (grid/hae-grid (grid/osa-polusta vanhempiosa [1]) :lapset)
                                                                                                                                                                                                          (grid/hae-grid (grid/osa-polusta vanhempiosa [:.. 1]) :lapset))
                                                                                                                                                                           tunnisteet (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                                                                                                                                              :tunniste-rajapinnan-dataan)
                                                                                                                                                                                            tallennettavien-arvojen-osat)]
                                                                                                                                                                       (e! (t/->TallennaKustannusarvoitu (tyyppi->tallennettava-asia tyyppi) tunnisteet))
                                                                                                                                                                       (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
                                                                                                                                                        :on-key-down (fn [event]
                                                                                                                                                                       (when (= "Enter" (.. event -key))
                                                                                                                                                                         (.. event -target blur)))}
                                                                                                                                            :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                         {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                             :on-blur [:positiivinen-numero
                                                                                                                                                                       {:eventin-arvo {:f poista-tyhjat}}
                                                                                                                                                                       {:oma {:f esta-blur-ja-lisaa-vaihtelua-teksti}}]}
                                                                                                                                            :parametrit {:size 2
                                                                                                                                                         :class #{"input-default"}}
                                                                                                                                            :fmt yhteenveto-format
                                                                                                                                            :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                                                                                               (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                             :fmt yhteenveto-format})
                                                                                                                               (solu/teksti {:parametrit {:class #{"table-default" "harmaa-teksti"}}
                                                                                                                                             :fmt yhteenveto-format})]
                                                                                                                        :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                       [{:sarakkeet [0 4] :rivit [0 1]}])
                                                                                                            {:key (str tyyppi "-yhteenveto")})
                                                                                                 (with-meta
                                                                                                   (grid/taulukko {:nimi ::data-sisalto
                                                                                                                   :alueet [{:sarakkeet [0 1] :rivit [0 12]}]
                                                                                                                   :koko konf/auto
                                                                                                                   :luokat #{"piillotettu" "salli-ylipiirtaminen"}}
                                                                                                                  (mapv
                                                                                                                    (fn [index]
                                                                                                                      (with-meta
                                                                                                                        (grid/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                                                                                    :sarakkeet :sama
                                                                                                                                                    :rivit :sama}}
                                                                                                                                    :osat [(with-meta
                                                                                                                                             (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                           :fmt (fn [paivamaara]
                                                                                                                                                                  (when paivamaara
                                                                                                                                                                    (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                                                                                                          mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                                                                                                       (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                                                                                                           (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                                                                                                      (if mennyt?
                                                                                                                                                                        (str teksti " (mennyt)")
                                                                                                                                                                        teksti))))})
                                                                                                                                             {:key (str tyyppi "-" index "-otsikko")})
                                                                                                                                           (with-meta
                                                                                                                                             (g-pohjat/->SyoteTaytaAlas (gensym "rahavaraus")
                                                                                                                                                                        false
                                                                                                                                                                        (partial tayta-alas-napin-toiminto
                                                                                                                                                                                 :aseta-rahavaraukset!
                                                                                                                                                                                 (tyyppi->tallennettava-asia tyyppi)
                                                                                                                                                                                 1)
                                                                                                                                                                        {:on-change (fn [arvo]
                                                                                                                                                                                      (when arvo
                                                                                                                                                                                        (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                                                               :arvo arvo
                                                                                                                                                                                                               :solu solu/*this*
                                                                                                                                                                                                               :ajettavat-jarejestykset #{:mapit}}
                                                                                                                                                                                                              false)))
                                                                                                                                                                         :on-focus (fn [_]
                                                                                                                                                                                     (grid/paivita-osa! solu/*this*
                                                                                                                                                                                                        (fn [solu]
                                                                                                                                                                                                          (assoc solu :nappi-nakyvilla? true))))
                                                                                                                                                                         :on-blur (fn [arvo]
                                                                                                                                                                                    (grid/paivita-osa! solu/*this*
                                                                                                                                                                                                       (fn [solu]
                                                                                                                                                                                                         (assoc solu :nappi-nakyvilla? false)))
                                                                                                                                                                                    (when arvo
                                                                                                                                                                                      (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                                                             :arvo arvo
                                                                                                                                                                                                             :solu solu/*this*
                                                                                                                                                                                                             :ajettavat-jarejestykset true
                                                                                                                                                                                                             :triggeroi-seuranta? true}
                                                                                                                                                                                                            true)
                                                                                                                                                                                      (e! (t/->TallennaKustannusarvoitu (tyyppi->tallennettava-asia tyyppi) [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))
                                                                                                                                                                                      (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))))
                                                                                                                                                                         :on-key-down (fn [event]
                                                                                                                                                                                        (when (= "Enter" (.. event -key))
                                                                                                                                                                                          (.. event -target blur)))}
                                                                                                                                                                        {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                                     {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                                         :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                                                                        {:size 2
                                                                                                                                                                         :class #{"input-default"}}
                                                                                                                                                                        summa-formatointi-uusi
                                                                                                                                                                        summa-formatointi-aktiivinen)
                                                                                                                                             {:key (str tyyppi "-" index "-maara")})
                                                                                                                                           (with-meta
                                                                                                                                             (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                           :fmt summa-formatointi})
                                                                                                                                             {:key (str tyyppi "-" index "-yhteensa")})
                                                                                                                                           (with-meta
                                                                                                                                             (solu/teksti {:parametrit {:class #{"table-default"}}})
                                                                                                                                             {:key (str tyyppi "-" index "-indeksikorjattu")})]
                                                                                                                                    :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                                   [{:sarakkeet [0 4] :rivit [0 1]}])
                                                                                                                        {:key (str tyyppi "-" index)}))
                                                                                                                    (range 12)))
                                                                                                   {:key (str tyyppi "-data-sisalto")})]})
                                                                              {:key tyyppi}))
                                                                          tyypit))})
                             (grid/rivi {:nimi ::yhteenveto
                                         :koko {:seuraa {:seurattava ::otsikko
                                                         :sarakkeet :sama
                                                         :rivit :sama}}
                                         :osat (conj (vec (repeatedly 2 (fn []
                                                                          (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}}))))
                                                     (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}
                                                                   :fmt yhteenveto-format})
                                                     (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum" "harmaa-teksti"}}
                                                                   :fmt yhteenveto-format}))}
                                        [{:sarakkeet [0 4] :rivit [0 1]}])]})]
    (e! (tuck-apurit/->MuutaTila [:gridit :rahavaraukset :grid] g))
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/rahavarausten-rajapinta
                                       (t/rahavarausten-dr)
                                       {[::otsikko] {:rajapinta :rahavaraukset-otsikot
                                                     :solun-polun-pituus 1
                                                     :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                     :datan-kasittely (fn [otsikot]
                                                                        (mapv (fn [otsikko]
                                                                                otsikko)
                                                                              (vals otsikot)))}
                                        [::yhteenveto] {:rajapinta :rahavaraukset-yhteensa
                                                        :solun-polun-pituus 1
                                                        :jarjestys [[:nimi :maara :yhteensa :indeksikorjattu]]
                                                        :datan-kasittely (fn [yhteensa]
                                                                           (mapv (fn [[_ nimi]]
                                                                                   nimi)
                                                                                 yhteensa))}
                                        [::data] {:rajapinta :rahavaraukset
                                                  :solun-polun-pituus 0
                                                  :jarjestys [{:keyfn key
                                                               :comp (fn [a b]
                                                                       (compare (t/rahavaraukset-jarjestys a) (t/rahavaraukset-jarjestys b)))}]
                                                  :luonti (fn [{:keys [arvot valittu-toimenpide hoitokauden-numero]}]
                                                            (map (fn [[tyyppi _]]
                                                                   (when-not (nil? tyyppi)
                                                                     (let [index (dec (get t/rahavaraukset-jarjestys tyyppi))]
                                                                       {[:. index ::data-yhteenveto] {:rajapinta (keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero))
                                                                                                      :solun-polun-pituus 1
                                                                                                      :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                                                      :datan-kasittely (fn [yhteenveto]
                                                                                                                         (mapv (fn [[_ v]]
                                                                                                                                 v)
                                                                                                                               yhteenveto))
                                                                                                      :tunnisteen-kasittely (fn [osat _]
                                                                                                                              (mapv (fn [osa]
                                                                                                                                      (when (instance? solu/Syote osa)
                                                                                                                                        {:osa :maara
                                                                                                                                         :tyyppi tyyppi}))
                                                                                                                                    (grid/hae-grid osat :lapset)))}
                                                                        [:. index ::data-sisalto] {:rajapinta (keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero))
                                                                                                   :solun-polun-pituus 2
                                                                                                   :jarjestys [{:keyfn :aika
                                                                                                                :comp (fn [aika-1 aika-2]
                                                                                                                        (when (and aika-1 aika-2)
                                                                                                                          (pvm/ennen? aika-1 aika-2)))}
                                                                                                               ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                                                                                                   :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                                      (mapv (fn [rivi]
                                                                                                                              (mapv (fn [[_ v]]
                                                                                                                                      v)
                                                                                                                                    rivi))
                                                                                                                            vuoden-hoidonjohtopalkkiot))
                                                                                                   :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                           (vec
                                                                                                                             (map-indexed (fn [i rivi]
                                                                                                                                            (vec
                                                                                                                                              (map-indexed (fn [j osa]
                                                                                                                                                             (when (or (instance? solu/Syote osa)
                                                                                                                                                                       (instance? g-pohjat/SyoteTaytaAlas osa))
                                                                                                                                                               {:osa :maara
                                                                                                                                                                :tyyppi tyyppi
                                                                                                                                                                :osan-paikka [i j]}))
                                                                                                                                                           (grid/hae-grid rivi :lapset))))
                                                                                                                                          (grid/hae-grid data-sisalto-grid :lapset))))}})))
                                                                 arvot))
                                                  :datan-kasittely (fn [arvot]
                                                                     {:arvot arvot
                                                                      :valittu-toimenpide (:valittu-toimenpide (meta arvot))
                                                                      :hoitokauden-numero (:hoitokauden-numero (meta arvot))})}})
    (grid/grid-tapahtumat g
                          tila/suunnittelu-kustannussuunnitelma
                          {:rahavaraukset-disablerivit {:polut [[:gridit :rahavaraukset :kuukausitasolla?]]
                                                        :toiminto! (fn [g _ kuukausitasolla-kaikki-tyypit]
                                                                     (doseq [[tyyppi kuukausitasolla?] kuukausitasolla-kaikki-tyypit
                                                                             :let [index (dec (get t/rahavaraukset-jarjestys tyyppi))]]
                                                                       (maara-solujen-disable! (grid/get-in-grid g [::data index ::data-sisalto])
                                                                                               (not kuukausitasolla?))
                                                                       (maara-solujen-disable! (grid/get-in-grid g [::data index ::data-yhteenveto])
                                                                                               kuukausitasolla?)))}})))

(defn johto-ja-hallintokorvaus-laskulla-grid []
  (let [nyt (pvm/nyt)
        taulukon-id "johto-ja-hallintokorvaus-laskulla-taulukko"
        yhteenveto-grid-rajapinta-asetukset (fn [toimenkuva maksukausi data-koskee-ennen-urakkaa?]
                                              (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                                                {:rajapinta (keyword (str "yhteenveto" yksiloiva-nimen-paate))
                                                 :solun-polun-pituus 1
                                                 :jarjestys [^{:nimi :mapit} [:toimenkuva :tunnit :tuntipalkka :yhteensa :kk-v]]
                                                 :datan-kasittely (fn [yhteenveto]
                                                                    (mapv (fn [[_ v]]
                                                                            v)
                                                                          yhteenveto))
                                                 :tunnisteen-kasittely (fn [osat data]
                                                                         (second
                                                                           (reduce (fn [[loytynyt? tunnisteet] osa]
                                                                                     (let [syote-osa? (instance? solu/Syote osa)
                                                                                           osa (when syote-osa?
                                                                                                 (if loytynyt?
                                                                                                   :tuntipalkka
                                                                                                   :tunnit))
                                                                                           tunniste {:osa osa :toimenkuva toimenkuva :maksukausi maksukausi
                                                                                                     :data-koskee-ennen-urakkaa? data-koskee-ennen-urakkaa?
                                                                                                     :osa-kuukaudesta-vaikuttaa? (= :tunnit osa)}]
                                                                                       [(or loytynyt? syote-osa?)
                                                                                        (conj tunnisteet tunniste)]))
                                                                                   [false []]
                                                                                   (grid/hae-grid osat :lapset))))}))
        muokkausrivien-rajapinta-asetukset (fn [nimi]
                                             {:rajapinta (keyword (str "yhteenveto-" nimi))
                                              :solun-polun-pituus 1
                                              :jarjestys [^{:nimi :mapit} [:toimenkuva :tunnit :tuntipalkka :yhteensa :maksukausi]]
                                              :datan-kasittely (fn [yhteenveto]
                                                                 (mapv (fn [[_ v]]
                                                                         v)
                                                                       yhteenveto))
                                              :tunnisteen-kasittely (fn [_ _]
                                                                      (mapv (fn [index]
                                                                              (assoc
                                                                                (case index
                                                                                  0 {:osa :toimenkuva}
                                                                                  1 {:osa :tunnit}
                                                                                  2 {:osa :tuntipalkka}
                                                                                  3 {:osa :yhteensa}
                                                                                  4 {:osa :maksukausi})
                                                                                :omanimi nimi))
                                                                            (range 5)))})
        blur-tallenna! (fn [tallenna-kaikki? etsittava-osa]
                         (if tallenna-kaikki?
                           (e! (t/->TallennaJohtoJaHallintokorvaukset :johto-ja-hallintokorvaus (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                                                        :tunniste-rajapinnan-dataan)
                                                                                                      (grid/hae-grid
                                                                                                        (get (grid/hae-grid (grid/etsi-osa (grid/root solu/*this*) etsittava-osa)
                                                                                                                            :lapset)
                                                                                                             1)
                                                                                                        :lapset))))
                           (e! (t/->TallennaJohtoJaHallintokorvaukset :johto-ja-hallintokorvaus [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))))
        nappia-painettu-tallenna! (fn [rivit-alla]
                                    (e! (t/->TallennaJohtoJaHallintokorvaukset :johto-ja-hallintokorvaus
                                                                               (vec (keep (fn [rivi]
                                                                                            (let [haettu-solu (grid/get-in-grid rivi [1])
                                                                                                  piillotettu? (grid/piillotettu? rivi)]
                                                                                              (when-not piillotettu?
                                                                                                (grid/solun-asia haettu-solu :tunniste-rajapinnan-dataan))))
                                                                                          rivit-alla)))))
        rividisable! (fn [g index kuukausitasolla?]
                       (maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data index ::data-sisalto])
                                               (not kuukausitasolla?))
                       (maara-solujen-disable! (if-let [osa (grid/get-in-grid g [::g-pohjat/data index ::data-yhteenveto 0 1])]
                                                 osa
                                                 (grid/get-in-grid g [::g-pohjat/data index ::data-yhteenveto 1]))
                                               kuukausitasolla?))
        vakiorivit (mapv (fn [{:keys [toimenkuva maksukausi hoitokaudet] :as toimenkuva-kuvaus}]
                           (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                             (if (t/toimenpide-koskee-ennen-urakkaa? hoitokaudet)
                               {:tyyppi :rivi
                                :nimi ::data-yhteenveto
                                :osat [{:tyyppi :teksti
                                        :luokat #{"table-default"}}
                                       (syote-solu {:nappi? false :fmt yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                    :blur-tallenna! (partial blur-tallenna! false nil)})
                                       (syote-solu {:nappi? false :fmt yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                    :blur-tallenna! (partial blur-tallenna! false nil)})
                                       {:tyyppi :teksti
                                        :luokat #{"table-default"}
                                        :fmt yhteenveto-format}
                                       {:tyyppi :teksti
                                        :luokat #{"table-default"}
                                        :fmt (fn [teksti]
                                               (if (nil? teksti)
                                                 ""
                                                 (let [sisaltaa-erottimen? (boolean (re-find #",|\." (str teksti)))]
                                                   (if sisaltaa-erottimen?
                                                     (fmt/desimaaliluku (clj-str/replace (str teksti) "," ".") 1 true)
                                                     teksti))))}]}
                               {:tyyppi :taulukko
                                :nimi (str toimenkuva "-" maksukausi "-taulukko")
                                :osat [{:tyyppi :rivi
                                        :nimi ::data-yhteenveto
                                        :osat [{:tyyppi :laajenna
                                                :aukaise-fn (fn [this auki?]
                                                              (if auki?
                                                                (rivi-kuukausifiltterilla! this
                                                                                           true
                                                                                           (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate))
                                                                                           [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi]
                                                                                           [:. ::t/yhteenveto] (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi false)
                                                                                           [:. ::t/valinta] {:rajapinta (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate))
                                                                                                             :solun-polun-pituus 1
                                                                                                             :datan-kasittely (fn [kuukausitasolla?]
                                                                                                                                [kuukausitasolla? nil nil nil])})
                                                                (do
                                                                  (rivi-ilman-kuukausifiltteria! this
                                                                                                 [:.. ::data-yhteenveto] (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi false))
                                                                  (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi] false))))
                                                              (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                :auki-alussa? false
                                                :luokat #{"table-default"}}
                                               (syote-solu {:nappi? false :fmt yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                            :blur-tallenna! (partial blur-tallenna! true (str toimenkuva "-" maksukausi "-taulukko"))})
                                               (syote-solu {:nappi? false :fmt yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                            :blur-tallenna! (partial blur-tallenna! true (str toimenkuva "-" maksukausi "-taulukko"))})
                                               {:tyyppi :teksti
                                                :luokat #{"table-default"}
                                                :fmt yhteenveto-format}
                                               {:tyyppi :teksti
                                                :luokat #{"table-default"}
                                                :fmt (fn [teksti]
                                                       (if (nil? teksti)
                                                         ""
                                                         (let [sisaltaa-erottimen? (boolean (re-find #",|\." (str teksti)))]
                                                           (if sisaltaa-erottimen?
                                                             (fmt/desimaaliluku (clj-str/replace (str teksti) "," ".") 1 true)
                                                             teksti))))}]}
                                       {:tyyppi :taulukko
                                        :nimi ::data-sisalto
                                        :luokat #{"piillotettu"}
                                        :osat (vec (repeatedly (t/kk-v-toimenkuvan-kuvaukselle toimenkuva-kuvaus)
                                                               (fn []
                                                                 {:tyyppi :rivi
                                                                  :osat [{:tyyppi :teksti
                                                                          :luokat #{"table-default"}
                                                                          :fmt (fn [paivamaara]
                                                                                 (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                       mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                    (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                        (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                   (if mennyt?
                                                                                     (str teksti " (mennyt)")
                                                                                     teksti)))}
                                                                         (syote-solu {:nappi? true :fmt yhteenveto-format :paivitettava-asia :aseta-tunnit!
                                                                                      :solun-index 1
                                                                                      :blur-tallenna! (partial blur-tallenna! false (str toimenkuva "-" maksukausi "-taulukko"))
                                                                                      :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                                         {:tyyppi :tyhja}
                                                                         {:tyyppi :tyhja}
                                                                         {:tyyppi :tyhja}]})))}]})))
                         t/johto-ja-hallintokorvaukset-pohjadata)
        muokattavat-rivit (mapv (fn [index]
                                  (let [rivin-nimi (t/jh-omienrivien-nimi index)]
                                    {:tyyppi :taulukko
                                     :nimi rivin-nimi
                                     :osat [{:tyyppi :rivi
                                             :nimi ::data-yhteenveto
                                             :osat [{:tyyppi :oma
                                                     ;[aukaise-fn auki-alussa? toiminnot kayttaytymiset parametrit fmt fmt-aktiivinen]
                                                     :constructor (fn [_]
                                                                    (laajenna-syote (fn [this auki?]
                                                                                      (if auki?
                                                                                        (rivi-kuukausifiltterilla! this
                                                                                                                   true
                                                                                                                   (keyword (str "kuukausitasolla?-" rivin-nimi))
                                                                                                                   [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? rivin-nimi]
                                                                                                                   [:. ::t/yhteenveto] (muokkausrivien-rajapinta-asetukset rivin-nimi)
                                                                                                                   [:. ::t/valinta] {:rajapinta (keyword (str "kuukausitasolla?-" rivin-nimi))
                                                                                                                                     :solun-polun-pituus 1
                                                                                                                                     :datan-kasittely (fn [kuukausitasolla?]
                                                                                                                                                        [kuukausitasolla? nil nil nil])})
                                                                                        (do
                                                                                          (rivi-ilman-kuukausifiltteria! this
                                                                                                                         [:.. ::data-yhteenveto] (muokkausrivien-rajapinta-asetukset rivin-nimi))
                                                                                          (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? rivin-nimi] false))))
                                                                                      (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                                                    false
                                                                                    {:on-change (fn [arvo]
                                                                                                  (when arvo
                                                                                                    (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                                                           :arvo arvo
                                                                                                                           :solu solu/*this*
                                                                                                                           :ajettavat-jarejestykset #{:mapit}}
                                                                                                                          false)))
                                                                                     :on-blur (fn [arvo]
                                                                                                (when arvo
                                                                                                  (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                                                         :arvo arvo
                                                                                                                         :solu solu/*this*
                                                                                                                         :ajettavat-jarejestykset true
                                                                                                                         :triggeroi-seuranta? true}
                                                                                                                        true)
                                                                                                  (e! (t/->TallennaToimenkuva rivin-nimi))))
                                                                                     :on-key-down (fn [event]
                                                                                                    (when (= "Enter" (.. event -key))
                                                                                                      (.. event -target blur)))}
                                                                                    {:on-change [:eventin-arvo]
                                                                                     :on-blur [:eventin-arvo]}
                                                                                    {:class #{"input-default"}}
                                                                                    identity
                                                                                    identity))
                                                     :auki-alussa? false
                                                     :luokat #{"table-default"}}
                                                    (syote-solu {:nappi? false :fmt yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                                 :blur-tallenna! (partial blur-tallenna! true rivin-nimi)
                                                                 :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                    (syote-solu {:nappi? false :fmt yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                                 :blur-tallenna! (partial blur-tallenna! true rivin-nimi)
                                                                 :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                    {:tyyppi :teksti
                                                     :luokat #{"table-default"}
                                                     :fmt yhteenveto-format}
                                                    {:tyyppi :pudotusvalikko
                                                     :valitse-fn (fn [maksukausi]
                                                                   (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                          :arvo maksukausi
                                                                                          :solu solu/*this*
                                                                                          :ajettavat-jarejestykset true
                                                                                          :triggeroi-seuranta? true}
                                                                                         false))
                                                     :format-fn (fn [teksti]
                                                                  (case teksti
                                                                    :kesa "5"
                                                                    :talvi "7"
                                                                    :molemmat "12"))
                                                     :rivin-haku (fn [pudotusvalikko]
                                                                   (grid/osa-polusta pudotusvalikko [:.. :..]))
                                                     :vayla-tyyli? true
                                                     :vaihtoehdot t/kk-v-valinnat}]}
                                            {:tyyppi :taulukko
                                             :nimi ::data-sisalto
                                             :luokat #{"piillotettu"}
                                             :osat (vec (repeatedly 12
                                                                    (fn []
                                                                      {:tyyppi :rivi
                                                                       :osat [{:tyyppi :teksti
                                                                               :luokat #{"table-default"}
                                                                               :fmt (fn [paivamaara]
                                                                                      (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                            mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                         (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                             (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                        (if mennyt?
                                                                                          (str teksti " (mennyt)")
                                                                                          teksti)))}
                                                                              (syote-solu {:nappi? true :fmt yhteenveto-format :paivitettava-asia :aseta-tunnit!
                                                                                           :solun-index 1
                                                                                           :blur-tallenna! (partial blur-tallenna! false rivin-nimi)
                                                                                           :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                                              {:tyyppi :tyhja}
                                                                              {:tyyppi :tyhja}
                                                                              {:tyyppi :tyhja}]})))}]}))
                                (range 1 (inc t/jh-korvausten-omiariveja-lkm)))
        g (g-pohjat/uusi-taulukko {:header [{:tyyppi :teksti
                                             :leveys 4
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 3
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 3
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 3
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                            {:tyyppi :teksti
                                             :leveys 1
                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}]
                                   :body (vec (concat vakiorivit
                                                      muokattavat-rivit))
                                   :taulukon-id taulukon-id
                                   :root-luokat #{"salli-ylipiirtaminen"}
                                   :root-asetus! (fn [g]
                                                   (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaukset :grid] g)))
                                   :root-asetukset {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :johto-ja-hallintokorvaukset :grid]))
                                                    :paivita! (fn [f]
                                                                (swap! tila/suunnittelu-kustannussuunnitelma
                                                                       (fn [tila]
                                                                         (update-in tila [:gridit :johto-ja-hallintokorvaukset :grid] f))))}})
        [vakio-viimeinen-index vakiokasittelijat] (reduce (fn [[index grid-kasittelijat] {:keys [toimenkuva maksukausi hoitokaudet]}]
                                                             (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                                                               [(inc index) (merge grid-kasittelijat
                                                                                   (if (t/toimenpide-koskee-ennen-urakkaa? hoitokaudet)
                                                                                     {[::g-pohjat/data ::data-yhteenveto] (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi true)}
                                                                                     {[::g-pohjat/data index ::data-yhteenveto] (yhteenveto-grid-rajapinta-asetukset toimenkuva maksukausi false)
                                                                                      [::g-pohjat/data index ::data-sisalto] {:rajapinta (keyword (str "johto-ja-hallintokorvaus" yksiloiva-nimen-paate))
                                                                                                                              :solun-polun-pituus 2
                                                                                                                              :jarjestys [{:keyfn :aika
                                                                                                                                           :comp (fn [aika-1 aika-2]
                                                                                                                                                   (pvm/ennen? aika-1 aika-2))}
                                                                                                                                          ^{:nimi :mapit} [:aika :tunnit :tuntipalkka :yhteensa :kk-v]]
                                                                                                                              :datan-kasittely (fn [vuoden-jh-korvaukset]
                                                                                                                                                 (mapv (fn [rivi]
                                                                                                                                                         (mapv (fn [[_ v]]
                                                                                                                                                                 v)
                                                                                                                                                               rivi))
                                                                                                                                                       vuoden-jh-korvaukset))
                                                                                                                              :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                                                                      (vec
                                                                                                                                                        (map-indexed (fn [i rivi]
                                                                                                                                                                       (vec
                                                                                                                                                                         (map-indexed (fn [j osa]
                                                                                                                                                                                        (when (instance? g-pohjat/SyoteTaytaAlas osa)
                                                                                                                                                                                          {:osa :tunnit
                                                                                                                                                                                           :osan-paikka [i j]
                                                                                                                                                                                           :toimenkuva toimenkuva
                                                                                                                                                                                           :maksukausi maksukausi}))
                                                                                                                                                                                      (grid/hae-grid rivi :lapset))))
                                                                                                                                                                     (grid/hae-grid data-sisalto-grid :lapset))))}}))]))
                                                           [0 {}]
                                                           t/johto-ja-hallintokorvaukset-pohjadata)
        muokkauskasittelijat (second
                               (reduce (fn [[rivi-index grid-kasittelijat] nimi-index]
                                         (let [rivin-nimi (t/jh-omienrivien-nimi nimi-index)]
                                           [(inc rivi-index) (merge grid-kasittelijat
                                                                    {[::g-pohjat/data rivi-index ::data-yhteenveto] (muokkausrivien-rajapinta-asetukset rivin-nimi)
                                                                     [::g-pohjat/data rivi-index ::data-sisalto] {:rajapinta (keyword (str "johto-ja-hallintokorvaus-" rivin-nimi))
                                                                                                                  :solun-polun-pituus 2
                                                                                                                  :jarjestys [{:keyfn :aika
                                                                                                                               :comp (fn [aika-1 aika-2]
                                                                                                                                       (pvm/ennen? aika-1 aika-2))}
                                                                                                                              ^{:nimi :mapit} [:aika :tunnit :tuntipalkka :yhteensa :maksukausi]]
                                                                                                                  :datan-kasittely (fn [vuoden-jh-korvaukset]
                                                                                                                                     (mapv (fn [rivi]
                                                                                                                                             (mapv (fn [[_ v]]
                                                                                                                                                     v)
                                                                                                                                                   rivi))
                                                                                                                                           vuoden-jh-korvaukset))
                                                                                                                  :tunnisteen-kasittely (fn [data-sisalto-grid _]
                                                                                                                                          (vec
                                                                                                                                            (map-indexed (fn [i rivi]
                                                                                                                                                           (vec
                                                                                                                                                             (map (fn [j osa]
                                                                                                                                                                    (when (instance? g-pohjat/SyoteTaytaAlas osa)
                                                                                                                                                                      {:osa :tunnit
                                                                                                                                                                       :osan-paikka [i j]
                                                                                                                                                                       :omanimi rivin-nimi}))
                                                                                                                                                                  (range)
                                                                                                                                                                  (grid/hae-grid rivi :lapset))))
                                                                                                                                                         (grid/hae-grid data-sisalto-grid :lapset))))}})]))
                                       [vakio-viimeinen-index {}]
                                       (range 1 (inc t/jh-korvausten-omiariveja-lkm))))]

    (grid/rajapinta-grid-yhdistaminen! g
                                       t/johto-ja-hallintokorvaus-rajapinta
                                       (t/johto-ja-hallintokorvaus-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [^{:nimi :mapit} [:toimenkuva :tunnit :tuntipalkka :yhteensa :kk-v]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}}

                                              vakiokasittelijat
                                              muokkauskasittelijat))
    (grid/grid-tapahtumat g
                          tila/suunnittelu-kustannussuunnitelma
                          (merge {:johto-ja-hallintokorvaukset-disablerivit {:polut [[:gridit :johto-ja-hallintokorvaukset :kuukausitasolla?]]
                                                                             :toiminto! (fn [g _ kuukausitasolla-kaikki-toimenkuvat]
                                                                                          (doseq [[toimenkuva maksukaudet-kuukausitasolla?] kuukausitasolla-kaikki-toimenkuvat]
                                                                                            ;; Jos totta, niin kyseessä on oma/itsetäytettävärivi
                                                                                            (if-let [itsetaytettavan-rivinumero (and (string? toimenkuva) (re-find #"\d$" toimenkuva))]
                                                                                              (rividisable! g
                                                                                                            (dec (+ (count t/johto-ja-hallintokorvaukset-pohjadata)
                                                                                                                    (js/Number itsetaytettavan-rivinumero)))
                                                                                                            maksukaudet-kuukausitasolla?)
                                                                                              (doseq [[maksukausi kuukausitasolla?] maksukaudet-kuukausitasolla?
                                                                                                      :let [index (first (keep-indexed (fn [index jh-pohjadata]
                                                                                                                                         (when (and (= toimenkuva (:toimenkuva jh-pohjadata))
                                                                                                                                                    (= maksukausi (:maksukausi jh-pohjadata)))
                                                                                                                                           index))
                                                                                                                                       t/johto-ja-hallintokorvaukset-pohjadata))]]
                                                                                                (rividisable! g index kuukausitasolla?)))))}}
                                 (reduce (fn [polut jarjestysnumero]
                                           (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                             (merge polut
                                                    {(keyword "piillota-itsetaytettyja-riveja-" nimi) {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :maksukausi]]
                                                                                                       :toiminto! (fn [g _ maksukausi]
                                                                                                                    (let [naytettavat-kuukaudet (into #{} (t/maksukauden-kuukaudet maksukausi))]
                                                                                                                      (doseq [rivi (grid/hae-grid (grid/get-in-grid (grid/etsi-osa g nimi) [1]) :lapset)]
                                                                                                                        (let [aika (grid/solun-arvo (grid/get-in-grid rivi [0]))
                                                                                                                              piillotetaan? (not (contains? naytettavat-kuukaudet (pvm/kuukausi aika)))]
                                                                                                                          (if piillotetaan?
                                                                                                                            (grid/piillota! rivi)
                                                                                                                            (grid/nayta! rivi))))
                                                                                                                      (t/paivita-raidat! (grid/osa-polusta g [::g-pohjat/data]))))}})))
                                         {}
                                         (range 1 (inc t/jh-korvausten-omiariveja-lkm)))))))

(defn johto-ja-hallintokorvaus-laskulla-yhteenveto-grid []
  (let [taulukon-id "johto-ja-hallintokorvaus-yhteenveto-taulukko"
        g (g-pohjat/uusi-taulukko {:header (-> (vec (repeat 7
                                                            {:tyyppi :teksti
                                                             :leveys 5
                                                             :luokat #{"table-default" "table-default-header" "lihavoitu"}}))
                                               (assoc-in [0 :leveys] 10)
                                               (assoc-in [1 :leveys] 4))
                                   :body (mapv (fn [_]
                                                 {:tyyppi :taulukko
                                                  :osat [{:tyyppi :rivi
                                                          :nimi ::yhteenveto
                                                          :osat (-> (vec (repeat 7
                                                                                 {:tyyppi :teksti
                                                                                  :luokat #{"table-default"}
                                                                                  :fmt summa-formatointi-uusi}))
                                                                    (assoc-in [0 :fmt] nil)
                                                                    (assoc-in [1 :fmt] (fn [teksti]
                                                                                         (if (nil? teksti)
                                                                                           ""
                                                                                           (let [sisaltaa-erottimen? (boolean (re-find #",|\." (str teksti)))]
                                                                                             (if sisaltaa-erottimen?
                                                                                               (fmt/desimaaliluku (clj-str/replace (str teksti) "," ".") 1 true)
                                                                                               teksti))))))}]})
                                               t/johto-ja-hallintokorvaukset-pohjadata)
                                   :footer (-> (vec (repeat 7
                                                            {:tyyppi :teksti
                                                             :luokat #{"table-default" "table-default-sum"}
                                                             :fmt yhteenveto-format}))
                                               (assoc 1 {:tyyppi :tyhja
                                                         :luokat #{"table-default" "table-default-sum"}}))
                                   :taulukon-id taulukon-id
                                   :root-asetus! (fn [g]
                                                   (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid] g)))
                                   :root-asetukset {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid]))
                                                    :paivita! (fn [f]
                                                                (swap! tila/suunnittelu-kustannussuunnitelma
                                                                       (fn [tila]
                                                                         (update-in tila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid] f))))}})
        g (grid/lisaa-rivi! g
                            (grid/rivi {:osat (-> (vec (repeatedly 7
                                                                   (fn []
                                                                     (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum" "harmaa-teksti"}}
                                                                                   :fmt yhteenveto-format}))))
                                                  (update-in [1] gov/teksti->tyhja #{"table-default" "table-default-sum" "harmaa-teksti"}))
                                        :koko {:seuraa {:seurattava ::g-pohjat/otsikko
                                                        :sarakkeet :sama
                                                        :rivit :sama}}
                                        :nimi ::indeksikorjattu}
                                       [{:sarakkeet [0 7] :rivit [0 1]}])
                            [3])]

    (grid/rajapinta-grid-yhdistaminen! g
                                       t/johto-ja-hallintokorvaus-yhteenveto-rajapinta
                                       (t/johto-ja-hallintokorvaus-yhteenveto-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [^{:nimi :mapit} [:toimenkuva :kk-v :hoitovuosi-1 :hoitovuosi-2 :hoitovuosi-3 :hoitovuosi-4 :hoitovuosi-5]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                        :solun-polun-pituus 1
                                                                        :datan-kasittely identity}
                                               [::indeksikorjattu] {:rajapinta :indeksikorjattu
                                                                    :solun-polun-pituus 1
                                                                    :datan-kasittely identity}}

                                              (second
                                                (reduce (fn [[index grid-kasittelijat] {:keys [toimenkuva maksukausi]}]
                                                          (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                                                            [(inc index) (merge grid-kasittelijat
                                                                                {[::g-pohjat/data index ::yhteenveto] {:rajapinta (keyword (str "yhteenveto" yksiloiva-nimen-paate))
                                                                                                                       :solun-polun-pituus 1
                                                                                                                       :datan-kasittely identity}})]))
                                                        [0 {}]
                                                        t/johto-ja-hallintokorvaukset-pohjadata))))))

(defn maarataulukko
  ([nimi yhteenvedot-polku] (maarataulukko nimi yhteenvedot-polku true))
  ([nimi yhteenvedot-polku paivita-kattohinta?]
   (let [polun-osa (keyword nimi)
         disablerivit-avain (keyword (str nimi "-disablerivit"))
         aseta-yhteenveto-avain (keyword (str "aseta-" nimi "-yhteenveto!"))
         aseta-avain (keyword (str "aseta-" nimi "!"))
         yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
                                              :solun-polun-pituus 1
                                              :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                              :datan-kasittely (fn [yhteenveto]
                                                                 (mapv (fn [[_ v]]
                                                                         v)
                                                                       yhteenveto))
                                              :tunnisteen-kasittely (fn [osat _]
                                                                      (mapv (fn [osa]
                                                                              (when (instance? solu/Syote osa)
                                                                                :maara))
                                                                            (grid/hae-grid osat :lapset)))}
         g (maarataulukon-pohja (t/hallinnollisten-idt polun-osa)
                                polun-osa
                                (fn [g]
                                  (swap! tila/suunnittelu-kustannussuunnitelma
                                         (fn [tila]
                                           (assoc-in tila [:gridit polun-osa :grid] g))))
                                {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit polun-osa :grid]))
                                 :paivita! (fn [f]
                                             (swap! tila/suunnittelu-kustannussuunnitelma
                                                    (fn [tila]
                                                      (update-in tila [:gridit polun-osa :grid] f))))}
                                [:gridit polun-osa :kuukausitasolla?]
                                (fn [arvo]
                                  (when arvo
                                    (t/paivita-solun-arvo {:paivitettava-asia aseta-yhteenveto-avain
                                                           :arvo arvo
                                                           :solu solu/*this*
                                                           :ajettavat-jarejestykset #{:mapit}
                                                           :triggeroi-seuranta? false}
                                                          false)))
                                (fn [arvo]
                                  (when arvo
                                    (t/paivita-solun-arvo {:paivitettava-asia aseta-yhteenveto-avain
                                                           :arvo arvo
                                                           :solu solu/*this*
                                                           :ajettavat-jarejestykset #{:mapit}
                                                           :triggeroi-seuranta? true}
                                                          true)
                                    (e! (t/->TallennaKustannusarvoitu polun-osa (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                                        :tunniste-rajapinnan-dataan)
                                                                                      (grid/hae-grid (grid/osa-polusta solu/*this* [:.. :.. 1]) :lapset))))
                                    (when paivita-kattohinta?
                                      (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
                                (fn [rivit-alla arvo]
                                  (when (and arvo (not (empty? rivit-alla)))
                                    (doseq [rivi rivit-alla
                                            :let [maara-solu (grid/get-in-grid rivi [1])]]
                                      (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                                             :arvo arvo
                                                             :solu maara-solu
                                                             :ajettavat-jarejestykset #{:mapit}
                                                             :triggeroi-seuranta? true}
                                                            true))
                                    (e! (t/->TallennaKustannusarvoitu polun-osa
                                                                      (vec (keep (fn [rivi]
                                                                                   (let [maara-solu (grid/get-in-grid rivi [1])
                                                                                         piillotettu? (grid/piillotettu? rivi)]
                                                                                     (when-not piillotettu?
                                                                                       (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                                                                                 rivit-alla))))
                                    (when paivita-kattohinta?
                                      (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
                                (fn [arvo]
                                  (when arvo
                                    (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                                           :arvo arvo
                                                           :solu solu/*this*
                                                           :ajettavat-jarejestykset #{:mapit}}
                                                          false)))
                                (fn [arvo]
                                  (grid/paivita-osa! solu/*this*
                                                     (fn [solu]
                                                       (assoc solu :nappi-nakyvilla? false)))
                                  (when arvo
                                    (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                                           :arvo arvo
                                                           :solu solu/*this*
                                                           :ajettavat-jarejestykset true
                                                           :triggeroi-seuranta? true}
                                                          true)
                                    (e! (t/->TallennaKustannusarvoitu polun-osa [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))
                                    (when paivita-kattohinta?
                                      (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))))))
         rajapinta (t/maarataulukon-rajapinta polun-osa aseta-yhteenveto-avain aseta-avain)]
     (grid/rajapinta-grid-yhdistaminen! g
                                        rajapinta
                                        (t/maarataulukon-dr rajapinta polun-osa yhteenvedot-polku aseta-avain aseta-yhteenveto-avain)
                                        {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                               :solun-polun-pituus 1
                                                               :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                               :datan-kasittely (fn [otsikot]
                                                                                  (mapv (fn [otsikko]
                                                                                          otsikko)
                                                                                        (vals otsikot)))}
                                         [::g-pohjat/data 0 ::data-yhteenveto] yhteenveto-grid-rajapinta-asetukset
                                         [::g-pohjat/data 0 ::data-sisalto] {:rajapinta polun-osa
                                                                             :solun-polun-pituus 2
                                                                             :jarjestys [{:keyfn :aika
                                                                                          :comp (fn [aika-1 aika-2]
                                                                                                  (pvm/ennen? aika-1 aika-2))}
                                                                                         ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                                                                             :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                                                                (mapv (fn [rivi]
                                                                                                        (mapv (fn [[_ v]]
                                                                                                                v)
                                                                                                              rivi))
                                                                                                      vuoden-hoidonjohtopalkkiot))
                                                                             :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                                                     (vec
                                                                                                       (map-indexed (fn [i rivi]
                                                                                                                      (vec
                                                                                                                        (map-indexed (fn [j osa]
                                                                                                                                       (when (instance? g-pohjat/SyoteTaytaAlas osa)
                                                                                                                                         {:osa :maara
                                                                                                                                          :aika (:aika (get data j))
                                                                                                                                          :osan-paikka [i j]}))
                                                                                                                                     (grid/hae-grid rivi :lapset))))
                                                                                                                    (grid/hae-grid data-sisalto-grid :lapset))))}
                                         [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                  :solun-polun-pituus 1
                                                                  :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                  :datan-kasittely (fn [yhteensa]
                                                                                     (mapv (fn [[_ nimi]]
                                                                                             nimi)
                                                                                           yhteensa))}})
     (grid/grid-tapahtumat g
                           tila/suunnittelu-kustannussuunnitelma
                           {disablerivit-avain {:polut [[:gridit polun-osa :kuukausitasolla?]]
                                                :toiminto! (fn [g _ kuukausitasolla?]
                                                             (maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data 0 ::data-sisalto])
                                                                                     (not kuukausitasolla?))
                                                             (maara-solujen-disable! (if-let [osa (grid/get-in-grid g [::g-pohjat/data 0 0 ::t/yhteenveto])]
                                                                                       osa
                                                                                       (grid/get-in-grid g [::g-pohjat/data 0 ::data-yhteenveto]))
                                                                                     kuukausitasolla?))}}))))


(defn haitari-laatikko [_ {:keys [alussa-auki? aukaise-fn otsikko-elementti]} & _]
  (let [auki? (atom alussa-auki?)
        otsikko-elementti (or otsikko-elementti :span)
        aukaise-fn! (comp (or aukaise-fn identity)
                          (fn [event]
                            (.preventDefault event)
                            (swap! auki? not)))]
    (fn [otsikko {:keys [id]} & sisalto]
      [:div.haitari-laatikko {:id id}
       [otsikko-elementti {:on-click aukaise-fn!
                           :class "klikattava"}
        otsikko
        (if @auki?
          ^{:key "haitari-auki"}
          [ikonit/livicon-chevron-up]
          ^{:key "haitari-kiinni"}
          [ikonit/livicon-chevron-down])]
       (when @auki?
         (doall (map-indexed (fn [index komponentti]
                               (with-meta
                                 komponentti
                                 {:key index}))
                             sisalto)))])))

(defn aseta-rivien-taustavari
  ([taulukko] (aseta-rivien-taustavari taulukko 0))
  ([taulukko rivista-eteenpain]
   (p/paivita-arvo taulukko :lapset
                   (fn [rivit]
                     (let [rivien-luokat (fn rivien-luokat [rivit i]
                                           (loop [[rivi & rivit] rivit
                                                  lopputulos []
                                                  i i]
                                             (if (nil? rivi)
                                               [i lopputulos]
                                               (let [konttirivi? (satisfies? p/Jana (first (p/arvo rivi :lapset)))
                                                     piillotettu-rivi? (contains? (p/arvo rivi :class) "piillotettu")
                                                     rivii (if konttirivi?
                                                             (rivien-luokat (p/arvo rivi :lapset) i)
                                                             rivi)]
                                                 (recur rivit
                                                        (if konttirivi?
                                                          (conj lopputulos
                                                                (p/aseta-arvo rivi :lapset (second rivii)))
                                                          (conj lopputulos
                                                                (if piillotettu-rivi?
                                                                  rivi
                                                                  (-> rivi
                                                                      (p/paivita-arvo :class conj (if (odd? i)
                                                                                                    "table-default-odd"
                                                                                                    "table-default-even"))
                                                                      (p/paivita-arvo :class disj (if (odd? i)
                                                                                                    "table-default-even"
                                                                                                    "table-default-odd"))))))
                                                        (cond
                                                          piillotettu-rivi? i
                                                          konttirivi? (if (odd? (first rivii))
                                                                        1 0)
                                                          :else (inc i)))))))]
                       (into []
                             (concat (take rivista-eteenpain rivit)
                                     (second (rivien-luokat (drop rivista-eteenpain rivit) 0)))))))))

(defn hintalaskurisarake
  ([yla ala] [hintalaskurisarake yla ala nil])
  ([yla ala {:keys [wrapper-luokat container-luokat]}]
   ;; Tämä div ottaa sen tasasen tilan muiden sarakkeiden kanssa, jotta vuodet jakautuu tasaisesti
   [:div {:class container-luokat}
    ;; Tämä div taas pitää sisällänsä ylä- ja alarivit, niin että leveys on maksimissaan sisällön leveys.
    ;; Tämä siksi, että ylarivin sisältö voidaan keskittää alariviin nähden
    [:div.sarake-wrapper {:class wrapper-luokat}
     [:div.hintalaskurisarake-yla yla]
     [:div.hintalaskurisarake-ala ala]]]))

(defn hintalaskuri
  [{:keys [otsikko selite hinnat]} {kuluva-hoitokauden-numero :hoitokauden-numero}]
  (if (some #(or (nil? (:summa %))
                 (js/isNaN (:summa %)))
            hinnat)
    [:div ""]
    [:div.hintalaskuri
     (when otsikko
       [:h5 otsikko])
     (when selite
       [:div selite])
     [:div.hintalaskuri-vuodet
      (doall
        (map-indexed (fn [index {:keys [summa teksti]}]
                       (let [hoitokauden-numero (inc index)]
                         ^{:key hoitokauden-numero}
                         [hintalaskurisarake (or teksti (str hoitokauden-numero ". vuosi"))
                          (fmt/euro summa)
                          (when (= hoitokauden-numero kuluva-hoitokauden-numero) {:wrapper-luokat "aktiivinen-vuosi"})]))
                     hinnat))
      [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
      [hintalaskurisarake "Yhteensä" (fmt/euro (reduce #(+ %1 (:summa %2)) 0 hinnat))]]]))

(defn indeksilaskuri
  ([hinnat indeksit] [indeksilaskuri hinnat indeksit nil])
  ([hinnat indeksit dom-id]
   (let [hinnat (vec (map-indexed (fn [index {:keys [summa]}]
                                    (let [hoitokauden-numero (inc index)
                                          {:keys [vuosi]} (get indeksit index)
                                          indeksikorjattu-summa (t/indeksikorjaa summa hoitokauden-numero)]
                                      {:vuosi vuosi
                                       :summa indeksikorjattu-summa
                                       :hoitokauden-numero hoitokauden-numero}))
                                  hinnat))]
     [:div.hintalaskuri.indeksilaskuri {:id dom-id}
      [:span "Indeksikorjattu"]
      [:div.hintalaskuri-vuodet
       (for [{:keys [vuosi summa hoitokauden-numero]} hinnat]
         ^{:key hoitokauden-numero}
         [hintalaskurisarake vuosi (fmt/euro summa)])
       [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
       [hintalaskurisarake " " (fmt/euro (reduce #(+ %1 (:summa %2)) 0 hinnat)) {:container-luokat "hintalaskuri-yhteensa"}]]])))

(defn kuluva-hoitovuosi [{:keys [hoitokauden-numero pvmt]}]
  (if (and hoitokauden-numero pvmt)
    [:div#kuluva-hoitovuosi
     [:span
      (str "Kuluva hoitovuosi: " hoitokauden-numero
           ". (" (pvm/pvm (first pvmt))
           " - " (pvm/pvm (second pvmt)) ")")]
     [:div.hoitovuosi-napit
      [napit/yleinen-ensisijainen "Laskutus" #(println "Painettiin Laskutus") {:ikoni [ikonit/euro] :disabled true}]
      [napit/yleinen-ensisijainen "Kustannusten seuranta" #(println "Painettiin Kustannusten seuranta") {:ikoni [ikonit/stats] :disabled true}]]]
    [yleiset/ajax-loader]))

(defn tavoite-ja-kattohinta-sisalto [yhteenvedot
                                     kuluva-hoitokausi
                                     indeksit]
  (let [tavoitehinnat (mapv (fn [summa]
                              {:summa summa})
                            (t/tavoitehinnan-summaus yhteenvedot))
        kattohinnat (mapv #(update % :summa * 1.1) tavoitehinnat)]
    [:div
     [hintalaskuri {:otsikko "Tavoitehinta"
                    :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
                    :hinnat (update tavoitehinnat 0 assoc :teksti "1. vuosi*")}
      kuluva-hoitokausi]
     [indeksilaskuri tavoitehinnat indeksit "tavoitehinnan-indeksikorjaus"]
     [hintalaskuri {:otsikko "Kattohinta"
                    :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
                    :hinnat kattohinnat}
      kuluva-hoitokausi]
     [indeksilaskuri kattohinnat indeksit]]))

(defn suunnitelman-selitteet [this luokat _]
  [:div#suunnitelman-selitteet {:class (apply str (interpose " " luokat))}
   [:span [ikonit/ok] "Kaikki kentät täytetty"]
   [:span [ikonit/livicon-question] "Keskeneräinen"]
   [:span [ikonit/remove] "Suunnitelma puuttuu"]])

(defn suunnitelmien-tila
  [suunnitelmien-tila-grid kantahaku-valmis?]
  (if kantahaku-valmis?
    [grid/piirra suunnitelmien-tila-grid]
    [yleiset/ajax-loader]))

(defn maksetaan-filter [_ _]
  (let [kausi-tekstiksi (fn [kausi]
                          (case kausi
                            :kesakausi "Kesäkaudella"
                            :talvikausi "Talvikaudella"
                            :molemmat "Kesä- ja talvikaudella"))]
    (fn [valitse-kausi maksetaan]
      [:div.pudotusvalikko-filter
       [:span "Maksetaan"]
       [yleiset/livi-pudotusvalikko {:valinta maksetaan
                                     :valitse-fn valitse-kausi
                                     :format-fn kausi-tekstiksi
                                     :vayla-tyyli? true}
        [:kesakausi :talvikausi :molemmat]]])))

(defn hankintojen-filter [_ _ _]
  (let [toimenpide-tekstiksi (r/partial (fn [toimenpide]
                                          (-> toimenpide name (clj-str/replace #"-" " ") t/aakkosta clj-str/upper-case)))
        valitse-toimenpide (r/partial (fn [toimenpide]
                                        (e! (tuck-apurit/->MuutaTila [:suodattimet :hankinnat :toimenpide] toimenpide))
                                        (t/laskutukseen-perustuvan-taulukon-nakyvyys!)))
        valitse-kausi (fn [suunnittellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid kausi]
                        (e! (tuck-apurit/->MuutaTila [:suodattimet :hankinnat :maksetaan] kausi))
                        (e! (t/->MaksukausiValittu))
                        (t/paivita-raidat! (grid/osa-polusta suunnittellut-hankinnat-grid [::g-pohjat/data]))
                        (t/paivita-raidat! (grid/osa-polusta laskutukseen-perustuvat-hankinnat-grid [::g-pohjat/data])))
        vaihda-fn (fn [event]
                    (.preventDefault event)
                    (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :kopioidaan-tuleville-vuosille?] not)))]
    (fn [suunnittellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid {:keys [toimenpide maksetaan kopioidaan-tuleville-vuosille?]}]
      (let [toimenpide (toimenpide-tekstiksi toimenpide)]
        [:div
         [:div.kustannussuunnitelma-filter
          [:div
           [:span "Toimenpide"]
           [yleiset/livi-pudotusvalikko {:valinta toimenpide
                                         :valitse-fn valitse-toimenpide
                                         :format-fn toimenpide-tekstiksi
                                         :vayla-tyyli? true}
            (sort-by t/toimenpiteiden-jarjestys t/toimenpiteet)]]
          [maksetaan-filter (r/partial valitse-kausi suunnittellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid) maksetaan]]
         [:input#kopioi-tuleville-hoitovuosille.vayla-checkbox
          {:type "checkbox" :checked kopioidaan-tuleville-vuosille?
           :on-change vaihda-fn}]
         [:label {:for "kopioi-tuleville-hoitovuosille"}
          "Kopioi kuluvan hoitovuoden summat tuleville vuosille samoille kuukausille"]]))))

(defn yleis-suodatin [_]
  (let [yksiloiva-id (str (gensym "kopioi-tuleville-hoitovuosille"))
        hoitovuodet (vec (range 1 6))
        vaihda-fn (fn [event]
                    (.preventDefault event)
                    (e! (tuck-apurit/->PaivitaTila [:suodattimet :kopioidaan-tuleville-vuosille?] not)))
        valitse-hoitovuosi (fn [hoitovuosi]
                             (e! (tuck-apurit/->MuutaTila [:suodattimet :hoitokauden-numero] hoitovuosi)))
        hoitovuositeksti (fn [hoitovuosi]
                           (str hoitovuosi ". hoitovuosi"))]
    (fn [{:keys [hoitokauden-numero kopioidaan-tuleville-vuosille?]}]
      (if hoitokauden-numero
        ^{:key :yleis-suodatin}
        [:div.kustannussuunnitelma-filter
         [:div
          [:input.vayla-checkbox {:id yksiloiva-id
                                  :type "checkbox" :checked kopioidaan-tuleville-vuosille?
                                  :on-change (r/partial vaihda-fn)}]
          [:label {:for yksiloiva-id}
           "Kopioi kuluvan hoitovuoden määrät tuleville vuosille"]]
         [:div.pudotusvalikko-filter
          [:span "Hoitovuosi"]
          [yleiset/livi-pudotusvalikko {:valinta hoitokauden-numero
                                        :valitse-fn valitse-hoitovuosi
                                        :format-fn hoitovuositeksti
                                        :vayla-tyyli? true}
           (filterv #(not= % hoitokauden-numero) hoitovuodet)]]]
        [yleiset/ajax-loader]))))

(defn osien-paivitys-fn [nimi maara yhteensa indeksikorjattu]
  (fn [osat]
    (mapv (fn [osa]
            (let [otsikko (p/osan-id osa)]
              (case otsikko
                "Nimi" (nimi osa)
                "Määrä" (maara osa)
                "Yhteensä" (yhteensa osa)
                "Indeksikorjattu" (indeksikorjattu osa))))
          osat)))

(defn arvioidaanko-laskutukseen-perustuen [_ _ _]
  (let [vaihda-fn (fn [toimenpide event]
                    (let [valittu? (.. event -target -checked)]
                      (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
                                                     (fn [valinnat]
                                                       (if valittu?
                                                         (conj valinnat toimenpide)
                                                         (disj valinnat toimenpide)))))
                      (t/laskutukseen-perustuvan-taulukon-nakyvyys!)))]
    (fn [{:keys [toimenpide]} laskutukseen-perustuen? on-oikeus?]
      [:div#laskutukseen-perustuen-filter
       [:input#lakutukseen-perustuen.vayla-checkbox
        {:type "checkbox" :checked laskutukseen-perustuen?
         :on-change (partial vaihda-fn toimenpide) :disabled (not on-oikeus?)}]
       [:label {:for "lakutukseen-perustuen"}
        "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle: "
        [:b (-> toimenpide name (clj-str/replace #"-" " ") t/aakkosta clj-str/capitalize)]]])))


(defn laskutukseen-perustuen-wrapper [g nayta-laskutukseen-perustuva-taulukko?]
  (when-not nayta-laskutukseen-perustuva-taulukko?
    (grid/piillota! g))
  (fn [g _]
    [grid/piirra g]))

(defn hankintakustannukset-taulukot [kirjoitusoikeus?
                                     indeksit
                                     kuluva-hoitokausi
                                     suunnittellut-hankinnat-grid
                                     laskutukseen-perustuvat-hankinnat-grid
                                     rahavaraukset-grid
                                     hankintakustannukset-yhteenvedot
                                     kantahaku-valmis?
                                     suodattimet]
  (let [{:keys [toimenpide laskutukseen-perustuen-valinta]} (:hankinnat suodattimet)
        suunnitellut-hankinnat-taulukko-valmis? (and suunnittellut-hankinnat-grid kantahaku-valmis?)
        laskutukseen-perustuva-taulukko-valmis? (and laskutukseen-perustuvat-hankinnat-grid kantahaku-valmis?)
        rahavaraukset-taulukko-valmis? (and rahavaraukset-grid kantahaku-valmis?)
        nayta-laskutukseen-perustuva-taulukko? (contains? laskutukseen-perustuen-valinta toimenpide)
        yhteenveto (mapv (fn [summa]
                           {:summa summa})
                         (mapv +
                               (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :rahavaraukset]))
                               (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :suunnitellut-hankinnat]))
                               (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :laskutukseen-perustuvat-hankinnat]))))]
    [:<>
     [:h2#hankintakustannukset "Hankintakustannukset"]
     (if yhteenveto
       ^{:key "hankintakustannusten-yhteenveto"}
       [:div.summa-ja-indeksilaskuri
        [hintalaskuri {:otsikko "Yhteenveto"
                       :selite "Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestoiti"
                       :hinnat yhteenveto}
         kuluva-hoitokausi]
        [indeksilaskuri yhteenveto indeksit]]
       ^{:key "hankintakustannusten-loader"}
       [yleiset/ajax-loader "Hankintakustannusten yhteenveto..."])
     [:h3 "Suunnitellut hankinnat"]
     [hankintojen-filter suunnittellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid (:hankinnat suodattimet)]
     (if suunnitellut-hankinnat-taulukko-valmis?
       [grid/piirra suunnittellut-hankinnat-grid]
       [yleiset/ajax-loader])
     [arvioidaanko-laskutukseen-perustuen (:hankinnat suodattimet) nayta-laskutukseen-perustuva-taulukko? kirjoitusoikeus?]
     (if laskutukseen-perustuva-taulukko-valmis?
       ^{:key "nayta-lpt"}
       [laskutukseen-perustuen-wrapper laskutukseen-perustuvat-hankinnat-grid nayta-laskutukseen-perustuva-taulukko?]
       [yleiset/ajax-loader])
     (when (contains? t/toimenpiteet-rahavarauksilla toimenpide)
         ^{:key "rahavaraukset-otsikko"}
         [:<>
          [:h3 "Toimenpiteen rahavarukset"]
          [yleis-suodatin (dissoc suodattimet :hankinnat)]
          (if rahavaraukset-taulukko-valmis?
            [grid/piirra rahavaraukset-grid]
            [yleiset/ajax-loader])])]))

(defn erillishankinnat-yhteenveto
  [erillishankinnat indeksit kuluva-hoitokausi kantahaku-valmis?]
  (if (and erillishankinnat kantahaku-valmis?)
    (let [yhteenveto (mapv (fn [summa]
                             {:summa summa})
                           erillishankinnat)]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                      :hinnat yhteenveto}
        kuluva-hoitokausi]
       [indeksilaskuri yhteenveto indeksit]])
    [yleiset/ajax-loader]))

(defn erillishankinnat-sisalto [erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi]
  (let [nayta-erillishankinnat-grid? (and kantahaku-valmis? erillishankinnat-grid)]
    [:<>
     [:h3 {:id (str (get t/hallinnollisten-idt :erillishankinnat) "-osio")} "Erillishankinnat"]
     [erillishankinnat-yhteenveto erillishankinnat-yhteensa indeksit kuluva-hoitokausi kantahaku-valmis?]
     [yleis-suodatin suodattimet]
     (when nayta-erillishankinnat-grid?
       [grid/piirra erillishankinnat-grid])
     [:span "Yhteenlaskettu kk-määrä: Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)"]]))

(defn johto-ja-hallintokorvaus-yhteenveto
  [johto-ja-hallintokorvaukset-yhteensa toimistokulut-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if kantahaku-valmis?
    (let [hinnat (mapv (fn [jh tk]
                         {:summa (+ jh tk)})
                       johto-ja-hallintokorvaukset-yhteensa
                       toimistokulut-yhteensa)]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Palkat + Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn johto-ja-hallintokorvaus [johto-ja-hallintokorvaus-grid
                                johto-ja-hallintokorvaus-yhteenveto-grid
                                toimistokulut-grid
                                suodattimet
                                johto-ja-hallintokorvaukset-yhteensa
                                toimistokulut-yhteensa
                                kuluva-hoitokausi
                                indeksit
                                kantahaku-valmis?]
  [:<>
   [:h3 {:id (str (get t/hallinnollisten-idt :johto-ja-hallintokorvaus) "-osio")} "Johto- ja hallintokorvaus"]
   [johto-ja-hallintokorvaus-yhteenveto johto-ja-hallintokorvaukset-yhteensa toimistokulut-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
   [:h3 "Tuntimäärät ja -palkat"]
   [yleis-suodatin suodattimet]
   (if (and johto-ja-hallintokorvaus-grid kantahaku-valmis?)
     [grid/piirra johto-ja-hallintokorvaus-grid]
     [yleiset/ajax-loader])
   (if (and johto-ja-hallintokorvaus-yhteenveto-grid kantahaku-valmis?)
     [grid/piirra johto-ja-hallintokorvaus-yhteenveto-grid]
     [yleiset/ajax-loader])
   [:h3 {:id (str (get t/hallinnollisten-idt :toimistokulut) "-osio")} "Johto ja hallinto: muut kulut"]
   [yleis-suodatin suodattimet]
   (if (and toimistokulut-grid kantahaku-valmis?)
     [grid/piirra toimistokulut-grid]
     [yleiset/ajax-loader])
   [:span
    "Yhteenlaskettu kk-määrä: Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten ja vierailujen järjestäminen sekä tarjoilukulut + Hoito- ja korjaustöiden pientarvikevarasto (työkalut, mutterit, lankut, naulat jne.)"]])

(defn hoidonjohtopalkkio-yhteenveto
  [hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if kantahaku-valmis?
    (let [hinnat (mapv (fn [hjp]
                         {:summa hjp})
                       hoidonjohtopalkkio-yhteensa)]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Palkat + Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio [hoidonjohtopalkkio-grid kantahaku-valmis?]
  (if (and hoidonjohtopalkkio-grid kantahaku-valmis?)
    [grid/piirra hoidonjohtopalkkio-grid]
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-sisalto [hoidonjohtopalkkio-grid suodattimet hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  [:<>
   [:h3 {:id (str (get t/hallinnollisten-idt :hoidonjohtopalkkio) "-osio")} "Hoidonjohtopalkkio"]
   [hoidonjohtopalkkio-yhteenveto hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
   [yleis-suodatin suodattimet]
   [hoidonjohtopalkkio hoidonjohtopalkkio-grid kantahaku-valmis?]])

(defn hallinnolliset-toimenpiteet-yhteensa [johto-ja-hallintokorvaukset-yhteensa erillishankinnat-yhteensa hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and kantahaku-valmis? indeksit)
    (let [hinnat (mapv (fn [jh eh hjp]
                        {:summa (+ jh eh hjp)})
                      johto-ja-hallintokorvaukset-yhteensa
                      erillishankinnat-yhteensa
                      hoidonjohtopalkkio-yhteensa)]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko "Yhteenveto"
                      :selite "Erillishankinnat + Johto-ja hallintokorvaus + Hoidonjohtopalkkio"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hallinnolliset-toimenpiteet-sisalto [indeksit
                                           kuluva-hoitokausi
                                           suodattimet
                                           erillishankinnat-grid
                                           johto-ja-hallintokorvaus-grid
                                           johto-ja-hallintokorvaus-yhteenveto-grid
                                           toimistokulut-grid
                                           hoidonjohtopalkkio-grid
                                           erillishankinnat-yhteensa
                                           johto-ja-hallintokorvaukset-yhteensa
                                           toimistokulut-yhteensa
                                           hoidonjohtopalkkio-yhteensa
                                           kantahaku-valmis?]
  [:<>
   [:h2#hallinnolliset-toimenpiteet "Hallinnolliset toimenpiteet"]
   [hallinnolliset-toimenpiteet-yhteensa johto-ja-hallintokorvaukset-yhteensa erillishankinnat-yhteensa hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
   [erillishankinnat-sisalto erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi]
   [johto-ja-hallintokorvaus johto-ja-hallintokorvaus-grid johto-ja-hallintokorvaus-yhteenveto-grid toimistokulut-grid suodattimet johto-ja-hallintokorvaukset-yhteensa toimistokulut-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
   [hoidonjohtopalkkio-sisalto hoidonjohtopalkkio-grid suodattimet hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]])


(defn tilaajan-varaukset [tilaajan-varaukset-grid suodattimet kantahaku-valmis?]
  (let [nayta-tilaajan-varaukset-grid? (and kantahaku-valmis? tilaajan-varaukset-grid)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :tilaajan-varaukset) "-osio")} "Tilaajan varaukset mm. bonuksien maksamista varten"]
     [:div "Näitä varauksia " [:span.lihavoitu "ei lasketa mukaan tavoitehintaan"]]
     [yleis-suodatin suodattimet]
     (if nayta-tilaajan-varaukset-grid?
       [grid/piirra tilaajan-varaukset-grid]
       [yleiset/ajax-loader])]))

(defn kustannussuunnitelma*
  [_ app]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->Hoitokausi))
                      (e! (t/->TaulukoidenVakioarvot))
                      (e! (t/->FiltereidenAloitusarvot))
                      (e! (t/->YleisSuodatinArvot))
                      (e! (t/->Oikeudet))
                      (e! (tuck-apurit/->AloitaViivastettyjenEventtienKuuntelu 1000 (:kaskytyskanava app)))
                      (go (let [g-s (suunnitelmien-tila-grid)
                                g-sh (suunnittellut-hankinnat-grid)
                                g-hlp (hankinnat-laskutukseen-perustuen-grid)
                                g-r (rahavarausten-grid)
                                g-er (maarataulukko "erillishankinnat" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                g-jhl (johto-ja-hallintokorvaus-laskulla-grid)
                                g-jhly (johto-ja-hallintokorvaus-laskulla-yhteenveto-grid)
                                g-t (maarataulukko "toimistokulut" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                g-hjp (maarataulukko "hoidonjohtopalkkio" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                g-tv (maarataulukko "tilaajan-varaukset" [:yhteenvedot :tilaajan-varaukset] false)]
                            (t/paivita-raidat! (grid/osa-polusta g-s [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-sh [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-hlp [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-jhl [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-jhly [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-er [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-t [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-hjp [::g-pohjat/data]))
                            (t/paivita-raidat! (grid/osa-polusta g-tv [::g-pohjat/data]))
                            (grid/triggeroi-tapahtuma! g-er :erillishankinnat-disablerivit)
                            (grid/triggeroi-tapahtuma! g-t :toimistokulut-disablerivit)
                            (grid/triggeroi-tapahtuma! g-hjp :hoidonjohtopalkkio-disablerivit)
                            (grid/triggeroi-tapahtuma! g-jhl :johto-ja-hallintokorvaukset-disablerivit)
                            (grid/triggeroi-tapahtuma! g-tv :tilaajan-varaukset-disablerivit)))
                      (e! (t/->HaeKustannussuunnitelma))))
    (fn [e*! {:keys [suodattimet] :as app}]
      (set! e! e*!)
      [:div#kustannussuunnitelma
       ;[debug/debug app]
       [:h1 "Kustannussuunnitelma"]
       [:div "Kun kaikki määrät on syötetty, voit seurata kustannuksia. Sampoa varten muodostetaan automaattisesti maksusuunnitelma, jotka löydät Laskutus-osiosta. Kustannussuunnitelmaa tarkennetaan joka hoitovuoden alussa."]
       [kuluva-hoitovuosi (get-in app [:domain :kuluva-hoitokausi])]
       [haitari-laatikko
        "Tavoite- ja kattohinta lasketaan automaattisesti"
        {:alussa-auki? true
         :id "tavoite-ja-kattohinta"}
        [tavoite-ja-kattohinta-sisalto
         (get app :yhteenvedot)
         (get-in app [:domain :kuluva-hoitokausi])
         (get-in app [:domain :indeksit])]
        [:span#tavoite-ja-kattohinta-huomio
         "*) Vuodet ovat hoitovuosia, ei kalenterivuosia."]]
       [:span.viiva-alas]
       [haitari-laatikko
        "Suunnitelmien tila"
        {:alussa-auki? true
         :otsikko-elementti :h2}
        [suunnitelmien-tila
         (get-in app [:gridit :suunnitelmien-tila :grid])
         (:kantahaku-valmis? app)]
        [:div "*) Hoitovuosisuunnitelmat lasketaan automaattisesti"]
        [:div "**) Kuukausisummat syöttää käyttäjä. Kuukausisuunnitelmista muodostuu maksusuunnitelma  Sampoa varten. Ks. Laskutus > Maksuerät."]]
       [:span.viiva-alas]
       [hankintakustannukset-taulukot
        (get-in app [:domain :kirjoitusoikeus?])
        (get-in app [:domain :indeksit])
        (get-in app [:domain :kuluva-hoitokausi])
        (get-in app [:gridit :suunnittellut-hankinnat :grid])
        (get-in app [:gridit :laskutukseen-perustuvat-hankinnat :grid])
        (get-in app [:gridit :rahavaraukset :grid])
        (get-in app [:yhteenvedot :hankintakustannukset])
        (:kantahaku-valmis? app)
        suodattimet]
       [:span.viiva-alas]
       [hallinnolliset-toimenpiteet-sisalto
        (get-in app [:domain :indeksit])
        (get-in app [:domain :kuluva-hoitokausi])
        (dissoc suodattimet :hankinnat)
        (get-in app [:gridit :erillishankinnat :grid])
        (get-in app [:gridit :johto-ja-hallintokorvaukset :grid])
        (get-in app [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
        (get-in app [:gridit :toimistokulut :grid])
        (get-in app [:gridit :hoidonjohtopalkkio :grid])
        (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :erillishankinnat])
        (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset])
        (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :toimistokulut])
        (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio])
        (:kantahaku-valmis? app)]
       [:span.viiva-alas.sininen]
       [tilaajan-varaukset
        (get-in app [:gridit :tilaajan-varaukset :grid])
        (dissoc suodattimet :hankinnat)
        (:kantahaku-valmis? app)]])))

(defn kustannussuunnitelma []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])
