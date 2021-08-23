(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.kustannussuunnitelma_view
  (:require [reagent.core :as r :refer [atom]]
            [clojure.string :as clj-str]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :as modal]
            [harja.ui.taulukko.grid-osan-vaihtaminen :as gov]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log error]]
            [harja.domain.palvelut.budjettisuunnittelu :as bj]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.tyokalut.vieritys :as vieritys]
            [goog.dom :as dom]
            [harja.ui.debug :as debug]
            [harja.ui.valinnat :as valinnat]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]
                   [harja.ui.taulukko.grid :refer [defsolu]]
                   [cljs.core.async.macros :refer [go go-loop]]))


(defonce e! nil)

;; -- Formatointi --

(defn summa-formatointi [teksti]
  (cond
    (= t/vaihtelua-teksti teksti) t/vaihtelua-teksti
    (or (nil? teksti) (= "" teksti) (js/isNaN teksti)) ""
    :else (let [teksti (clj-str/replace (str teksti) "," ".")]
            (fmt/desimaaliluku teksti 2 true))))

(defn summa-formatointi-uusi [teksti]
  (if (or (= "" teksti) (js/isNaN teksti) (nil? teksti))
    ""
    (let [teksti (clj-str/replace (str teksti) "," ".")]
      (fmt/desimaaliluku teksti 2 true))))

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

(defn aika-fmt [paivamaara]
  (when paivamaara
    (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))))

(defn aika-tekstilla-fmt [paivamaara]
  (when paivamaara
    (let [teksti (aika-fmt paivamaara)
          nyt (pvm/nyt)
          mennyt? (and (pvm/ennen? paivamaara nyt)
                       (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                           (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
      (if mennyt?
        (str teksti " (mennyt)")
        teksti))))

(defn fmt-euro [summa]
  (try (fmt/euro summa)
       (catch :default e
         summa)))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))




;;;;; ### TAULUKOT / GRIDIT ### ;;;;;;;

;; Hox: Määrittelee funktion vayla-checkbox.
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

;; Hox: Määrittelee funktion laajenna-syote
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
                                                   rivit-alla)))))))

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
           nappia-painettu-tallenna! parametrit]}]
  (let [toiminto-fn!
        (fn [paivitettava-asia tallenna! asiat]
          (println "toiminto-fn syote" paivitettava-asia asiat)
          (tallenna! asiat))]
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
                              (t/paivita-solun-arvo {:paivitettava-asia paivitettava-asia
                                                     :arvo arvo
                                                     :solu solu/*this*
                                                     :ajettavat-jarejestykset true
                                                     :triggeroi-seuranta? true}
                                                    true)
                              (toiminto-fn! paivitettava-asia blur-tallenna! solu/*this*))
                   :on-key-down (fn [event]
                                  (when (= "Enter" (.. event -key))
                                    (.. event -target blur)))}
       :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                    {:eventin-arvo {:f poista-tyhjat}}]
                        :on-blur (if nappi?
                                   [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]
                                   [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}} {:oma {:f esta-blur-ja-lisaa-vaihtelua-teksti}}])}
       :parametrit (merge {:size 2}
                          parametrit)
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
                               (toiminto-fn! paivitettava-asia nappia-painettu-tallenna! rivit-alla)))
         :nappi-nakyvilla? false}))))

;; -- Modaalit --

(defn modal-aiheteksti [aihe {:keys [toimenpide toimenkuva]}]
  [:h3 {:style {:padding-bottom "calc(1.0625em + 5px)"}}
   (case aihe
     :maaramitattava (str "Määrämitattavat: " (t/toimenpide-formatointi toimenpide))
     :toimenkuva (str "Toimenkuva: " toimenkuva))])

(defn modal-lista [data-hoitokausittain]
  [:div {:style {:max-height "70vh"
                 :overflow-y "auto"}}
   (doall
     (map-indexed (fn [index hoitokauden-data]
                    ^{:key index}
                    [:div
                     [:span.lihavoitu (str (inc index) ". hoitovuosi")]
                     (for [{:keys [aika maara]} hoitokauden-data]
                       ^{:key (pvm/kuukausi aika)}
                       [:div.map-lista
                        [:div (aika-fmt aika)]
                        [:div (str (summa-formatointi maara) " €/kk")]])])
                  data-hoitokausittain))])

(defn modal-napit [poista! peruuta!]
  (let [poista! (r/partial (fn []
                             (binding [t/*e!* e!]
                               (poista!))))]
    [:div {:style {:padding-top "15px"}}
     [napit/yleinen-toissijainen "Peruuta" peruuta! {:ikoni [ikonit/remove]}]
     [napit/kielteinen "Poista tiedot" poista! {:ikoni [ikonit/livicon-trash]}]]))

(defn poista-modal!
  ([aihe data-hoitokausittain poista! tiedot] (poista-modal! aihe data-hoitokausittain poista! tiedot nil))
  ([aihe data-hoitokausittain poista! tiedot peruuta!]
   (let [otsikko "Haluatko varmasti poistaa seuraavat tiedot?"
         sulje! (r/partial modal/piilota!)
         peruuta! (if peruuta!
                    (r/partial (comp sulje! peruuta!))
                    sulje!)]
     (modal/nayta! {:otsikko otsikko
                    :content-tyyli {:overflow "hidden"}
                    :body-tyyli {:padding-right "0px"}
                    :sulje-fn peruuta!}
                   [:div
                    [modal-aiheteksti aihe (select-keys tiedot #{:toimenpide :toimenkuva})]
                    [modal-lista data-hoitokausittain]
                    [modal-napit poista! peruuta!]]))))


;; -- Grid-operaatiot??? --

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


;; #### GRIDIT ####

(defn hankintojen-pohja [taulukon-id
                         root-asetus!
                         root-asetukset
                         nappia-painettu!
                         on-change
                         on-blur]
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
                                                                          :fmt aika-tekstilla-fmt}
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
                                                                          :fmt summa-formatointi
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
                           :root-asetukset root-asetukset}))

(defn suunnittellut-hankinnat-grid []
  (let [g (hankintojen-pohja "suunnittellut-hankinnat-taulukko"
                             (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :suunnittellut-hankinnat :grid] g)))
                             {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :suunnittellut-hankinnat :grid]))
                              :paivita! (fn [f]
                                          (swap! tila/suunnittelu-kustannussuunnitelma
                                                 (fn [tila]
                                                   (update-in tila [:gridit :suunnittellut-hankinnat :grid] f))))}
                             (fn [hoitokauden-numero rivit-alla arvo]
                               (when (not (empty? rivit-alla))
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
                                                                              rivit-alla))))))
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
                               (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnittellut-hankinnat!
                                                      :arvo arvo
                                                      :solu solu/*this*
                                                      :ajettavat-jarejestykset true
                                                      :triggeroi-seuranta? true}
                                                     true
                                                     hoitokauden-numero
                                                     :hankinnat)
                               (e! (t/->TallennaHankintojenArvot :hankintakustannus hoitokauden-numero [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))))]
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/suunnittellut-hankinnat-rajapinta
                                       (t/suunnittellut-hankinnat-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                        :solun-polun-pituus 1
                                                                        :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
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
                               (when (not (empty? rivit-alla))
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
                                                                              rivit-alla))))))
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
                               (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                                      :arvo arvo
                                                      :solu solu/*this*
                                                      :ajettavat-jarejestykset true
                                                      :triggeroi-seuranta? true}
                                                     true
                                                     hoitokauden-numero
                                                     :hankinnat)
                               (e! (t/->TallennaHankintojenArvot :laskutukseen-perustuva-hankinta hoitokauden-numero [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))))]
    (grid/rajapinta-grid-yhdistaminen! g
                                       t/laskutukseen-perustuvat-hankinnat-rajapinta
                                       (t/laskutukseen-perustuvat-hankinnat-dr)
                                       (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                                     :solun-polun-pituus 1
                                                                     :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                     :datan-kasittely (fn [otsikot]
                                                                                        (mapv (fn [otsikko]
                                                                                                otsikko)
                                                                                              (vals otsikot)))}
                                               [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                                                        :solun-polun-pituus 1
                                                                        :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
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
  (let [dom-id "rahavaraukset-taulukko"
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
                                                                                                               ::data-sisalto 1}))
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
                                                                                                                                                                     (e! (t/->TallennaKustannusarvoitu (tyyppi->tallennettava-asia tyyppi) tunnisteet))))
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
                                                                                                                                            :fmt summa-formatointi
                                                                                                                                            :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                                                                                               (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                             :fmt summa-formatointi})
                                                                                                                               (solu/teksti {:parametrit {:class #{"table-default" "harmaa-teksti"}}
                                                                                                                                             :fmt summa-formatointi})]
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
                                                                                                                                                           :fmt aika-tekstilla-fmt})
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
                                                                                                                                                                                    (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                                                                                                                           :arvo arvo
                                                                                                                                                                                                           :solu solu/*this*
                                                                                                                                                                                                           :ajettavat-jarejestykset true
                                                                                                                                                                                                           :triggeroi-seuranta? true}
                                                                                                                                                                                                          true)
                                                                                                                                                                                    (e! (t/->TallennaKustannusarvoitu (tyyppi->tallennettava-asia tyyppi) [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)])))
                                                                                                                                                                         :on-key-down (fn [event]
                                                                                                                                                                                        (when (= "Enter" (.. event -key))
                                                                                                                                                                                          (.. event -target blur)))}
                                                                                                                                                                        {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                                     {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                                         :on-blur [:positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                                                                        {:size 2
                                                                                                                                                                         :class #{"input-default"}}
                                                                                                                                                                        summa-formatointi
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
  (let [taulukon-id "johto-ja-hallintokorvaus-laskulla-taulukko"
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
        blur-tallenna! (fn -blur-tallenna!
                         ([tallenna-kaikki? etsittava-osa solu]
                          (-blur-tallenna! tallenna-kaikki? etsittava-osa solu nil))
                         ([tallenna-kaikki? etsittava-osa solu muutos]
                          (println "blur tallenna JHO" tallenna-kaikki? etsittava-osa)
                          (if tallenna-kaikki?
                            (e! (t/->TallennaJohtoJaHallintokorvaukset :johto-ja-hallintokorvaus (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                                                         :tunniste-rajapinnan-dataan)
                                                                                                       (grid/hae-grid
                                                                                                         (get (grid/hae-grid (grid/etsi-osa (grid/root solu) etsittava-osa)
                                                                                                                             :lapset)
                                                                                                              1)
                                                                                                         :lapset))))
                            (e! (t/->TallennaJohtoJaHallintokorvaukset :johto-ja-hallintokorvaus [(grid/solun-asia solu :tunniste-rajapinnan-dataan)])))))
        nappia-painettu-tallenna! (fn -nappi-tallenna!
                                    ([rivit-alla]
                                     (-nappi-tallenna! rivit-alla nil))
                                    ([rivit-alla muutos]
                                     (println "nappi tallenna JHO")
                                     (e! (t/->TallennaJohtoJaHallintokorvaukset :johto-ja-hallintokorvaus
                                                                                (vec (keep (fn [rivi]
                                                                                             (let [haettu-solu (grid/get-in-grid rivi [1])
                                                                                                   piillotettu? (grid/piillotettu? rivi)]
                                                                                               (when-not piillotettu?
                                                                                                 (grid/solun-asia haettu-solu :tunniste-rajapinnan-dataan))))
                                                                                           rivit-alla))))))
        rividisable! (fn [g index kuukausitasolla?]
                       (maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data index ::data-sisalto])
                                               (not kuukausitasolla?))
                       (maara-solujen-disable! (if-let [osa (grid/get-in-grid g [::g-pohjat/data index ::data-yhteenveto 0 1])]
                                                 osa
                                                 (grid/get-in-grid g [::g-pohjat/data index ::data-yhteenveto 1]))
                                               kuukausitasolla?))
        disable-osa-indexissa! (fn [rivi indexit disabled?]
                                 (grid/paivita-grid! rivi
                                                     :lapset
                                                     (fn [osat]
                                                       (vec (map-indexed (fn [index solu]
                                                                           (if (contains? indexit index)
                                                                             (if (grid/pudotusvalikko? solu)
                                                                               (assoc-in solu [:livi-pudotusvalikko-asetukset :disabled?] disabled?)
                                                                               (assoc-in solu [:parametrit :disabled?] disabled?))
                                                                             solu))
                                                                         osat)))))
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
                                                                          :fmt aika-tekstilla-fmt}
                                                                         (syote-solu {:nappi? true :fmt summa-formatointi-uusi :paivitettava-asia :aseta-tunnit!
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
                                                                                                  (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                                                         :arvo arvo
                                                                                                                         :solu solu/*this*
                                                                                                                         :ajettavat-jarejestykset #{:mapit}}
                                                                                                                        false))
                                                                                     :on-blur (fn [arvo]
                                                                                                (let [arvo (if (= "" (clj-str/trim arvo))
                                                                                                             nil
                                                                                                             arvo)
                                                                                                      solu solu/*this*
                                                                                                      paivita-ui! (fn []
                                                                                                                    (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                                                                           :arvo arvo
                                                                                                                                           :solu solu
                                                                                                                                           :ajettavat-jarejestykset true
                                                                                                                                           :triggeroi-seuranta? true}
                                                                                                                                          true))
                                                                                                      paivita-kanta! (fn [] (e! (t/->TallennaToimenkuva rivin-nimi)))
                                                                                                      paivita! (fn []
                                                                                                                 (paivita-ui!)
                                                                                                                 (paivita-kanta!))
                                                                                                      peruuta! (fn [vanha-arvo]
                                                                                                                 (e! (tuck-apurit/->MuutaTila [:gridit :johto-ja-hallintokorvaukset :yhteenveto rivin-nimi :toimenkuva] vanha-arvo)))]
                                                                                                  (if (nil? arvo)
                                                                                                    (let [paivita-seuraavalla-tickilla! (fn []
                                                                                                                                          (r/next-tick paivita!))]
                                                                                                      (e! (t/->PoistaOmaJHDdata :toimenkuva
                                                                                                                                rivin-nimi
                                                                                                                                nil
                                                                                                                                modal/piilota!
                                                                                                                                paivita-seuraavalla-tickilla!
                                                                                                                                (r/partial (fn [toimenkuva data-hoitokausittain poista! vanhat-arvot]
                                                                                                                                             (poista-modal! :toimenkuva
                                                                                                                                                            data-hoitokausittain
                                                                                                                                                            poista!
                                                                                                                                                            {:toimenkuva toimenkuva}
                                                                                                                                                            (partial peruuta! (get-in vanhat-arvot [0 :toimenkuva]))))))))
                                                                                                    (paivita!))))
                                                                                     :on-key-down (fn [event]
                                                                                                    (when (= "Enter" (.. event -key))
                                                                                                      (.. event -target blur)))}
                                                                                    {:on-change [:eventin-arvo]
                                                                                     :on-blur [:eventin-arvo]}
                                                                                    {:class #{"input-default"}
                                                                                     :placeholder "Kirjoita muu toimenkuva"}
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
                                                                   (let [solu solu/*this*
                                                                         paivita-ui! (fn []
                                                                                       (r/next-tick
                                                                                         (fn []
                                                                                           (t/paivita-solun-arvo {:paivitettava-asia :aseta-jh-yhteenveto!
                                                                                                                  :arvo maksukausi
                                                                                                                  :solu solu
                                                                                                                  :ajettavat-jarejestykset true
                                                                                                                  :triggeroi-seuranta? true}
                                                                                                                 false))))]
                                                                     (if (= :molemmat maksukausi)
                                                                       (paivita-ui!)
                                                                       (e! (t/->PoistaOmaJHDdata :maksukausi
                                                                                                 rivin-nimi
                                                                                                 maksukausi
                                                                                                 modal/piilota!
                                                                                                 paivita-ui!
                                                                                                 (r/partial (fn [toimenkuva data-hoitokausittain poista! _]
                                                                                                              (poista-modal! :toimenkuva
                                                                                                                             data-hoitokausittain
                                                                                                                             poista!
                                                                                                                             {:toimenkuva toimenkuva}))))))))
                                                     :format-fn (fn [teksti]
                                                                  (str (t/maksukausi-kuukausina teksti)))
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
                                                                               :fmt aika-tekstilla-fmt}
                                                                              (syote-solu {:nappi? true :fmt summa-formatointi-uusi :paivitettava-asia :aseta-tunnit!
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
                                                                                                                  :tunnisteen-kasittely (fn [data-sisalto-grid data]
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
                                                                                            (when-not (and (string? toimenkuva) (re-find #"\d$" toimenkuva))
                                                                                              (doseq [[maksukausi kuukausitasolla?] maksukaudet-kuukausitasolla?
                                                                                                      :let [index (first (keep-indexed (fn [index jh-pohjadata]
                                                                                                                                         (when (and (= toimenkuva (:toimenkuva jh-pohjadata))
                                                                                                                                                    (= maksukausi (:maksukausi jh-pohjadata)))
                                                                                                                                           index))
                                                                                                                                       t/johto-ja-hallintokorvaukset-pohjadata))]]
                                                                                                (rividisable! g index kuukausitasolla?)))))}
                                  :lisaa-yhteenvetorivi {:polut (reduce (fn [polut jarjestysnumero]
                                                                          (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                                                            (conj polut [:domain :johto-ja-hallintokorvaukset nimi])))
                                                                        []
                                                                        (range 1 (inc t/jh-korvausten-omiariveja-lkm)))
                                                         :toiminto! (fn [_ data & oma-data]
                                                                      (let [omien-rivien-tiedot (transduce (comp (map ffirst)
                                                                                                                 (map-indexed #(assoc %2 :jarjestysnumero (inc %1))))
                                                                                                           conj
                                                                                                           []
                                                                                                           oma-data)
                                                                            lisatyt-rivit (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :lisatyt-rivit] #{})]
                                                                        (doseq [{:keys [toimenkuva toimenkuva-id jarjestysnumero]} omien-rivien-tiedot]
                                                                          (cond
                                                                            (and (not (contains? lisatyt-rivit toimenkuva-id))
                                                                                 (not (empty? toimenkuva)))
                                                                            (let [omanimi (t/jh-omienrivien-nimi jarjestysnumero)
                                                                                  lisattava-rivi (grid/aseta-nimi (grid/samanlainen-osa (grid/get-in-grid (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                                                                                                                                          [::g-pohjat/data 0]))
                                                                                                                  omanimi)
                                                                                  rivin-paikka-index (count (grid/hae-grid (grid/get-in-grid (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                                                                                                                             [::g-pohjat/data])
                                                                                                                           :lapset))]
                                                                              (e! (tuck-apurit/->PaivitaTila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :lisatyt-rivit]
                                                                                                             (fn [lisatyt-rivit]
                                                                                                               (conj (or lisatyt-rivit #{}) toimenkuva-id))))
                                                                              (grid/lisaa-rivi! (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                                                                                lisattava-rivi
                                                                                                [1 rivin-paikka-index])
                                                                              (grid/lisaa-uuden-osan-rajapintakasittelijat (grid/get-in-grid (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                                                                                                                             [1 rivin-paikka-index])
                                                                                                                           [:. ::yhteenveto] {:rajapinta (keyword (str "yhteenveto-" omanimi))
                                                                                                                                              :solun-polun-pituus 1
                                                                                                                                              :datan-kasittely identity})
                                                                              (t/paivita-raidat! (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])))
                                                                            (and (contains? lisatyt-rivit toimenkuva-id)
                                                                                 (empty? toimenkuva))
                                                                            (let [omanimi (t/jh-omienrivien-nimi jarjestysnumero)
                                                                                  g (get-in data [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                                                                                  poistettava-rivi (grid/get-in-grid g
                                                                                                                     [::g-pohjat/data omanimi])]
                                                                              (e! (tuck-apurit/->PaivitaTila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :lisatyt-rivit]
                                                                                                             (fn [lisatyt-rivit]
                                                                                                               (disj (or lisatyt-rivit #{}) toimenkuva-id))))
                                                                              (grid/poista-osan-rajapintakasittelijat poistettava-rivi)
                                                                              (grid/poista-rivi! g poistettava-rivi))
                                                                            :else nil))))}}
                                 (reduce (fn [polut jarjestysnumero]
                                           (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                             (merge polut
                                                    {(keyword "piillota-itsetaytettyja-riveja-" nimi) {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :maksukausi]]
                                                                                                       :toiminto! (fn [g _ maksukausi]
                                                                                                                    (let [naytettavat-kuukaudet (into #{} (bj/maksukauden-kuukaudet maksukausi))]
                                                                                                                      (doseq [rivi (grid/hae-grid (grid/get-in-grid (grid/etsi-osa g nimi) [1]) :lapset)]
                                                                                                                        (let [aika (grid/solun-arvo (grid/get-in-grid rivi [0]))
                                                                                                                              piillotetaan? (and aika (not (contains? naytettavat-kuukaudet (pvm/kuukausi aika))))]
                                                                                                                          (if piillotetaan?
                                                                                                                            (grid/piillota! rivi)
                                                                                                                            (grid/nayta! rivi))))
                                                                                                                      (t/paivita-raidat! (grid/osa-polusta g [::g-pohjat/data]))))}
                                                     (keyword "omarivi-disable-" nimi) {:polut [[:domain :johto-ja-hallintokorvaukset nimi]
                                                                                                [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? nimi]]
                                                                                        :toiminto! (fn [g tila oma-jh-korvausten-tila kuukausitasolla?]
                                                                                                     (let [hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
                                                                                                           toimenkuva (get-in oma-jh-korvausten-tila [(dec hoitokauden-numero) 0 :toimenkuva])
                                                                                                           index (dec (+ (count t/johto-ja-hallintokorvaukset-pohjadata)
                                                                                                                         jarjestysnumero))
                                                                                                           yhteenvetorivi (if (grid/rivi? (grid/get-in-grid g [::g-pohjat/data index ::data-yhteenveto 0]))
                                                                                                                            (grid/get-in-grid g [::g-pohjat/data index ::data-yhteenveto 0])
                                                                                                                            (grid/get-in-grid g [::g-pohjat/data index ::data-yhteenveto]))]
                                                                                                       (if (empty? toimenkuva)
                                                                                                         (do
                                                                                                           (maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data index ::data-sisalto])
                                                                                                                                   true)
                                                                                                           (disable-osa-indexissa! yhteenvetorivi #{1 2 4} true))
                                                                                                         (do (rividisable! g
                                                                                                                           (dec (+ (count t/johto-ja-hallintokorvaukset-pohjadata)
                                                                                                                                   jarjestysnumero))
                                                                                                                           kuukausitasolla?)
                                                                                                             (disable-osa-indexissa! yhteenvetorivi #{2 4} false)))))}})))
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


(defn- maarataulukon-pohja [taulukon-id
                           indeksikorjaus?
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
  (let [yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
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
        g (g-pohjat/uusi-taulukko {:header (cond-> [{:tyyppi :teksti
                                                     :leveys 3
                                                     :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                                    {:tyyppi :teksti
                                                     :leveys 2
                                                     :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                                    {:tyyppi :teksti
                                                     :leveys 2
                                                     :luokat #{"table-default" "table-default-header" "lihavoitu"}}]
                                             indeksikorjaus? (conj {:tyyppi :teksti
                                                                    :leveys 1
                                                                    :luokat #{"table-default" "table-default-header" "harmaa-teksti" "lihavoitu"}}))
                                   :body [{:tyyppi :taulukko
                                           :nimi ::data-rivi
                                           :osat [{:tyyppi :rivi
                                                   :nimi ::data-yhteenveto
                                                   :osat (cond-> [{:tyyppi :laajenna
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
                                                                               :on-blur on-blur
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
                                                                   :fmt summa-formatointi
                                                                   :fmt-aktiivinen summa-formatointi-aktiivinen}
                                                                  {:tyyppi :teksti
                                                                   :luokat #{"table-default"}
                                                                   :fmt summa-formatointi}]
                                                           indeksikorjaus? (conj {:tyyppi :teksti
                                                                                  :luokat #{"table-default" "harmaa-teksti"}
                                                                                  :fmt summa-formatointi}))}
                                                  {:tyyppi :taulukko
                                                   :nimi ::data-sisalto
                                                   :luokat #{"piillotettu"}
                                                   :osat (vec (repeatedly 12
                                                                (fn []
                                                                  {:tyyppi :rivi
                                                                   :osat (cond-> [{:tyyppi :teksti
                                                                                   :luokat #{"table-default"}
                                                                                   :fmt aika-tekstilla-fmt}
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
                                                                                   :fmt summa-formatointi
                                                                                   :fmt-aktiivinen summa-formatointi-aktiivinen}
                                                                                  {:tyyppi :teksti
                                                                                   :luokat #{"table-default"}
                                                                                   :fmt summa-formatointi}]
                                                                           indeksikorjaus? (conj {:tyyppi :teksti
                                                                                                  :luokat #{"table-default" "harmaa-teksti"}}))})))}]}]
                                   :footer (cond-> [{:tyyppi :teksti
                                                     :luokat #{"table-default" "table-default-sum"}}
                                                    {:tyyppi :teksti
                                                     :luokat #{"table-default" "table-default-sum"}}
                                                    {:tyyppi :teksti
                                                     :luokat #{"table-default" "table-default-sum"}
                                                     :fmt yhteenveto-format}]
                                             indeksikorjaus? (conj {:tyyppi :teksti
                                                                    :luokat #{"table-default" "table-default-sum" "harmaa-teksti"}
                                                                    :fmt yhteenveto-format}))
                                   :taulukon-id taulukon-id
                                   :root-asetus! root-asetus!
                                   :root-asetukset root-asetukset
                                   :root-luokat (when (t/alin-taulukko? taulukon-id)
                                                  #{"viimeinen-taulukko"})})]
    g))

(defn maarataulukko-grid
  "Luo määrataulukko-gridin ja tallentaa sen tilan annetulla nimellä :gridit-polkuun, kuten muutkin gridit."
  ([nimi yhteenvedot-polku] (maarataulukko-grid nimi yhteenvedot-polku true true))
  ([nimi yhteenvedot-polku paivita-kattohinta? indeksikorjaus?]
   (let [toiminto-fn! (fn -t-fn!
                        ([polun-osa solu]
                         (println "toiminto-fn maara" polun-osa)
                         (let [vanhempiosa (grid/osa-polusta solu [:.. :..])
                               tallennettavien-arvojen-osat (if (= ::data-rivi (grid/hae-osa vanhempiosa :nimi))
                                                              (grid/hae-grid (grid/osa-polusta vanhempiosa [1]) :lapset)
                                                              (grid/hae-grid (grid/osa-polusta vanhempiosa [:.. 1]) :lapset))]
                           (e! (t/->TallennaKustannusarvoitu polun-osa (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                               :tunniste-rajapinnan-dataan)
                                                                             tallennettavien-arvojen-osat))))
                         (when paivita-kattohinta?
                           (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
         toiminto-nappi-fn! (fn -t-nappi-fn!
                              ([polun-osa rivit-alla]
                               (println "toiminto-fn maara nappi" polun-osa)
                               (e! (t/->TallennaKustannusarvoitu polun-osa
                                                                 (vec (keep (fn [rivi]
                                                                              (let [maara-solu (grid/get-in-grid rivi [1])
                                                                                    piillotettu? (grid/piillotettu? rivi)]
                                                                                (when-not piillotettu?
                                                                                  (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                                                                            rivit-alla))))
                               (when paivita-kattohinta?
                                 (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
         toiminto-kk-fn! (fn -t-kk-fn!
                           ([polun-osa solu]
                            (println "toiminto-fn maara kk" polun-osa)
                            (e! (t/->TallennaKustannusarvoitu polun-osa [(grid/solun-asia solu :tunniste-rajapinnan-dataan)]))
                            (when paivita-kattohinta?
                              (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
         polun-osa (keyword nimi)
         disablerivit-avain (keyword (str nimi "-disablerivit"))
         aseta-yhteenveto-avain (keyword (str "aseta-" nimi "-yhteenveto!"))
         aseta-avain (keyword (str "aseta-" nimi "!"))
         jarjestysvektori (with-meta (if indeksikorjaus?
                                       [:nimi :maara :yhteensa :indeksikorjattu]
                                       [:nimi :maara :yhteensa])
                                     {:nimi :mapit})
         jarjestysvektori-body (with-meta (if indeksikorjaus?
                                            [:aika :maara :yhteensa :indeksikorjattu]
                                            [:aika :maara :yhteensa])
                                          {:nimi :mapit})
         yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
                                              :solun-polun-pituus 1
                                              :jarjestys [jarjestysvektori]
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
                                indeksikorjaus?
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
                                  (t/paivita-solun-arvo {:paivitettava-asia aseta-yhteenveto-avain
                                                         :arvo arvo
                                                         :solu solu/*this*
                                                         :ajettavat-jarejestykset #{:mapit}
                                                         :triggeroi-seuranta? true}
                                                        true)
                                  (toiminto-fn! polun-osa solu/*this*))
                                (fn [rivit-alla arvo]
                                  (when (not (empty? rivit-alla))
                                    (doseq [rivi rivit-alla
                                            :let [maara-solu (grid/get-in-grid rivi [1])]]
                                      (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                                             :arvo arvo
                                                             :solu maara-solu
                                                             :ajettavat-jarejestykset #{:mapit}
                                                             :triggeroi-seuranta? true}
                                                            true))
                                    (toiminto-nappi-fn! polun-osa rivit-alla)))
                                (fn [arvo]
                                  (when arvo
                                    (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                                           :arvo arvo
                                                           :solu solu/*this*
                                                           :ajettavat-jarejestykset #{:mapit}}
                                                          false)))
                                (fn [arvo]
                                  (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                                         :arvo arvo
                                                         :solu solu/*this*
                                                         :ajettavat-jarejestykset true
                                                         :triggeroi-seuranta? true}
                                                        true)
                                  (println aseta-avain)
                                  (toiminto-kk-fn! polun-osa solu/*this*)))
         rajapinta (t/maarataulukon-rajapinta polun-osa aseta-yhteenveto-avain aseta-avain)]
     (grid/rajapinta-grid-yhdistaminen! g
                                        rajapinta
                                        (t/maarataulukon-dr indeksikorjaus? rajapinta polun-osa yhteenvedot-polku aseta-avain aseta-yhteenveto-avain)
                                        {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                                               :solun-polun-pituus 1
                                                               :jarjestys [jarjestysvektori]
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
                                                                                         jarjestysvektori-body]
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
                                                                  :jarjestys [jarjestysvektori]
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
  [{:keys [otsikko selite hinnat data-cy]} {kuluva-hoitokauden-numero :hoitokauden-numero}]
  (if (some #(or (nil? (:summa %))
                 (js/isNaN (:summa %)))
            hinnat)
    [:div ""]
    [:div.hintalaskuri {:data-cy data-cy}
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
                          (fmt-euro summa)
                          (when (= hoitokauden-numero kuluva-hoitokauden-numero) {:wrapper-luokat "aktiivinen-vuosi"})]))
                     hinnat))
      [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
      [hintalaskurisarake "Yhteensä" (fmt-euro (reduce #(+ %1 (:summa %2)) 0 hinnat))]]]))

(defn indeksilaskuri
  ([hinnat indeksit] [indeksilaskuri hinnat indeksit nil])
  ([hinnat indeksit {:keys [dom-id data-cy]}]
   (let [hinnat (vec (map-indexed (fn [index {:keys [summa]}]
                                    (let [hoitokauden-numero (inc index)
                                          {:keys [vuosi]} (get indeksit index)
                                          indeksikorjattu-summa (t/indeksikorjaa summa hoitokauden-numero)]
                                      {:vuosi vuosi
                                       :summa indeksikorjattu-summa
                                       :hoitokauden-numero hoitokauden-numero}))
                                  hinnat))]
     [:div.hintalaskuri.indeksilaskuri {:id dom-id
                                        :data-cy data-cy}
      [:span "Indeksikorjatut yhteensä"]
      [:div.hintalaskuri-vuodet
       (for [{:keys [vuosi summa hoitokauden-numero]} hinnat]
         ^{:key hoitokauden-numero}
         [hintalaskurisarake vuosi (fmt-euro summa)])
       [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
       [hintalaskurisarake "Yhteensä" (fmt-euro (reduce #(+ %1 (:summa %2)) 0 hinnat)) {:container-luokat "hintalaskuri-yhteensa"}]]])))

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
                                          (-> toimenpide t/toimenpide-formatointi clj-str/upper-case)))
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
         [:input#kopioi-hankinnat-tuleville-hoitovuosille.vayla-checkbox
          {:type "checkbox" :checked kopioidaan-tuleville-vuosille?
           :on-change vaihda-fn}]
         [:label {:for "kopioi-hankinnat-tuleville-hoitovuosille"}
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



;; #### OSIOT ####

;; -- hankintakustannukset-taulukot-osio --

(defn arvioidaanko-laskutukseen-perustuen [_ _ _]
  (let [vaihda-fn (fn [toimenpide event]
                    (let [valittu? (.. event -target -checked)
                          paivita-ui! (fn []
                                        (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
                                                                       (fn [valinnat]
                                                                         (disj valinnat toimenpide))))
                                        (t/laskutukseen-perustuvan-taulukon-nakyvyys!)
                                        (modal/piilota!))]
                      (if valittu?
                        (do (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
                                                           (fn [valinnat]
                                                             (conj valinnat toimenpide))))
                            (t/laskutukseen-perustuvan-taulukon-nakyvyys!))
                        (t/poista-laskutukseen-perustuen-data! toimenpide
                                                               paivita-ui!
                                                               (r/partial (fn [data-hoitokausittain poista!]
                                                                            (poista-modal! :maaramitattava
                                                                                           data-hoitokausittain
                                                                                           (comp poista!
                                                                                                 paivita-ui!)
                                                                                           {:toimenpide toimenpide})))))))]
    (fn [{:keys [toimenpide]} laskutukseen-perustuen? on-oikeus?]
      [:div#laskutukseen-perustuen-filter
       [:input#laskutukseen-perustuen.vayla-checkbox
        {:type "checkbox" :checked laskutukseen-perustuen?
         :on-change (partial vaihda-fn toimenpide) :disabled (not on-oikeus?)}]
       [:label {:for "laskutukseen-perustuen"}
        "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle: "
        [:b (t/toimenpide-formatointi toimenpide)]]])))


(defn laskutukseen-perustuen-wrapper [g nayta-laskutukseen-perustuva-taulukko?]
  (when-not nayta-laskutukseen-perustuva-taulukko?
    (grid/piillota! g))
  (fn [g _]
    [grid/piirra g]))

(defn hankintakustannukset-taulukot-osio [kirjoitusoikeus?
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
     [:h2#hankintakustannukset-osio "Hankintakustannukset"]
     (if yhteenveto
       ^{:key "hankintakustannusten-yhteenveto"}
       [:div.summa-ja-indeksilaskuri
        [hintalaskuri {:otsikko "Yhteenveto"
                       :selite "Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestointi"
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
        [:h3 "Toimenpiteen rahavaraukset"]
        [yleis-suodatin (dissoc suodattimet :hankinnat)]
        (if rahavaraukset-taulukko-valmis?
          [grid/piirra rahavaraukset-grid]
          [yleiset/ajax-loader])])]))


;; -- erillishankinnat-osio --

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

(defn erillishankinnat-osio
  [erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi]
  [erillishankinnat-sisalto erillishankinnat-grid erillishankinnat-yhteensa indeksit kantahaku-valmis? suodattimet kuluva-hoitokausi])


;; -- johto-ja-hallintokorvaus-osio --

(defn johto-ja-hallintokorvaus-yhteenveto
  [johto-ja-hallintokorvaukset-yhteensa toimistokulut-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    (let [hinnat (mapv (fn [jh tk]
                         {:summa (+ jh tk)})
                   johto-ja-hallintokorvaukset-yhteensa
                   toimistokulut-yhteensa)]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Palkat + Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten järj. jne. + Hoito- ja korjaustöiden pientarvikevarasto"
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

(defn johto-ja-hallintokorvaus-osio
  [johto-ja-hallintokorvaus-grid
   johto-ja-hallintokorvaus-yhteenveto-grid
   toimistokulut-grid
   suodattimet
   johto-ja-hallintokorvaukset-yhteensa
   toimistokulut-yhteensa
   kuluva-hoitokausi
   indeksit
   kantahaku-valmis?]
  [johto-ja-hallintokorvaus johto-ja-hallintokorvaus-grid johto-ja-hallintokorvaus-yhteenveto-grid toimistokulut-grid suodattimet johto-ja-hallintokorvaukset-yhteensa toimistokulut-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?])


;; -- hoidonjohtopalkkio-osio --

(defn hoidonjohtopalkkio [hoidonjohtopalkkio-grid kantahaku-valmis?]
  (if (and hoidonjohtopalkkio-grid kantahaku-valmis?)
    [grid/piirra hoidonjohtopalkkio-grid]
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-yhteenveto
  [hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    (let [hinnat (mapv (fn [hjp]
                         {:summa hjp})
                   hoidonjohtopalkkio-yhteensa)]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Hoidonjohtopalkkio"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-sisalto [hoidonjohtopalkkio-grid suodattimet hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  [:<>
   [:h3 {:id (str (get t/hallinnollisten-idt :hoidonjohtopalkkio) "-osio")} "Hoidonjohtopalkkio"]
   [hoidonjohtopalkkio-yhteenveto hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
   [yleis-suodatin suodattimet]
   [hoidonjohtopalkkio hoidonjohtopalkkio-grid kantahaku-valmis?]])

(defn hoidonjohtopalkkio-osio
  [hoidonjohtopalkkio-grid
   hoidonjohtopalkkio-yhteensa
   indeksit
   kuluva-hoitokausi
   suodattimet
   kantahaku-valmis?]
  [hoidonjohtopalkkio-sisalto hoidonjohtopalkkio-grid suodattimet hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?])


;; -- tavoite-ja-kattohinto-osio --

(defn- tavoitehinta-yhteenveto
[tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [hintalaskuri {:otsikko "Tavoitehinta"
                    :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
                    :hinnat (update tavoitehinnat 0 assoc :teksti "1. vuosi*")
                    :data-cy "tavoitehinnan-hintalaskuri"}
      kuluva-hoitokausi]
     [indeksilaskuri tavoitehinnat indeksit {:dom-id "tavoitehinnan-indeksikorjaus"
                                             :data-cy "tavoitehinnan-indeksilaskuri"}]]
    [yleiset/ajax-loader]))

(defn- kattohinta-yhteenveto
  [kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [hintalaskuri {:otsikko "Kattohinta"
                    :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
                    :hinnat kattohinnat}
      kuluva-hoitokausi]
     [indeksilaskuri kattohinnat indeksit]]
    [yleiset/ajax-loader]))

(defn tavoite-ja-kattohinto-osio [yhteenvedot kuluva-hoitokausi indeksit kantahaku-valmis?]
  ;; TODO: Toteuta kattohinnalle käsin syöttämisen mahdollisuus myöhemmin: VHAR-4858
  (let [tavoitehinnat (mapv (fn [summa]
                              {:summa summa})
                        (t/tavoitehinnan-summaus yhteenvedot))
        kattohinnat (mapv #(update % :summa * 1.1) tavoitehinnat)]
    [:<>
     [:h3 {:id (str "tavoite-ja-kattohinta" "-osio")} "Tavoite- ja kattohinta"]
     [tavoitehinta-yhteenveto tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
     [:span#tavoite-ja-kattohinta-huomio "Vuodet ovat hoitovuosia"]
     [kattohinta-yhteenveto kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]]))



(defn tilaajan-varaukset-osio [tilaajan-varaukset-grid suodattimet kantahaku-valmis?]
  (let [nayta-tilaajan-varaukset-grid? (and kantahaku-valmis? tilaajan-varaukset-grid)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :tilaajan-varaukset) "-osio")} "Tilaajan rahavaraukset"]
     [:div [:span "Varaukset mm. bonuksien laskemista varten. Näitä varauksia"] [:span.lihavoitu " ei lasketa mukaan tavoitehintaan"]]
     [yleis-suodatin suodattimet]
     (if nayta-tilaajan-varaukset-grid?
       [grid/piirra tilaajan-varaukset-grid]
       [yleiset/ajax-loader])]))


;; ----

(defn- summa-komp
  [m]
  [:div.sisalto
   [:div.otsikko (str (or (:otsikko m)
                          "-"))]
   [:div.summa (str
                 (fmt/euro
                   (or (:summa m)
                       0)))]])

(defn- navigointivalikko
  "Navigointivalikko, joka näyttää vierityslinkit osioon ja tiedon siitä onko osio vahvistettu vai ei."
  [avaimet hoitokausi {:keys [urakka indeksit-saatavilla?]} tiedot]
  [:<>
   [:div.flex-row
    [:div
     [:h3 "Kustannussuunnitelma"]
     [:div.pieni-teksti urakka]]
    [valinnat/hoitovuosi-rivivalitsin (range 1 6) hoitokausi #(e! (tuck-apurit/->MuutaTila [:suodattimet :hoitokauden-numero] %1))]]
   [:div#tilayhteenveto.hintalaskuri
    ;; Taulukon rivit
    (into [:<>]
      (keep identity
        (mapcat identity
          (for [a avaimet]
            (let [{:keys [nimi suunnitelma-vahvistettu? summat] :as tieto} (a tiedot)
                  summat (mapv summa-komp summat)
                  {:keys [ikoni tyyppi teksti]}
                  (cond suunnitelma-vahvistettu?
                        {:ikoni ikonit/check :tyyppi ::yleiset/ok :teksti "Vahvistettu"}

                        indeksit-saatavilla?
                        {:ikoni ikonit/aika :tyyppi ::yleiset/info :teksti "Odottaa vahvistusta"}

                        :else
                        {:ikoni ikonit/exclamation-sign :tyyppi ::yleiset/huomio :teksti "Indeksejä ei vielä saatavilla"})]
              (when tieto
                [[:div.flex-row
                  [:div
                   [:div [yleiset/linkki (str nimi) (vieritys/vierita a)]]]
                  [:div
                   [:div [yleiset/infolaatikko ikoni teksti tyyppi]]]]
                 (vec (keep identity
                        (concat [:div.flex-row.alkuun]
                          summat)))]))))))]])

(defn- laske-hankintakustannukset
  [hoitokausi suunnitellut laskutus varaukset]
  (let [indeksi (dec hoitokausi)
        kaikki (concat (mapcat vals #{suunnitellut laskutus})
                       (mapcat vals (vals varaukset)))]
    (reduce #(+ %1 (nth %2 indeksi)) 0 kaikki)))

(defn- menukomponentti
  [{:keys [avaimet app indeksit-saatavilla?]}]
  (r/with-let
    [poisto-fn (fn [[avain solut]]
                 [avain (->> solut
                             (keep identity)
                             vec)])
     poista-nilit (fn [m]
                    (into {}
                          (mapv poisto-fn m)))]
    (if (:kantahaku-valmis? app)
      (let [hoitokausi (get-in app [:suodattimet :hoitokauden-numero])
            indeksikerroin (get-in app [:domain :indeksit (dec hoitokausi) :indeksikerroin])
            {{:keys [suunnitellut-hankinnat
                     laskutukseen-perustuvat-hankinnat
                     rahavaraukset] :as _summat} :summat :as _hankintakustannukset} (get-in app [:yhteenvedot :hankintakustannukset])
            hankintakustannukset-summa (laske-hankintakustannukset
                                         hoitokausi
                                         suunnitellut-hankinnat
                                         laskutukseen-perustuvat-hankinnat
                                         rahavaraukset)
            erillishankinnat-summa (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :erillishankinnat (dec hoitokausi)])
            johto-ja-hallintokorvaukset-summa (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset (dec hoitokausi)])
            hoidonjohtopalkkio-summa (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio (dec hoitokausi)])
            tavoitehinta-summa (+ hankintakustannukset-summa erillishankinnat-summa johto-ja-hallintokorvaukset-summa hoidonjohtopalkkio-summa)
            kattohinta-summa (* tavoitehinta-summa 1.1)
            tilaajan-varaukset-summa (get-in app [:yhteenvedot :tilaajan-varaukset :summat :tilaajan-varaukset (dec hoitokausi)])

            haettavat-tilat #{:erillishankinnat :hankintakustannukset :hoidonjohtopalkkio :johto-ja-hallintokorvaus :tavoite-ja-kattohinta :tilaajan-varaukset}
            suunnitelman-tilat (get-in app [:domain :tilat])
            {hankintakustannukset-vahvistettu? :hankintakustannukset
             erillishankinnat-vahvistettu? :erillishankinnat
             johto-ja-hallintokorvaus-vahvistettu? :johto-ja-hallintokorvaus
             hoidonjohtopalkkio-vahvistettu? :hoidonjohtopalkkio
             tavoite-ja-kattohinta-vahvistettu? :tavoite-ja-kattohinta
             tilaajan-varaukset-vahvistettu? :tilaajan-varaukset} (into {} (mapv #(-> [% (get-in suunnitelman-tilat [% hoitokausi])]) haettavat-tilat))

            {:keys [summa-hankinnat summa-erillishankinnat summa-hoidonjohtopalkkio summa-tilaajan-varaukset summa-johto-ja-hallintokorvaus summa-tavoite-ja-kattohinta]}
            (poista-nilit
              {:summa-hankinnat [{:otsikko "Yhteensä"
                                  :summa hankintakustannukset-summa}
                                 (when indeksit-saatavilla? {:otsikko "Indeksikorjattu"
                                                             :summa (* hankintakustannukset-summa indeksikerroin)})]
               :summa-erillishankinnat [{:otsikko "Yhteensä"
                                         :summa erillishankinnat-summa}
                                        (when indeksit-saatavilla?
                                          {:otsikko "Indeksikorjattu"
                                           :summa (* erillishankinnat-summa indeksikerroin)})]
               :summa-hoidonjohtopalkkio [{:otsikko "Yhteensä"
                                           :summa hoidonjohtopalkkio-summa}
                                          (when indeksit-saatavilla?
                                            {:otsikko "Indeksikorjattu"
                                             :summa (* hoidonjohtopalkkio-summa indeksikerroin)})]
               :summa-tilaajan-varaukset [{:otsikko "Yhteensä"
                                           :summa tilaajan-varaukset-summa}
                                          (when indeksit-saatavilla?
                                            {:otsikko "Indeksikorjattu"
                                             :summa (* tilaajan-varaukset-summa indeksikerroin)})]
               :summa-johto-ja-hallintokorvaus [{:otsikko "Yhteensä"
                                                 :summa johto-ja-hallintokorvaukset-summa}
                                                (when indeksit-saatavilla?
                                                  {:otsikko "Indeksikorjattu"
                                                   :summa (* johto-ja-hallintokorvaukset-summa indeksikerroin)})]
               :summa-tavoite-ja-kattohinta [{:summa tavoitehinta-summa
                                              :otsikko "Tavoitehinta yhteensä"}
                                             (when indeksit-saatavilla?
                                               {:summa (* tavoitehinta-summa indeksikerroin)
                                                :otsikko "Tavoitehinta indeksikorjattu"})
                                             {:summa kattohinta-summa
                                              :otsikko "Kattohinta yhteensä"}
                                             (when indeksit-saatavilla?
                                               {:summa (* kattohinta-summa indeksikerroin)
                                                :otsikko "Kattohinta indeksikorjattu"})]})]
        [navigointivalikko
         avaimet
         hoitokausi
         {:urakka (-> @tila/yleiset :urakka :nimi)
          :soluja (count summa-tavoite-ja-kattohinta)
          :indeksit-saatavilla? indeksit-saatavilla?}
         {::hankintakustannukset {:nimi "Hankintakustannukset"
                                  :summat summa-hankinnat
                                  :suunnitelma-vahvistettu? hankintakustannukset-vahvistettu?}
          ::erillishankinnat {:nimi "Erillishankinnat"
                              :summat summa-erillishankinnat
                              :suunnitelma-vahvistettu? erillishankinnat-vahvistettu?}
          ::johto-ja-hallintokorvaukset {:nimi "Johto- ja hallintokorvaus"
                                         :summat summa-johto-ja-hallintokorvaus
                                         :suunnitelma-vahvistettu? johto-ja-hallintokorvaus-vahvistettu?}
          ::hoidonjohtopalkkio {:nimi "Hoidonjohtopalkkio"
                                :summat summa-hoidonjohtopalkkio
                                :suunnitelma-vahvistettu? hoidonjohtopalkkio-vahvistettu?}
          ::tavoite-ja-kattohinta {:nimi "Tavoite- ja kattohinta"
                                   :suunnitelma-vahvistettu? tavoite-ja-kattohinta-vahvistettu?
                                   :summat summa-tavoite-ja-kattohinta}
          ::tilaajan-varaukset {:nimi "Tilaajan rahavaraukset"
                                :summat summa-tilaajan-varaukset
                                :suunnitelma-vahvistettu? tilaajan-varaukset-vahvistettu?}}])
      [yleiset/ajax-loader "Haetaan tietoja"])))

(defn- osionavigointi
  "Kontrollit navigointiin kustannussuunnitelman osien välillä ylös / alas / takaisin sivun alkuun."
  [{:keys [avaimet nykyinen]}]
  (loop [edellinen nil
         jaljella avaimet]
    (if (or (= nykyinen (first jaljella))
          (nil? (first jaljella)))
      [:div.navigointirivi
       [:div.ylos
        [napit/kotiin "Takaisin alkuun" (vieritys/vierita-ylos)]]
       [:div.edellinen-seuraava
        (when edellinen [napit/ylos "Edellinen osio" (vieritys/vierita edellinen)])
        (when (second jaljella) [napit/alas "Seuraava osio" (vieritys/vierita (second jaljella))])]
       [:div.piiloon                                        ; tämä on semmoinen hack että elementit tasoittuu oikein, ihan puhtaasti
        [napit/kotiin "Tää on puhdas hack" (vieritys/vierita-ylos)]]]
      (recur (first jaljella)
        (rest jaljella)))))


;; -- Osion vahvistus --

(defn selite-modal
  [laheta-fn! muuta-fn! vahvistus]
  [modal/modal {:otsikko "Sun pitää vahvistaa tää"
                :nakyvissa? true
                :sulje-fn #(e! (t/->SuljeVahvistus))}
   [:div "Please confirm"
    [:div "vahvistus" [debug/debug vahvistus]]
    (for [v (keys (:vahvistettavat-vuodet vahvistus))]
      [:div
       [:h3 (str "vuosi " v)]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :muutoksen-syy)}]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :selite)}]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :maara)}]])
    [:button {:on-click (r/partial laheta-fn! e! (:tiedot vahvistus))} "Klikkeris"]]])

(defn vahvista-suunnitelman-osa-komponentti
  "Komponentilla vahvistetaan yksittäinen kustannussuunnitelman osio.
  TODO: Keskeneräinen placeholder."
  [_ _]
  (let [auki? (r/atom false)
        tilaa-muutettu? false
        vahvista-suunnitelman-osa-fn #(e! (t/->VahvistaSuunnitelmanOsioVuodella {:tyyppi %1
                                                                                 :hoitovuosi %2}))
        avaa-tai-sulje #(swap! auki? not)]
    (fn [osion-nimi {:keys [hoitovuosi indeksit-saatavilla? on-tila?]}]
      (let [disabloitu? (not (and (roolit/tilaajan-kayttaja? @istunto/kayttaja)
                               indeksit-saatavilla?))]
        [:div.vahvista-suunnitelman-osa
         [:div.flex-row
          [yleiset/placeholder "IKONI"]
          (str "Vahvista suunnitelma ja hoitovuoden " hoitovuosi " indeksikorjaukset")
          [yleiset/placeholder (str "Auki? " @auki?)
           {:placeholderin-optiot {:on-click avaa-tai-sulje}}]]
         (when @auki?
           [:<>
            [:div.flex-row
             [yleiset/placeholder (pr-str @istunto/kayttaja)]
             [yleiset/placeholder (str "Oon auki" osion-nimi " ja disabloitu? " disabloitu? "ja on tila? " on-tila? " ja indeksit-saatavilla? " indeksit-saatavilla? " ja " (roolit/tilaajan-kayttaja? @istunto/kayttaja))]]
            [:div.flex-row
             "Jos suunnitelmaa muutetaan tämän jälkeen, ei erotukselle tehdä enää indeksikorjausta. Indeksikorjaus on laskettu vain alkuperäiseen lukuun."]
            [:div.flex-row
             (if (and on-tila?
                   (not disabloitu?)
                   (not tilaa-muutettu?))
               "Kumoa vahvistus"
               [napit/yleinen-ensisijainen "Vahvista"
                vahvista-suunnitelman-osa-fn
                {:disabled disabloitu?
                 :toiminto-args [osion-nimi hoitovuosi]}])
             [yleiset/placeholder (str (when disabloitu? "Vain urakan aluevastaava voi vahvistaa suunnitelman") indeksit-saatavilla? disabloitu?)]]])]))))


;; --  Päänäkymä ---

(defonce ^{:doc "Jos vaihdellaan tabeja kustannussuunnitelmasta jonnekkin muualle
                 nopeasti, niin async taulukkojen luonti voi aiheuttaa ongelmia.
                 Tämän avulla tarkastetaan, että taulukkojen tila on ok."}
  lopeta-taulukkojen-luonti? (cljs.core/atom false))

(def gridien-polut
  "Gridien polut näkymän tilassa. Näitä käytetään gridien piirtämisessä.
   Yksittäisellä kustannussuunnitelman osiolla voi olla tarve päästä käsiksi usean gridin tilaan."
  [[:gridit :suunnittellut-hankinnat :grid]
   [:gridit :laskutukseen-perustuvat-hankinnat :grid]
   [:gridit :rahavaraukset :grid]
   [:gridit :erillishankinnat :grid]
   [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid]
   [:gridit :johto-ja-hallintokorvaukset :grid]
   [:gridit :toimistokulut :grid]
   [:gridit :hoidonjohtopalkkio :grid]
   [:gridit :tilaajan-varaukset :grid]])

(defn kustannussuunnitelma*
  [_ app]
  (let [nakyman-setup (cljs.core/atom {:lahdetty-nakymasta? false})]
    (komp/luo
      ;; Alusta tila
      (komp/piirretty
        (fn [_]
          (swap! nakyman-setup
            (fn [{:keys [lahdetty-nakymasta?] :as setup}]
              (assoc setup
                :tilan-alustus
                (go-loop [siivotaan-edellista-taulukkoryhmaa? @lopeta-taulukkojen-luonti?]
                  (cond
                    lahdetty-nakymasta? nil
                    siivotaan-edellista-taulukkoryhmaa? (do (<! (async/timeout 500))
                                                            (recur @lopeta-taulukkojen-luonti?))
                    :else (do
                            (log "[kustannussuunnitelma] TILAN ALUSTUS")
                            (swap! tila/suunnittelu-kustannussuunnitelma (fn [_] tila/kustannussuunnitelma-default))
                            ;; Kutsutaan tilan alustavat Tuck-eventit
                            (loop [[event & events] [(t/->Hoitokausi)
                                                     (t/->TaulukoidenVakioarvot)
                                                     (t/->FiltereidenAloitusarvot)
                                                     (t/->YleisSuodatinArvot)
                                                     (t/->Oikeudet)
                                                     (t/->HaeKustannussuunnitelma)
                                                     (t/->HaeKustannussuunnitelmanTilat)]]
                              (when (and (not (:lahdetty-nakymasta? @nakyman-setup))
                                      (not (nil? event)))
                                (e! event)
                                (recur events)))


                            ;; Luo/päivittää taulukko-gridit ja tallentaa ne tilaan esim. [:gridit :suunnitelmien-tila :grid]
                            ;;  jos ne voi myöhemmin hakea piirrettäväksi grid/piirra!-funktiolla.
                            (loop [[tf & tfs]
                                   ;; tf = taulukko-f paivita-raidat? tapahtumien-tunnisteet
                                   [[suunnittellut-hankinnat-grid true nil]
                                    [hankinnat-laskutukseen-perustuen-grid true nil]
                                    [rahavarausten-grid false nil]
                                    [(partial maarataulukko-grid "erillishankinnat" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:erillishankinnat-disablerivit}]
                                    [johto-ja-hallintokorvaus-laskulla-grid true
                                     (reduce (fn [tapahtumien-tunnisteet jarjestysnumero]
                                               (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                                 (conj tapahtumien-tunnisteet (keyword "piillota-itsetaytettyja-riveja-" nimi))))
                                       #{}
                                       (range 1 (inc t/jh-korvausten-omiariveja-lkm)))]
                                    [johto-ja-hallintokorvaus-laskulla-yhteenveto-grid true nil]
                                    [(partial maarataulukko-grid "toimistokulut" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:toimistokulut-disablerivit}]
                                    [(partial maarataulukko-grid "hoidonjohtopalkkio" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:hoidonjohtopalkkio-disablerivit}]
                                    [(partial maarataulukko-grid "tilaajan-varaukset" [:yhteenvedot :tilaajan-varaukset] false false)
                                     true #{:tilaajan-varaukset-disablerivit}]]
                                   lahdetty-nakymasta? (:lahdetty-nakymasta? @nakyman-setup)]
                              (when (and (not lahdetty-nakymasta?)
                                      (not (nil? tf)))
                                (let [[taulukko-f paivita-raidat? tapahtumien-tunnisteet] tf
                                      taulukko (taulukko-f)]
                                  (when paivita-raidat?
                                    (t/paivita-raidat! (grid/osa-polusta taulukko [::g-pohjat/data])))
                                  (when tapahtumien-tunnisteet
                                    (doseq [tapahtuma-tunniste tapahtumien-tunnisteet]
                                      (grid/triggeroi-tapahtuma! taulukko tapahtuma-tunniste)))
                                  (recur tfs
                                    (:lahdetty-nakymasta? @nakyman-setup)))))))))))))

      ;; Siivoa näkymä
      (komp/ulos (fn []
                   (swap! nakyman-setup assoc :lahdetty-nakymasta? true)
                   (swap! tila/suunnittelu-kustannussuunnitelma assoc :gridit-vanhentuneet? true)
                   (when-not (some true? (for [grid-polku gridien-polut]
                                           (nil? (get-in @tila/suunnittelu-kustannussuunnitelma grid-polku))))
                     (reset! lopeta-taulukkojen-luonti? true)

                     (go (<! (:tilan-alustus @nakyman-setup))
                       ;; Gridien siivous
                       (doseq [grid-polku gridien-polut]
                         (grid/siivoa-grid! (get-in @tila/suunnittelu-kustannussuunnitelma grid-polku)))
                       (grid/poista-data-kasittelija! tila/suunnittelu-kustannussuunnitelma)

                       (log "[kustannussuunnitelma] SIIVOTTU")
                       (reset! lopeta-taulukkojen-luonti? false)))))

      ;; Render
      (fn [e*! {:keys [suodattimet gridit-vanhentuneet?] {{:keys [vaaditaan-muutoksen-vahvistus? tee-kun-vahvistettu]} :vahvistus} :domain :as app}]
        (set! e! e*!)
        (r/with-let [indeksit-saatavilla? (fn [app]
                                            (let [alkuvuosi (-> @tila/yleiset :urakka :alkupvm pvm/vuosi)
                                                  hoitovuodet (into {}
                                                                (map-indexed #(-> [(inc %1) %2])
                                                                  (range alkuvuosi (+ alkuvuosi 5))))]
                                              (some? (first (filter #(= (:vuosi %)
                                                                       (-> app
                                                                         (get-in [:suodattimet :hoitokauden-numero])
                                                                         hoitovuodet))
                                                              (get-in app [:domain :indeksit]))))))
                     onko-tila? (fn [avain app]
                                  (let [hoitovuosi (get-in app [:suodattimet :hoitokauden-numero])]
                                    (get-in app [:domain :tilat avain hoitovuosi])))]
          (if gridit-vanhentuneet?
            [yleiset/ajax-loader]
            ;; -- Intro / kustannussuunnitelma-tabin selostus
            [:div#kustannussuunnitelma
             [:div [:p "Suunnitelluista kustannuksista muodostetaan summa Sampon kustannussuunnitelmaa varten. Kustannussuunnitelmaa voi tarkentaa hoitovuoden kuluessa."]
              [:p "Hallinnollisiin toimenpiteisiin suunnitellut kustannukset siirtyvät kuukauden viimeisenä päivänä kuluna Sampon maksuerään." [:br]
               "Muut kulut urakoitsija syöttää Kulut-osiossa. Ne lasketaan mukaan maksueriin eräpäivän mukaan."]
              [:p "Sampoon lähetettävien kustannussuunnitelmasummien ja maksuerien tiedot löydät Kulut > Maksuerät-sivulta. " [:br]]]

             (when (< (count @urakka/urakan-toimenpideinstanssit) 7)
               [yleiset/virheviesti-sailio (str "Urakasta puuttuu toimenpideinstansseja, jotka täytyy siirtää urakkaan Samposta. Toimenpideinstansseja on urakassa nyt "
                                             (count @urakka/urakan-toimenpideinstanssit) " kun niitä tarvitaan 7.")])


             ;; -- Kustannussuunnitelman päämenu, jonka linkkejä klikkaamalla vieretetään näkymä liittyvään osioon.
             (vieritys/vieritettava-osio
               {:osionavigointikomponentti osionavigointi
                :menukomponentti menukomponentti
                :parametrit {:menu {:app app
                                    :indeksit-saatavilla? (indeksit-saatavilla? app)}
                             :navigointi {:indeksit-saatavilla? (indeksit-saatavilla? app)}}}

               ;; Osiot
               ::hankintakustannukset
               [debug/debug (get-in app [:domain :tilat])]
               [hankintakustannukset-taulukot-osio
                (get-in app [:domain :kirjoitusoikeus?])
                (get-in app [:domain :indeksit])
                (get-in app [:domain :kuluva-hoitokausi])
                (get-in app [:gridit :suunnittellut-hankinnat :grid])
                (get-in app [:gridit :laskutukseen-perustuvat-hankinnat :grid])
                (get-in app [:gridit :rahavaraukset :grid])
                (get-in app [:yhteenvedot :hankintakustannukset])
                (:kantahaku-valmis? app)
                suodattimet]
               [vahvista-suunnitelman-osa-komponentti :hankintakustannukset {:hoitovuosi (get-in app [:suodattimet :hoitokauden-numero])
                                                                             :indeksit-saatavilla? (indeksit-saatavilla? app)
                                                                             :on-tila? (onko-tila? :hankintakustannukset app)}]

               ::erillishankinnat
               [erillishankinnat-osio
                (get-in app [:gridit :erillishankinnat :grid])
                (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :erillishankinnat])
                (get-in app [:domain :indeksit])
                (:kantahaku-valmis? app)
                (dissoc suodattimet :hankinnat)
                (get-in app [:domain :kuluva-hoitokausi])]
               [vahvista-suunnitelman-osa-komponentti :erillishankinnat {:hoitovuosi (get-in app [:suodattimet :hoitokauden-numero])
                                                                         :indeksit-saatavilla? (indeksit-saatavilla? app)
                                                                         :on-tila? (onko-tila? :erillishankinnat app)}]

               ::johto-ja-hallintokorvaukset
               [johto-ja-hallintokorvaus-osio
                (get-in app [:gridit :johto-ja-hallintokorvaukset :grid])
                (get-in app [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid])
                (get-in app [:gridit :toimistokulut :grid])
                (dissoc suodattimet :hankinnat)
                (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset])
                (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :toimistokulut])
                (get-in app [:domain :kuluva-hoitokausi])
                (get-in app [:domain :indeksit])
                (:kantahaku-valmis? app)]
               [vahvista-suunnitelman-osa-komponentti :johto-ja-hallintokorvaus {:hoitovuosi (get-in app [:suodattimet :hoitokauden-numero])
                                                                                 :indeksit-saatavilla? (indeksit-saatavilla? app)
                                                                                 :on-tila? (onko-tila? :johto-ja-hallintokorvaus app)}]

               ::hoidonjohtopalkkio
               [hoidonjohtopalkkio-osio
                (get-in app [:gridit :hoidonjohtopalkkio :grid])
                (get-in app [:yhteenvedot :johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio])
                (get-in app [:domain :indeksit])
                (get-in app [:domain :kuluva-hoitokausi])
                (dissoc suodattimet :hankinnat)
                (:kantahaku-valmis? app)]
               [vahvista-suunnitelman-osa-komponentti :hoidonjohtopalkkio {:hoitovuosi (get-in app [:suodattimet :hoitokauden-numero])
                                                                           :indeksit-saatavilla? (indeksit-saatavilla? app)
                                                                           :on-tila? (onko-tila? :hoidonjohtopalkkio app)}]

               ::tavoite-ja-kattohinta
               [tavoite-ja-kattohinto-osio
                (get app :yhteenvedot)
                (get-in app [:domain :kuluva-hoitokausi])
                (get-in app [:domain :indeksit])
                (:kantahaku-valmis? app)]
               [vahvista-suunnitelman-osa-komponentti :tavoite-ja-kattohinta {:hoitovuosi (get-in app [:suodattimet :hoitokauden-numero])
                                                                              :indeksit-saatavilla? (indeksit-saatavilla? app)
                                                                              :on-tila? (onko-tila? :tavoite-ja-kattohinta app)}]

               ::tilaajan-varaukset
               [tilaajan-varaukset-osio
                (get-in app [:gridit :tilaajan-varaukset :grid])
                (dissoc suodattimet :hankinnat)
                (:kantahaku-valmis? app)]
               [vahvista-suunnitelman-osa-komponentti :tilaajan-varaukset {:hoitovuosi (get-in app [:suodattimet :hoitokauden-numero])
                                                                           :indeksit-saatavilla? (indeksit-saatavilla? app)
                                                                           :on-tila? (onko-tila? :tilaajan-varaukset app)}])
             (when vaaditaan-muutoksen-vahvistus?
               [selite-modal
                tee-kun-vahvistettu
                (r/partial (fn [hoitovuosi polku e]
                             (let [arvo (.. e -target -value)
                                   numero? (-> arvo js/Number js/isNaN not)
                                   arvo (if numero?
                                          (js/Number arvo)
                                          arvo)]
                               (e! (tuck-apurit/->MuutaTila [:domain :vahvistus :tiedot hoitovuosi polku] arvo)))))
                (get-in app [:domain :vahvistus])])]))))))


(defn kustannussuunnitelma
  "Kustannussuunnitelma välilehti"
  []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])
