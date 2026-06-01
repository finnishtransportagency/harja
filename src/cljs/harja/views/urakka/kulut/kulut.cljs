(ns harja.views.urakka.kulut.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [goog.string.format]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kulut :as tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.views.urakka.kulut.kululomake :as kululomake]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.fmt :as fmt])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defn toimenpide-otsikko
  [auki? toimenpiteet tpi summa erapaiva maksuera maksuera-alias]
  [:tr.table-default-strong.klikattava
   {:on-click #(swap! auki? not)}
   [:td.col-xs-1 (str (pvm/pvm erapaiva))]
   [:td.col-xs-2.sailyta-rivilla (if maksuera-alias (str "HA" maksuera " / " maksuera-alias) (str "HA" maksuera))]
   [:td.col-xs-2 (get-in toimenpiteet [tpi :toimenpide])]
   [:td.col-xs-3
    [:span.col-xs-6.yhteensa "Yhteensä"]
    [:span.col-xs-6
     (if @auki?
       [ikonit/harja-icon-navigation-up]
       [ikonit/harja-icon-navigation-down])]]
   [:td.col-xs-2 ""]
   [:td.col-xs-1.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td.col-xs-1 ""]])

(defn koontilasku-otsikko
  [nro summa]
  [:tr.table-default-thin.valiotsikko.table-default-strong
   [:td {:colSpan "5"}
    (str (if (zero? nro)
           "Kulut ilman koontilaskun nroa"
           (str "Koontilasku nro " nro)) " yhteensä")]
   [:td.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td ""]])

(defn laskun-erapaiva-otsikko
  [erapaiva]
  [:tr.table-default-thin.valiotsikko.table-default-strong
   [:td {:colSpan "8"} (str erapaiva)]])

(defn kulu-rivi
  [{:keys [e!]} {:keys [id toimenpide-nimi tehtavaryhma-nimi maksuera
                        maksuera-alias liitteet summa erapaiva lisatieto harjan-generoima]}]
  [(if harjan-generoima :tr :tr.klikattava)
   (when-not harjan-generoima
     {:on-click (fn [] (e! (tiedot/->AvaaKulu id)))})
   [:td.col-xs-1 (str (when erapaiva (pvm/pvm erapaiva)))]
   [:td.col-xs-2.sailyta-rivilla (if maksuera-alias (str "HA" maksuera " / " maksuera-alias) (str "HA" maksuera))]
   [:td.col-xs-2 toimenpide-nimi]
   [:td.col-xs-3 tehtavaryhma-nimi]
   [:td.col-xs-2 lisatieto]
   [:td.col-xs-1.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td.col-xs-1.tasaa-oikealle (when-not (empty? liitteet) [ikonit/harja-icon-action-add-attachment])]])

(defn toimenpide-expandattava
  [_ {:keys [toimenpiteet tehtavaryhmien_nimet]}]
  (let [auki? (r/atom false)]
    (fn [[_ tpi summa rivit] {:keys [e!]}]
      (if (> (count rivit) 1)
        [:<>
         [toimenpide-otsikko auki? toimenpiteet tpi summa (-> rivit first :erapaiva) (-> rivit first :maksuera-numero) (-> rivit first :maksuera-alias)]
         (when @auki?
           (into [:<>]
             (loop [[{:keys [id toimenpideinstanssi tehtavaryhma liitteet summa
                             maksuera-numero maksuera-alias lisatieto harjan-generoima] :as rivi} & loput] rivit
                    odd? false
                    elementit []]
               (if (nil? rivi)
                 elementit
                 (recur loput
                   (not odd?)
                   ^{:key (gensym "rivi-")}
                   (conj elementit [kulu-rivi
                                    {:e! e! :odd? odd?}
                                    {:toimenpide-nimi (get-in toimenpiteet [toimenpideinstanssi :toimenpide])
                                     :tehtavaryhma-nimi (-> (filter #(= (:tehtavaryhma %) tehtavaryhma) tehtavaryhmien_nimet)
                                                          first
                                                          :tehtavaryhma_nimi)
                                     :maksuera maksuera-numero
                                     :maksuera-alias maksuera-alias
                                     :summa summa
                                     :liitteet liitteet
                                     :erapaiva nil
                                     :lisatieto lisatieto
                                     :harjan-generoima harjan-generoima
                                     :id id}]))))))]
        (let [{:keys [id toimenpideinstanssi tehtavaryhma liitteet summa erapaiva
                      maksuera-numero maksuera-alias lisatieto harjan-generoima]} (first rivit)]
          [kulu-rivi
           {:e! e! :odd? false}
           {:toimenpide-nimi (get-in toimenpiteet [toimenpideinstanssi :toimenpide])
            :tehtavaryhma-nimi (-> (filter #(= (:tehtavaryhma %) tehtavaryhma) tehtavaryhmien_nimet)
                                 first
                                 :tehtavaryhma_nimi)
            :maksuera maksuera-numero
            :maksuera-alias maksuera-alias
            :summa summa
            :liitteet liitteet
            :erapaiva erapaiva
            :lisatieto lisatieto
            :harjan-generoima harjan-generoima
            :id id}])))))

(defn taulukko-tehdas
  [{:keys [toimenpiteet tehtavaryhmien_nimet tiedot e!]} t]
  (cond
    (and (vector? t)
      (= (first t) :pvm))
    (let [[_ erapaiva & _loput] t]
      ^{:key (gensym "erap-")} [laskun-erapaiva-otsikko erapaiva])

    (and (vector? t)
      (= (first t) :laskun-numero))
    (let [[_ nro summa] t]
      ^{:key (gensym "kl-")} [koontilasku-otsikko nro summa])

    (and (vector? t)
      (= (first t) :tpi))
    ^{:key (gensym "tp-")} [toimenpide-expandattava t {:toimenpiteet toimenpiteet
                                                       :tiedot tiedot
                                                       :tehtavaryhmien_nimet tehtavaryhmien_nimet
                                                       :e! e!}]
    :else
    ^{:key (gensym "d-")} [:tr]))

(defn kulutaulukko
  [{:keys [e! tiedot tehtavaryhmien_nimet toimenpiteet haetaan?]}]
  (let [toimenpiteet (reduce #(assoc %1 (:toimenpideinstanssi %2) %2) {} toimenpiteet)]
    [:div.livi-grid
     [:table.grid
      [:thead
       [:tr
        [:th.col-xs-1 "Pvm"]
        [:th.col-xs-2 "Maksuerä"]
        [:th.col-xs-2 "Toimenpide"]
        [:th.col-xs-3 "Tehtäväryhmä"]
        [:th.col-xs-2 "Lisätieto"]
        [:th.col-xs-1.tasaa-oikealle "Määrä"]
        [:th.col-xs-1 ""]]]
      [:tbody
       (cond
         (and (empty? tiedot)
           (not haetaan?))
         [:tr
          [:td {:colSpan "6"} "Annetuilla hakuehdoilla ei näytettäviä kuluja"]]

         haetaan?
         [:tr
          [:td {:colSpan "6"} "Haku käynnissä, odota hetki"]]

         :else
         (into [:<>] (comp (map (r/partial taulukko-tehdas {:toimenpiteet toimenpiteet
                                                            :tiedot tiedot
                                                            :tehtavaryhmien_nimet tehtavaryhmien_nimet
                                                            :e! e!}))
                       (keep identity))
           tiedot))]]]))

(defn hae-loppupvm
  [hoitokausi]
  (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc hoitokausi))))

(defn- laske-kulujen-summa
  "Laskee kulujen kokonaissumman. Kulut voi olla joko map (jossa :kokonaissumma)
   tai lista vektoreita, joissa kulut ovat :tpi-tagilla ja niistä löytyy :kokonaissumma.

   Tpi-vektorin tietomalli:
   [:tpi <toimenpideinstanssi-numero> <summa> [{:erapaiva <paiva> :muutos-voimassa-alkaen <paiva> :kokonaissumma <summa>
   :lisatyon-lisatieto <lisatieto>, :maksueratyyppi <tyyppi>, :muu-tehtava-kaytossa <boolean>, :tehtava {:nimi <nimi>, :id <id>},
   :summa <summa>, :kohdistus-id <id>, :maksuera-alias <maksuera-alias>, :laskun-numero <numero>, :toimenpideinstanssi <numero>,
   :rahavaraus <numero>, :id <id>, :liitteet [], :tehtavaryhma <numero>, :maksuera-numero <numero>, :tyyppi <tyyppi>, :lisatieto <lisatieto>,
   :muutos-nimi <nimi>, :muutos-id <id>, :rivi <rivi>,:koontilaskun-kuukausi <kk/vuosi>}]]

   Jos samalla toimenpideinstanssilla on useampi kulu, ne ovat mappeina saman vektorin sisällä. Muuten jokaisesta toimenpideinstanssista on oma vektori."
  [kulut]
  (if (map? kulut)
    (:summa kulut)
    (reduce
      (fn [summa item]
        (if (and (vector? item)
              (= :tpi (first item)))
          (let [rivit (nth item 3 nil)]
            (+ summa (reduce + 0 (map #(/ (Math/round (* (or (:summa %) 0) 100.0)) 100.0) rivit))))
          summa))
      0
      kulut)))

(defn laskutusraja-komponentti
  [e! _app valittu-hoitokausi _hoitovuodet _haun-kuukausi hae-kulut?]
  (let [edellinen-hoitokausi-atom (r/atom valittu-hoitokausi)
        edellinen-hakukuukausi-atom (r/atom nil)]
    (komp/luo
      (komp/piirretty
        (fn [_this]
          (let [hoitovuoden-alkupvm (pvm/hoitokauden-alkupvm valittu-hoitokausi)
                hoitovuoden-loppupvm (hae-loppupvm valittu-hoitokausi)]
            (e! (tiedot/->HaeLaskutusraja valittu-hoitokausi))
            (e! (tiedot/->HaeHoitokaudenKulujenSumma hoitovuoden-alkupvm hoitovuoden-loppupvm)))))

      (komp/kun-muuttuu
        (fn [e! _app uusi-hoitokausi _hoitovuodet uusi-haun-kuukausi]
          (let [edellinen-hoitokausi @edellinen-hoitokausi-atom
                hoitokauden-alkupvm (pvm/hoitokauden-alkupvm uusi-hoitokausi)
                edellinen-hakukuukausi @edellinen-hakukuukausi-atom]

            (when (not= edellinen-hoitokausi uusi-hoitokausi)
              (let [hoitokauden-loppupvm (hae-loppupvm uusi-hoitokausi)]
                (reset! edellinen-hoitokausi-atom uusi-hoitokausi)
                (e! (tiedot/->HaeLaskutusraja uusi-hoitokausi))
                (e! (tiedot/->HaeHoitokaudenKulujenSumma hoitokauden-alkupvm hoitokauden-loppupvm))))

            (when (and uusi-haun-kuukausi (not= edellinen-hakukuukausi uusi-haun-kuukausi))
              (let [valitun-kuukauden-alkupvm (first uusi-haun-kuukausi)
                    edellisen-kuukauden-loppupvm (pvm/ajan-muokkaus valitun-kuukauden-alkupvm false 1 :paiva)]
                (reset! edellinen-hakukuukausi-atom uusi-haun-kuukausi)
                (when hae-kulut?
                  (e! (tiedot/->HaeUrakanKulut
                        {:id (-> @tila/yleiset :urakka :id)
                         :alkupvm (first uusi-haun-kuukausi)
                         :loppupvm (second uusi-haun-kuukausi)})))
                (e! (tiedot/->HaeKulutYhteensaHakukuukauteenAsti hoitokauden-alkupvm edellisen-kuukauden-loppupvm))))

            (when (and (not uusi-haun-kuukausi) (not= edellinen-hakukuukausi uusi-haun-kuukausi))
              (reset! edellinen-hakukuukausi-atom uusi-haun-kuukausi)))))

      (fn [_ {:keys [haku-kaynnissa? laskutusraja-kaytossa? laskutusraja kulut hoitokauden-kulujen-summa kulut-yhteensa-hakukuukauteen-asti parametrit] :as app}
           valittu-hoitokausi hoitovuodet haun-kuukausi]

        (let [{:keys [haun-alkupvm haun-loppupvm]} parametrit
              haetun-aikarajan-kulujen-summa (laske-kulujen-summa kulut)
              kulut-yhteensa-hakukuukauteen-asti (or kulut-yhteensa-hakukuukauteen-asti 0)
              yhteensa (+ haetun-aikarajan-kulujen-summa kulut-yhteensa-hakukuukauteen-asti)
              ylitys (when laskutusraja (- yhteensa laskutusraja))
              laskutusrajaan-sisaltyva (- haetun-aikarajan-kulujen-summa ylitys)
              laskutusrajan-ylittava (when laskutusraja (- yhteensa laskutusraja))
              vapaan-aikavalin-alkupvm haun-alkupvm
              vapaan-aikavalin-loppupvm haun-loppupvm
              vapaan-aikavalin-alkupvm-hoitokausi (when vapaan-aikavalin-alkupvm (pvm/paivamaaran-hoitokausi vapaan-aikavalin-alkupvm))
              vapaan-aikavalin-loppupvm-hoitokausi (when vapaan-aikavalin-loppupvm (pvm/paivamaaran-hoitokausi vapaan-aikavalin-loppupvm))
              eri-hoitovuosilla? (when (and vapaan-aikavalin-alkupvm-hoitokausi vapaan-aikavalin-loppupvm-hoitokausi
                                         (not= vapaan-aikavalin-alkupvm-hoitokausi vapaan-aikavalin-loppupvm-hoitokausi)) true)]

          (when (and laskutusraja-kaytossa? (not eri-hoitovuosilla?))
            [:div.laskutusraja
             [:div
              [:h2 "Laskutusraja"]
              (cond
                haku-kaynnissa?
                [yleiset/ajax-loader "Ladataan laskutusrajaa..." {:sama-rivi? false :luokka "keskitetty-pysty"}]

                (some? laskutusraja)
                [:div
                 (when (> hoitokauden-kulujen-summa laskutusraja)
                   [yleiset/info-laatikko :vahva-ilmoitus "Laskutusraja on täynnä."
                    [:span "Kaikki laskutusrajan yli menevät toteutuneet kustannukset kirjataan edelleen normaalisti Harjaan, mutta niitä ei saa laskuttaa. Maksuosuuksista päätetään "
                     [:a.klikattava.alleviivaa {:href "#"
                                                :on-click #(siirtymat/siirry-annettuun-valilehteen
                                                             @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
                                                             {:taso1 :urakat
                                                              :taso2 :valikatselmus})}
                      "välikatselmuksessa"] "."]
                    nil {:ikoni-fn #(ikonit/harja-icon-status-alert) :luokka "tasan"}])
                 [:div.sarakkeet-yhteensa
                  [:div.leveampi-sarake
                   [:div.lukema-label "Laskutusrajan käyttö " (fmt/hoitokauden-jarjestysluku-ja-vuodet valittu-hoitokausi hoitovuodet "Hoitovuosi")]
                   [:div.lukema (if (and hoitokauden-kulujen-summa (< hoitokauden-kulujen-summa laskutusraja))
                                  (fmt/euro-opt false hoitokauden-kulujen-summa)
                                  (fmt/euro-opt false laskutusraja)) " / " (fmt/euro-opt laskutusraja)]]

                  (when (and hoitokauden-kulujen-summa (> hoitokauden-kulujen-summa laskutusraja))
                    [:div.leveampi-sarake
                     [:div.lukema-label.oikeaan-reunaan "Laskutusrajan ylittävä osuus (kumulatiivinen)"]
                     [:div.lukema.oikeaan-reunaan (fmt/euro-opt (- hoitokauden-kulujen-summa laskutusraja))]])]

                 (let [prosenttiosuus (if (and laskutusraja (pos? laskutusraja) (some? hoitokauden-kulujen-summa))
                                        (min 100 (* 100 (/ hoitokauden-kulujen-summa laskutusraja)))
                                        0)]
                   [:div.edistymispalkki-tausta
                    [:div.edistymispalkki {:style {:width (str prosenttiosuus "%")}}]])

                 (when haun-kuukausi
                   [:div
                    [:h3 (str (pvm/kuukausi-isolla (pvm/kuukausi (first haun-kuukausi))) " " (pvm/vuosi (first haun-kuukausi)))]
                    [:div.sarakkeet
                     [:div.kapeampi-sarake
                      [:div.lukema-label "Tavoitehintaan kuuluvat kulut"]
                      [:div.lukema (fmt/euro-opt haetun-aikarajan-kulujen-summa)]]
                     (when (and (> haetun-aikarajan-kulujen-summa 0) (some? kulut-yhteensa-hakukuukauteen-asti) (> yhteensa laskutusraja))
                       [:<>
                        [:div.kapeampi-sarake
                         [:div.lukema-label "Laskutusrajaan sisältyvä osuus"]
                         [:div.lukema (if (> kulut-yhteensa-hakukuukauteen-asti laskutusraja)
                                        (fmt/euro-opt 0)
                                        (fmt/euro-opt laskutusrajaan-sisaltyva))]]
                        [:div.kapeampi-sarake
                         [:div.lukema-label "Laskutusrajan ylittävä osuus"]
                         [:div.lukema (if (> kulut-yhteensa-hakukuukauteen-asti laskutusraja)
                                        (fmt/euro-opt haetun-aikarajan-kulujen-summa)
                                        (fmt/euro-opt laskutusrajan-ylittava))]]])]])
                 (when (and vapaan-aikavalin-alkupvm vapaan-aikavalin-loppupvm)
                   [:div
                    [:h3 (str (pvm/pvm vapaan-aikavalin-alkupvm) " - " (pvm/pvm vapaan-aikavalin-loppupvm))]
                    [:div.lukema-label "Tavoitehintaan kuuluvat kulut"]
                    [:div.lukema (fmt/euro-opt haetun-aikarajan-kulujen-summa)]])]

                :else
                [yleiset/info-laatikko :vahva-ilmoitus "Hoitovuoden alun indeksikorjattu tavoitehinta on vahvistamatta."
                 [:span "Laskutusraja vaatii tiedon vahvistamisen. "
                  [:a.klikattava.alleviivaa {:href "#"
                                             :on-click #(siirtymat/siirry-annettuun-valilehteen
                                                          @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
                                                          {:taso1 :urakat
                                                           :taso2 :suunnittelu
                                                           :taso3 :uusi-kustannussuunnitelma})}
                   "Siirry Hoitovuoden alun tavoitehinta-sivulle"]]
                 nil {:ikoni-fn #(ikonit/harja-icon-status-alert) :luokka "tasan"}])]]))))))

(defn- kohdistetut*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [_this]
                      (let [tiedot (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])
                            vuosi (when @u/valittu-aikavali
                                    (pvm/vuosi (-> @u/valittu-aikavali first)))]
                        (e! (tiedot/->ValitseHoitokausi vuosi))
                        (e! (tiedot/->HaeUrakanToimenpiteet tiedot))
                        (e! (tiedot/->HaeKaikkiTehtavaryhmat tiedot))
                        (e! (tiedot/->HaeUrakanHintapaatokset))
                        (e! (tiedot/->HaeUrakanRahavaraukset)))))
    (komp/ulos #(e! (tiedot/->NakymastaPoistuttiin)))
    (fn [e! {kulut :kulut syottomoodi :syottomoodi
             {:keys [haetaan haun-kuukausi haun-alkupvm haun-loppupvm]}
             :parametrit
             tehtavaryhmien_nimet :kaikkien_tehtavaryhmien_nimet
             toimenpiteet :toimenpiteet :as app}]
      (let [urakan-alkupvm (-> @tila/yleiset :urakka :alkupvm)
            urakan-loppupvm (-> @tila/yleiset :urakka :loppupvm)
            ;; Varmista, että käsitellään vain valitun urakan ajalta kuluja
            aikaisin-mahdollinen-nyt (if (pvm/sama-tai-jalkeen? (pvm/nyt) urakan-alkupvm)
                                       (pvm/nyt)
                                       urakan-alkupvm)
            ;; Jos haun-kuukausi on defaulteissa asetettu pienemmäksi kuin urakan alkupäivä, niin muuta se
            haun-kuukausi (if (pvm/ennen? (first haun-kuukausi) urakan-alkupvm)
                            (pvm/kuukauden-aikavali urakan-alkupvm)
                            haun-kuukausi)

            haun-alkupvm (cond
                           ;; Alkupvm on nil, mutta hoitokausi valittuna
                           ;; -> Aseta alkupäiväksi hoitokauden alku
                           (and
                             (nil? haun-alkupvm)
                             (:valittu-hoitokausi app)
                             (= 2 (count (:valittu-hoitokausi app))))
                           (first (:valittu-hoitokausi app))
                           ;; Fallback
                           :else haun-alkupvm)

            haun-loppupvm (cond
                            ;; Loppupvm on nil, mutta hoitokausi valittuna
                            ;; -> Aseta alkupäiväksi hoitokauden loppu
                            (and
                              (nil? haun-loppupvm)
                              (:valittu-hoitokausi app)
                              (= 2 (count (:valittu-hoitokausi app))))
                            (second (:valittu-hoitokausi app))
                            ;; Fallback
                            :else haun-loppupvm)

            [hk-alkupvm hk-loppupvm] (pvm/paivamaaran-hoitokausi (if (:valittu-hoitokausi app)
                                                                   (first (:valittu-hoitokausi app))
                                                                   aikaisin-mahdollinen-nyt))
            kuukaudet (pvm/aikavalin-kuukausivalit
                        [hk-alkupvm
                         hk-loppupvm])
            kuukaudet (conj kuukaudet nil)
            urakan-alkuvuosi (pvm/vuosi urakan-alkupvm)
            urakan-loppuvuosi (pvm/vuosi urakan-loppupvm)
            valittu-hoitokausi (if (nil? (:hoitokauden-alkuvuosi app))
                                 (tiedot/kuluva-hoitovuosi aikaisin-mahdollinen-nyt)
                                 (:hoitokauden-alkuvuosi app))
            hoitovuodet (into [] (range urakan-alkuvuosi urakan-loppuvuosi))
            haun-alkupvm-atom (r/atom (get-in app [:parametrit :haun-alkupvm]))
            haun-loppupvm-atom (r/atom (get-in app [:parametrit :haun-loppupvm]))
            haku-menossa (boolean (get-in app [:parametrit :haku-menossa]))]
        [:div
         (if syottomoodi
           [:div.kulujen-kirjaus
            [kululomake/kululomake e! app]]
           [:div#vayla.kulujen-listaus
            [:div.flex-row
             #_[debug/debug app]
             [:h1 "Kulujen kohdistus"]
             ^{:key "raporttixls"}
             [:form {:style {:margin-left "auto"}
                     :target "_blank" :method "POST"
                     :action (k/excel-url :kulut)}
              [:input {:type "hidden" :name "parametrit"
                       :value (t/clj->transit {:urakka-id (-> @tila/yleiset :urakka :id)
                                               :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                               :alkupvm (or (first haun-kuukausi) haun-alkupvm)
                                               :loppupvm (or (second haun-kuukausi) haun-loppupvm)})}]
              [napit/tallenna "Tallenna Excel" (constantly true)
               {:ikoni (ikonit/harja-icon-action-download) :luokka "nappi-toissijainen" :type "submit"
                :esta-prevent-default? true}]]
             ^{:key "raporttipdf"}
             [:form {:style {:margin-left "16px"
                             :margin-right "64px"}
                     :target "_blank" :method "POST"
                     :action (k/pdf-url :kulut)}
              [:input {:type "hidden" :name "parametrit"
                       :value (t/clj->transit {:urakka-id (-> @tila/yleiset :urakka :id)
                                               :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                               :alkupvm (or (first haun-kuukausi) haun-alkupvm)
                                               :loppupvm (or (second haun-kuukausi) haun-loppupvm)})}]
              [napit/tallenna "Tallenna PDF" (constantly true)
               {:ikoni (ikonit/harja-icon-action-download) :luokka "nappi-toissijainen" :type "submit"
                :esta-prevent-default? true}]]

             [napit/yleinen-ensisijainen
              "Uusi kulu"
              #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
              {:ikoni [ikonit/harja-icon-action-add]}]]

            [:div.display-flex.flex-col.sisalto-leveys
             [:div.flex-row.alkuun
              [:div.filtteri.label-ja-alasveto
               [:label.alasvedon-otsikko {:for "kulut-hoitokausi-valinta"} "Hoitovuosi"]
               [yleiset/livi-pudotusvalikko {:elementin-id "kulut-hoitokausi-valinta"
                                             :valinta valittu-hoitokausi
                                             :disabled haku-menossa
                                             :vayla-tyyli? true
                                             :data-cy "hoitokausi-valinta"
                                             :valitse-fn #(do
                                                            ;; Nullaa mahdollinen aikaväli
                                                            (e! (tiedot/->AsetaHakuPaivamaara nil nil))
                                                            (e! (tiedot/->ValitseHoitokausi %)))
                                             :format-fn #(fmt/hoitokauden-jarjestysluku-ja-vuodet % hoitovuodet "Hoitovuosi")
                                             :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
                hoitovuodet]]
              [valinnat/kuukausi {:nil-valinta "Koko hoitokausi"
                                  :vayla-tyyli? true
                                  :disabled haku-menossa
                                  :valitse-fn #(do
                                                 (e! (tiedot/->AsetaHakukuukausi %))
                                                 (e! (tiedot/->HaeUrakanKulut
                                                       {:id (-> @tila/yleiset :urakka :id)
                                                        :alkupvm (if (nil? %) hk-alkupvm (first %))
                                                        :loppupvm (if (nil? %) hk-loppupvm (second %))})))}

               kuukaudet haun-kuukausi]
              [:span {:class "label-ja-aikavali"}
               (when-not haku-menossa
                 [:div.label-ja-alasveto.aikavali
                  [:label.alasvedon-otsikko {:for "kulut-aikavali-alku"} (str "Aikaväli")]
                  [:div.aikavali-valinnat
                   [kentat/tee-kentta {:tyyppi :pvm
                                       :vayla-tyyli? true
                                       :elementin-nimi "kulut-aikavali-alku"
                                       :on-datepicker-select #(do
                                                                (e! (tiedot/->AsetaHakuAlkuPvm %))
                                                                (when (and % @haun-loppupvm-atom)
                                                                  ;; Tarkista että alkupvm on ennen loppupvm:ää
                                                                  (if (pvm/ennen? % @haun-loppupvm-atom)
                                                                    (e! (tiedot/->HaeUrakanKulut
                                                                          {:id (-> @tila/yleiset :urakka :id)
                                                                           :alkupvm %
                                                                           :loppupvm @haun-loppupvm-atom}))
                                                                    ;; Jos alkupvm on loppupvm:n jälkeen, aseta loppupvm samaksi
                                                                    (do
                                                                      (reset! haun-loppupvm-atom %)
                                                                      (e! (tiedot/->AsetaHakuLoppuPvm %))
                                                                      (e! (tiedot/->HaeUrakanKulut
                                                                            {:id (-> @tila/yleiset :urakka :id)
                                                                             :alkupvm %
                                                                             :loppupvm %}))))))}
                    haun-alkupvm-atom]
                   [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
                   [kentat/tee-kentta {:tyyppi :pvm
                                       :vayla-tyyli? true
                                       :elementin-nimi "kulut-aikavali-loppu"
                                       :on-datepicker-select (fn [loppupvm]
                                                               (do
                                                                 (e! (tiedot/->AsetaHakuLoppuPvm loppupvm))
                                                                 (when (and (not (nil? loppupvm)) (not (nil? @haun-alkupvm-atom)))
                                                                   ;; Tarkista että loppupvm on alkupvm:n jälkeen tai samana päivänä
                                                                   (if (or (pvm/jalkeen? loppupvm @haun-alkupvm-atom)
                                                                         (pvm/sama-pvm? loppupvm @haun-alkupvm-atom))
                                                                     (e! (tiedot/->HaeUrakanKulut
                                                                           {:id (-> @tila/yleiset :urakka :id)
                                                                            :alkupvm @haun-alkupvm-atom
                                                                            :loppupvm loppupvm}))
                                                                     ;; Jos loppupvm on ennen alkupvm:ää, aseta alkupvm samaksi
                                                                     (do
                                                                       (reset! haun-alkupvm-atom loppupvm)
                                                                       (e! (tiedot/->AsetaHakuAlkuPvm loppupvm))
                                                                       (e! (tiedot/->HaeUrakanKulut
                                                                             {:id (-> @tila/yleiset :urakka :id)
                                                                              :alkupvm loppupvm
                                                                              :loppupvm loppupvm})))))))}
                    haun-loppupvm-atom]]])]]
             [laskutusraja-komponentti e! app valittu-hoitokausi hoitovuodet haun-kuukausi false]]

            (when kulut
              [:div
               (if haku-menossa
                 [yleiset/ajax-loader "Ladataan..."]
                 [kulutaulukko {:e! e! :haetaan? (> haetaan 0)
                                :tiedot kulut
                                :tehtavaryhmien_nimet tehtavaryhmien_nimet
                                :toimenpiteet toimenpiteet}])])])]))))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])
