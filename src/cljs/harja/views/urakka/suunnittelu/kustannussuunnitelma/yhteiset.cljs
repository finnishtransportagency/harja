(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset
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
            [harja.loki :refer [log]]
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

;; Hox: Defsolu määrittelee funktion vayla-checkbox.
;; Declare defsolu VaylaCheckbox luoma fn etukäteen, jotta Cursive löytää sen
(declare vayla-checkbox)
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

;; Hox: Defsolu määrittelee funktion laajenna-syote
;; Declare defsolu LaajennaSyote luoma fn etukäteen, jotta Cursive löytää sen
(declare laajenna-syote)
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

;; --


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

;; --

(defn- hintalaskurisarake
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



;; -- Maarataulukko grid apufunktio--
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
        g (g-pohjat/uusi-taulukko
            {:header (cond-> [{:tyyppi :teksti
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
                             :osat
                             (cond->
                               [{:tyyppi :laajenna
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
                                               (t/laajenna-solua-klikattu this auki? taulukon-id
                                                 [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
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
                             :osat
                             (vec (repeatedly 12
                                    (fn []
                                      {:tyyppi :rivi
                                       :osat (cond->
                                               [{:tyyppi :teksti
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
  "Luo määrataulukko-tyyppisen gridin ja tallentaa sen tilan annetulla nimellä :gridit-polkuun, kuten muutkin gridit."
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
       {[::g-pohjat/otsikko]
        {:rajapinta :otsikot
         :solun-polun-pituus 1
         :jarjestys [jarjestysvektori]
         :datan-kasittely (fn [otsikot]
                            (mapv (fn [otsikko]
                                    otsikko)
                              (vals otsikot)))}

        [::g-pohjat/data 0 ::data-yhteenveto]
        yhteenveto-grid-rajapinta-asetukset

        [::g-pohjat/data 0 ::data-sisalto]
        {:rajapinta polun-osa
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

        [::g-pohjat/yhteenveto]
        {:rajapinta :yhteensa
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
