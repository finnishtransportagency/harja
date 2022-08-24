(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset
  (:require [reagent.core :as r :refer [atom]]
            [clojure.string :as clj-str]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :as modal]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.domain.roolit :as roolit])
  (:require-macros [harja.ui.taulukko.grid :refer [defsolu]]))


(defonce e! nil)

;; -- Roolit --

(def ^{:private true} oikeus-vahvistaa-osio-roolit #{"ELY_Urakanvalvoja"})

(defn oikeus-vahvistaa-osio? [kayttaja urakka-id]
  (or (roolit/rooli-urakassa? kayttaja oikeus-vahvistaa-osio-roolit urakka-id) (roolit/jvh? kayttaja)))

;; -- Muut apurit --

(defn osio-vahvistettu?
  "Tarkastaa osioiden tilasta hoitovuoden ja osion tunnisteen perusteella onko osio vahvistettu."
  [osioiden-tilat osio-kw hoitovuosi-nro]
  (boolean (get-in osioiden-tilat [osio-kw hoitovuosi-nro])))


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
                                                                     (binding [solu/*this* (::t/tama-komponentti solu/*this*)]
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
                  [grid/piirra (assoc input-osa ::t/tama-komponentti this
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
                                                         :ajettavat-jarjestykset #{:mapit}}
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
                                                     :ajettavat-jarjestykset true
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
                                             piilotettu? (grid/piilotettu? rivi)]]
                                 (when-not piilotettu?
                                   (t/paivita-solun-arvo {:paivitettava-asia paivitettava-asia
                                                          :arvo arvo
                                                          :solu maara-solu
                                                          :ajettavat-jarjestykset true
                                                          :triggeroi-seuranta? true}
                                                         true)))
                               (toiminto-fn! paivitettava-asia nappia-painettu-tallenna! rivit-alla)))
         :nappi-nakyvilla? false}))))



;; -- Modaalit --

(defn modal-aiheteksti [aihe {:keys [toimenpide toimenkuva]}]
  [:div.modal-aiheteksti
   [:h3
    (case aihe
      :maaramitattava (str "Määrämitattavat: " (t/toimenpide-formatointi toimenpide))
      :toimenkuva (str "Toimenkuva: " toimenkuva))]])

(defn modal-lista [data-hoitokausittain]
  [:div.modal-lista
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

(defn poista-modal-napit [poista! peruuta!]
  (let [poista! (r/partial (fn []
                             (binding [t/*e!* e!]
                               (poista!))))]
    [:div.modal-napit {:style {:padding-top "15px"}}
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
                    :modal-luokka "kustannussuunnitelma-poista-modal"
                    :leveys "max-content"
                    :sulje-fn peruuta!
                    :footer [poista-modal-napit poista! peruuta!]}
                   [:div
                    [modal-aiheteksti aihe (select-keys tiedot #{:toimenpide :toimenkuva})]
                    [modal-lista data-hoitokausittain]]))))
;; |--



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
                                                                   ::t/otsikko)
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
                   ::t/data-yhteenveto))

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


(defn yleis-suodatin
  ([suodattimet]
   [yleis-suodatin suodattimet {}])
  ([suodattimet opts]
   (let [yksiloiva-id (str (gensym "kopioi-tuleville-hoitovuosille"))
         hoitovuodet (vec (range 1 6))
         vaihda-fn (fn [event]
                     (.preventDefault event)
                     (e! (tuck-apurit/->PaivitaTila [:suodattimet :kopioidaan-tuleville-vuosille?] not)))
         valitse-hoitovuosi (fn [hoitovuosi]
                              (do
                                (e! (tuck-apurit/->MuutaTila [:suodattimet :hoitokauden-numero] hoitovuosi))
                                (e! (t/->HaeBudjettitavoite))))
         hoitovuositeksti (fn [hoitovuosi]
                            (str hoitovuosi ". hoitovuosi"))]
     (fn [{:keys [hoitokauden-numero kopioidaan-tuleville-vuosille?]} {:keys [piilota-kopiointi?]}]
       (if hoitokauden-numero
         ^{:key :yleis-suodatin}
         [:div.kustannussuunnitelma-filter
          [:div
           (when-not piilota-kopiointi?
             [:<>
              [:input.vayla-checkbox {:id yksiloiva-id
                                      :type "checkbox" :checked kopioidaan-tuleville-vuosille?
                                      :on-change (r/partial vaihda-fn)}]
              [:label {:for yksiloiva-id}
               "Kopioi kuluvan hoitovuoden määrät tuleville vuosille"]])]
          [:div.pudotusvalikko-filter
           [:span "Hoitovuosi"]
           [yleiset/livi-pudotusvalikko {:valinta hoitokauden-numero
                                         :valitse-fn valitse-hoitovuosi
                                         :format-fn hoitovuositeksti
                                         :vayla-tyyli? true
                                         :elementin-id (str (gensym "yleissuodatin-hoitovuosi-valikko"))}
            (filterv #(not= % hoitokauden-numero) hoitovuodet)]]]
         [yleiset/ajax-loader])))))

;; --


;; ### Yhteiset UI-komponentit ###

;; Hintalaskuri UI-komponentti --
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

;; |--


;; Indeksilaskuri UI-komponentti
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

(defn indeksilaskuri-ei-indeksikorjausta
  "Indeksilaskuri-komponentti ilman UI:n puolella laskettavaa indeksikorjausta.
  Indeksit-parametria tarvitaan silti indeksin vuosiluvun hakemiseen hoitovuoden numeron perusteella."
  ([summat indeksit] [indeksilaskuri summat indeksit nil])
  ([summat indeksit {:keys [dom-id data-cy]}]
   (let [summat (vec (map-indexed (fn [index {:keys [summa]}]
                                    (let [hoitokauden-numero (inc index)
                                          ;; Indeksien kautta saadaan indeksin vuosiluku
                                          {:keys [vuosi]} (get indeksit index)]
                                      {:vuosi vuosi
                                       ;; Näytä summa vain jos indeksin vuosi löytyy
                                       :summa (when vuosi summa)
                                       :hoitokauden-numero hoitokauden-numero}))
                       summat))]
     [:div.hintalaskuri.indeksilaskuri {:id dom-id
                                        :data-cy data-cy}
      [:span "Indeksikorjatut yhteensä"]
      [:div.hintalaskuri-vuodet
       (for [{:keys [vuosi summa hoitokauden-numero]} summat]
         ^{:key hoitokauden-numero}
         [hintalaskurisarake vuosi (fmt-euro summa)])
       [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
       [hintalaskurisarake "Yhteensä" (fmt-euro (reduce #(+ %1 (:summa %2)) 0 summat)) {:container-luokat "hintalaskuri-yhteensa"}]]])))

