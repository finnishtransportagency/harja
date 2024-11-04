(ns harja.views.hallinta.kojelauta
  (:require [harja.pvm :as pvm]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [tuck.core :refer [tuck]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.kojelauta :as tiedot]))

(def hoitokausia-taaksepain 4)
(def hoitokausia-eteenpain 6)

(defn- mahdolliset-hoitokauden-alkuvuodet [pvm-nyt]
  (range (- (pvm/vuosi pvm-nyt) hoitokausia-taaksepain)
    (+ hoitokausia-eteenpain (pvm/vuosi pvm-nyt))))

(defn suodattimet [e! {:keys [valinnat urakkahaku] :as app}]
  [:div
   [yleiset/pudotusvalikko
    "Urakkatyyppi"
    {:valitse-fn #(do
                    (e! (tiedot/->AsetaSuodatin :urakkatyyppi %))
                    (e! (tiedot/->HaeUrakat)))
     :valinta (:urakkatyyppi valinnat)
     :format-fn :nimi
     :vayla-tyyli? true}
    (filter (fn [ut]
              (#{:hoito :paallystys} (:arvo ut)))
      nav/+urakkatyypit+)]
   [:div
    [yleiset/pudotusvalikko
     "ELY"
     {:valitse-fn #(do
                     (e! (tiedot/->AsetaSuodatin :ely %))
                     (e! (tiedot/->HaeUrakat)))
      :valinta (:ely valinnat)
      :format-fn #(or (hal/elynumero-ja-nimi %) "Kaikki")
      :vayla-tyyli? true}
     (into [nil] (map #(select-keys % [:id :nimi :elynumero])
                   @hal/vaylamuodon-hallintayksikot))]
    [yleiset/pudotusvalikko
     (if (= :paallystys (get-in valinnat [:urakkatyyppi :arvo]))
       "Vuosi"
       "Hoitokauden alkuvuosi")
     {:valitse-fn #(do
                     (e! (tiedot/->AsetaSuodatin :urakkavuosi %))
                     (e! (tiedot/->HaeUrakat)))
      :valinta (:urakkavuosi valinnat)
      :vayla-tyyli? true}
     (mahdolliset-hoitokauden-alkuvuodet (pvm/nyt))]

    [:div.label-ja-alasveto
     [:label.alasvedon-otsikko-vayla {:for "urakkahaku"} "Hae urakkaa"]
     [kentat/tee-kentta
      {:tyyppi :haku
       :input-id "urakkahaku"
       :nayta :nimi :fmt :nimi
       :hae-kun-yli-n-merkkia 0
       :vayla-tyyli? true
       :lahde urakkahaku
       :monivalinta? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :hakuikoni? true
       :placeholder "Käytä suurennuslasia tai anna urakan nimi"
       :monivalinta-teksti #(case (count %)
                              0 ""
                              1 (:nimi (first %))
                              (str (count %) " urakkaa valittu"))}
      (r/wrap (:urakat valinnat) #(e! (tiedot/->AsetaSuodatin :urakat %)))]]]])


(defn valikatselmus-sarake
  [rivi]
  (let [{:keys [rahapaatokset lupauspaatokset hoitokauden_alkuvuosi]} rivi
        edellisen-hoitokauden-alkuvuosi (- (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt)))) 1)
        ;; 15.11. on takaraja, milloin edellisen hoitokauden välikatselmus pitää olla tehtynä (edellinen --> kuluva hk -1)
        valikatselmuksen-takaraja-ohi? (or
                                         (< hoitokauden_alkuvuosi edellisen-hoitokauden-alkuvuosi)
                                         (and
                                           (= hoitokauden_alkuvuosi edellisen-hoitokauden-alkuvuosi)
                                           (> (pvm/nyt)
                                             (kustannusten-seuranta-tiedot/valikatselmuksen-takarajapvm (+ hoitokauden_alkuvuosi 1)))))]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry kustannusten seurantaan"]
     [:a.klikattava {:on-click #(siirtymat/kustannusten-seurantaan-valitussa-urakassa (:ely_id rivi) (:id rivi))}
      [:div.rahapaatokset
       (if (nil? rahapaatokset)
         (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
           {:fmt-fn (constantly "Ei katto- tai tavoitehintapäätöksiä")})
         (for [rp rahapaatokset]
           ^{:key (hash rp)}
           [:span
            (yleiset/tila-indikaattori "valmis"
              {:fmt-fn (constantly (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi rp))})]))]
      [:div.lupauspaatokset

       (if (nil? lupauspaatokset)
         (yleiset/tila-indikaattori (if valikatselmuksen-takaraja-ohi? "hylatty" "kesken")
           {:fmt-fn (constantly "Ei lupauspäätöksiä")})
         (for [lp lupauspaatokset]
           ^{:key (hash lp)}
           [:span
            (yleiset/tila-indikaattori "valmis"
              {:fmt-fn (constantly (kustannusten-seuranta-tiedot/valikatselmuksen-paatostyypin-nimi lp))})]))]]]))

(defn kustannussuunitelman-tila-sarake
  [rivi]
  (let [indeksi-saatavilla? (boolean (:indeksikerroin rivi))
        {:keys [aloittamattomia vahvistamattomia vahvistettuja suunnitelman_tila]} (:ks_tila rivi)]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry kustannussuunnitelmaan"]
     [:a.klikattava {:on-click #(siirtymat/kustannussuunnitelmaan-valitussa-urakassa (:ely_id rivi) (:id rivi))}
      (cond
        (= "aloittamatta" suunnitelman_tila)
        (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly "Aloittamatta")})

        (= "aloitettu" suunnitelman_tila)
        (yleiset/tila-indikaattori (if indeksi-saatavilla?
                                     "hylatty"
                                     "kesken")
          {:fmt-fn #(str
                      (when indeksi-saatavilla? "Indeksi saatavilla. ")
                      "Aloittamatta: " aloittamattomia
                      ", kesken: " vahvistamattomia
                      ", vahvistettuja: " vahvistettuja)})

        (= "vahvistettu" suunnitelman_tila)
        (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Valmis")}))]]))

(defn taulukko-paallystysurakat [e!  {:keys [urakat haku-kaynnissa?]}]
  [:div "Tänne tulee lähiaikoina päällystysurakoiden tietoa..."])

(defn taulukko-hoitourakat [e! {:keys [urakat haku-kaynnissa?]}]
  [grid/grid
   {:otsikko (str "")
    :tyhja (if haku-kaynnissa?
             [ajax-loader "Ladataan tietoja"]
             "Ei tietoja, tarkistathan valitut suodattimet.")
    :rivi-jalkeen-fn (fn [urakat]
                       (let [ks-tilojen-yhteenveto (tiedot/ks-tilojen-yhteenveto urakat)
                             valikatselmus-tilojen-yhteenveto (tiedot/valikatselmus-tilojen-yhteenveto urakat)]
                         (when-not (empty? urakat)
                           [{:teksti "Yhteensä" :luokka "lihavoitu"}
                            {:teksti (str (count urakat) " kpl urakoita") :luokka "lihavoitu"}
                            {:teksti ks-tilojen-yhteenveto :luokka "lihavoitu"}
                            {:teksti valikatselmus-tilojen-yhteenveto :luokka "lihavoitu"}])))}
   [{:otsikko "Urakka"
     :tyyppi :string
     :nimi :nimi
     :leveys 5
     :muokattava? (constantly false)}
    {:otsikko "Hoito\u00ADvuosi"
     :muokattava? (constantly false)
     :nimi :hoitokauden_alkuvuosi :leveys 3
     :tyyppi :string :fmt #(pvm/hoitokausi-str-alkuvuodesta %)}
    {:otsikko "Kustannus\u00ADsuunnitelma"
     :muokattava? (constantly false)
     :nimi :ks_tila :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [kustannussuunitelman-tila-sarake rivi])}
    {:otsikko "Väli\u00ADkatselmus"
     :muokattava? (constantly false)
     :nimi :ks_tila :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [valikatselmus-sarake rivi])}]
   urakat])

(defn listaus
  "Listauskomponentti, joka toimii pohjana eri urakkatyypeille, ja hoitaa urakkatyypeille yhteisen suodattamisen elyn, vuoden sekä valitun urakan perusteella."
  [e! {:keys [valinnat urakat haku-kaynnissa?] :as app}]
  (let [valitut-urakat (:urakat valinnat)
        valittu-ely (get-in valinnat [:ely :id])
        valittu-hk-alkuvuosi (:urakkavuosi valinnat)
        ;; ely-suodatus
        urakat (if (nil? valittu-ely)
                 urakat
                 (filter #(= valittu-ely (:ely_id %)) urakat))
        ;; hoitokausisuodatus valittu-hk-alkuvuosi
        urakat (if (nil? valittu-hk-alkuvuosi)
                 urakat
                 (filter #(= valittu-hk-alkuvuosi (:hoitokauden_alkuvuosi %)) urakat))
        ;; urakkasuodatus
        urakat (if (empty? valitut-urakat)
                 urakat
                 (filter #((into #{} (map :id valitut-urakat)) (:id %)) urakat))]
    [:div
     ;; [debug/debug urakat]
     (if (= :paallystys (get-in app [:valinnat :urakkatyyppi :arvo]))
       [taulukko-paallystysurakat e! {:urakat urakat
                                      :haku-kaynnissa? haku-kaynnissa?}]
       [taulukko-hoitourakat e! {:urakat urakat
                                 :haku-kaynnissa? haku-kaynnissa?}])]))


(defn kojelauta* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! app]
      [:div.kojelauta-hallinta
       [:h1 "Urakoiden tilanne"]
       [suodattimet e! app]
       ;; [debug/debug app]
       [listaus e! app]])))

(defn kojelauta []
  [tuck tiedot/tila kojelauta*])
