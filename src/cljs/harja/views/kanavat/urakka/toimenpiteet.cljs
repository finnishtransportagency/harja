(ns harja.views.kanavat.urakka.toimenpiteet
  (:require [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [reagent.core :refer [atom] :as r]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as kohteenosa]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.vesivaylat.materiaali :as materiaali]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.tiedot.kanavat.urakka.toimenpiteet :as kanavatoimenpidetiedot]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]))

(defn valittu-tehtava [toimenpide]
  (or (::kanavan-toimenpide/toimenpidekoodi-id toimenpide)
      (get-in toimenpide [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/id])))

(defn toimenpidesarakkeet [e! app {:keys [rivi-valittu?-fn rivi-valittu-fn kaikki-valittu?-fn otsikko-valittu-fn]}]
  [{:otsikko "Päivä\u00ADmäärä"
    :nimi ::kanavan-toimenpide/pvm
    :tyyppi :pvm
    :fmt pvm/pvm-opt
    :leveys 5}
   {:otsikko "Kohde"
    :nimi :kohde
    :tyyppi :string
    :leveys 10
    :hae kanavan-toimenpide/fmt-toimenpiteen-kohde}
   {:otsikko "Huolto\u00ADkohde"
    :nimi :huoltokohde
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/nimi])
    :fmt str/lower-case
    :leveys 10}
   {:otsikko "Tehtävä"
    :nimi :toimenpide
    :tyyppi :string
    :hae #(get-in % [::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi])
    :leveys 15}
   {:otsikko "Lisä\u00ADtieto"
    :nimi ::kanavan-toimenpide/lisatieto
    :tyyppi :string
    :leveys 10}
   {:otsikko "Muu toimen\u00ADpide"
    :nimi ::kanavan-toimenpide/muu-toimenpide
    :tyyppi :string
    :leveys 10}
   {:otsikko "Suorit\u00ADtaja"
    :nimi ::kanavan-toimenpide/suorittaja
    :tyyppi :string
    :leveys 10}
   {:otsikko "Kuit\u00ADtaaja"
    :nimi ::kanavan-toimenpide/kuittaaja
    :tyyppi :string
    :hae #(kayttaja/kokonimi (::kanavan-toimenpide/kuittaaja %))
    :leveys 10}
   (grid/rivinvalintasarake
     {:otsikkovalinta? true
      :kaikki-valittu?-fn kaikki-valittu?-fn
      :otsikko-valittu-fn otsikko-valittu-fn
      :rivi-valittu?-fn rivi-valittu?-fn
      :rivi-valittu-fn rivi-valittu-fn
      :leveys 5})])

(defn toimenpiteiden-toiminto-suoritettu [toimenpiteiden-lkm toiminto]
  (str toimenpiteiden-lkm " "
       (if (= 1 toimenpiteiden-lkm) "toimenpide" "toimenpidettä")
       " " toiminto "."))


(defn varaosataulukko [urakan-materiaalit avattu-toimenpide muokkaa-materiaaleja-fn lisaa-virhe-fn varaosat-virheet]
  (assert urakan-materiaalit)
  (let [voi-muokata? true
        avatun-materiaalit (::materiaali/materiaalit avattu-toimenpide)
        virhe-atom (r/wrap varaosat-virheet lisaa-virhe-fn)
        vertailuavaimet-jarjestysnumerolla (fn [materiaalin-kirjaus]
                  (if (and (get-in materiaalin-kirjaus [:varaosa ::materiaali/nimi])
                           (nil? (:jarjestysnumero materiaalin-kirjaus)))
                    [nil (get-in materiaalin-kirjaus [:varaosa ::materiaali/nimi])]
                    [(:jarjestysnumero materiaalin-kirjaus) nil]))
        muokatut-atom (r/wrap
                    (zipmap (range)
                            (sort-by vertailuavaimet-jarjestysnumerolla avatun-materiaalit))
                    #(muokkaa-materiaaleja-fn (sort-by vertailuavaimet-jarjestysnumerolla (vals %)))
                    )]
    (log "varaosataulukko: muokatut sis " (if muokatut (pr-str @muokatut) "nil") )
    (log "materiaalit sis " (pr-str avatun-materiaalit))
    (log "avatun avaimet " (pr-str (keys avattu-toimenpide)))
    [grid/muokkaus-grid
     {:voi-muokata? voi-muokata?
      :voi-lisata? false
      :voi-poistaa? (constantly voi-muokata?)
      :voi-kumota? false
      :virheet virhe-atom
      :piilota-toiminnot? false
      :tyhja "Ei varaosia"
      :otsikko "Varaosat"}
     [{:otsikko "Varaosa"
       :nimi :varaosa
       :validoi [[:ei-tyhja "Tieto puuttuu"]]
       :tyyppi :valinta
       :valinta-nayta #(or (::materiaali/nimi %) "- Valitse varaosa -")
       :valinnat urakan-materiaalit}
      {:otsikko "Käytetty määrä"
       :nimi :maara
       :validoi [[:ei-tyhja "Tieto puuttuu"]]
       :tyyppi :positiivinen-numero
       :kokonaisluku? true}]
     muokatut-atom]))

(defn toimenpidelomakkeen-kentat [{:keys [toimenpide sopimukset kohteet huoltokohteet
                                          toimenpideinstanssit tehtavat urakan-materiaalit lisaa-materiaali-fn
                                          muokkaa-materiaaleja-fn lisaa-virhe-fn varaosat-virheet]}]
  (assert urakan-materiaalit)
  (let [tehtava (valittu-tehtava toimenpide)
        valittu-kohde-id (get-in toimenpide [::kanavan-toimenpide/kohde ::kohde/id])
        valitun-kohteen-osat (::kohde/kohteenosat (kohde/kohde-idlla kohteet valittu-kohde-id))]
    [{:otsikko "Sopimus"
      :nimi ::kanavan-toimenpide/sopimus-id
      :tyyppi :valinta
      :valinta-arvo first
      :valinta-nayta second
      :valinnat sopimukset
      :pakollinen? true}
     {:otsikko "Päivämäärä"
      :nimi ::kanavan-toimenpide/pvm
      :tyyppi :pvm
      :fmt pvm/pvm-opt
      :pakollinen? true}
     (lomake/rivi
       {:otsikko "Kohde"
       :nimi ::kanavan-toimenpide/kohde
       :tyyppi :valinta
       :valinta-nayta #(or (::kohde/nimi %) "- Valitse kohde -")
       :valinnat kohteet})
     (when (::kanavan-toimenpide/kohde toimenpide)
       {:otsikko "Kohteen osa"
        :nimi ::kanavan-toimenpide/kohteenosa
        :tyyppi :valinta
        :valinta-nayta #(or (kohteenosa/fmt-kohdeosa %) "- Valitse osa -")
        :valinnat (or valitun-kohteen-osat [])})
     (when (::kanavan-toimenpide/kohde toimenpide)
       {:otsikko "Huoltokohde"
        :nimi ::kanavan-toimenpide/huoltokohde
        :tyyppi :valinta
        :valinta-nayta #(or (when-let [nimi (::kanavan-huoltokohde/nimi %)]
                              (str/lower-case nimi))
                            "- Valitse huoltokohde -")
        :valinnat huoltokohteet
        :pakollinen? true})
     {:otsikko "Toimenpide"
      :nimi ::kanavan-toimenpide/toimenpideinstanssi-id
      :pakollinen? true
      :tyyppi :valinta
      :uusi-rivi? true
      :valinnat toimenpideinstanssit
      :fmt #(:tpi_nimi (urakan-toimenpiteet/toimenpideinstanssi-idlla % toimenpideinstanssit))
      :valinta-arvo :tpi_id
      :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
      :aseta (fn [rivi arvo]
               (-> rivi
                   (assoc ::kanavan-toimenpide/toimenpideinstanssi-id arvo)
                   (assoc-in [:tehtava :toimenpideinstanssi :id] arvo)
                   (assoc-in [:tehtava :toimenpidekoodi :id] nil)
                   (assoc-in [:tehtava :yksikko] nil)))}
     {:otsikko "Tehtävä"
      :nimi ::kanavan-toimenpide/toimenpidekoodi-id
      :pakollinen? true
      :tyyppi :valinta
      :valinnat tehtavat
      :valinta-arvo :id
      :valinta-nayta #(or (:nimi %) "- Valitse tehtävä -")
      :hae #(valittu-tehtava %)
      :aseta (fn [rivi arvo]
               (-> rivi
                   (assoc ::kanavan-toimenpide/toimenpidekoodi-id arvo)
                   (assoc-in [:tehtava :tpk-id] arvo)
                   (assoc-in [:tehtava :yksikko] (:yksikko (urakan-toimenpiteet/tehtava-idlla arvo tehtavat)))))}
     (when (kanavatoimenpidetiedot/valittu-tehtava-muu? tehtava tehtavat)
       {:otsikko "Muu toimenpide"
        :nimi ::kanavan-toimenpide/muu-toimenpide
        :tyyppi :string})
     {:otsikko "Lisätieto"
      :nimi ::kanavan-toimenpide/lisatieto
      :tyyppi :string}
     {:otsikko "Suorittaja"
      :nimi ::kanavan-toimenpide/suorittaja
      :tyyppi :string
      :pakollinen? true}
     {:otsikko "Kuittaaja"
      :nimi ::kanavan-toimenpide/kuittaaja
      :tyyppi :string
      :hae #(kayttaja/kokonimi (::kanavan-toimenpide/kuittaaja %))
      :muokattava? (constantly false)}
     {:nimi :varaosat
      :tyyppi :komponentti
      :palstoja 2
      :komponentti (fn [_]
                     [varaosataulukko urakan-materiaalit toimenpide muokkaa-materiaaleja-fn lisaa-virhe-fn varaosat-virheet])}
     {:nimi :lisaa-varaosa
      :tyyppi :komponentti
      :uusi-rivi? true
      :komponentti (fn [_]
                     (assert lisaa-materiaali-fn)
                     [napit/uusi "Lisää varaosa"
                      lisaa-materiaali-fn
                      ;; todo: katsotaan oikeustarkistuksesta näytetäänkö nappia
                      {:disabled false}])}]))

(defn- ei-yksiloity-vihje []
  [yleiset/vihje-elementti [:span
                            [:span "Ei-yksilöidyt toimenpiderivit näytetään "]
                            [:span.bold "lihavoituna"]
                            [:span "."]]])

(defn- lomake-toiminnot [{:keys [tallenna-lomake-fn poista-toimenpide-fn]}
                         {:keys [tallennus-kaynnissa?] :as app}
                         toimenpide]
  [:div
   [napit/tallenna
    "Tallenna"
    #(tallenna-lomake-fn toimenpide)
    {:tallennus-kaynnissa? tallennus-kaynnissa?
     :disabled (or (not (lomake/voi-tallentaa? toimenpide))
                   (not-empty (:varaosat-taulukon-virheet toimenpide)))}]
   (when (not (nil? (::kanavan-toimenpide/id toimenpide)))
     [napit/poista
      "Poista"
      #(varmista-kayttajalta/varmista-kayttajalta
         {:otsikko "Toimenpiteen poistaminen"
          :sisalto [:div "Haluatko varmasti poistaa toimenpiteen?"]
          :hyvaksy "Poista"
          :toiminto-fn (fn [] (poista-toimenpide-fn toimenpide))})])])

(defn toimenpidelomake [{:keys [huoltokohteet avattu-toimenpide
                                toimenpideinstanssit tehtavat urakan-materiaalit] :as app}
                        {:keys [tyhjenna-fn aseta-toimenpiteen-tiedot-fn
                                tallenna-lomake-fn poista-toimenpide-fn lisaa-materiaali-fn
                                muokkaa-materiaaleja-fn lisaa-virhe-fn]}]
  (let [urakka (get-in app [:valinnat :urakka])
        sopimukset (:sopimukset urakka)
        kanavakohteet @kanavaurakka/kanavakohteet
        lomake-valmis? (and (not (empty? huoltokohteet))
                            (not (empty? kanavakohteet)))]
    [:div
     [napit/takaisin "Takaisin toimenpideluetteloon" tyhjenna-fn]
     (if lomake-valmis?
       [lomake/lomake
        {:otsikko (if (::kanavan-toimenpide/id avattu-toimenpide) "Muokkaa toimenpidettä" "Uusi toimenpide")
         :muokkaa! aseta-toimenpiteen-tiedot-fn
         :footer-fn (fn [toimenpide]
                      (lomake-toiminnot {:tallenna-lomake-fn tallenna-lomake-fn
                                         :poista-toimenpide-fn poista-toimenpide-fn}
                                        app toimenpide))}
        (toimenpidelomakkeen-kentat {:toimenpide avattu-toimenpide
                                     :sopimukset sopimukset
                                     :kohteet kanavakohteet
                                     :huoltokohteet huoltokohteet
                                     :toimenpideinstanssit toimenpideinstanssit
                                     :tehtavat tehtavat
                                     :urakan-materiaalit urakan-materiaalit
                                     :lisaa-materiaali-fn lisaa-materiaali-fn
                                     :muokkaa-materiaaleja-fn muokkaa-materiaaleja-fn
                                     :lisaa-virhe-fn lisaa-virhe-fn
                                     :varaosat-virheet (-> app :avattu-toimenpide :varaosat-taulukon-virheet)})
        avattu-toimenpide]
       [ajax-loader "Ladataan..."])]))
