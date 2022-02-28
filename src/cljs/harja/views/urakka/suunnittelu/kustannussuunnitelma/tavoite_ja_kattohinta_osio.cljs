(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoite-ja-kattohinta-osio
  (:require [reagent.core :as r :refer [cursor]]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]))

;; -- Tavoite- ja kattohinta osion apufunktiot --

(defn- tavoitehinta-yhteenveto
  [tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
  (if (and indeksit kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [ks-yhteiset/hintalaskuri
      {:otsikko "Tavoitehinta"
       :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
       :hinnat (update tavoitehinnat 0 assoc :teksti "1. vuosi*")
       :data-cy "tavoitehinnan-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri tavoitehinnat indeksit {:dom-id "tavoitehinnan-indeksikorjaus"
                                                         :data-cy "tavoitehinnan-indeksilaskuri"}]]
    [yleiset/ajax-loader]))

;; Kattohinnan grid käyttää harja.ui.grid/muokkaus-tyyppistä taulukkoa.
;; Tälle pitää antaa tiedot atomissa, jonka muoto on jotain tällaista:
;;
;; {:kattohinta
;;  {:kattohinta-vuosi-1 1234
;;   :kattohinta-vuosi-2 1234
;;   ...
;;   :yhteensa 5432
;;   :rivi :kattohinta}
;;  :indeksikorjaukset
;;  {:kattohinta-vuosi-1 1235
;;   :kattohinta-vuosi-2 1235
;;   ...
;;   :yhteensa 5436
;;   :rivi :indeksikorjattu} <- rivi-avaimesta päätellään, mitkä rivit disabloidaan.
;;  :oikaistut
;; {:kattohinta-vuosi-1 1236
;;  ...
;; :rivi :oikaistut
;; }
;; }
(defn- manuaalinen-kattohinta-grid
  [e! tavoitehinnat suodattimet]
  (let [ohjauskahva (grid/grid-ohjaus)]
    (komp/luo
      (komp/piirretty #(grid/validoi-grid ohjauskahva))
      (komp/kun-muuttui #(grid/validoi-grid ohjauskahva))
      (fn [e! tavoitehinnat {:keys [hoitokauden-numero] :as suodattimet}]
        [:<>
         [:h5 "Kattohinta"]
         [:div.kattohinta-hoitovuosi-suodatin {:data-cy "erillishankinnat-taulukko-suodattimet"}
          [ks-yhteiset/yleis-suodatin suodattimet {:piilota-kopiointi? true}]]

         [:div
          {:on-blur #(when (nil? (get-in @t/kattohinta-virheet
                                   [:kattohinta (keyword (str "kattohinta-vuosi-" hoitokauden-numero))])) (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))}

          [grid/muokkaus-grid
           {:ohjaus ohjauskahva
            :otsikko "Kattohinta"
            :luokat ["kattohinta-grid"]
            :data-cy "manuaalinen-kattohinta-grid"
            :piilota-toiminnot? true
            :muokkauspaneeli? false
            :ulkoinen-validointi? false
            :virheet t/kattohinta-virheet
            :virheet-dataan? true
            :muutos #(e! (t/->PaivitaKattohintaGrid))
            :valiotsikot {:indeksikorjaukset (grid/otsikko "Indeksikorjattu")
                          :oikaistut (grid/otsikko "Kattohinta oikaistu")}
            :disabloi-rivi? #(not= :kattohinta (:rivi %))
            :sisalto-kun-rivi-disabloitu #(fmt/euro-opt ((:nimi %) (:rivi %)))}
           (merge
             (mapv (fn [hoitokausi]
                     {:otsikko (str hoitokausi ".hoitovuosi")
                      :nimi (keyword (str "kattohinta-vuosi-" hoitokausi))
                      :fmt #(fmt/euro-opt %)
                      :validoi [[:manuaalinen-kattohinta (get-in tavoitehinnat [(dec hoitokausi) :summa])]]
                      :muokattava? #(= hoitokausi hoitokauden-numero)
                      :tyyppi :positiivinen-numero})
               (range 1 6))
             {:otsikko "Yhteensä"
              :nimi :yhteensa
              :tyyppi :positiivinen-numero
              :muokattava? (constantly false)
              :disabled? true
              :fmt fmt/euro-opt
              :hae #(apply + (vals
                               (select-keys % t/kattohinta-grid-avaimet)))})
           t/kattohinta-grid]]]))))

(defn- kattohinta-yhteenveto
  [kattohinnat kuluva-hoitokausi indeksit]
  (if indeksit
    [:div.summa-ja-indeksilaskuri
     [ks-yhteiset/hintalaskuri
      {:otsikko "Kattohinta"
       :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
       :hinnat kattohinnat
       :data-cy "kattohinnan-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri kattohinnat indeksit {:dom-id "kattohinnan-indeksikorjaus"
                                                       :data-cy "kattohinnan-indeksilaskuri"}]]
    [yleiset/ajax-loader]))



;; ### Tavoite- ja kattohinta osion pääkomponentti ###
;; Käyttää vaihtoehtoista harja.ui.grid/muokkaus - komponenttia

(defn osio
  [e! vahvistettu? urakka yhteenvedot kuluva-hoitokausi indeksit suodattimet kantahaku-valmis?]
  ;; TODO: Toteuta kattohinnalle käsin syöttämisen mahdollisuus myöhemmin: VHAR-4858
  (let [tavoitehinnat (mapv (fn [summa]
                              {:summa summa})
                        (t/tavoitehinnan-summaus yhteenvedot))
        kattohinnat (mapv #(update % :summa * 1.1) tavoitehinnat)
        urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))
        manuaalinen-kattohinta? (some #(= urakan-aloitusvuosi %) t-yhteiset/manuaalisen-kattohinnan-syoton-vuodet)]
    [:<>
     [:h2 {:id (str "tavoite-ja-kattohinta" "-osio")} "Tavoite- ja kattohinta"]
     [tavoitehinta-yhteenveto tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
     [:span#tavoite-ja-kattohinta-huomio "Vuodet ovat hoitovuosia"]
     (if kantahaku-valmis?
       (if manuaalinen-kattohinta?
         ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
         [:div {:class (when vahvistettu? "osio-vahvistettu")}
          [manuaalinen-kattohinta-grid e! tavoitehinnat suodattimet]]
         [kattohinta-yhteenveto kattohinnat kuluva-hoitokausi indeksit])
       [yleiset/ajax-loader])]))