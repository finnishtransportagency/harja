(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.kustannussuunnitelma-view
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
            [harja.tiedot.istunto :as istunto]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.hankintakustannukset-osio :as hankintakustannukset-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.erillishankinnat-osio :as erillishankinnat-osio])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;;;;; ### TAULUKOT / GRIDIT ### ;;;;;;;

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
                                (when ks-yhteiset/esta-blur_
                                  (set! ks-yhteiset/esta-blur_ false))
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
                                     (set! ks-yhteiset/esta-blur_ true)
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
                                    {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]
                        :on-blur (if nappi?
                                   [:positiivinen-numero {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]
                                   [:positiivinen-numero {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}} {:oma {:f ks-yhteiset/esta-blur-ja-lisaa-vaihtelua-teksti}}])}
       :parametrit (merge {:size 2}
                          parametrit)
       :fmt fmt
       :fmt-aktiivinen ks-yhteiset/summa-formatointi-aktiivinen}
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
                        [:div (ks-yhteiset/aika-fmt aika)]
                        [:div (str (ks-yhteiset/summa-formatointi maara) " €/kk")]])])
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
                                             (instance? ks-yhteiset/LaajennaSyote osa))
                                         (assoc osa :auki-alussa? true)
                                         osa))
                                 osat))))
                         (grid/rivi {:osat (vec
                                             (cons (ks-yhteiset/vayla-checkbox (fn [this event]
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
                                                         (instance? ks-yhteiset/LaajennaSyote osa))
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
                                       (syote-solu {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                    :blur-tallenna! (partial blur-tallenna! false nil)})
                                       (syote-solu {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                    :blur-tallenna! (partial blur-tallenna! false nil)})
                                       {:tyyppi :teksti
                                        :luokat #{"table-default"}
                                        :fmt ks-yhteiset/yhteenveto-format}
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
                                               (syote-solu {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                            :blur-tallenna! (partial blur-tallenna! true (str toimenkuva "-" maksukausi "-taulukko"))})
                                               (syote-solu {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                            :blur-tallenna! (partial blur-tallenna! true (str toimenkuva "-" maksukausi "-taulukko"))})
                                               {:tyyppi :teksti
                                                :luokat #{"table-default"}
                                                :fmt ks-yhteiset/yhteenveto-format}
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
                                                                          :fmt ks-yhteiset/aika-tekstilla-fmt}
                                                                         (syote-solu {:nappi? true :fmt ks-yhteiset/summa-formatointi-uusi :paivitettava-asia :aseta-tunnit!
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
                                                                    (ks-yhteiset/laajenna-syote (fn [this auki?]
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
                                                    (syote-solu {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                                 :blur-tallenna! (partial blur-tallenna! true rivin-nimi)
                                                                 :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                    (syote-solu {:nappi? false :fmt ks-yhteiset/yhteenveto-format :paivitettava-asia :aseta-jh-yhteenveto!
                                                                 :blur-tallenna! (partial blur-tallenna! true rivin-nimi)
                                                                 :nappia-painettu-tallenna! nappia-painettu-tallenna!})
                                                    {:tyyppi :teksti
                                                     :luokat #{"table-default"}
                                                     :fmt ks-yhteiset/yhteenveto-format}
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
                                                                               :fmt ks-yhteiset/aika-tekstilla-fmt}
                                                                              (syote-solu {:nappi? true :fmt ks-yhteiset/summa-formatointi-uusi :paivitettava-asia :aseta-tunnit!
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
                                                                                  :fmt ks-yhteiset/summa-formatointi-uusi}))
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
                                                             :fmt ks-yhteiset/yhteenveto-format}))
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
                                                                                   :fmt ks-yhteiset/yhteenveto-format}))))
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



;; #### OSIOT ####

;; -- johto-ja-hallintokorvaus-osio --

(defn johto-ja-hallintokorvaus-yhteenveto
  [johto-ja-hallintokorvaukset-yhteensa toimistokulut-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    (let [hinnat (mapv (fn [jh tk]
                         {:summa (+ jh tk)})
                   johto-ja-hallintokorvaukset-yhteensa
                   toimistokulut-yhteensa)]
      [:div.summa-ja-indeksilaskuri
       [ks-yhteiset/hintalaskuri {:otsikko nil
                      :selite "Palkat + Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten järj. jne. + Hoito- ja korjaustöiden pientarvikevarasto"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [ks-yhteiset/indeksilaskuri hinnat indeksit]])
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
   [ks-yhteiset/yleis-suodatin suodattimet]
   (if (and johto-ja-hallintokorvaus-grid kantahaku-valmis?)
     [grid/piirra johto-ja-hallintokorvaus-grid]
     [yleiset/ajax-loader])
   (if (and johto-ja-hallintokorvaus-yhteenveto-grid kantahaku-valmis?)
     [grid/piirra johto-ja-hallintokorvaus-yhteenveto-grid]
     [yleiset/ajax-loader])
   [:h3 {:id (str (get t/hallinnollisten-idt :toimistokulut) "-osio")} "Johto ja hallinto: muut kulut"]
   [ks-yhteiset/yleis-suodatin suodattimet]
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
       [ks-yhteiset/hintalaskuri {:otsikko nil
                      :selite "Hoidonjohtopalkkio"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [ks-yhteiset/indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio-sisalto [hoidonjohtopalkkio-grid suodattimet hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
  [:<>
   [:h3 {:id (str (get t/hallinnollisten-idt :hoidonjohtopalkkio) "-osio")} "Hoidonjohtopalkkio"]
   [hoidonjohtopalkkio-yhteenveto hoidonjohtopalkkio-yhteensa kuluva-hoitokausi indeksit kantahaku-valmis?]
   [ks-yhteiset/yleis-suodatin suodattimet]
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
     [ks-yhteiset/hintalaskuri {:otsikko "Tavoitehinta"
                    :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
                    :hinnat (update tavoitehinnat 0 assoc :teksti "1. vuosi*")
                    :data-cy "tavoitehinnan-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri tavoitehinnat indeksit {:dom-id "tavoitehinnan-indeksikorjaus"
                                             :data-cy "tavoitehinnan-indeksilaskuri"}]]
    [yleiset/ajax-loader]))

(defn- kattohinta-yhteenveto
  [kattohinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [ks-yhteiset/hintalaskuri {:otsikko "Kattohinta"
                    :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
                    :hinnat kattohinnat
                    :data-cy "kattohinnan-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri kattohinnat indeksit {:dom-id "kattohinnan-indeksikorjaus"
                                           :data-cy "kattohinnan-indeksilaskuri"}]]
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
     [ks-yhteiset/yleis-suodatin suodattimet]
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
  [
   ;; Hankintakustannukset osio
   [:gridit :suunnittellut-hankinnat :grid]
   [:gridit :laskutukseen-perustuvat-hankinnat :grid]
   [:gridit :rahavaraukset :grid]

   ;; Erillishankinnat osio (grid generoitu maarataulukko-grid apufunktiolla)
   [:gridit :erillishankinnat :grid]

   ;;
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
                                   [[hankintakustannukset-osio/suunnittellut-hankinnat-grid true nil]
                                    [hankintakustannukset-osio/hankinnat-laskutukseen-perustuen-grid true nil]
                                    [hankintakustannukset-osio/rahavarausten-grid false nil]
                                    [(partial ks-yhteiset/maarataulukko-grid "erillishankinnat" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:erillishankinnat-disablerivit}]
                                    [johto-ja-hallintokorvaus-laskulla-grid true
                                     (reduce (fn [tapahtumien-tunnisteet jarjestysnumero]
                                               (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                                 (conj tapahtumien-tunnisteet (keyword "piillota-itsetaytettyja-riveja-" nimi))))
                                       #{}
                                       (range 1 (inc t/jh-korvausten-omiariveja-lkm)))]
                                    [johto-ja-hallintokorvaus-laskulla-yhteenveto-grid true nil]
                                    [(partial ks-yhteiset/maarataulukko-grid "toimistokulut" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:toimistokulut-disablerivit}]
                                    [(partial ks-yhteiset/maarataulukko-grid "hoidonjohtopalkkio" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:hoidonjohtopalkkio-disablerivit}]
                                    [(partial ks-yhteiset/maarataulukko-grid "tilaajan-varaukset" [:yhteenvedot :tilaajan-varaukset] false false)
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
               [hankintakustannukset-osio/osio
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
               [erillishankinnat-osio/osio
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
