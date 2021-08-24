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
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.erillishankinnat-osio :as erillishankinnat-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.johto-ja-hallintokorvaus-osio :as johto-ja-hallintokorvaus-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.hoidonjohtopalkkio-osio :as hoidonjohtopalkkio-osio]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoite-ja-kattohinta-osio :as tavoite-ja-kattohinta-osio])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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


;; #### OSIOT ####


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

   ;; Erillishankinnat osio
   [:gridit :erillishankinnat :grid]

   ;; Johto- ja hallintokorvaukset osio
   [:gridit :johto-ja-hallintokorvaukset-yhteenveto :grid]
   [:gridit :johto-ja-hallintokorvaukset :grid]
   [:gridit :toimistokulut :grid]

   ;; Hoidonjohtopalkkio osio
   [:gridit :hoidonjohtopalkkio :grid]

   ;;
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

                                   ;; Hankintakustannukset osio
                                   [[hankintakustannukset-osio/suunnittellut-hankinnat-grid true nil]
                                    [hankintakustannukset-osio/hankinnat-laskutukseen-perustuen-grid true nil]
                                    [hankintakustannukset-osio/rahavarausten-grid false nil]

                                    ;; Erillishankinnat osio
                                    [(partial ks-yhteiset/maarataulukko-grid "erillishankinnat" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:erillishankinnat-disablerivit}]

                                    ;; Johto- ja hallintokorvaukset osio
                                    [johto-ja-hallintokorvaus-osio/johto-ja-hallintokorvaus-laskulla-grid
                                     true (reduce (fn [tapahtumien-tunnisteet jarjestysnumero]
                                               (let [nimi (t/jh-omienrivien-nimi jarjestysnumero)]
                                                 (conj tapahtumien-tunnisteet (keyword "piillota-itsetaytettyja-riveja-" nimi))))
                                       #{}
                                       (range 1 (inc t/jh-korvausten-omiariveja-lkm)))]
                                    [johto-ja-hallintokorvaus-osio/johto-ja-hallintokorvaus-laskulla-yhteenveto-grid true nil]
                                    [(partial ks-yhteiset/maarataulukko-grid "toimistokulut" [:yhteenvedot :johto-ja-hallintokorvaukset])
                                     true #{:toimistokulut-disablerivit}]

                                    ;; Hoidonjohtopalkkio osio
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

               ;; FIXME: Arvojen tallentamisessa on jokin häikkä. Tallennus ei onnistu. (Oli ennen ositustakin sama homma)
               ;; FIXME: Pääyhteenvetonäkymässä ei näy johto- ja halllintokorvauksien arvoja. Ovat nollia joka hoitovuodelle  (oli ennen ositustakin)
               ::johto-ja-hallintokorvaukset
               [johto-ja-hallintokorvaus-osio/osio
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
               [hoidonjohtopalkkio-osio/osio
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
               [tavoite-ja-kattohinta-osio/osio
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
