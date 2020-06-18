(ns harja.views.ilmoitukset.tietyoilmoituslomake
  (:require [reagent.core :as r :refer [atom]]
            [harja.ui.lomake :as lomake]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.views.ilmoitukset.tietyoilmoitukset-yhteinen :as tietyo-yhteiset]
            [harja.domain.tietyoilmoitus :as t]
            [harja.domain.tietyoilmoituksen-email :as e]
            [harja.domain.tierekisteri :as tr]
            [reagent.core :refer [atom] :as r]
            [harja.ui.grid :refer [muokkaus-grid] :as grid]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+
                                                  kuvaus-ja-avainarvopareja vihje]]
            [harja.ui.grid.protokollat :as grid-protokollat]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [harja.ui.kentat :as kentat]
            [harja.transit :as transit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.modal :as modal]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.loki :as loki]
            [harja.ui.debug :as ui-debug])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn- urakka-valinnat [urakat]
  (into [[nil "Ei liity HARJA-urakkaan"]]
        (map (juxt :id :nimi))
        urakat))

(defn- pvm-vali-paivina [p1 p2]
  (when (and p1 p2)
    (.toFixed (/ (Math/abs (- p1 p2)) (* 1000 60 60 24)) 2)))

(def tyhja-kentta {:nimi :blank
                   :otsikko "" :hae (constantly "")
                   :muokattava false
                   :tyyppi :tyhja})

(defn tienpinnat-komponentti-grid [e! avain tienpinnat-tiedot]
  (let [tp-valinnat [["paallystetty" "Päällystetty"]
                     ["jyrsitty" "Jyrsitty"]
                     ["murske" "Murske"]]
        tienpinnat-tiedot-atom (atom (into {}
                                           (map-indexed (fn [i ta]
                                                          [i ta]))
                                           tienpinnat-tiedot))
        muutos-fn (fn [g]
                    (let [taulukon-tila (grid-protokollat/hae-muokkaustila g)]
                      (e! (tiedot/->PaivitaTienPinnat (vals taulukon-tila) avain))))
        ]

    (fn []
      [muokkaus-grid {:otsikko ""
                      :voi-muokata? (constantly true)
                      :voi-poistaa? (constantly true)
                      :piilota-toiminnot? false
                      :tyhja "Ei tienpintatietoja"
                      :jarjesta :jarjestysnro
                      :tunniste :jarjestysnro
                      :muutos muutos-fn
                      :uusi-rivi (fn [rivi]
                                   (if (::t/materiaali rivi)
                                     rivi
                                     (assoc rivi ::t/materiaali (ffirst tp-valinnat))))}
       [{:otsikko "Materiaali" :nimi ::t/materiaali :tyyppi :valinta
         :valinnat tp-valinnat
         :valinta-arvo first
         :valinta-nayta second
         :pakollinen? true
         :validoi [[:ei-tyhja "Valitse materiaali"]]
         :leveys 1}
        {:otsikko "Matka (m)" :nimi ::t/matka :tyyppi :positiivinen-numero
         :leveys 1}]
       tienpinnat-tiedot-atom])))

(defn nopeusrajoitukset-komponentti-grid [e! nr-tiedot]
  (let [nr-tiedot-atom (atom (into {}
                                   (map-indexed (fn [i na] [i na]))
                                   nr-tiedot))
        muutos-fn (fn [g]
                    (let [taulukon-tilat (grid-protokollat/hae-muokkaustila g)]
                      (e! (tiedot/->PaivitaNopeusrajoitukset (vals taulukon-tilat)))))]

    (fn []
      [muokkaus-grid {:otsikko ""
                      :voi-muokata? (constantly true)
                      :voi-poistaa? (constantly true)
                      :piilota-toiminnot? false
                      :muutos muutos-fn
                      :tyhja "Ei nopeusrajoituksia"
                      :jarjesta :jarjestysnro
                      :tunniste :jarjestysnro
                      :uusi-rivi (fn [rivi]
                                   (if (::t/rajoitus rivi)
                                     rivi
                                     (assoc rivi ::t/rajoitus (first t/nopeusrajoitukset))))}
       [{:otsikko "Rajoitus (km/h)" :nimi ::t/rajoitus
         :tyyppi :valinta
         :valinnat t/nopeusrajoitukset
         :pakollinen? true
         :validoi [[:ei-tyhja "Valitse rajoitus"]]
         :leveys 1}
        {:otsikko "Matka (m)" :nimi ::t/matka :tyyppi :positiivinen-numero
         :leveys 1}]
       nr-tiedot-atom])))

(defn kokorajoitukset-komponentti [e! ilmoitus]
  [muokkaus-grid {:otsikko            "Ajoneuvon kokorajoitukset"
                  :voi-muokata?       true
                  :voi-poistaa?       false
                  :voi-lisata?        false
                  :voi-kumota?        false
                  :piilota-toiminnot? true}
   [{:otsikko "Maks. korkeus (m)" :nimi ::t/max-korkeus
     :tyyppi  :positiivinen-numero}
    {:otsikko "Maks. leveys (m)" :nimi ::t/max-leveys
     :tyyppi  :positiivinen-numero}
    {:otsikko "Maks. pituus (m)" :nimi ::t/max-pituus
     :tyyppi  :positiivinen-numero}
    {:otsikko "Maks. paino (kg)" :nimi ::t/max-paino
     :tyyppi  :positiivinen-numero}]
   (r/wrap {0 (::t/ajoneuvorajoitukset ilmoitus)}
           #(e! (tiedot/->PaivitaKokorajoitukset (get % 0))))])


(defn- grid-virheita?
  "Palauttaa true/false onko annetussa muokkaus-grid datassa virheitä"
  [uusi-data]
  (boolean (some (comp not empty? ::grid/virheet)
                 (vals uusi-data))))

(defn pysaytys-ajat-komponentti [e! ilmoitus]
  [muokkaus-grid {:otsikko ""
                  :voi-muokata? true
                  :voi-poistaa? false
                  :voi-lisata? false
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  :virheet-dataan? true}
   [{:otsikko "Pysäytykset alkavat" :nimi ::t/pysaytysten-alku
     :tyyppi :pvm-aika}
    {:otsikko "Pysäytykset päättyvät" :nimi ::t/pysaytysten-loppu
     :tyyppi :pvm-aika
     :validoi [[:pvm-kentan-jalkeen ::t/pysaytysten-alku "Lopun on oltava alun jälkeen"]]}]
   (r/wrap {0 (select-keys ilmoitus [::t/pysaytysten-alku ::t/pysaytysten-loppu])}
           #(e! (tiedot/->PaivitaPysaytysAjat (get % 0) grid-virheita?)))])

(def paiva-lyhyt #(str/upper-case (subs % 0 2)))
(def viikonpaivat ["maanantai" "tiistai" "keskiviikko" "torstai" "perjantai" "lauantai" "sunnuntai"])
(def aikataulu-grid-kentat
  [{:otsikko "Viikonpäivät" :nimi ::t/paivat :tyyppi :checkbox-group
    :tasaa :keskita
    :vaihtoehdot viikonpaivat
    :vaihtoehto-nayta paiva-lyhyt
    :nayta-rivina? true
    :leveys 5}
   {:otsikko "Alkuaika" :tyyppi :aika :placeholder "esim. 08:00"
    :nimi ::t/alkuaika
    :leveys 1}
   {:otsikko "Loppuaika" :tyyppi :aika :placeholder "esim. 18:00"
    :nimi ::t/loppuaika
    :leveys 1}])

(def aikataulu-grid-optiot {:otsikko ""
                            :voi-muokata? (constantly true)
                            :voi-poistaa? (constantly true)
                            :piilota-toiminnot? false
                            :tyhja "Ei työaikoja"
                            :virheet-dataan? true
                            :jarjesta-avaimen-mukaan identity})
(defn tyoajat-komponentti-grid [e! tyoajat]
  (let [tyoajat-atom (atom (into {}
                                 (map-indexed (fn [i ta]
                                                [i (update ta ::t/paivat
                                                           #(into #{} %))]))
                                 tyoajat))
        muutos-fn (fn [g]
                    (let [taulukon-tila (grid-protokollat/hae-muokkaustila g)]
                      (e! (tiedot/->PaivitaTyoajat (sort-by :id (vals taulukon-tila))
                                                   (grid-virheita? taulukon-tila)))))]
    (fn [_ tyoajat]
      [muokkaus-grid
       (assoc aikataulu-grid-optiot
         :uusi-id (count tyoajat)
         :muutos muutos-fn)
       aikataulu-grid-kentat
       tyoajat-atom])))

(defn- valittu-tyon-tyyppi? [tyotyypit tyyppi]
  (loki/log "valittu kutsuttu")
  (some #(= (::t/tyyppi %) tyyppi) tyotyypit))

(defn- valitse-tyon-tyyppi [tyotyypit tyyppi valittu?]
  (loki/log (pr-str (map ::t/tyyppi tyotyypit)))
  (if-not valittu?
    (vec (remove #(= (::t/tyyppi %) tyyppi) tyotyypit))
    (conj (or tyotyypit [])
          {::t/tyyppi tyyppi})))

(defn- tyotyypin-kuvaus [tyotyypit tyyppi]
  (some #(when (= (::t/tyyppi %) tyyppi)
           (::t/kuvaus %)) tyotyypit))

(defn- aseta-tyotyypin-kuvaus [tyotyypit tyyppi kuvaus]
  (mapv (fn [tt]
          (if (= (::t/tyyppi tt) tyyppi)
            (assoc tt ::t/kuvaus kuvaus)
            tt))
        tyotyypit))

(defn- tyotyypit [e!]
  (let [osio (fn [nimi otsikko vaihtoehdot]
               {:otsikko otsikko
                :nimi nimi
                :hae ::t/tyotyypit
                :aseta (fn [a b]
                         #_(loki/log "aseta " a b (e! (tiedot/->PaivitaTyotyypit b)))
                         (assoc a ::t/tyotyypit b))
                :tyyppi :checkbox-group
                :vaihtoehdot (map first vaihtoehdot)
                :vaihtoehto-nayta t/tyotyyppi-vaihtoehdot-map
                :disabloi? (constantly false)
                :valittu-fn valittu-tyon-tyyppi?
                :valitse-fn valitse-tyon-tyyppi})]
    (lomake/ryhma
      "Työn tyyppi"
      (osio :tyotyypit-a "Tienrakennustyöt" t/tyotyyppi-vaihtoehdot-tienrakennus)
      (osio :tyotyypit-b "Huolto- ja ylläpitotyöt" t/tyotyyppi-vaihtoehdot-huolto)
      (osio :tyotyypit-c "Asennustyöt" t/tyotyyppi-vaihtoehdot-asennus)
      (merge (osio :tyotyypit-d "Muut" t/tyotyyppi-vaihtoehdot-muut)
             {:muu-vaihtoehto "Muu, mikä?"
              :muu-kentta {:otsikko "" :nimi :muu-tyotyyppi-kuvaus :tyyppi :string
                           :hae #(tyotyypin-kuvaus % "Muu, mikä?")
                           :aseta #(aseta-tyotyypin-kuvaus %1 "Muu, mikä?" %2)
                           :placeholder "(Muu tyyppi?)"}}))))

(defn yhteyshenkilo [otsikko avain pakollinen? & kentat-ennen]
  (apply
    lomake/ryhma
    otsikko

    (concat kentat-ennen
            [{:nimi (keyword (name avain) "-etunimi")
              :otsikko "Yhteyshenkilön etunimi"
              :pakollinen? pakollinen?
              :uusi-rivi? true
              :hae #(-> % avain ::t/etunimi)
              :aseta #(assoc-in %1 [avain ::t/etunimi] %2)
              :muokattava? (constantly true)
              :tyyppi :string
              :pituus-max 32}
             {:nimi (keyword (name avain) "-sukunimi")
              :otsikko "Yhteyshenkilön sukunimi"
              :pakollinen? pakollinen?
              :hae #(-> % avain ::t/sukunimi)
              :aseta #(assoc-in %1 [avain ::t/sukunimi] %2)
              :muokattava? (constantly true)
              :tyyppi :string
              :pituus-max 32}
             {:nimi (keyword (name avain) "-matkapuhelin")
              :otsikko "Yhteyshenkilön puhelinnumero"
              :hae #(-> % avain ::t/matkapuhelin)
              :aseta #(assoc-in %1 [avain ::t/matkapuhelin] %2)
              :pakollinen? pakollinen?
              :tyyppi :puhelin}
             {:nimi (keyword (name avain) "-sahkoposti")
              :otsikko "Yhteyshenkilön sähköposti"
              :hae #(-> % avain ::t/sahkoposti)
              :aseta #(assoc-in %1 [avain ::t/sahkoposti] %2)
              :tyyppi :string
              :pituus-max 128}])))


(defn pdf-sahkopostilahetyksen-modal
  "Modaali, jossa valitaan Tieliikennekeskus minne tietyöilmoituslähetetään."
  [e! app]
  (let [{:keys [avaa-pdf? ilmoitus
                lomakedata nakyvissa?] :as data} (:sahkopostilahetyksen-modal-data app)
        voi-lahettaa? (some? (:vastaanottaja lomakedata))]
    [modal/modal
     {:otsikko "Tietyöilmoituksen PDF:n sähköpostilähetys"
      :luokka "tietyoilmoituspdfn-sahkopostilahetyksen-modal"
      :nakyvissa? nakyvissa?
      :sulje-fn #(e! (tiedot/->SuljeSahkopostinLahetysModal))
      :footer [:div
               [napit/peruuta
                "Peruuta"
                #(e! (tiedot/->SuljeSahkopostinLahetysModal))]

               [napit/yleinen-ensisijainen
                "Lähetä PDF sähköpostitse"
                #(e!
                   (tiedot/->TallennaIlmoitus
                     (lomake/ilman-lomaketietoja ilmoitus) true avaa-pdf?
                     {:vastaanottaja (:vastaanottaja lomakedata)
                      :muut-vastaanottajat (yleiset/sahkopostiosoitteet-str->set (:muut-vastaanottajat lomakedata))
                      :kopio-itselle? (:kopio-itselle? lomakedata)
                      :saate (:saate lomakedata)}))

                {:disabled (not voi-lahettaa?)
                 :luokka "nappi-myonteinen"
                 :ikoni (ikonit/envelope)}]]}

     [:div
      [vihje "Olet lähettämässä tietyöilmoituksen PDF:n Harjasta valitsemasi Tieliikennekeskuksen sähköpostiosoitteeseen."]
      [lomake/lomake {:otsikko ""
                      :muokkaa! (fn [uusi-data]
                                  (e! (tiedot/->SahkopostiModalinLomakettaMuokattu uusi-data)))}

       [{:nimi :vastaanottaja
         :otsikko "Tieliikennekeskus minne sähköposti lähetetään"
         :tyyppi :valinta
         :pakollinen? true
         :valinnat tiedot/tieliikennekeskusten-sahkopostiosoitteet
         :valinta-nayta #(if (nil? %) "- valitse -" (first %))
         :valinta-arvo second}
        {:nimi :infokentta :tyyppi :string :otsikko ""
         :hae #(if (:vastaanottaja lomakedata)
                 (:vastaanottaja lomakedata))
         :muokattava? (constantly false)}
        varmista-kayttajalta/modal-muut-vastaanottajat
        varmista-kayttajalta/modal-saateviesti
        varmista-kayttajalta/modal-sahkopostikopio]
       lomakedata]]]))

(defn- lomaketoiminnot [e! kayttajan-urakat tallennus-kaynnissa? ilmoitus]
  (r/with-let [avaa-pdf? (r/atom false)]
    [:div
     [lomake/nayta-puuttuvat-pakolliset-kentat ilmoitus]
     [tee-kentta {:tyyppi :checkbox :teksti "Lataa PDF"} avaa-pdf?]
     [napit/tallenna
      "Tallenna ilmoitus"
      #(e! (tiedot/->TallennaIlmoitus (lomake/ilman-lomaketietoja ilmoitus) true @avaa-pdf? {}))
      {:disabled (or tallennus-kaynnissa?
                     (not (t/voi-tallentaa? (into #{} (map :id) kayttajan-urakat) ilmoitus))
                     (not (lomake/voi-tallentaa? ilmoitus)))
       :tallennus-kaynnissa? tallennus-kaynnissa?
       :ikoni (ikonit/tallenna)}]

     (when (istunto/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys)
       [napit/yleinen-toissijainen
        "Tallenna ilmoitus ja lähetä PDF sähköpostitse Tieliikennekeskukseen"
        #(e! (tiedot/->AvaaSahkopostinLahetysModal (lomake/ilman-lomaketietoja ilmoitus)
                                                   @avaa-pdf?))
        {:disabled (or tallennus-kaynnissa?
                       (not (t/voi-tallentaa? (into #{} (map :id) kayttajan-urakat) ilmoitus))
                       (not (lomake/voi-tallentaa? ilmoitus)))
         :tallennus-kaynnissa? tallennus-kaynnissa?
         :ikoni (ikonit/envelope)}])]))

(defn lomake [e! app tallennus-kaynnissa? ilmoitus kayttajan-urakat]
  [:div
   [ui-debug/debug ilmoitus]
   [:span
    [napit/takaisin "Palaa ilmoitusluetteloon" #(e! (tiedot/->PoistaIlmoitusValinta))]
    [lomake/lomake {:otsikko (if (::t/id ilmoitus) "Muokkaa ilmoitusta" "Uusi tietyöilmoitus")
                    :muokkaa! #(e! (tiedot/->IlmoitustaMuokattu %))
                    :footer-fn (partial lomaketoiminnot e! kayttajan-urakat tallennus-kaynnissa?)
                    :luokka "ryhma-reuna"}
     [(lomake/ryhma
        "Urakka"
        {:nimi ::t/urakka-id
         :otsikko "Liittyy urakkaan"
         :tyyppi :valinta
         :valinnat (urakka-valinnat kayttajan-urakat)
         :valinta-nayta second
         :valinta-arvo first
         :aseta (fn [rivi arvo]
                  (if (= (::t/urakka-id rivi) arvo)
                    rivi
                    (do (e! (tiedot/->UrakkaValittu arvo))
                        (assoc rivi ::t/urakka-id arvo))))
         :muokattava? (constantly true)}
        (when-not (::t/urakka-id ilmoitus)
          {:nimi ::t/urakan-nimi
           :otsikko "Projektin tai urakan nimi"
           :tyyppi :string
           :pakollinen? true
           :muokattava? (constantly true)
           :pituux-max 256})
        (when-not (or (empty? (::t/urakan-nimi ilmoitus))
                      (empty? (:urakan-kohteet ilmoitus)))
          {:otsikko "Kohde urakassa"
           :nimi ::t/yllapitokohde
           :tyyppi :valinta
           :valinnat (concat [{:nimi "Ei kohdetta" :yllapitokohde-id nil}] (:urakan-kohteet ilmoitus)) :valinta-nayta :nimi
           :valinta-arvo :yllapitokohde-id
           :aseta (fn [rivi arvo]
                    (if (= (::t/yllapitokohde rivi) arvo)
                      rivi
                      (do
                        (e! (tiedot/->ValitseYllapitokohde
                              (first
                                (filter #(= arvo (:yllapitokohde-id %)) (:urakan-kohteet ilmoitus)))))
                        (assoc rivi ::t/yllapitokohde arvo))))
           :muokattava? (constantly true)}))

      (yhteyshenkilo "Urakoitsijan yhteyshenkilo" ::t/urakoitsijayhteyshenkilo false
                     {:nimi ::t/urakoitsijan-nimi
                      :otsikko "Nimi"
                      :muokattava? (constantly true)
                      :tyyppi :string
                      :pituus-max 128}
                     {:nimi ::t/urakoitsijan-ytunnus
                      :otsikko "Y-tunnus"
                      :muokattava? (constantly true)
                      :tyyppi :string
                      :pituus-max 9})

      (yhteyshenkilo "Tilaaja" ::t/tilaajayhteyshenkilo false
                     {:nimi ::t/tilaajan-nimi
                      :otsikko "Tilaajan nimi"
                      :muokattava? (constantly true)
                      :tyyppi :string
                      :pituus-max 128})

      (lomake/ryhma
        "Tiedot kohteesta"
        {:otsikko "Osoite"
         :nimi ::t/osoite
         :pakollinen? true
         :tyyppi :tierekisteriosoite
         :avaimet kentat/tr-osoite-domain-avaimet
         :ala-nayta-virhetta-komponentissa? true
         :validoi [[:validi-tr "Reittiä ei saada tehtyä" [::t/osoite ::tr/geometria]]]
         :sijainti (r/wrap (::tr/geometria (::t/osoite ilmoitus))
                           #(e! (tiedot/->PaivitaIlmoituksenSijainti %)))}
        {:otsikko "Tien nimi" :nimi ::t/tien-nimi
         :tyyppi :string :uusi-rivi? true :pakollinen? true
         :pituus-max 256}
        {:otsikko "Kunta/kunnat" :nimi ::t/kunnat
         :tyyppi :string}
        {:otsikko "Työn alkupiste (osoite, paikannimi)" :nimi ::t/alkusijainnin-kuvaus
         :tyyppi :string}
        {:otsikko "Työn loppupiste (osoite, paikannimi)" :nimi ::t/loppusijainnin-kuvaus
         :tyyppi :string}
        {:otsikko "Työn aloituspvm" :nimi ::t/alku :tyyppi :pvm :pakollinen? true
         :varoita [[:pvm-sama (:kohteen-alku (::t/kohteen-aikataulu ilmoitus))
                    (when (:kohteen-alku (::t/kohteen-aikataulu ilmoitus))
                      (str "Kohteen aloitus merkitty aikatauluun "
                           (pvm/pvm (:kohteen-alku (::t/kohteen-aikataulu ilmoitus)))))]]}
        {:otsikko "Työn lopetuspvm" :nimi ::t/loppu :tyyppi :pvm :pakollinen? true
         :varoita [[:pvm-sama (:paallystys-valmis (::t/kohteen-aikataulu ilmoitus))
                    (when (:paallystys-valmis (::t/kohteen-aikataulu ilmoitus))
                      (str "Kohteen päällystyksen valmistuminen merkitty aikatauluun "
                           (pvm/pvm (:paallystys-valmis (::t/kohteen-aikataulu ilmoitus)))))]]
         :validoi [[:pvm-toisen-pvmn-jalkeen (::t/alku ilmoitus)
                    "Lopetuksen pitää olla alun jälkeen"]]})
      (tyotyypit e!)
      {:otsikko "Päivittäinen työaika"
       :nimi ::t/tyoajat
       :tyyppi :komponentti
       :komponentti (fn [{{tyoajat ::t/tyoajat} :data}]
                      [tyoajat-komponentti-grid e! tyoajat])
       :validoi [#(when (get-in %2 [:komponentissa-virheita? :tyoajat])
                    "virhe")]
       :palstoja 2}
      (lomake/ryhma "Vaikutukset liikenteelle"
                    {:otsikko "Arvioitu viivytys normaalissa liikenteessä (min)"
                     :leveys 1
                     :nimi ::t/viivastys-normaali-liikenteessa
                     :tyyppi :positiivinen-numero}
                    {:otsikko "Arvioitu viivytys ruuhka-aikana (min)"
                     :leveys 1
                     :nimi ::t/viivastys-ruuhka-aikana
                     :tyyppi :positiivinen-numero
                     }
                    {:otsikko "Kaistajärjestelyt"
                     :tyyppi :valinta
                     :nimi ::t/kaistajarjestelyt
                     :hae #(get-in % [::t/kaistajarjestelyt ::t/jarjestely])
                     :aseta #(assoc-in %1 [::t/kaistajarjestelyt ::t/jarjestely] %2)
                     :valinnat t/kaistajarjestelyt-vaihtoehdot
                     :valinta-nayta t/kaistajarjestelyt-vaihtoehdot-map
                     }
                    (if (= "muu" (get-in ilmoitus [::t/kaistajarjestelyt ::t/jarjestely]))
                      {:otsikko "Muu kaistajärjestely"
                       :nimi :muu-kaistajarjestely :tyyppi :string
                       :placeholder "(muu kaistajärjestely?)"
                       :hae #(get-in % [::t/kaistajarjestelyt ::t/selite])
                       :aseta #(assoc-in %1 [::t/kaistajarjestelyt ::t/selite] %2)}
                      (assoc tyhja-kentta :nimi :kaistajarjestely-blank))
                    {:otsikko "Nopeusrajoitukset"
                     :tyyppi :komponentti
                     :komponentti (fn [{{nopeusrajoitukset ::t/nopeusrajoitukset} :data}]
                                    [nopeusrajoitukset-komponentti-grid e! nopeusrajoitukset])
                     :nimi ::t/nopeusrajoitukset

                     }
                    {:tyyppi :komponentti
                     :nimi :kokorajoitukset
                     :komponentti (fn [_]
                                    [kokorajoitukset-komponentti e! ilmoitus])
                     }
                    {:otsikko "Tien pinta työmaalla"
                     :nimi ::t/tienpinnat
                     :tyyppi :komponentti
                     :komponentti (fn [{{tienpinnat ::t/tienpinnat} :data}]
                                    [tienpinnat-komponentti-grid e! ::t/tienpinnat tienpinnat])
                     }
                    {:nimi ::t/huomautukset
                     ;; jostain syystä tuli virheitä disjoin-operaation käytöstä vektorille
                     ;; ilman set-kutsuja, vaikka muutti :vaihtoehdot-arvot setiksi?
                     :hae #(set (::t/huomautukset %))
                     :aseta #(assoc %1 ::t/huomautukset (set %2))
                     :tyyppi :checkbox-group
                     :vaihtoehdot #{"avotuli" "tyokoneitaLiikenteenSeassa"}
                     :vaihtoehto-nayta {"avotuli" "Kuumennin käytössä (avotuli)"
                                        "tyokoneitaLiikenteenSeassa" "Työkoneita liikenteen seassa"}
                     :disabloi? (constantly false)}
                    {:otsikko "Kiertotietien pinnat"
                     :nimi ::t/kiertotienpinnat
                     :tyyppi :komponentti
                     :komponentti (fn [{{kiertotienpinnat ::t/kiertotienpinnat} :data}]
                                    [tienpinnat-komponentti-grid e! ::t/kiertotienpinnat kiertotienpinnat])}
                    {:otsikko "Kiertotietien pituus (m)"
                     :nimi ::t/kiertotien-pituus
                     :tyyppi :positiivinen-numero}
                    {:otsikko "Kiertotien mutkaisuus"
                     :tyyppi :valinta
                     :uusi-rivi? true
                     :nimi ::t/kiertotien-mutkaisuus
                     ;; :valinnat ["Loivat mutkat", "Jyrkät mutkat (erkanee yli 45° kulmassa)" "Päällystetty" "Murske" "Kantavuus rajoittaa", "___ tonnia"]
                     :valinnat [nil "loivatMutkat" "jyrkatMutkat"]
                     ;; q: rautalangan kantavuus ___ tonnia
                     :valinta-nayta {nil "(Ei valintaa)"
                                     "loivatMutkat" "Loivat mutkat"
                                     "jyrkatMutkat" "Jyrkät mutkat (erkanee yli 45° kulmassa)"}
                     }
                    {:otsikko "Liikenteen ohjaaja"
                     :tyyppi :valinta
                     :uusi-rivi? true
                     :nimi ::t/liikenteenohjaaja
                     :valinnat [nil "liikennevalot" "liikenteenohjaaja"]
                     :valinta-nayta {nil "(Ei valintaa)"
                                     "liikennevalot" "Liikennevalot"
                                     "liikenteenohjaaja" "Liikenteen ohjaaja"}
                     }
                    {:otsikko "Liikenteenohjaus"
                     :tyyppi :valinta
                     :nimi ::t/liikenteenohjaus
                     :valinnat [nil "ohjataanVuorotellen" "ohjataanKaksisuuntaisena"]
                     :valinta-nayta {nil "(Ei valintaa)"
                                     "ohjataanVuorotellen" "Ohjataan vuorotellen"
                                     "ohjataanKaksisuuntaisena" "Ohjataan kaksisuuntaisena"}
                     }
                    {:otsikko "Pysäytykset ja sulkemiset"
                     :tyyppi :checkbox
                     :nimi ::t/ajoittain-suljettu-tie
                     :teksti "Tie ajoittain suljettu"}
                    {:tyyppi :checkbox
                     :nimi ::t/ajoittaiset-pysaytykset
                     :aseta #(do (if %2
                                   (assoc %1 ::t/ajoittaiset-pysaytykset %2)
                                   ;; else
                                   (assoc %1
                                     ::t/ajoittaiset-pysaytykset %2
                                     ::t/pysaytysten-alku nil
                                     ::t/pysaytysten-loppu nil)))
                     :teksti "Pysäytyksiä ajoittain (aikataulu, jos kesto yli 5 min)"}
                    (if (-> ilmoitus ::t/ajoittaiset-pysaytykset)
                      {:otsikko ""
                       :nimi :liikenteenohjaus-aikataulu
                       :tyyppi :komponentti
                       :komponentti (fn [_]
                                       [pysaytys-ajat-komponentti e! ilmoitus])
                       :validoi [#(when (get-in %2 [:komponentissa-virheita? :pysaytysajat])
                                    "virhe")]}
                      ;; else
                      (assoc tyhja-kentta :nimi :aikataulu-blank))
                    {:otsikko "Vaikutussuunta"
                     :tyyppi :valinta
                     :nimi ::t/vaikutussuunta
                     :valinnat (into [nil] (keys t/vaikutussuunta-vaihtoehdot-map))
                     :valinta-nayta #(or (t/vaikutussuunta-vaihtoehdot-map %) "- Valitse -")
                     :pakollinen? true
                     :validoi [[:ei-tyhja]]})
      (lomake/ryhma "Muuta"
                    {:otsikko "Lisätietoja"
                     :nimi ::t/lisatietoja
                     :tyyppi :text
                     :koko [90 8]}
                    {:otsikko "Luvan diaarinumero"
                     :nimi ::t/luvan-diaarinumero
                     :tyyppi :string
                     :pituus-max 32})
      (yhteyshenkilo "Ilmoittaja" ::t/ilmoittaja true)
      (when (istunto/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys)
        {:nimi :email-lahetykset-tloikiin
         :otsikko "PDF:n sähköpostilähetykset Harjasta Tieliikennekeskuksen sähköpostiin"
         :tyyppi :komponentti
         :komponentti (fn [{:keys [data]}]
                         [tietyo-yhteiset/tietyoilmoituksen-lahetystiedot-komponentti data])})]
     ilmoitus]]

   (when (istunto/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys)
     [pdf-sahkopostilahetyksen-modal e! app])])
