(ns harja.views.hallinta.kojelauta
  (:require [harja.pvm :as pvm]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :as kentat]
            [harja.ui.valinnat :as valinnat]
            [reagent.core :as r]
            [tuck.core :refer [tuck]]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.hallintayksikot :as hy]
            [harja.tiedot.hallinta.kojelauta :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn- mahdolliset-hoitokauden-alkuvuodet [pvm-nyt]
  (range (- (pvm/vuosi pvm-nyt) 2)
    (+ 6 (pvm/vuosi pvm-nyt))))

(defn suodattimet [e! {:keys [valinnat urakkahaku] :as app}]
  [:<>
   [yleiset/pudotusvalikko
    "ELY"
    {:valitse-fn #(do
                    (e! (tiedot/->Valitse :ely %))
                    (e! (tiedot/->HaeUrakat)))
     :valinta (:ely valinnat)
     :format-fn #(or (hal/elynumero-ja-nimi %) "Kaikki")
     :vayla-tyyli? true}
    (into [nil] (map #(select-keys % [:id :nimi :elynumero])
                  @hy/vaylamuodon-hallintayksikot))]
   [yleiset/pudotusvalikko
    "Hoitokauden alkuvuosi"
    {:valitse-fn  #(do
                     (e! (tiedot/->Valitse :urakkavuosi %))
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
                             0 "Kaikki valittu"
                             1 (:nimi (first %))
                             (str (count %) " urakkaa valittu"))}
     (r/wrap (:urakat valinnat) #(e! (tiedot/->Valitse :urakat %)))]]])


(defn kustannussuunitelman-tila-sarake
  [rivi]
  (let [{:keys [vahvistamattomia vahvistettuja suunnitelman_tila]} (:ks_tila rivi)]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Klikkaa siirtyäksesi urakan kustannussuunnitelmaan"]
     [:div.klikattava {:on-click #(do
                                    (prn "Jarno on click! urakassa " (:id rivi) " ja elyssä " (:ely_id rivi))
                                    (siirtymat/kustannusten-seurantaan-valitussa-urakassa (:ely_id rivi) (:id rivi)))}
      (cond
        (= "aloittamatta" suunnitelman_tila)
        (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly "Aloittamatta")})

        (= "aloitettu" suunnitelman_tila)
        (yleiset/tila-indikaattori "kesken" {:fmt-fn #(str "Aloitettuja: " vahvistamattomia
                                                        ", vahvistettuja: " vahvistettuja)})

        (= "vahvistettu" suunnitelman_tila)
        (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Valmis")}))]]))

(defn listaus [e! {:keys [valinnat urakat urakoiden-tilat] :as app}]
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
     [grid/grid
      {:otsikko (str "Urakoiden tilat")
       :tyhja (if (nil? urakat) [ajax-loader "Ladataan tietoja"] "Ei tietoja, tarkistathan valitut suodattimet.")
       :rivi-jalkeen-fn (fn [urakat]
                          (let [ks-tilojen-yhteenveto (tiedot/ks-tilojen-yhteenveto urakat)]
                            (when-not (empty? urakat)
                              [{:teksti "Yhteensä" :luokka "lihavoitu"}
                               {:teksti (str (count urakat) " kpl urakoita") :luokka "lihavoitu"}
                               {:teksti ks-tilojen-yhteenveto :luokka "lihavoitu"}])))}
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
        :komponentti (fn [rivi] [kustannussuunitelman-tila-sarake rivi])}]
      urakat]]))


(defn kojelauta* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! app]
      [:div.kojelauta-hallinta
       [:h1 "Etusivu"]
       [suodattimet e! app]
       ;; [debug/debug app]
       [listaus e! app]])))

(defn kojelauta []
  [tuck tiedot/tila kojelauta*])