(ns harja.views.urakka.muutokset.yhteiset
  "Muutokset välilehden yhteiset komponentit"
  (:require [reagent.core :as r]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))


(defn liite-kentta
  "Lomakkeen liitekenttä, joka näyttää liitteiden listauksen ja mahdollistaa uusien liitteiden lisäämisen."
  [e! {:keys [uusi-liite muokattava-muutos] :as _app}]
  [{:otsikko "Liite"
    :nimi :liitteet
    :kaariva-luokka "muutosliite"
    :tyyppi :komponentti
    ::lomake/col-luokka "col-xs-12"
    :uusi-rivi? true
    :komponentti (fn [_]
                   (when (every? :nimi (:liitteet muokattava-muutos))
                     [liitteet/liitteet-ja-lisays
                      @nav/valittu-urakka-id
                      (:liitteet muokattava-muutos)
                      {:uusi-liite-atom (r/wrap uusi-liite
                                          #(e! (t-yhteiset/->LisaaLiite %)))
                       :uusi-liite-teksti "Lisää liite"
                       :nayta-lisatyt-liitteet? false
                       :lisaa-usea-liite? true
                       :salli-poistaa-lisatty-liite? true
                       :poista-lisatty-liite-fn #(e! (t-yhteiset/->PoistaLisattyLiite))
                       :salli-poistaa-tallennettu-liite? true
                       :poista-tallennettu-liite-fn #(e! (t-yhteiset/->PoistaTallennettuLiite %))}]))}])


(defn kehystetty-avattava-grid
  "Piirtää yhtenäisesti Muutoksien taulukot collapsoitaviksi.
   summan saa piiloon antamalla sille arvon :ei-summaa"
  [e! app {:keys [taulukon-avain taulukon-nakyvyys-event
                  otsikko summa toiminnot toiminnot-asetukset taulukko] :as _tiedot}]

  (let [sisalto-nakyvissa? (get-in app [:taulukko-nakyvissa? taulukon-avain])
        toiminnot-asetukset (merge
                              {:tasaa :vasen}
                              (when (map? toiminnot-asetukset) toiminnot-asetukset))]
    [:div.collapsoitava-osio
     [:div.otsikkorivi.klikattava {:on-click taulukon-nakyvyys-event}
      [:span
       [ikonit/navigation-ympyrassa (if sisalto-nakyvissa?
                                      :down
                                      :right)]
       [:h2 otsikko]]

      (when-not (= summa :ei-summaa)
        [:div.summa {:aria-label (str otsikko " yhteensä " summa " euroa")}
         (fmt/euro-opt summa)])]

     (when sisalto-nakyvissa?
       [:span
        [:div.toiminnot {:style (merge {}
                                  (when (= :oikea (:tasaa toiminnot-asetukset))
                                    {:float "right"}))}
         [toiminnot e! app]]
        [:div.taulukko
         [taulukko e! app]]])]))


(defn +rivi-muutoksen-syy+ []
  {:nimi :syy
   :otsikko "Muutoksen syy"
   :tyyppi :text
   :palstoja 2
   :koko [90 4]
   :aputeksti (str
                "Kuvaile muutos mahdollisimman tarkasti. "
                "Ethän syötä kenttään henkilö- tai muuta arkaluontoista tietoa.")
   :pituus-max 1000
   :uusi-rivi? true
   :pakollinen? true
   :validoi [#(when (nil? (seq %)) "Syötä muutoksen syy")]
   ::lomake/col-luokka "perustiedot col-sm-6 aputeksti"})


(defn +rivi-muutos-voimassa+
  ([urakan-hoitokaudet valittu-hoitokausi]
   (+rivi-muutos-voimassa+ urakan-hoitokaudet valittu-hoitokausi {}))
  ([urakan-hoitokaudet valittu-hoitokausi {:keys [pakota-valittuun-hoitokauteen? voi-muokata?]
                                           :or {pakota-valittuun-hoitokauteen? true
                                                voi-muokata? true} :as opts}]
   {:otsikko "Voimassa alkaen"
    :nimi :voimassa_alkaen
    :tyyppi :pvm
    :muokattava? (constantly voi-muokata?)
    :pakollinen? true
    ;; Rajoita valinta hoitokaudelle 
    :validoi [(fn [valittu-pvm]
                (if pakota-valittuun-hoitokauteen?
                  ;; Katsotaan että voimassa alkaen osuu valittuun hoitokauteen
                  ;; Erityisesti tärkeä muutostyö kirjauksissa 
                  (let [pvm-hk-valissa? (boolean (when valittu-hoitokausi
                                                   (pvm/valissa?
                                                     valittu-pvm
                                                     (first valittu-hoitokausi)
                                                     (second valittu-hoitokausi))))]
                    (when (and
                            valittu-hoitokausi
                            (not pvm-hk-valissa?))
                      (str
                        "Voimassa alkaen täytyy kohdistua valittuun hoitokauteen "
                        "("
                        (pvm/pvm (first valittu-hoitokausi)) " - "
                        (pvm/pvm (second valittu-hoitokausi))
                        ").")))

                  ;; Esimerkiksi pysyvässä muutoksessa kevyempi validointi riittää,
                  ;; koska pysyvässä muutoksessa valitaan hoitokausi erikseen ja voimassa-alkaen
                  ;; saa osua mihin tahansa hoitokauteen
                  (when (nil? valittu-pvm) "Syötä muutoksen voimassaolo pvm")))]
    ;; Pysyvän muutoksen lomakkeella valitaan hoitokausi mistä eteenpäin muutos vaikuttaa. Se ei saa olla
    ;; pienempi kuin voimassa alkaen, joten kutsuttava :aseta funktiota. Ei vaikuta ainakaan vielä muissa muutostyypeissä
    :aseta (fn [rivi arvo]
             (-> rivi
               (assoc :voimassa_alkaen arvo)
               (assoc :mahdolliset-hoitovuodet-lomakkeella urakan-hoitokaudet)))}))


(defn +rivi-muutos-tavoitehinta+ []
  {:otsikko "Tavoitehinnan muutos"
   :pakollinen? true
   :vayla-tyyli? true
   :nimi :tavoitehinnan-muutos
   :tyyppi :euro
   :teksti-oikealla "EUR"
   :validoi [#(when (nil? %) "Syötä tavoitehinnan muutos")
             [:rajattu-numero -999999999 999999999 "Anna arvo väliltä 0 - 999 999 999"]]
   ::lomake/col-luokka "perustiedot col-xs-6"})


(defn lomake-yhteinen [e!
                       {:keys [urakan-hoitokaudet valittu-hoitokausi] :as app}]
  (concat
    [(lomake/ryhma {:otsikko "Perustiedot"}
       (+rivi-muutoksen-syy+)
       (+rivi-muutos-voimassa+ urakan-hoitokaudet valittu-hoitokausi)
       (+rivi-muutos-tavoitehinta+))]
    (liite-kentta e! app)))
