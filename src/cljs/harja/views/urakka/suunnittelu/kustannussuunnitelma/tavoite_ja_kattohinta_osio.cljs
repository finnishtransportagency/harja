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
            [harja.ui.komponentti :as komp]))


;; -- Tavoite- ja kattohinta osioon liittyvät gridit --

;; NOTE: Gridit määritellään kustannussuunnitelma_view pääkomponentissa suoraan maarataulukko-grid apurilla.

;; | -- Gridit päättyy



;; -----
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
;; {0 <- Pakollinen indeksi jokaisen rivin avaimena
;;  {:kattohinta-vuosi-1 1234
;;   :kattohinta-vuosi-2 1234
;;   ...
;;   :yhteensa 5432
;;   :rivi :kattohinta}
;;  1 <- uusi rivi
;;  {:kattohinta-vuosi-1 1235
;;   :kattohinta-vuosi-2 1235
;;   ...
;;   :yhteensa 5436
;;   :rivi :indeksikorjattu}} <- rivi-avaimesta päätellään, mitkä rivit disabloidaan.
(defn- manuaalinen-kattohinta-grid
  [e! tavoitehinnat]
  (let [ohjauskahva (grid/grid-ohjaus)]
    (komp/luo
      (komp/piirretty #(grid/validoi-grid ohjauskahva))
      (komp/kun-muuttui #(grid/validoi-grid ohjauskahva))
      (fn [e! tavoitehinnat]
        [:div
         [:h5 "Kattohinta"]

         [:div
          {:on-blur #(when (empty? @t/kattohinta-virheet) (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))}

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
            :muutos #(e! (t/->PaivitaKattohintaGrid %))
            :valiotsikot {1 (grid/otsikko "Indeksikorjatut")}
            :disabloi-rivi? #(not= :kattohinta (:rivi %))
            :sisalto-kun-rivi-disabloitu #(fmt/euro-opt ((:nimi %) (:rivi %)))}
           (merge
             (mapv (fn [hoitovuosi-numero]
                     {:otsikko (str hoitovuosi-numero ".hoitovuosi")
                      :nimi (keyword (str "kattohinta-vuosi-" hoitovuosi-numero))
                      :fmt fmt/euro-opt
                      :validoi [[:manuaalinen-kattohinta (get-in tavoitehinnat [(dec hoitovuosi-numero) :summa])]]
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
  [e! vahvistettu? urakka yhteenvedot kuluva-hoitokausi indeksit kantahaku-valmis?]
  ;; TODO: Toteuta kattohinnalle käsin syöttämisen mahdollisuus myöhemmin: VHAR-4858
  (let [tavoitehinnat (mapv (fn [summa]
                              {:summa summa})
                        (t/tavoitehinnan-summaus yhteenvedot))
        kattohinnat (mapv #(update % :summa * 1.1) tavoitehinnat)
        urakan-aloitusvuosi (pvm/vuosi (:alkupvm urakka))
        manuaalinen-kattohinta? (some #(= urakan-aloitusvuosi %) t/manuaalisen-kattohinnan-syoton-vuodet)]
    [:<>
     [:h2 {:id (str "tavoite-ja-kattohinta" "-osio")} "Tavoite- ja kattohinta"]
     [tavoitehinta-yhteenveto tavoitehinnat kuluva-hoitokausi indeksit kantahaku-valmis?]
     [:span#tavoite-ja-kattohinta-huomio "Vuodet ovat hoitovuosia"]
     (if kantahaku-valmis?
       (if manuaalinen-kattohinta?
         ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
         [:div {:class (when vahvistettu? "osio-vahvistettu")}
          [manuaalinen-kattohinta-grid e! tavoitehinnat]]
         [kattohinta-yhteenveto kattohinnat kuluva-hoitokausi indeksit])
       [yleiset/ajax-loader])]))