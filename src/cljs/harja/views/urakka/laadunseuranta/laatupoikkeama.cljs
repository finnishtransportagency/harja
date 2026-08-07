(ns harja.views.urakka.laadunseuranta.laatupoikkeama
  "Yksittäisen laatupoikkeaman näkymä"
  (:require [harja.ui.debug :as debug]
            [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko info-laatikko]]
            [clojure.string]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.ui.napit :as napit]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.navigaatio.reitit :as reitit]
            [harja.views.urakka.laadunseuranta.tarkastukset :as tarkastukset-nakyma]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.urakka :as u-domain]
            [harja.domain.kommentti :as kommentti]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.views.urakka.laadunseuranta.sanktiot-lomake :as sanktiot-lomake])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn paatos?
  "Onko annetussa laatupoikkeamassa päätös?"
  [laatupoikkeama]
  (not (nil? (get-in laatupoikkeama [:paatos :paatos]))))

(defn uuden-sanktion-rivi
  [rivi urakkatyyppi mahdolliset-sanktiolajit urakan-tpit]
  (assoc rivi
    :laji (sanktiot/oletus-uuden-sanktion-laji urakkatyyppi mahdolliset-sanktiolajit)
    :toimenpideinstanssi (when (= 1 (count urakan-tpit))
                           (:tpi_id (first urakan-tpit)))))

(declare sanktiotaulukon-rivit)

(defn tallenna-laatupoikkeama
  "Tallentaa annetun laatupoikkeaman palvelimelle. Lukee serveriltä palautuvan laatupoikkeaman ja
   päivittää/lisää sen nykyiseen listaukseen, jos se kuuluu listauksen aikavälille."
  [laatupoikkeama nakyma]
  (let [laatupoikkeama (as-> (lomake/ilman-lomaketietoja laatupoikkeama) lp
                         (assoc lp :sanktiot (sanktiotaulukon-rivit lp))
                             ;; Varmistetaan, että tietyssä näkymäkontekstissa tallennetaan vain näkymän
                             ;; sisältämät asiat (esim. on mahdollista vaihtaa koko valittu urakka päällystyksestä
                             ;; hoitoon, ja emme halua että hoidon lomakkeessa tallentuu myös ylläpitokohde)
                         (if (some #(= nakyma %) [:paallystys :paikkaus :tiemerkinta])
                           (dissoc lp :kohde)
                           (dissoc lp :yllapitokohde))
                         (if (integer? (:yllapitokohde lp))
                           lp
                           (assoc lp :yllapitokohde (get-in lp [:yllapitokohde :id]))))]
    (go
      (let [tulos (<! (laatupoikkeamat/tallenna-laatupoikkeama laatupoikkeama))]
        (if (k/virhe? tulos)
          ;; Palautetaan virhe, jotta nappi näyttää virheviestin
          tulos
          ;; Laatupoikkeama tallennettu onnistuneesti, päivitetään sen tiedot
          (let [uusi-laatupoikkeama tulos
                aika (:aika uusi-laatupoikkeama)
                [alku loppu] @urakka/valittu-aikavali]
            (when (and (pvm/sama-tai-jalkeen? aika alku)
                    (pvm/sama-tai-ennen? aika loppu))
              ;; Kuuluu aikavälille, lisätään tai päivitetään
              (if (:id laatupoikkeama)
                ;; Päivitetty olemassaolevaa
                (swap! tila/laatupoikkeamat
                  (fn [tila]
                    (let [laatupoikkeamat (:laatupoikkeamat tila)]
                      (assoc tila :laatupoikkeamat
                        (mapv (fn [h]
                                (if (= (:id h) (:id uusi-laatupoikkeama))
                                  uusi-laatupoikkeama
                                  h)) laatupoikkeamat)))))
                ;; Luotu uusi
                (swap! tila/laatupoikkeamat
                  (fn [tila]
                    (let [laatupoikkeamat (:laatupoikkeamat tila)
                          laatupoikkeamat (conj laatupoikkeamat uusi-laatupoikkeama)]
                      (assoc tila :laatupoikkeamat laatupoikkeamat)))
                  conj uusi-laatupoikkeama)))
            true))))))

(defn laatupoikkeaman-sanktio-sivupaneeli
  "Sivupaneeli laatupoikkeaman sanktion lisäämistä/muokkausta varten.
   Käyttää samaa sanktio-lomaketta kuin Sanktiot, bonukset ja arvonvähennykset -sivu,
   mutta ilman laji-valintaa (vain sanktio).
   Tallentaa sanktion laatupoikkeaman sanktiot-atomiin eikä suoraan kantaan."
  [sivupaneeli-auki?-atom laatupoikkeama paatosoikeus? muokattava? sanktiot-atom]
  (let [muokattu (atom @sanktiot/valittu-sanktio)
        lukutila? false
        oikeus-muokata? (and paatosoikeus? muokattava?)
        tallenna-fn (fn [sanktio]
                      ;; Lisää tai päivitä sanktio laatupoikkeaman sanktiot-atomiin
                      ;; Käytetään ::paikallinen-avain tunnistamaan sanktioita joilla ei ole :id:tä
                      (let [avain (or (:id sanktio)
                                    (:paikallinen-avain sanktio)
                                    (gensym "sanktio"))
                            sanktio-avaimella (assoc sanktio :paikallinen-avain avain)]
                        (swap! sanktiot-atom assoc avain sanktio-avaimella)))]
    [:div.padding-16.ei-sulje-sivupaneelia
     (when-not (:id @muokattu) [:h2 (if oikeus-muokata? "Muokkaa sanktiota" "Lisää sanktio")])
     (when (and oikeus-muokata? (:lukutila? @muokattu))
       [:div.flex-row.alkuun.valistys16
        [napit/yleinen-reunaton "Muokkaa" #(swap! sanktiot/valittu-sanktio update :lukutila? not)]])
     ;; Käytetään sanktio-lomaketta ilman laji-valintaa (sanktio/bonus/arvonvähennys).
     ;; Lomakkeella voi syöttää vain sanktion.
     [sanktiot-lomake/sanktio-lomake sivupaneeli-auki?-atom lukutila? oikeus-muokata?
      {:tallenna-fn tallenna-fn}]]))

(defn laatupoikkeaman-sanktiot
  "Näyttää laatupoikkeaman sanktiot listana ja tarjoaa mahdollisuuden lisätä/muokata
   sanktioita sivupaneelin kautta."
  [_sanktiot-atom _paatosoikeus? _laatupoikkeama _muokattava? _optiot]
  (let [sivupaneeli-auki? (r/atom false)]
    (fn [sanktiot-atom paatosoikeus? laatupoikkeama muokattava? _optiot]
      (let [voi-muokata? (and paatosoikeus? muokattava?)
            sanktiot-lista (vals @sanktiot-atom)
            sanktio-konfiguraation-tila @sanktiot/valitun-urakan-sanktio-konfiguraation-tila]
        (if (= :valmis sanktio-konfiguraation-tila)
          [:div.sanktiot
         ;; Sivupaneeli sanktion lisäämistä/muokkausta varten
         (when @sivupaneeli-auki?
           [sivupalkki/oikea
            {:leveys "600px"
             :sulku-fn #(do
                          (reset! sivupaneeli-auki? false)
                          (reset! sanktiot/valittu-sanktio nil))}
            [laatupoikkeaman-sanktio-sivupaneeli sivupaneeli-auki? laatupoikkeama paatosoikeus? muokattava? sanktiot-atom]])

         [:h3 "Sanktiot"]
         ;; "Lisää sanktio" -nappi
         (when voi-muokata?
           (let [kasittelyaika (get-in @laatupoikkeama [:paatos :kasittelyaika])
                 kasittelytapa (get-in @laatupoikkeama [:paatos :kasittelytapa])
                 perustelu (get-in @laatupoikkeama [:paatos :perustelu])
                 puuttuvat (cond-> []
                             (nil? kasittelyaika) (conj "Käsittelyn pvm on pakollinen tieto")
                             (nil? kasittelytapa) (conj "Käsittelytapa on pakollinen tieto")
                             (clojure.string/blank? perustelu) (conj "Perustelu on pakollinen tieto"))
                 disabled? (seq puuttuvat)]
             [:div
              [:div.flex-row {:style {:margin-top "8px"}}
               [napit/yleinen-toissijainen "Lisää uusi"
                (fn []
                  ;; Alustetaan uusi sanktio laatupoikkeaman tiedoilla
                   (let [kasittelyaika (get-in @laatupoikkeama [:paatos :kasittelyaika])
                         uusi (-> (merge (sanktiot/uusi-sanktio (:tyyppi @nav/valittu-urakka) (pvm/vuosi (first @tiedot-urakka/valittu-hoitokausi))) {:suorasanktio false})
                                (assoc :maarattypvm kasittelyaika)
                                (assoc :perintapvm kasittelyaika)
                                 (assoc :kasittelytapa (if (tila/mhu25-urakka? @nav/valittu-urakka)
                                                         :valikatselmus
                                                         (get-in @laatupoikkeama [:paatos :kasittelytapa])))
                                (assoc-in [:laatupoikkeama :paatos :kasittelyaika] kasittelyaika))
                         siivottu-laatupoikkeama (lomake/ilman-lomaketietoja @laatupoikkeama)
                         uusi-sanktio-atom (atom uusi)
                         mahdolliset-kulun-kohdistukset (sanktiot/mahdolliset-kulun-kohdistukset false (pvm/vuosi (:alkupvm @nav/valittu-urakka)) uusi-sanktio-atom)]
                    (reset! sivupaneeli-auki? true)
                    (reset! sanktiot/valittu-sanktio
                      (-> (uuden-sanktion-rivi uusi (:tyyppi @nav/valittu-urakka) @sanktiot/valitun-urakan-sanktiolajit mahdolliset-kulun-kohdistukset)
                        (assoc :laatupoikkeama siivottu-laatupoikkeama)
                        (assoc :perustelu (:paatoksen-selitys siivottu-laatupoikkeama))
                        (assoc :laatupoikkeamaaika (:aika siivottu-laatupoikkeama))))))
                {:ikoni (ikonit/livicon-plus)
                 :disabled (boolean disabled?)}]]
              (when disabled?
                [info-laatikko :varoitus
                 "Sanktiota ei voida lisätä, sillä osa laatupoikkeaman pakollisista tiedoista puuttuu"
                 [:ul.body-text
                  (for [puute puuttuvat]
                    ^{:key puute}
                    [:li puute])]
                 nil
                 {:ikoni-fn #(ikonit/harja-icon-status-alert) :luokka "tasan"}])]))

         ;; Sanktioiden listaus
         [grid/grid
          {:otsikko ""
           :tunniste #(or (:id %) (:paikallinen-avain %) (hash %))
           :tyhja "Ei kirjattuja sanktioita."
           :rivi-klikattu (fn [sanktio]
                            (reset! sanktiot/valittu-sanktio
                              (merge sanktio
                                {:lukutila? true}))
                            (reset! sivupaneeli-auki? true))}
          [(if (tila/mhu25-urakka? @nav/valittu-urakka)
             {:otsikko "Määrätty"  :nimi :maarattypvm :fmt pvm/pvm-opt :leveys 1.2}
             {:otsikko "Käsitelty" :nimi :kasittelyaika :hae #(get-in % [:laatupoikkeama :paatos :kasittelyaika]) :fmt pvm/pvm-opt :leveys 1.2})
           {:otsikko "Laji" :nimi :laji :hae #(sanktiot/valitun-urakan-sanktiolajin-nimi (:laji %)) :leveys 2}
           {:otsikko "Tyyppi" :nimi :tyyppi-nimi :hae #(get-in % [:tyyppi :nimi]) :leveys 2}
           {:otsikko "Tapah\u00ADtuma\u00ADpaik\u00ADka/kuvaus" :nimi :tapahtumapaikka :hae #(get-in % [:laatupoikkeama :kuvaus]) :leveys 3}
           {:otsikko "Määrä (€)" :nimi :summa :hae #(when (:summa %) (str (:summa %))) :tyyppi :numero :tasaa :oikea :leveys 1.7}]
          (or sanktiot-lista [])]]
          (case sanktio-konfiguraation-tila
            :haku-kaynnissa [ajax-loader "Ladataan..."]
            :haku-epaonnistui [:div
                               [:p "Sanktioita ei voitu ladata juuri nyt."]
                               [:p "Yritä hetken kuluttua uudelleen. Jos ongelma jatkuu, ota yhteyttä Harja-tukeen."]]
            :ei-konfiguraatiota [:div
                                 [:p "Sanktioita ei ole määritelty tälle urakalle valitulla hoitokaudella."]
                                 [:p "Ota yhteyttä Harja-tukeen jotta asia saadaan korjattua."]]
            [ajax-loader "Ladataan..."]))))))

(defn avaa-tarkastus [tarkastus-id]
  (tarkastukset-nakyma/valitse-tarkastus tarkastus-id)
  (reset! (reitit/valittu-valilehti-atom :laadunseuranta) :tarkastukset))

(defn- sanktiotaulukon-rivit [laatupoikkeama]
  (vals (:sanktiot laatupoikkeama)))

(defn- sanktiotaulukko-tyhja? [laatupoikkeama]
  (empty? (sanktiotaulukon-rivit laatupoikkeama)))

(defn- pakolliset-kentat
  [nakyma sakko?]
  (let [pakolliset-yllapito-sakko [[:perintapvm] [:laji] [:toimenpideinstanssi] [:summa]]
        pakolliset-yllapito-muistutus [[:perintapvm] [:laji]]
        pakolliset-hoito-sakko [[:perintapvm] [:laji] [:tyyppi] [:toimenpideinstanssi] [:summa]]
        pakolliset-hoito-muistutus [[:perintapvm] [:laji] [:tyyppi] [:toimenpideinstanssi]]]
    (if (or (= :yllapito nakyma) (= :vesivayla nakyma))
      (if sakko?
        pakolliset-yllapito-sakko
        pakolliset-yllapito-muistutus)
      (if sakko?
        pakolliset-hoito-sakko
        pakolliset-hoito-muistutus))))

(defn- tarkasta-sanktiorivi [sanktiorivi nakyma]
  (let [sanktio-poistettu? (:poistettu sanktiorivi)
        ;; Jos sanktio on poistettu, niin pakollisia kenttiä ei ole
        kentat (when-not sanktio-poistettu? (map #(get-in sanktiorivi %)
                                              (pakolliset-kentat nakyma (sanktio-domain/muu-kuin-muistutus? sanktiorivi))))]
    (every? some? kentat)))

(defn- sanktiorivit-ok? [laatupoikkeama nakyma]
  (cond
    (and (not (sanktio-domain/paatos-on-sanktio? laatupoikkeama))
      (sanktiotaulukko-tyhja? laatupoikkeama))
    true

    (and (sanktio-domain/paatos-on-sanktio? laatupoikkeama)
      (sanktiotaulukko-tyhja? laatupoikkeama))
    false

    ;; muokkausgridiltä tulee [{id {rivi}} ..]
    ;; Tarkasta että jokaiselle riville muokkausgridissä on jokainen vaadittava arvo
    :default
    (every? true? (map #(tarkasta-sanktiorivi % nakyma) (sanktiotaulukon-rivit laatupoikkeama)))))

(defn- tarkasta-sanktiotiedot [vertailu-fn laatupoikkeama]
  (vertailu-fn #(not (nil? %)) (map #(get-in laatupoikkeama %)
                                 [[:paatos :kasittelyaika]
                                  [:paatos :kasittelytapa]
                                  [:paatos :paatos]])))

(defn kaikki-sanktiotiedot-annettu? [laatupoikkeama]
  (tarkasta-sanktiotiedot every? laatupoikkeama))

(defn sanktiotietoja-annettu? [laatupoikkeama]
  (tarkasta-sanktiotiedot some laatupoikkeama))

(defn validoi-sanktiotiedot [laatupoikkeama]
  ;; Joko ei mitään sanktiotietoja, tai kaikki
  (or (not (sanktiotietoja-annettu? laatupoikkeama))
    (kaikki-sanktiotiedot-annettu? laatupoikkeama)))

(defn validoi-laatupoikkeama [laatupoikkeama]
  (if (and (not (lomake/muokattu? laatupoikkeama))
        (:id laatupoikkeama))
    false
    (not (and (lomake/voi-tallentaa-ja-muokattu? laatupoikkeama)
           (validoi-sanktiotiedot laatupoikkeama)))))

(defn lisaa-sanktion-validointi [tietoja-annettu-fn? kentta viesti]
  (merge kentta
    (when (tietoja-annettu-fn?)
      {:validoi [[:ei-tyhja viesti]]
       :pakollinen? true})))

(defn nayta-siirtymisnappi?
  "Nappi näytetään jos laatupoikkeamalle on tarkastus JA; tarkastus on julkinen TAI käyttäjä on tilaajan edustaja"
  [{:keys [tarkastusid nayta-tarkastus-urakoitsijalle]}]
  (and tarkastusid
    (or nayta-tarkastus-urakoitsijalle
      (roolit/tilaajan-kayttaja? @istunto/kayttaja))))

(defn siirtymisnapin-vihje [{:keys [nayta-tarkastus-urakoitsijalle]}]
  (cond
    nayta-tarkastus-urakoitsijalle
    "Tallentaa muutokset ja avaa tarkastuksen, jonka pohjalta laatupoikkeama on tehty."

    (not nayta-tarkastus-urakoitsijalle)
    "Tallentaa muutokset ja avaa urakoitsijalta piilotetun tarkastuksen, jonka pohjalta laatupoikkeama on tehty."))

(defn siirtymisnapin-teksti [{:keys [nayta-tarkastus-urakoitsijalle]}]
  (cond
    (not nayta-tarkastus-urakoitsijalle)
    "Avaa piilotettu tarkastus"

    nayta-tarkastus-urakoitsijalle
    "Avaa tarkastus"))

(defn laatupoikkeamalomake
  ([e! laatupoikkeama] (laatupoikkeamalomake e! laatupoikkeama {}))
  ([e! laatupoikkeama optiot]
   (let [sanktio-virheet (atom {})
         muokattava? (not (paatos? @laatupoikkeama))
         urakka-id (:id @nav/valittu-urakka)
         voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-laatupoikkeamat
                           urakka-id)
         paatosoikeus? (oikeudet/on-muu-oikeus? "päätös"
                         oikeudet/urakat-laadunseuranta-sanktiot
                         urakka-id @istunto/kayttaja)]
     (komp/luo
       (fn [e! laatupoikkeama optiot]
         (let [uusi? (not (:id @laatupoikkeama))
               sanktion-validointi (partial lisaa-sanktion-validointi
                                     #(sanktiotietoja-annettu? @laatupoikkeama))
               kohde-muuttui? (fn [vanha uusi] (not= vanha uusi))
               yllapitokohteet (:yllapitokohteet optiot)
               yllapito? @urakka/yllapitourakka?
               nakyma (:nakyma optiot)
               tallennus-kaynnissa? (:tallennus-kaynnissa? optiot)
               vesivayla? (u-domain/vesivaylaurakkatyyppi? nakyma)
               yllapitokohdeurakka? @urakka/yllapitokohdeurakka?
               urakan-alkuvuosi (pvm/vuosi (:alkupvm @nav/valittu-urakka))]
           (if (and yllapitokohdeurakka? (nil? yllapitokohteet)) ;; Pakko olla ylläpitokohteet ennen kuin lomaketta voi näyttää
             [ajax-loader "Ladataan..."]
             [:div.laatupoikkeama
              [napit/takaisin "Takaisin laatupoikkeamaluetteloon" #(reset! laatupoikkeamat/valittu-laatupoikkeama-id nil)]
              [lomake/lomake
               {:otsikko "Laatupoikkeaman tiedot"
                :muokkaa! #(do
                             (when (contains? (:sijainti %) :virhe)
                               (viesti/nayta! (get-in % [:sijainti :virhe])
                                 :danger))
                             (let [uusi-lp (cond-> %
                                             (kohde-muuttui? (get-in @laatupoikkeama [:yllapitokohde :id])
                                               (get-in % [:yllapitokohde :id])) (laatupoikkeamat/paivita-yllapitokohteen-tr-tiedot yllapitokohteet)
                                             (contains? (:sijainti %) :virhe) (assoc :sijainti nil))]
                               (reset! laatupoikkeama uusi-lp)))
                :tarkkaile-ulkopuolisia-muutoksia? true
                :voi-muokata? @laatupoikkeamat/voi-kirjata?
                :footer-fn (fn [sisalto]
                             (when voi-kirjoittaa?
                               [napit/yleinen-ensisijainen
                                (if (paatos? sisalto)
                                  "Tallenna ja lukitse laatupoikkeama"
                                  "Tallenna laatupoikkeama")
                                (fn []
                                  (if (paatos? sisalto)
                                    (varmista-kayttajalta/varmista-kayttajalta
                                      {:otsikko "Tallenna ja lukitse laatupoikkeama?"
                                       :sisalto "Laatupoikkeamaa ei voi enää muokata tai poistaa tallentamisen jälkeen."
                                       :hyvaksy "Tallenna ja lukitse"
                                       :napit [:tallenna :peruuta]
                                       :toiminto-fn #(e! (laatupoikkeamat/->TallennaLaatuPoikkeama (lomake/ilman-lomaketietoja sisalto) nakyma))})
                                    (e! (laatupoikkeamat/->TallennaLaatuPoikkeama (lomake/ilman-lomaketietoja sisalto) nakyma))))
                                {:ikoni (ikonit/tallenna)
                                 :disabled (or
                                             tallennus-kaynnissa?
                                             (not (validoi-sanktiotiedot sisalto))
                                             (not (sanktiorivit-ok? sisalto (cond yllapito? :yllapito
                                                                              vesivayla? :vesivayla
                                                                              :default :hoito)))
                                             (not (lomake/voi-tallentaa-ja-muokattu? sisalto)))}]))}

               [{:otsikko "Havaittu"
                 :pakollinen? true
                 :muokattava? (constantly muokattava?)
                 :tyyppi :pvm-aika
                 :nimi :aika
                 :validoi [[:ei-tyhja "Anna laatupoikkeaman havainnon päivämäärä ja aika"]]
                 :huomauta [[:urakan-aikana-ja-hoitokaudella]]
                 :palstoja 1}

                (when yllapitokohdeurakka?
                  {:otsikko "Yllä\u00ADpito\u00ADkohde" :tyyppi :valinta :nimi :yllapitokohde
                   :palstoja 1
                   :pakollinen? false
                   :muokattava? (constantly muokattava?)
                   :valinnat yllapitokohteet
                   :nil-valinta {:id nil}
                   :jos-tyhja "Ei valittavia kohteita"
                   :valinta-nayta (fn [arvo muokattava?]
                                    (cond
                                      (nil? (:id arvo)) "Ei liity kohteeseen"
                                      arvo (yllapitokohde-domain/yllapitokohde-tekstina
                                             arvo
                                             {:osoite {:tr-numero (:tr-numero arvo)
                                                       :tr-alkuosa (:tr-alkuosa arvo)
                                                       :tr-alkuetaisyys (:tr-alkuetaisyys arvo)
                                                       :tr-loppuosa (:tr-loppuosa arvo)
                                                       :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                                      muokattava? "- Valitse kohde -"
                                      :else ""))})

                (when (and (not yllapitokohdeurakka?) (not vesivayla?))
                  {:otsikko "Kohde" :tyyppi :string :nimi :kohde
                   :palstoja 1
                   :pakollinen? true
                   :muokattava? (constantly muokattava?)
                   :validoi [[:ei-tyhja "Anna laatupoikkeaman kohde"]]})

                {:otsikko "Tekijä" :nimi :tekija
                 :uusi-rivi? true
                 :tyyppi :valinta
                 :valinnat [:tilaaja :urakoitsija :konsultti]
                 :valinta-nayta #(case %
                                   :tilaaja "Tilaaja"
                                   :urakoitsija "Urakoitsija"
                                   :konsultti "Konsultti"
                                   "- valitse osapuoli -")
                 :palstoja 1
                 :muokattava? (constantly muokattava?)
                 :validoi [[:ei-tyhja "Valitse laatupoikkeaman tehnyt osapuoli"]]}

                (if vesivayla?
                  {:nimi :sijainti
                   :otsikko "Sijainti"
                   :tyyppi :sijaintivalitsin
                   :pakollinen? true
                   :karttavalinta-tehty-fn #(swap! laatupoikkeama assoc :sijainti %)}
                  {:tyyppi :tierekisteriosoite
                   :nimi :tr
                   :muokattava? (constantly muokattava?)
                   :sijainti (r/wrap (:sijainti @laatupoikkeama)
                               #(swap! laatupoikkeama assoc :sijainti %))})

                (when-not (= :urakoitsija (:tekija @laatupoikkeama))
                  {:nimi :selvitys-pyydetty
                   :tyyppi :checkbox
                   :teksti "Urakoitsijan selvitystä pyydetään"})

                {:nimi :sisaltaa-poikkeamaraportin?
                 :tyyppi :checkbox
                 :teksti "Sisältää poikkeamaraportin"}

                {:otsikko "Kuvaus"
                 :uusi-rivi? true
                 :nimi :kuvaus
                 :muokattava? (constantly muokattava?)
                 :tyyppi :text
                 :pakollinen? true
                 :palstoja 2
                 :validoi [[:ei-tyhja "Kirjoita kuvaus"]] :pituus-max 4096
                 :placeholder "Kirjoita kuvaus..." :koko [80 :auto]}



                {:otsikko "Liitteet" :nimi :liitteet
                 :palstoja 2
                 :tyyppi :komponentti
                 :komponentti
                 (fn [{:keys [muokkaa-lomaketta]}]
                   [liitteet/liitteet-ja-lisays urakka-id (:liitteet @laatupoikkeama)
                    {:uusi-liite-atom (r/wrap (:uudet-liitteet @laatupoikkeama)
                                        (fn [uusi-liite]
                                          (if (:uudet-liitteet @laatupoikkeama)
                                            (swap! laatupoikkeama update :uudet-liitteet conj uusi-liite)
                                            (swap! laatupoikkeama assoc :uudet-liitteet [uusi-liite]))
                                          (muokkaa-lomaketta @laatupoikkeama)))
                     :uusi-liite-teksti "Lisää liite laatupoikkeamaan"
                     :salli-poistaa-lisatty-liite? true
                     :poista-lisatty-liite-fn (fn [liite-id]
                                                (swap! laatupoikkeama update :uudet-liitteet
                                                  (fn [uudet] (vec (remove #(= (:id %) liite-id) uudet))))
                                                (muokkaa-lomaketta @laatupoikkeama))
                     :salli-poistaa-tallennettu-liite? true
                     :lisaa-usea-liite? true
                     :poista-tallennettu-liite-fn
                     (fn [liite-id]
                       (liitteet/poista-liite-kannasta
                         {:urakka-id urakka-id
                          :domain :laatupoikkeama
                          :domain-id (:id @laatupoikkeama)
                          :liite-id liite-id
                          :poistettu-fn (fn []
                                          (swap! laatupoikkeama assoc :liitteet
                                            (filter (fn [liite]
                                                      (not= (:id liite) liite-id))
                                              (:liitteet @laatupoikkeama)))
                                          (muokkaa-lomaketta @laatupoikkeama))}))}])}
                (when-not uusi?
                  (lomake/ryhma
                    "Kommentit"
                    {:otsikko "" :nimi :uusi-kommentti :tyyppi :komponentti
                     :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                                    [kommentit/kommentit
                                     {:voi-kommentoida? true
                                      :voi-liittaa? true
                                      :salli-poistaa-lisatty-liite? true
                                      :salli-poistaa-tallennettu-liite? true
                                      :poista-tallennettu-liite-fn
                                      (fn [liite-id]
                                        (let [liitteen-kommentti (kommentti/liitteen-kommentti
                                                                   (:kommentit @laatupoikkeama)
                                                                   liite-id)
                                              kommentit-ilman-poistettua-liitetta
                                              (map (fn [kommentti]
                                                     (if (= (get-in kommentti [:liite :id]) liite-id)
                                                       (dissoc kommentti :liite)
                                                       kommentti))
                                                (:kommentit @laatupoikkeama))]
                                          (liitteet/poista-liite-kannasta
                                            {:urakka-id urakka-id
                                             :domain :laatupoikkeama-kommentti-liite
                                             :domain-id (:id @laatupoikkeama)
                                             :liite-id liite-id
                                             :poistettu-fn (fn []
                                                             (swap! laatupoikkeama assoc :kommentit
                                                               kommentit-ilman-poistettua-liitetta))})))
                                      :liita-nappi-teksti "Lisää liite kommenttiin"
                                      :placeholder "Kirjoita kommentti..."
                                      :uusi-kommentti (r/wrap (:uusi-kommentti @laatupoikkeama)
                                                        #(muokkaa-lomaketta (assoc data :uusi-kommentti %)))}
                                     (:kommentit @laatupoikkeama)])}))

                ;; Päätös
                (when (:id @laatupoikkeama)
                  (lomake/ryhma
                    "Käsittely ja päätös"

                    (sanktion-validointi
                      {:otsikko "Käsittelyn pvm"
                       :nimi :paatos-pvm
                       ::lomake/col-luokka "col-xs-4"
                       :hae (comp :kasittelyaika :paatos)
                       :aseta #(assoc-in %1 [:paatos :kasittelyaika] %2)
                       :tyyppi :pvm
                       :muokattava? (constantly (and muokattava? paatosoikeus?))}
                      "Anna käsittelyn päivämäärä")

                    (sanktion-validointi
                      {:otsikko "Käsitelty"
                       :nimi :kasittelytapa
                       ::lomake/col-luokka "col-xs-4"
                       :hae (comp :kasittelytapa :paatos)
                       :aseta #(assoc-in %1 [:paatos :kasittelytapa] %2)
                       :tyyppi :valinta
                       :valinnat [:valikatselmus :tyomaakokous :puhelin :kommentit :muu]
                       :valinta-nayta #(if % (laatupoikkeamat/kuvaile-kasittelytapa %)
                                         (if paatosoikeus?
                                           "- Valitse käsittelytapa -"
                                           ""))
                       :palstoja 2
                       :muokattava? (constantly (and muokattava? paatosoikeus?))}
                      "Anna käsittelytapa")

                    (when (= :muu (:kasittelytapa (:paatos @laatupoikkeama)))
                      {:otsikko "Muu käsittelytapa"
                       :nimi :kasittelytapa-selite
                       ::lomake/col-luokka "col-xs-4"
                       :hae (comp :muukasittelytapa :paatos)
                       :aseta #(assoc-in %1 [:paatos :muukasittelytapa] %2)
                       :tyyppi :string
                       :palstoja 2
                       :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]
                       :muokattava? (constantly (and muokattava? paatosoikeus?))})


                    (sanktion-validointi
                      {:otsikko "Päätös"
                       :nimi :paatos-paatos
                       ::lomake/col-luokka "col-xs-4"
                       :tyyppi :valinta
                       :valinnat [:sanktio :ei_sanktiota :hylatty]
                       :hae (comp :paatos :paatos)
                       :aseta (fn [rivi arvo]
                                (let [paivitetty (assoc-in rivi [:paatos :paatos] arvo)]
                                  (if (sanktio-domain/paatos-on-sanktio? paivitetty)
                                    paivitetty
                                    (assoc paivitetty :sanktiot nil))))
                       :valinta-nayta #(if % (laatupoikkeamat/kuvaile-paatostyyppi %)
                                         (if paatosoikeus?
                                           "- Valitse päätös -"
                                           ""))
                       :palstoja 2
                       :muokattava? (constantly (and muokattava? paatosoikeus?))}
                      "Anna päätös")

                    (when (:paatos (:paatos @laatupoikkeama))
                      {:otsikko (if (>= urakan-alkuvuosi 2025) "Perustelu" "Päätöksen selitys")
                       :nimi :paatoksen-selitys
                       :pakollinen? true
                       :tyyppi :text
                       :hae (comp :perustelu :paatos)
                       :koko [80 :auto]
                       :palstoja 2
                       :aseta #(assoc-in %1 [:paatos :perustelu] %2)
                       :muokattava? (constantly (and muokattava? paatosoikeus?))
                       :validoi [[:ei-tyhja "Anna päätöksen selitys"]]})


                    (when (sanktio-domain/paatos-on-sanktio? @laatupoikkeama)
                      {:otsikko ""
                       :nimi :sanktiot
                       :tyyppi :komponentti
                       :palstoja 3
                       :komponentti (fn [_]
                                      [laatupoikkeaman-sanktiot
                                       (r/cursor laatupoikkeama [:sanktiot])
                                       paatosoikeus?
                                       laatupoikkeama
                                       muokattava? optiot])})
                    (when (nayta-siirtymisnappi? @laatupoikkeama)
                      {:rivi? true
                       :uusi-rivi? true
                       :nimi :laatupoikkeama
                       :vihje (siirtymisnapin-vihje @laatupoikkeama)
                       :tyyppi :komponentti
                       :komponentti (fn [_]
                                      [napit/yleinen-toissijainen
                                       (siirtymisnapin-teksti @laatupoikkeama)
                                       (fn []
                                         (tallenna-laatupoikkeama @laatupoikkeama nakyma)
                                         (avaa-tarkastus (:tarkastusid @laatupoikkeama)))
                                       {:ikoni (ikonit/livicon-arrow-left)}])})))]
               @laatupoikkeama]
              [debug/debug @laatupoikkeama]])))))))
